<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import { Check, CreditCard, RefreshCw, ShieldCheck, Sparkles, X } from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import AsyncState from '@/components/common/AsyncState.vue'
import Alert from '@/components/ui/Alert.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import { errorMessage } from '@/api/client'
import * as plansApi from '@/api/plans'
import { useToast } from '@/composables/useToast'
import { useAppStore } from '@/stores/app'
import type { Plan } from '@/types'
import { formatCents } from '@/utils/format'
import { cn } from '@/utils/cn'

type FeatureState = 'included' | 'excluded' | 'coming'

interface FeatureRow {
  label: string
  state: FeatureState
  note?: string
}

interface FaqItem {
  icon: Component
  title: string
  body: string
}

const store = useAppStore()
const { toast } = useToast()

const plans = ref<Plan[]>([])
const loading = ref(true)
const loadError = ref<string | null>(null)

const FEATURE_LABELS: Record<string, string> = {
  rewrittenAnswer: '建议改写',
  pdfExport: 'PDF 报告导出',
  priorityQueue: '优先生成队列',
  questionTargeting: '岗位针对性出题',
  fiveDimension: '逐题五维评分',
  resumeParse: '简历结构化解析',
  jdMatch: 'JD 匹配分析',
}

const isPro = computed(() => store.quota.planCode === 'PRO')

function isCurrent(plan: Plan): boolean {
  return store.quota.planCode === plan.code
}

function planFeatures(plan: Plan): FeatureRow[] {
  const features = plan.features ?? {}
  const keys = Object.keys(features)
  if (keys.length === 0) {
    return [{ label: '套餐能力以服务端额度与功能开关为准', state: 'coming' }]
  }
  return keys.map((key) => {
    const value = features[key]
    const label = FEATURE_LABELS[key] ?? key
    if (value === true) return { label, state: 'included' }
    if (value === false) return { label, state: 'excluded' }
    return { label, state: 'coming', note: typeof value === 'string' ? value : '即将开放' }
  })
}

function planPrice(plan: Plan): string {
  if (plan.priceCents <= 0) return '免费'
  return formatCents(plan.priceCents)
}

function planPeriod(plan: Plan): string {
  if (plan.priceCents <= 0) return ''
  if (plan.period) return `/ ${plan.period}`
  if (plan.periodDays) return `/ ${plan.periodDays} 天`
  return ''
}

function planCredits(plan: Plan): string {
  const interview = plan.interviewCredits ?? 0
  const resume = plan.resumeCredits ?? 0
  if (interview === 0 && resume === 0) return '额度以账户页为准'
  return `每月 ${interview} 次面试 + ${resume} 次简历解析`
}

function planDescription(plan: Plan): string {
  if (plan.description) return plan.description
  return plan.code === 'PRO'
    ? '适合密集求职期：多岗位练习、完整报告与建议改写。'
    : '先体验完整流程，看看模拟面试与反馈是否适合你的岗位。'
}

function onSelect(plan: Plan) {
  toast(
    '在线支付即将开放',
    'info',
    `${plan.name}的在线支付与套餐切换即将开放；当前升级入口不会产生扣费。`,
  )
}

const faqItems: FaqItem[] = [
  {
    icon: CreditCard,
    title: '额度如何扣减',
    body: '创建面试会话时预扣 1 次面试额度，上传简历时预扣 1 次解析额度。单题评分不单独扣额度，报告生成也不额外扣费。',
  },
  {
    icon: RefreshCw,
    title: '失败会返还吗',
    body: '出题失败、简历解析失败会自动返还对应额度。未提交任何回答前主动放弃会话，全额返还面试额度；已作答则不再返还。',
  },
  {
    icon: ShieldCheck,
    title: '报告重试是否扣费',
    body: '报告生成失败可手动重试，重试不扣额度，每份报告最多 3 次。已生成的报告可重复查看，不重复计费。',
  },
  {
    icon: Sparkles,
    title: '免费版有哪些限制',
    body: '免费版每天最多创建 3 个面试会话，无论剩余额度是否充足。面试与解析额度按自然月重置。',
  },
]

async function loadPlans(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    plans.value = await plansApi.listPlans()
    await store.loadQuota()
  } catch (cause) {
    loadError.value = errorMessage(cause, '套餐加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadPlans()
})
</script>

