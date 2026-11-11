<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowRight, FileText, Plus, RotateCcw, Search, X } from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import MetricStrip from '@/components/common/MetricStrip.vue'
import AsyncState from '@/components/common/AsyncState.vue'
import Button from '@/components/ui/Button.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { errorMessage } from '@/api/client'
import * as reportsApi from '@/api/reports'
import { usePolling } from '@/composables/usePolling'
import { useToast } from '@/composables/useToast'
import type { ReportSummary } from '@/types'
import { formatDate, formatRelative, formatScore, mean, round1, scoreTone } from '@/utils/format'

type RangeOption = 'all' | '30d'

const { toast } = useToast()
const keyword = ref('')
const range = ref<RangeOption>('all')
const reports = ref<ReportSummary[]>([])
const total = ref(0)
const loading = ref(true)
const loadError = ref<string | null>(null)
const retryingId = ref('')

const rangeOptions: { value: RangeOption; label: string }[] = [
  { value: 'all', label: '全部' },
  { value: '30d', label: '最近 30 天' },
]

const scored = computed(() =>
  reports.value.filter((report) => typeof report.overallScore === 'number'),
)
const averageScore = computed(() =>
  scored.value.length > 0
    ? round1(mean(scored.value.map((report) => report.overallScore ?? 0)))
    : 0,
)
const latestReport = computed<ReportSummary | null>(() => reports.value[0] ?? null)
const overviewItems = computed(() => [
  { label: '报告数', value: total.value, hint: '已完成的模拟面试' },
  {
    label: '平均分',
    value: scored.value.length ? formatScore(averageScore.value) : '—',
    hint: scored.value.length ? `基于最近 ${scored.value.length} 份报告` : '完成面试后统计',
  },
  {
    label: '最近一次',
    value:
      latestReport.value && latestReport.value.overallScore !== null
        ? formatScore(latestReport.value.overallScore)
        : '—',
    hint: latestReport.value ? formatDate(latestReport.value.createdAt) : '完成面试后生成',
  },
])

const sortedReports = computed(() =>
  [...reports.value].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt)),
)
const filteredReports = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  const cutoff = Date.now() - 30 * 86_400_000
  return sortedReports.value.filter((report) => {
    if (range.value === '30d' && Date.parse(report.createdAt) < cutoff) return false
    if (!query) return true
    return [report.company, report.role, report.title].some((field) =>
      field.toLowerCase().includes(query),
    )
  })
})
const hasFilters = computed(() => keyword.value.trim().length > 0 || range.value !== 'all')
const libraryEmpty = computed(() => !loading.value && reports.value.length === 0)

const poller = usePolling(async () => {
  try {
    const page = await reportsApi.listReports({ page: 1, size: 50 })
    reports.value = page.items
    total.value = page.total
  } catch {
    return false
  }
  return reports.value.some((report) => report.status === 'GENERATING')
}, 3000)

async function loadReports(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const page = await reportsApi.listReports({ page: 1, size: 50 })
    reports.value = page.items
    total.value = page.total
    if (page.items.some((report) => report.status === 'GENERATING')) poller.start()
  } catch (cause) {
    loadError.value = errorMessage(cause, '报告列表加载失败')
  } finally {
    loading.value = false
  }
}

async function retryReport(report: ReportSummary): Promise<void> {
  if (retryingId.value) return
  retryingId.value = report.id
  try {
    await reportsApi.retryReport(report.id)
    toast('已提交重试', 'info', '报告已回到生成中，不重复扣额度')
    await loadReports()
  } catch (cause) {
    toast('重试失败', 'error', errorMessage(cause))
  } finally {
    retryingId.value = ''
  }
}

function resetFilters(): void {
  keyword.value = ''
  range.value = 'all'
}

const toneText: Record<ReturnType<typeof scoreTone>, string> = {
  success: 'text-success-strong',
  accent: 'text-accent-strong',
  warning: 'text-warning-strong',
  danger: 'text-danger-strong',
}

function statusMeta(report: ReportSummary): { label: string } {
  if (report.status === 'READY') return { label: '已完成' }
  if (report.status === 'GENERATING') return { label: '生成中' }
  return { label: '生成失败' }
}

onMounted(() => {
  void loadReports()
})

onBeforeUnmount(() => {
  poller.stop()
})
</script>

