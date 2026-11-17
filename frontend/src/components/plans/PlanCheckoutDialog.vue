<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { CircleCheck, Copy, ExternalLink } from 'lucide-vue-next'
import * as QRCode from 'qrcode'
import Alert from '@/components/ui/Alert.vue'
import Button from '@/components/ui/Button.vue'
import Dialog from '@/components/ui/Dialog.vue'
import Spinner from '@/components/ui/Spinner.vue'
import { errorMessage } from '@/api/client'
import * as ordersApi from '@/api/orders'
import type { Order, PayChannel, PayChannelCode, Plan } from '@/types'
import { formatCents } from '@/utils/format'

const props = defineProps<{ open: boolean; plan: Plan | null }>()
const emit = defineEmits<{ 'update:open': [boolean]; paid: [] }>()

const CHANNEL_LABELS: Record<PayChannelCode, string> = {
  wechat: '微信支付',
  alipay: '支付宝',
  mock: '模拟支付（本地联调）',
}
const POLL_INTERVAL_MS = 2500
const MAX_POLL_MS = 5 * 60 * 1000

type Phase = 'loading' | 'select' | 'paying' | 'success' | 'failed'

const phase = ref<Phase>('loading')
const channels = ref<PayChannel[]>([])
const channel = ref<PayChannelCode | ''>('')
const order = ref<Order | null>(null)
const qrDataUrl = ref('')
const message = ref('')
const copied = ref(false)
let pollTimer: number | null = null
let pollStartedAt = 0

const enabledChannels = computed(() => channels.value.filter((item) => item.enabled))
const selected = computed(
  () => channels.value.find((item) => item.channel === channel.value) ?? null,
)
const isMock = computed(() => selected.value?.channel === 'mock')
const amountLabel = computed(() => (props.plan ? formatCents(props.plan.priceCents) : ''))

watch(
  () => props.open,
  (open) => {
    if (open) void begin()
    else stopPolling()
  },
)

onBeforeUnmount(stopPolling)

async function begin(): Promise<void> {
  stopPolling()
  channels.value = []
  channel.value = ''
  order.value = null
  qrDataUrl.value = ''
  message.value = ''
  copied.value = false
  if (!props.plan) {
    phase.value = 'failed'
    message.value = '未选择套餐'
    return
  }
  phase.value = 'loading'
  try {
    channels.value = await ordersApi.listPayChannels()
    const first = enabledChannels.value[0]
    if (!first) {
      phase.value = 'failed'
      message.value = '当前没有可用的支付方式，请稍后再试'
      return
    }
    channel.value = first.channel
    phase.value = 'select'
  } catch (cause) {
    phase.value = 'failed'
    message.value = errorMessage(cause, '支付方式加载失败')
  }
}

function createIdempotencyKey(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `order_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`
}

async function pay(): Promise<void> {
  if (!props.plan || !channel.value) return
  phase.value = 'paying'
  message.value = ''
  try {
    const created = await ordersApi.createOrder(
      { planCode: props.plan.code, channel: channel.value },
      createIdempotencyKey(),
    )
    order.value = created
    await renderQr(created.codeUrl)
    startPolling()
  } catch (cause) {
    phase.value = 'failed'
    message.value = errorMessage(cause, '下单失败，请重试')
  }
}

async function renderQr(codeUrl: string | null): Promise<void> {
  if (!codeUrl) {
    qrDataUrl.value = ''
    return
  }
  try {
    qrDataUrl.value = await QRCode.toDataURL(codeUrl, {
      margin: 1,
      width: 220,
      color: { dark: '#111827', light: '#ffffff' },
    })
  } catch {
    qrDataUrl.value = ''
  }
}

function startPolling(): void {
  stopPolling()
  pollStartedAt = Date.now()
  pollTimer = window.setInterval(() => void poll(), POLL_INTERVAL_MS)
}

async function poll(): Promise<void> {
  if (!order.value) return
  if (Date.now() - pollStartedAt > MAX_POLL_MS) {
    stopPolling()
    phase.value = 'failed'
    message.value = '支付超时，请重新下单'
    return
  }
  try {
    const latest = await ordersApi.getOrder(order.value.orderNo)
    order.value = latest
    if (latest.status === 'PAID') {
      stopPolling()
      phase.value = 'success'
      emit('paid')
      return
    }
    if (latest.status === 'CLOSED') {
      stopPolling()
      phase.value = 'failed'
      message.value = '订单已关闭或过期，请重新下单'
      return
    }
    if (latest.status === 'FAILED') {
      stopPolling()
      phase.value = 'failed'
      message.value = '支付失败，请重新下单'
      return
    }
    if (latest.status === 'REFUNDED') {
      stopPolling()
      phase.value = 'failed'
      message.value = '该订单已退款'
      return
    }
    if (latest.expiresAt && new Date(latest.expiresAt).getTime() < Date.now()) {
      stopPolling()
      phase.value = 'failed'
      message.value = '二维码已过期，请重新下单'
    }
  } catch {
    // 轮询期间网络抖动不打断，等待下一次轮询
  }
}

