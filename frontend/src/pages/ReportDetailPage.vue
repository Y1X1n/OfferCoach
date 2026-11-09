<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ArrowLeft, FileText, ListChecks, Loader2, Printer, Radar, RotateCcw } from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import AsyncState from '@/components/common/AsyncState.vue'
import RadarChart from '@/components/charts/RadarChart.vue'
import ScoreRing from '@/components/charts/ScoreRing.vue'
import DimensionBars from '@/components/report/DimensionBars.vue'
import ImprovementPlanList from '@/components/report/ImprovementPlanList.vue'
import ReportInsightCards from '@/components/report/ReportInsightCards.vue'
import ReportQuestionItem from '@/components/report/ReportQuestionItem.vue'
import Alert from '@/components/ui/Alert.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import Spinner from '@/components/ui/Spinner.vue'
import { ApiError, errorMessage } from '@/api/client'
import * as reportsApi from '@/api/reports'
import { usePolling } from '@/composables/usePolling'
import { useToast } from '@/composables/useToast'
import type { Report } from '@/types'
import { formatDate, formatScore } from '@/utils/format'
import { buildReportFileName } from '@/utils/reportExport'

const route = useRoute()
const { toast } = useToast()

const reportId = computed(() => {
  const value = route.params.id
  return Array.isArray(value) ? (value[0] ?? '') : (value ?? '')
})

const report = ref<Report | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const notFound = ref(false)
const retrying = ref(false)

const poller = usePolling(async () => {
  try {
    const next = await reportsApi.getReport(reportId.value)
    report.value = next
    if (next.status === 'GENERATING') return true
    if (next.status === 'READY') toast('报告已生成', 'success')
    return false
  } catch (cause) {
    // 404 说明报告不存在；其他瞬时错误继续轮询，避免一次断网就永久停在“生成中”。
    return !(cause instanceof ApiError && cause.status === 404)
  }
}, 2000)

const scoreVerdict = computed(() => {
  const score = report.value?.overallScore ?? 0
  if (score >= 85) return '表现稳定，继续保持'
  if (score >= 70) return '整体良好，仍有提升空间'
  if (score >= 60) return '基础完整，建议针对性补强'
  return '建议先打磨结论与结构'
})

async function loadReport(): Promise<void> {
  loading.value = true
  error.value = null
  notFound.value = false
  try {
    const next = await reportsApi.getReport(reportId.value)
    report.value = next
    if (next.status === 'GENERATING') poller.start()
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === 404) notFound.value = true
    else error.value = errorMessage(cause, '报告加载失败')
  } finally {
    loading.value = false
  }
}

async function retryGenerate(): Promise<void> {
  if (retrying.value || !report.value) return
  retrying.value = true
  try {
    await reportsApi.retryReport(report.value.id)
    if (report.value) report.value.status = 'GENERATING'
    poller.start()
    toast('已提交重试', 'info', '报告已回到生成中，不重复扣额度')
  } catch (cause) {
    toast('重试失败', 'error', errorMessage(cause))
  } finally {
    retrying.value = false
  }
}

function onPrintReport(): void {
  const target = report.value
  if (!target) return
  const previousTitle = document.title
  const wasDark = document.documentElement.classList.contains('dark')
  const details = Array.from(document.querySelectorAll<HTMLDetailsElement>('main details'))
  const openStates = details.map((item) => item.open)
  document.title = buildReportFileName(target.company, target.role, target.generatedAt)
  document.documentElement.classList.remove('dark')
  details.forEach((item) => {
    item.open = true
  })
  let restored = false
  const restore = () => {
    if (restored) return
    restored = true
    document.title = previousTitle
    document.documentElement.classList.toggle('dark', wasDark)
    details.forEach((item, index) => {
      item.open = openStates[index] ?? false
    })
    window.removeEventListener('afterprint', restore)
  }
  window.addEventListener('afterprint', restore)
  window.print()
  window.setTimeout(restore, 1000)
}

