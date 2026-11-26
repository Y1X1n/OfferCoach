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
import { useToast } from '@/composables/useToast'
import type { Order, PayChannel, PayChannelCode, Plan } from '@/types'
import { formatCents } from '@/utils/format'

const props = defineProps<{ open: boolean; plan: Plan | null }>()
const emit = defineEmits<{ 'update:open': [boolean]; paid: [] }>()
const { toast } = useToast()

const CHANNEL_LABELS: Record<PayChannelCode, string> = {
  wechat: '微信支付',
  alipay: '支付宝',
  mock: '模拟支付（本地联调）',
}
const POLL_INTERVAL_MS = 2500

type Phase = 'loading' | 'select' | 'paying' | 'success' | 'failed'

const phase = ref<Phase>('loading')
const channels = ref<PayChannel[]>([])
const channel = ref<PayChannelCode | ''>('')
const order = ref<Order | null>(null)
const qrDataUrl = ref('')
const message = ref('')
const copied = ref(false)
const creating = ref(false)
const pollPaused = ref(false)
const pollError = ref('')
let pollTimer: number | null = null
/** 首次下单确定的支付截止时间；轮询返回的 expiresAt 不覆盖它。 */
let deadlineAt = 0
let disposed = false
/** 重试期间复用的下单幂等键，以及它所属的「套餐:渠道」作用域。 */
let pendingOrderKey = ''
let pendingOrderScope = ''

const enabledChannels = computed(() => channels.value.filter((item) => item.enabled))
const selected = computed(
  () => channels.value.find((item) => item.channel === channel.value) ?? null,
)
const isMock = computed(() => selected.value?.channel === 'mock')
const amountLabel = computed(() => (props.plan ? formatCents(props.plan.priceCents) : ''))

watch(
  () => props.open,
  (open) => {
    if (open) {
      void begin()
      return
    }
    // 遮罩、Esc 等路径也走这里，确保关闭弹窗时旧订单被关闭
    const current = order.value
    resetPayState()
    void cancelPendingOrder(current, '关闭支付弹窗')
  },
)

onBeforeUnmount(() => {
  disposed = true
  stopPolling()
  void cancelPendingOrder(order.value, '离开页面')
})