<template>
  <div>
    <PageHeader
      title="套餐与额度"
      description="按求职节奏选择套餐。在线支付开放前，套餐变更由运营在后台配置。"
    >
      <template #actions>
        <span class="text-xs text-text-muted">
          当前 {{ store.quota.planName }} · 剩余
          <span class="figure text-text">{{ store.quota.interviewCredits }}</span> 次面试 ·
          <span class="figure text-text">{{ store.quota.resumeCredits }}</span> 次解析
        </span>
      </template>
    </PageHeader>

    <AsyncState
      :loading="loading"
      :error="loadError"
      error-title="套餐加载失败"
      :loading-lines="6"
      class="mt-6"
      @retry="loadPlans"
    >
      <section class="grid gap-4 md:grid-cols-2 md:items-stretch" aria-label="套餐对比">
        <Card
          v-for="plan in plans"
          :key="plan.code"
          padding="lg"
          :class="
            cn(
              'relative flex flex-col',
              (plan.recommended ?? plan.code === 'PRO') && 'border-text/25',
            )
          "
        >
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <div class="flex flex-wrap items-center gap-2">
                <h2 class="text-lg font-semibold text-text">{{ plan.name }}</h2>
                <Badge v-if="plan.recommended ?? plan.code === 'PRO'" variant="brand">推荐</Badge>
                <Badge v-if="isCurrent(plan)" variant="success" dot>当前</Badge>
              </div>
              <p class="mt-2 text-sm leading-6 text-text-muted">{{ planDescription(plan) }}</p>
            </div>
          </div>

          <div class="mt-5 flex flex-wrap items-end gap-x-2 gap-y-1">
            <span class="tabular text-3xl font-semibold text-text">{{ planPrice(plan) }}</span>
            <span v-if="planPeriod(plan)" class="pb-1 text-xs text-text-muted">
              {{ planPeriod(plan) }}
            </span>
          </div>
          <p class="mt-1 text-xs text-text-muted">{{ planCredits(plan) }}</p>

          <ul class="mt-5 flex-1 space-y-2.5 border-t border-border pt-4 text-sm">
            <li v-for="row in planFeatures(plan)" :key="row.label" class="flex items-start gap-2">
              <Check
                v-if="row.state === 'included'"
                class="mt-0.5 h-4 w-4 shrink-0 text-success"
                aria-hidden="true"
              />
              <X
                v-else-if="row.state === 'excluded'"
                class="mt-0.5 h-4 w-4 shrink-0 text-text-muted"
                aria-hidden="true"
              />
              <Check v-else class="mt-0.5 h-4 w-4 shrink-0 text-warning" aria-hidden="true" />
              <span
                :class="[
                  'min-w-0 flex-1',
                  row.state === 'excluded' ? 'text-text-muted' : 'text-text',
                ]"
              >
                {{ row.label }}
              </span>
              <span v-if="row.state === 'coming' && row.note" class="text-2xs text-text-muted">
                {{ row.note }}
              </span>
            </li>
          </ul>

          <div class="mt-5">
            <Button
              :variant="isCurrent(plan) ? 'secondary' : plan.code === 'PRO' ? 'primary' : 'outline'"
              block
              :disabled="isCurrent(plan) || (plan.code === 'FREE' && isPro)"
              @click="onSelect(plan)"
            >
              {{
                isCurrent(plan)
                  ? '当前套餐'
                  : plan.code === 'PRO'
                    ? '升级到专业版'
                    : '降级暂未开放'
              }}
            </Button>
          </div>
        </Card>
      </section>

      <section class="mt-8" aria-labelledby="plans-faq-title">
        <div class="flex flex-wrap items-end justify-between gap-2">
          <div>
            <h2 id="plans-faq-title" class="text-base font-semibold text-text">额度与计费说明</h2>
            <p class="mt-1 text-sm text-text-muted">规则以服务端额度流水与套餐配置为准。</p>
          </div>
          <Badge variant="neutral" size="md">在线支付即将开放</Badge>
        </div>

        <div class="mt-4 grid gap-3 md:grid-cols-2">
          <Card v-for="item in faqItems" :key="item.title" padding="sm" class="flex gap-3">
            <span
              class="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-surface-2 text-text-muted"
            >
              <component :is="item.icon" class="h-4 w-4" aria-hidden="true" />
            </span>
            <div class="min-w-0">
              <h3 class="text-sm font-semibold text-text">{{ item.title }}</h3>
              <p class="mt-1 text-sm leading-6 text-text-muted">{{ item.body }}</p>
            </div>
          </Card>
        </div>

        <Alert
          variant="info"
          class="mt-4"
          title="关于套餐变更"
          description="当前在线支付暂未开放，升级按钮不会发起支付；开通后会以服务端订单与额度流水为准。套餐说明不构成效果承诺。"
        />
      </section>
    </AsyncState>
  </div>
</template>