function stopPolling(): void {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

function openMockPay(): void {
  if (order.value?.codeUrl) {
    window.open(order.value.codeUrl, '_blank', 'noopener')
  }
}

async function copyCodeUrl(): Promise<void> {
  if (!order.value?.codeUrl) return
  try {
    await navigator.clipboard.writeText(order.value.codeUrl)
    copied.value = true
    window.setTimeout(() => (copied.value = false), 2000)
  } catch {
    copied.value = false
  }
}

function backToSelect(): void {
  stopPolling()
  order.value = null
  qrDataUrl.value = ''
  phase.value = 'select'
}

function close(): void {
  stopPolling()
  emit('update:open', false)
}
</script>

<template>
  <Dialog
    :open="open"
    :title="plan ? `升级到${plan.name}` : '套餐支付'"
    :description="amountLabel ? `应付 ${amountLabel}，支付完成后额度立即生效` : undefined"
    @update:open="emit('update:open', $event)"
  >
    <div v-if="phase === 'loading'" class="flex justify-center py-10">
      <Spinner label="正在获取支付方式…" />
    </div>

    <div v-else-if="phase === 'select'" class="space-y-3">
      <p class="text-sm text-text-muted">选择支付方式</p>
      <div class="grid gap-2">
        <button
          v-for="item in enabledChannels"
          :key="item.channel"
          type="button"
          :class="[
            'flex items-center justify-between rounded-md border px-4 py-3 text-left transition-colors',
            channel === item.channel
              ? 'border-accent/60 bg-surface-2'
              : 'border-border hover:border-text/25 hover:bg-surface-2',
          ]"
          @click="channel = item.channel"
        >
          <span class="text-sm font-medium text-text">{{ CHANNEL_LABELS[item.channel] }}</span>
          <span v-if="item.channel === 'mock'" class="text-2xs text-text-muted"
            >仅本地联调，不产生真实扣费</span
          >
        </button>
      </div>
      <Button block :disabled="!channel" @click="pay">立即支付 {{ amountLabel }}</Button>
    </div>

    <div v-else-if="phase === 'paying'" class="flex flex-col items-center gap-4 py-2">
      <template v-if="qrDataUrl">
        <img :src="qrDataUrl" alt="支付二维码" class="h-52 w-52 rounded-md border border-border" />
        <p class="text-sm text-text-muted">
          {{
            isMock ? '本地联调：打开下方链接即视为支付成功' : '请使用对应 App 扫描二维码完成支付'
          }}
        </p>
      </template>
      <Spinner v-else label="正在生成支付二维码…" />
      <div v-if="isMock && order?.codeUrl" class="flex flex-wrap justify-center gap-2">
        <Button variant="outline" @click="openMockPay">
          <ExternalLink class="h-4 w-4" aria-hidden="true" />打开模拟支付页
        </Button>
        <Button variant="ghost" @click="copyCodeUrl">
          <Copy class="h-4 w-4" aria-hidden="true" />{{ copied ? '已复制' : '复制链接' }}
        </Button>
      </div>
      <p class="text-xs text-text-muted">支付完成后本页会自动刷新，无需手动操作。</p>
      <button type="button" class="text-xs text-text-muted underline" @click="backToSelect">
        重新选择支付方式
      </button>
    </div>

    <div v-else-if="phase === 'success'" class="flex flex-col items-center gap-3 py-6 text-center">
      <CircleCheck class="h-12 w-12 text-success" aria-hidden="true" />
      <p class="text-base font-semibold text-text">{{ plan?.name }}已开通</p>
      <p class="text-sm text-text-muted">额度与功能已生效，可前往工作台查看最新剩余次数。</p>
    </div>

    <div v-else class="space-y-3">
      <Alert variant="warning" title="未能完成支付" :description="message" />
      <Button @click="begin">重试</Button>
    </div>

    <template #footer>
      <Button v-if="phase === 'paying'" variant="ghost" @click="close">暂不支付</Button>
      <Button
        v-else-if="phase === 'success' || phase === 'failed'"
        variant="secondary"
        @click="close"
      >
        完成
      </Button>
    </template>
  </Dialog>
</template>