onMounted(() => {
  void loadReport()
})

onBeforeUnmount(() => {
  poller.stop()
})
</script>

<template>
  <div class="animate-fade-up">
    <PageHeader title="面试报告" description="逐题证据、五维能力与下一步改进计划，都可以带走复盘。">
      <template #actions>
        <Button v-if="report?.status === 'READY'" variant="secondary" class="print:hidden" @click="onPrintReport">
          <Printer class="h-4 w-4" aria-hidden="true" />
          打印 / 存为 PDF
        </Button>
        <RouterLink
          to="/reports"
          class="inline-flex h-10 items-center justify-center gap-2 rounded-md border border-border bg-transparent px-4 text-sm font-medium text-text transition-colors hover:bg-surface-2 print:hidden"
        >
          <ArrowLeft class="h-4 w-4" aria-hidden="true" />
          返回报告列表
        </RouterLink>
      </template>
    </PageHeader>

    <AsyncState
      :loading="loading"
      :error="error"
      error-title="报告加载失败"
      :loading-lines="6"
      class="mt-5"
      @retry="loadReport"
    >
      <Card v-if="notFound" padding="lg" class="mt-1">
        <div class="flex flex-col items-center py-6 text-center">
          <span class="flex h-12 w-12 items-center justify-center rounded-full bg-surface-2 text-text-muted">
            <FileText class="h-5 w-5" aria-hidden="true" />
          </span>
          <h2 class="mt-4 text-sm font-semibold text-text">没有找到这份报告</h2>
          <p class="mt-1 max-w-sm text-sm text-text-muted">
            报告可能已被删除，或链接中的编号不正确。可以返回列表查看全部报告。
          </p>
          <RouterLink
            to="/reports"
            class="mt-5 inline-flex h-10 items-center justify-center gap-2 rounded-md bg-brand px-4 text-sm font-medium text-brand-foreground"
          >
            <ArrowLeft class="h-4 w-4" aria-hidden="true" />
            返回报告列表
          </RouterLink>
        </div>
      </Card>

      <Card v-else-if="!report" padding="lg" class="mt-1">
        <Alert variant="info" title="报告暂不可用" description="可以稍后刷新页面再试。" />
      </Card>

      <Card v-else-if="report.status === 'GENERATING'" padding="lg" class="mt-1">
        <div class="flex flex-col items-center py-10 text-center">
          <span class="flex h-12 w-12 items-center justify-center rounded-full bg-brand/10 text-brand">
            <Loader2 class="h-5 w-5 animate-spin" aria-hidden="true" />
          </span>
          <h2 class="mt-4 text-base font-semibold text-text">报告生成中</h2>
          <p class="mt-1 max-w-md text-sm leading-6 text-text-muted">
            正在汇总逐题评分与改进计划，通常需要 20 秒左右；完成后会自动展示。
          </p>
          <Spinner class="mt-4" size="sm" label="生成中" />
        </div>
      </Card>

      <template v-else-if="report.status === 'FAILED'">
        <Alert
          variant="danger"
          title="报告生成失败"
          description="重试不会重复扣额度，失败后可由你手动重新发起生成。"
          class="mt-1"
        >
          <Button variant="outline" size="sm" class="mt-3" :loading="retrying" @click="retryGenerate">
            <RotateCcw class="h-3.5 w-3.5" aria-hidden="true" />
            重试生成
          </Button>
        </Alert>
      </template>

      <template v-else>
        <Card padding="none" class="mt-5 overflow-hidden print:mt-2 print:border-0 print:shadow-none">
          <div class="bg-gradient-to-br from-brand/5 via-surface to-accent/10 p-5 sm:p-6 lg:p-8">
            <div class="flex flex-col gap-6 lg:flex-row lg:items-center lg:gap-8">
              <div class="flex justify-center lg:block">
                <ScoreRing
                  :score="report.overallScore ?? 0"
                  :size="160"
                  label="总分"
                  :sublabel="scoreVerdict"
                />
              </div>

              <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-2">
                  <Badge variant="brand">模拟面试</Badge>
                  <Badge variant="neutral">报告 #{{ report.id }}</Badge>
                  <span class="tabular text-xs text-text-muted">
                    {{ formatDate(report.generatedAt, true) }}
                  </span>
                </div>

                <h2 class="mt-2 text-xl font-semibold tracking-tight text-text sm:text-2xl">
                  本场面试总评
                </h2>
                <p class="mt-2 max-w-2xl text-sm leading-6 text-text-muted">{{ report.summary }}</p>

                <dl class="mt-4 grid max-w-md grid-cols-3 gap-3">
                  <div class="rounded-md border border-border bg-surface/70 p-3">
                    <dt class="text-xs text-text-muted">覆盖题目</dt>
                    <dd class="tabular mt-1 text-lg font-semibold text-text">
                      {{ report.breakdown.length }}
                    </dd>
                  </div>
                  <div class="rounded-md border border-border bg-surface/70 p-3">
                    <dt class="text-xs text-text-muted">表现亮点</dt>
                    <dd class="tabular mt-1 text-lg font-semibold text-text">
                      {{ report.highlights.length }}
                    </dd>
                  </div>
                  <div class="rounded-md border border-border bg-surface/70 p-3">
                    <dt class="text-xs text-text-muted">风险项</dt>
                    <dd class="tabular mt-1 text-lg font-semibold text-text">
                      {{ report.risks.length }}
                    </dd>
                  </div>
                </dl>
              </div>
            </div>
          </div>
        </Card>

        <div class="mt-4 grid gap-4 lg:grid-cols-5">
          <Card padding="lg" class="lg:col-span-3 print:border-0 print:shadow-none">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <h2 class="flex items-center gap-2 text-base font-semibold text-text">
                <Radar class="h-4 w-4 text-brand" aria-hidden="true" />
                五维能力
              </h2>
              <span class="text-xs text-text-muted">单项满分 10 分</span>
            </div>

            <div class="mt-4 grid items-center gap-6 sm:grid-cols-2">
              <RadarChart v-if="report.radar.length" :items="report.radar" :size="260" />
              <div
                v-else
                class="flex h-48 items-center justify-center rounded-md border border-dashed border-border bg-surface-2/40 px-4 text-center text-sm text-text-muted"
              >
                本次没有生成雷达图数据。
              </div>
              <DimensionBars :items="report.radar" />
            </div>
          </Card>

          <div class="lg:col-span-2">
            <ReportInsightCards :highlights="report.highlights" :risks="report.risks" />
          </div>
        </div>

        <div class="mt-4">
          <ImprovementPlanList :items="report.improvementPlan" />
        </div>

        <section class="mt-4 print:mt-2" aria-labelledby="report-breakdown-title">
          <Card padding="lg" class="print:border-0 print:shadow-none">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <h2
                id="report-breakdown-title"
                class="flex items-center gap-2 text-base font-semibold text-text"
              >
                <ListChecks class="h-4 w-4 text-brand" aria-hidden="true" />
                逐题拆解
              </h2>
              <div class="flex items-center gap-2 text-xs text-text-muted">
                <Badge variant="neutral">{{ report.breakdown.length }} 题</Badge>
                <span>展开查看回答、证据与建议</span>
              </div>
            </div>

            <div v-if="report.breakdown.length" class="mt-4 space-y-2.5">
              <ReportQuestionItem
                v-for="(item, index) in report.breakdown"
                :key="item.questionId"
                :item="item"
                :open="index === 0"
              />
            </div>
            <p v-else class="mt-4 text-sm leading-6 text-text-muted">
              这份报告还没有逐题记录，可以重新完成一场模拟面试获取完整拆解。
            </p>
          </Card>
        </section>

        <p v-if="report.overallScore !== null" class="sr-only">
          报告总分 {{ formatScore(report.overallScore) }}。
        </p>
      </template>
    </AsyncState>
  </div>
</template>