<template>
  <div class="animate-fade-up">
    <PageHeader
      title="面试报告"
      description="每一次练习都留下可复盘的证据：总分、五维能力、证据引用与改进计划"
    >
      <template #actions>
        <RouterLink
          to="/interview/new"
          class="inline-flex h-10 items-center justify-center gap-2 rounded-md bg-brand px-4 text-sm font-medium text-brand-foreground transition-colors hover:bg-brand/90"
        >
          <Plus class="h-4 w-4" aria-hidden="true" />
          开始模拟面试
        </RouterLink>
      </template>
    </PageHeader>

    <AsyncState
      :loading="loading"
      :error="loadError"
      error-title="报告列表加载失败"
      :empty="libraryEmpty"
      empty-title="还没有面试报告"
      empty-description="完成一次模拟面试后，这里会生成可回溯的五维评分、证据引用和逐题改进建议。"
      :empty-icon="FileText"
      :loading-lines="5"
      @retry="loadReports"
    >
      <template #action>
        <RouterLink
          to="/interview/new"
          class="inline-flex h-10 items-center justify-center gap-2 rounded-md bg-brand px-4 text-sm font-medium text-brand-foreground transition-colors hover:bg-brand/90"
        >
          <Plus class="h-4 w-4" aria-hidden="true" />
          去开始模拟面试
        </RouterLink>
      </template>

      <MetricStrip class="mt-5" :items="overviewItems" label="报告概览" />

      <section
        class="mt-5 flex flex-col gap-3 rounded-lg border border-border bg-surface p-4 sm:flex-row sm:items-center"
        aria-label="筛选报告"
      >
        <div class="relative flex-1">
          <label for="report-search" class="sr-only">按公司或岗位搜索报告</label>
          <Search
            class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted"
            aria-hidden="true"
          />
          <input
            id="report-search"
            v-model="keyword"
            type="search"
            placeholder="搜索公司或岗位，例如「云帆科技」"
            class="h-10 w-full rounded-md border border-border bg-surface pl-9 pr-9 text-sm text-text transition-colors placeholder:text-muted focus:border-accent/60"
          />
          <button
            v-if="keyword"
            type="button"
            class="absolute right-1.5 top-1/2 flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded text-text-muted transition-colors hover:bg-surface-2 hover:text-text sm:h-7 sm:w-7"
            aria-label="清空搜索"
            @click="keyword = ''"
          >
            <X class="h-4 w-4" aria-hidden="true" />
          </button>
        </div>

        <div
          class="flex shrink-0 items-center gap-1 self-start rounded-md bg-surface-2 p-1 sm:self-auto"
          role="group"
          aria-label="时间范围筛选"
        >
          <button
            v-for="option in rangeOptions"
            :key="option.value"
            type="button"
            :aria-pressed="range === option.value"
            :class="[
              'h-11 rounded-sm px-3 text-xs font-medium transition-colors sm:h-8',
              range === option.value
                ? 'bg-surface text-text shadow-card'
                : 'text-text-muted hover:text-text',
            ]"
            @click="range = option.value"
          >
            {{ option.label }}
          </button>
        </div>
      </section>

      <div class="mt-3 flex flex-wrap items-center justify-between gap-2">
        <p class="tabular text-xs text-text-muted">
          共 {{ filteredReports.length }} 份报告 · 按时间倒序
        </p>
        <button
          v-if="hasFilters"
          type="button"
          class="text-xs font-medium text-text-2 hover:text-text max-sm:inline-flex max-sm:h-11 max-sm:items-center"
          @click="resetFilters"
        >
          清除筛选
        </button>
      </div>

      <ul v-if="filteredReports.length" class="mt-3 divide-y divide-border" role="list">
        <li v-for="report in filteredReports" :key="report.id">
          <div class="flex items-start gap-5 py-4">
            <span class="w-16 shrink-0 text-right">
              <span
                :class="[
                  'figure block text-2xl leading-8',
                  report.overallScore === null
                    ? 'text-text'
                    : toneText[scoreTone(report.overallScore)],
                ]"
              >
                {{ report.overallScore === null ? '—' : formatScore(report.overallScore) }}
              </span>
              <span class="block text-3xs text-muted">总分</span>
            </span>

            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-baseline gap-x-2 gap-y-1">
                <RouterLink
                  :to="`/reports/${report.id}`"
                  class="truncate text-sm font-medium text-text transition-colors hover:text-accent-strong"
                >
                  {{ report.title }}
                </RouterLink>
                <span
                  :class="[
                    'text-2xs',
                    report.status === 'FAILED' ? 'text-danger-strong' : 'text-text-muted',
                  ]"
                >
                  {{ statusMeta(report).label }}
                </span>
              </div>

              <p class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-text-muted">
                <span>{{ report.company }}</span>
                <span aria-hidden="true">·</span>
                <span>{{ report.role }}</span>
                <span aria-hidden="true">·</span>
                <span :title="formatDate(report.createdAt, true)">{{
                  formatRelative(report.createdAt)
                }}</span>
                <span aria-hidden="true">·</span>
                <span class="figure">{{ report.questionCount }} 题</span>
              </p>

              <p class="mt-2 line-clamp-2 text-sm leading-6 text-text-2">
                {{ report.summary || '报告生成后这里会展示总评摘要' }}
              </p>

              <div class="mt-3 flex flex-wrap items-center gap-2">
                <Button
                  v-if="report.status === 'FAILED'"
                  variant="outline"
                  size="sm"
                  :loading="retryingId === report.id"
                  @click="retryReport(report)"
                >
                  <RotateCcw class="h-3.5 w-3.5" aria-hidden="true" />
                  重试生成
                </Button>
                <RouterLink
                  :to="`/reports/${report.id}`"
                  class="ml-auto inline-flex items-center gap-1 text-xs text-text-2 transition-colors hover:text-text"
                >
                  查看详情
                  <ArrowRight class="h-3.5 w-3.5" aria-hidden="true" />
                </RouterLink>
              </div>
            </div>
          </div>
        </li>
      </ul>

      <div v-else class="mt-3">
        <EmptyState
          :icon="Search"
          title="没有匹配的报告"
          description="换一个公司或岗位关键词，或者把时间范围切换为「全部」。"
        >
          <template #action>
            <Button variant="outline" @click="resetFilters">清除筛选</Button>
          </template>
        </EmptyState>
      </div>
    </AsyncState>
  </div>
</template>