async function begin(): Promise<void> {
  // 重新下单前先关掉上一笔未支付订单，避免旧二维码继续可付
  const current = order.value
  resetPayState()
  clearIdempotencyKey()
  void cancelPendingOrder(current, '重新下单')
  channels.value = []
  channel.value = ''
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

/**
 * 下单幂等键按「套餐 + 渠道」维度保持稳定。
 *
 * 每次点击都换新键时，若首次请求已在服务端建成订单但响应丢失（超时/断网），
 * 用户点「重试」会再建一笔订单，最终可能付两笔。保持同一个键则重试命中服务端幂等，
 * 拿回的是同一笔订单。
 */
function idempotencyKeyFor(planCode: string, channelCode: PayChannelCode): string {
  const scope = `${planCode}:${channelCode}`
  if (pendingOrderKey && pendingOrderScope === scope) return pendingOrderKey
  pendingOrderKey = createIdempotencyKey()
  pendingOrderScope = scope
  return pendingOrderKey
}

function clearIdempotencyKey(): void {
  pendingOrderKey = ''
  pendingOrderScope = ''
}

async function pay(): Promise<void> {
  if (!props.plan || !channel.value || creating.value) return
  const planCode = props.plan.code
  const channelCode = channel.value
  creating.value = true
  phase.value = 'paying'
  message.value = ''
  pollPaused.value = false
  pollError.value = ''
  try {
    const created = await ordersApi.createOrder(
      { planCode, channel: channelCode },
      idempotencyKeyFor(planCode, channelCode),
    )
    // 订单已落地，后续重试应视为新订单（旧单已在下面关单或已完成）。
    clearIdempotencyKey()
    // 下单期间弹窗已关闭、组件已卸载或已重新开始：立即关掉这笔订单
    if (disposed || !props.open || phase.value !== 'paying') {
      void cancelPendingOrder(created, '下单结果已失效')
      return
    }
    order.value = created
    // 截止时间只取首次下单的 expiresAt，后续轮询刷新不覆盖
    deadlineAt = ordersApi.orderDeadline(created)
    await renderQr(created.codeUrl)
    if (
      disposed ||
      !props.open ||
      phase.value !== 'paying' ||
      order.value?.orderNo !== created.orderNo
    ) {
      return
    }
    startPolling()
  } catch (cause) {
    // 已关闭或已重新开始的流程不再展示旧请求的错误
    if (disposed || !props.open || phase.value !== 'paying') return
    phase.value = 'failed'
    message.value = errorMessage(cause, '下单失败，请重试')
  } finally {
    creating.value = false
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
  pollTimer = window.setInterval(() => void poll(), POLL_INTERVAL_MS)
}

async function poll(): Promise<void> {
  const current = order.value
  if (!current || phase.value !== 'paying') return
  if (ordersApi.isOrderExpired(deadlineAt)) {
    expireOrder()
    return
  }
  try {
    const latest = await ordersApi.getOrder(current.orderNo)
    // 请求返回前订单可能已切换或重置，忽略过期响应
    if (phase.value !== 'paying' || order.value?.orderNo !== current.orderNo) return
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
      message.value = '订单已关闭，请重新下单'
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
    if (ordersApi.isOrderExpired(deadlineAt)) {
      expireOrder()
    }
  } catch (cause) {
    if (phase.value !== 'paying' || order.value?.orderNo !== current.orderNo) return
    // 网络抖动/超时不等于订单过期：暂停轮询，保留二维码，由用户决定是否继续刷新
    stopPolling()
    pollPaused.value = true
    pollError.value = errorMessage(cause, '订单状态查询失败，请检查网络后重试')
  }
}

function expireOrder(): void {
  stopPolling()
  pollPaused.value = false
  pollError.value = ''
  phase.value = 'failed'
  message.value = '订单已过期，请重新下单'
}

/** 轮询因网络异常暂停后，继续查询直到订单截止时间。 */
function resumePolling(): void {
  if (!order.value) return
  if (ordersApi.isOrderExpired(deadlineAt)) {
    expireOrder()
    return
  }
  pollPaused.value = false
  pollError.value = ''
  startPolling()
  void poll()
}

/**
 * best-effort 关闭未支付订单；失败不阻断 UI，仅轻提示，避免静默失败。
 */
async function cancelPendingOrder(target: Order | null, reason: string): Promise<void> {
  if (!target || !ordersApi.needsCancel(target.status)) return
  try {
    const latest = await ordersApi.cancelOrder(target.orderNo)
    if (disposed || latest.status !== 'PAID') return
    // 关单时发现订单已支付：同步成功态，避免用户对同一套餐再次下单
    emit('paid')
    if (props.open && !order.value) {
      order.value = latest
      phase.value = 'success'
    }
  } catch {
    toast('旧订单关闭失败', 'warning', `${reason}时未能关闭订单，该订单可能仍可支付。`)
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

/** 切换支付方式：若已存在未支付订单，先关闭它，避免两笔订单同时可付。 */
function chooseChannel(next: PayChannelCode): void {
  if (channel.value === next) return
  const current = order.value
  channel.value = next
  // 渠道变了就是另一笔订单，幂等键不能再复用。
  clearIdempotencyKey()
  if (!current) return
  resetPayState()
  phase.value = 'select'
  void cancelPendingOrder(current, '切换支付方式')
}

function resetPayState(): void {
  stopPolling()
  order.value = null
  qrDataUrl.value = ''
  deadlineAt = 0
  pollPaused.value = false
  pollError.value = ''
}

function backToSelect(): void {
  const current = order.value
  resetPayState()
  phase.value = 'select'
  void cancelPendingOrder(current, '重新选择支付方式')
}

function close(): void {
  const current = order.value
  resetPayState()
  emit('update:open', false)
  void cancelPendingOrder(current, '关闭支付弹窗')
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
          @click="chooseChannel(item.channel)"
        >
          <span class="text-sm font-medium text-text">{{ CHANNEL_LABELS[item.channel] }}</span>
          <span v-if="item.channel === 'mock'" class="text-2xs text-text-muted"
            >仅本地联调，不产生真实扣费</span
          >
        </button>
      </div>
      <Button block :disabled="!channel || creating" @click="pay">
        立即支付 {{ amountLabel }}
      </Button>
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
      <div v-if="pollPaused" class="flex flex-col items-center gap-2 text-center">
        <p class="text-xs text-warning">{{ pollError }}</p>
        <Button variant="outline" size="sm" @click="resumePolling">继续刷新</Button>
      </div>
      <p v-else class="text-xs text-text-muted">支付完成后本页会自动刷新，无需手动操作。</p>
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
