<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowRight, CheckCircle2, Mic, Upload } from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import MetricStrip from '@/components/common/MetricStrip.vue'
import AsyncState from '@/components/common/AsyncState.vue'
import RecentReportItem from '@/components/dashboard/RecentReportItem.vue'
import Card from '@/components/ui/Card.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import Progress from '@/components/ui/Progress.vue'
import { errorMessage } from '@/api/client'
import * as dashboardApi from '@/api/dashboard'
import { DEFAULT_INTERVIEW_CONFIG } from '@/api/interviews'
import { useAppStore } from '@/stores/app'
import type {
  DashboardActiveSession,
  InterviewSession,
  Quota,
  ReportSummary,
} from '@/types'
import { formatDate, formatScore } from '@/utils/format'

interface QuickStep {
  key: string
  title: string
  description: string
  done: boolean
  to: string
  action: string
}

const store = useAppStore()

const loading = ref(true)
const loadError = ref<string | null>(null)
const quota = ref<Quota | null>(null)
const sessions = ref<InterviewSession[]>([])
const reports = ref<ReportSummary[]>([])
const reportsTotal = ref(0)
const resumeReady = ref(false)
const jdCount = ref(0)
const averageScore = ref<number | null>(null)
const scoredReportCount = ref(0)

const primaryLinkClass =
  'inline-flex h-10 items-center justify-center gap-2 rounded-md bg-brand px-4 text-sm font-medium text-brand-foreground transition-colors hover:bg-brand/90'
const secondaryLinkClass =
  'inline-flex h-10 items-center justify-center gap-2 rounded-md border border-border bg-surface px-4 text-sm font-medium text-text transition-colors hover:bg-surface-2'
const smallPrimaryLinkClass =
  'inline-flex h-8 items-center justify-center gap-1.5 rounded-md bg-brand px-3 text-xs font-medium text-brand-foreground transition-colors hover:bg-brand/90 max-sm:h-11'

/* The title names the period, not the person. */
const periodTitle = computed(() =>
  new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long' }).format(new Date()),
)

const averageValue = computed(() =>
  averageScore.value === null ? '—' : formatScore(averageScore.value),
)
const averageHint = computed(() =>
  scoredReportCount.value > 0 ? `近 ${scoredReportCount.value} 场平均` : '完成面试后统计',
)

const overviewItems = computed(() => [
  {
    label: '面试额度',
    value: quota.value?.interviewCredits ?? '—',
    hint: quota.value ? '本月剩余' : '额度加载中',
  },
  { label: '解析额度', value: quota.value?.resumeCredits ?? '—', hint: '本月剩余' },
  { label: '已完成面试', value: reportsTotal.value, hint: '累计生成报告' },
  { label: '平均分', value: averageValue.value, hint: averageHint.value },
])

const activeSession = computed<InterviewSession | null>(() => {
  const open = sessions.value.filter(
    (session) =>
      session.status === 'IN_PROGRESS' ||
      session.status === 'PAUSED' ||
      session.status === 'READY' ||
      session.status === 'CREATED',
  )
  return (
    [...open].sort(
      (a, b) =>
        Date.parse(b.lastActiveAt ?? b.startedAt ?? '') - Date.parse(a.lastActiveAt ?? a.startedAt ?? ''),
    )[0] ?? null
  )
})
const activeResumeTitle = computed(
  () => activeSession.value?.resumeTitle ?? '未选择简历',
)
const answeredCount = computed(() => activeSession.value?.progress?.answered ?? 0)
const questionTotal = computed(
  () => activeSession.value?.progress?.total ?? activeSession.value?.questionCount ?? 0,
)
const sessionProgress = computed(() =>
  questionTotal.value > 0 ? Math.round((answeredCount.value / questionTotal.value) * 100) : 0,
)
const continueLink = computed(() =>
  activeSession.value ? `/interview/${activeSession.value.id}` : '/interview/new',
)

const isPro = computed(() => quota.value?.planCode === 'PRO')
const dailyPercent = computed(() => {
  const daily = quota.value?.daily
  if (!daily || daily.sessionsLimit <= 0) return 0
  return Math.min(100, Math.round((daily.sessionsUsed / daily.sessionsLimit) * 100))
})

const jdReady = computed(() => jdCount.value > 0)
const readyToStart = computed(() => resumeReady.value && jdReady.value)

const quickSteps = computed<QuickStep[]>(() => [
  {
    key: 'resume',
    title: '简历就绪',
    description: resumeReady.value ? '已有一份解析完成的简历' : '上传并解析一份简历',
    done: resumeReady.value,
    to: '/resumes',
    action: resumeReady.value ? '管理简历' : '上传简历',
  },
  {
    key: 'jd',
    title: 'JD 就绪',
    description: jdReady.value ? `已添加 ${jdCount.value} 个目标岗位` : '粘贴目标岗位 JD 并分析',
    done: jdReady.value,
    to: '/jds',
    action: jdReady.value ? '管理岗位' : '添加 JD',
  },
  {
    key: 'interview',
    title: '开始面试',
    description: readyToStart.value ? '选择简历与 JD，配置题量后开始' : '完成前两步后即可开始',
    done: false,
    to: '/interview/new',
    action: '去开始',
  },
])

const recentReports = computed(() =>
  [...reports.value]
    .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
    .slice(0, 4),
)
const latestReport = computed(() => recentReports.value[0] ?? null)
const weeklyTip = computed(() => {
  const report = latestReport.value
  if (!report) {
    return '上传简历并添加目标岗位 JD，完成第一场模拟面试后，这里会结合你的报告给出本周练习重点。'
  }
  return report.summary || '继续保持练习节奏，优先补齐报告中的改进项，再开始下一场模拟面试。'
})

function toSession(session: DashboardActiveSession): InterviewSession {
  return {
    id: session.id,
    status: session.status,
    config: DEFAULT_INTERVIEW_CONFIG,
    progress: {
      answered: session.answeredCount,
      skipped: 0,
      total: session.questionCount,
    },
    questionCount: session.questionCount,
    resumeId: session.resumeId,
    jdId: session.jdId,
    resumeTitle: session.resumeTitle,
    jdTitle: session.jdTitle,
    jdCompany: session.jdCompany,
    startedAt: session.startedAt,
    lastActiveAt: session.lastActiveAt,
  }
}

async function loadAll(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const data = await dashboardApi.getDashboard()
    quota.value = data.quota
    store.setQuota(data.quota)
    resumeReady.value = data.resumeReady
    jdCount.value = data.jdCount
    sessions.value = data.activeSession ? [toSession(data.activeSession)] : []
    reports.value = data.recentReports
    reportsTotal.value = data.reportTotal
    averageScore.value = data.averageScore ?? null
    scoredReportCount.value = data.scoredReportCount
  } catch (error) {
    loadError.value = errorMessage(error, '工作台数据加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadAll()
})
</script>

<template>
  <div class="flex min-h-[calc(100vh-8rem)] flex-col">
    <PageHeader :title="periodTitle" description="先确认简历与目标岗位，再进行一场限时模拟面试">
      <template #actions>
        <RouterLink to="/resumes" :class="secondaryLinkClass">
          <Upload class="h-4 w-4" aria-hidden="true" />
          上传简历
        </RouterLink>
        <RouterLink to="/interview/new" :class="primaryLinkClass">
          <Mic class="h-4 w-4" aria-hidden="true" />
          开始模拟面试
        </RouterLink>
      </template>
    </PageHeader>

    <div v-if="loadError" class="mt-6">
      <AsyncState :error="loadError" error-title="工作台加载失败" @retry="loadAll" />
    </div>

    <div v-else class="mt-6 flex min-h-0 min-w-0 flex-1 flex-col">
      <AsyncState :loading="loading" :loading-lines="6">
        <MetricStrip :items="overviewItems" label="概览数据" />

        <div class="mt-6 grid min-w-0 flex-1 gap-6 lg:grid-cols-3">
          <div class="flex min-h-0 min-w-0 flex-col gap-6 lg:col-span-2">
            <Card>
              <div class="flex items-baseline justify-between gap-3">
                <h2 class="text-sm font-medium text-text">继续上次面试</h2>
                <span v-if="activeSession" class="text-2xs text-text-muted">
                  {{ activeSession.status === 'PAUSED' ? '已暂停' : '进行中' }}
                </span>
              </div>

              <template v-if="activeSession">
                <div class="mt-4 flex flex-col gap-5 sm:flex-row sm:items-end sm:justify-between">
                  <div class="min-w-0 flex-1">
                    <p class="truncate text-base font-medium text-text">
                      {{ activeSession.jdTitle ?? '模拟面试' }}
                    </p>
                    <p class="mt-1 truncate text-xs text-text-muted">
                      {{ activeSession.jdCompany ?? '目标公司' }} · {{ activeResumeTitle }}
                    </p>

                    <div class="mt-4 max-w-sm">
                      <div class="flex items-baseline justify-between text-xs">
                        <span class="text-text-muted">
                          已答 {{ answeredCount }}/{{ questionTotal }} 题
                        </span>
                        <span class="figure text-sm text-text">{{ sessionProgress }}%</span>
                      </div>
                      <Progress class="mt-2" :value="sessionProgress" label="面试进度" />
                    </div>
                  </div>

                  <RouterLink :to="continueLink" :class="`${primaryLinkClass} shrink-0`">
                    继续作答
                    <ArrowRight class="h-4 w-4" aria-hidden="true" />
                  </RouterLink>
                </div>
              </template>

              <template v-else>
                <p class="mt-4 text-sm text-text-2">还没有进行中的面试</p>
                <p class="mt-1 text-sm leading-6 text-text-muted">
                  选好简历与目标岗位，完成一场面试后即可查看逐题反馈与改进计划
                </p>
                <div class="mt-5 flex flex-wrap gap-2">
                  <RouterLink to="/interview/new" :class="smallPrimaryLinkClass">
                    开始新面试
                    <ArrowRight class="h-3.5 w-3.5" aria-hidden="true" />
                  </RouterLink>
                  <RouterLink to="/resumes" :class="secondaryLinkClass">上传简历</RouterLink>
                </div>
              </template>
            </Card>

            <Card class="flex flex-1 flex-col">
              <div class="flex items-baseline justify-between gap-3">
                <h2 class="text-sm font-medium text-text">最近报告</h2>
                <RouterLink
                  to="/reports"
                  class="text-xs text-text-muted transition-colors hover:text-text"
                >
                  查看全部
                </RouterLink>
              </div>

              <div v-if="recentReports.length > 0" class="mt-2 divide-y divide-border">
                <RecentReportItem
                  v-for="report in recentReports"
                  :key="report.id"
                  :report="report"
                />
              </div>

              <EmptyState
                v-else
                class="mt-4"
                title="还没有面试报告"
                description="完成一场模拟面试后，这里会生成逐题反馈与改进计划"
              >
                <template #action>
                  <RouterLink to="/interview/new" :class="smallPrimaryLinkClass">
                    开始模拟面试
                  </RouterLink>
                </template>
              </EmptyState>
            </Card>
          </div>

          <aside class="space-y-6" aria-label="额度与快捷入口">
            <Card>
              <div class="flex items-baseline justify-between gap-2">
                <h2 class="text-sm font-medium text-text">本月额度</h2>
                <span class="text-2xs text-text-muted">
                  {{ quota?.planName ?? '额度加载中' }}
                </span>
              </div>

              <div class="mt-4">
                <div class="flex items-baseline justify-between text-xs">
                  <span class="text-text-muted">今日面试用量</span>
                  <span class="figure text-sm text-text">
                    {{ quota?.daily.sessionsUsed ?? 0 }}/{{ quota?.daily.sessionsLimit ?? 0 }}
                  </span>
                </div>
                <Progress
                  class="mt-2"
                  :value="dailyPercent"
                  :tone="dailyPercent >= 100 ? 'warning' : 'brand'"
                  label="今日面试用量"
                />
              </div>

              <dl class="mt-5 space-y-2.5 border-t border-border pt-4 text-xs">
                <div class="flex items-baseline justify-between gap-3">
                  <dt class="text-text-muted">周期至</dt>
                  <dd class="text-text-2">
                    {{ quota?.periodEnd ? formatDate(quota.periodEnd) : '—' }}
                  </dd>
                </div>
              </dl>

              <p v-if="isPro" class="mt-4 text-xs text-text-muted">已解锁建议改写与优先生成队列</p>
              <RouterLink
                v-else
                to="/plans"
                class="mt-4 inline-flex items-center gap-1 text-xs text-text-2 transition-colors hover:text-text"
              >
                升级专业版解锁建议改写
                <ArrowRight class="h-3.5 w-3.5" aria-hidden="true" />
              </RouterLink>
            </Card>

            <Card>
              <div class="flex items-baseline justify-between gap-2">
                <h2 class="text-sm font-medium text-text">快速开始</h2>
                <span class="text-2xs text-text-muted">3 步</span>
              </div>

              <ol class="mt-4 space-y-3.5">
                <li
                  v-for="(step, index) in quickSteps"
                  :key="step.key"
                  class="flex items-start gap-3"
                >
                  <span
                    :class="[
                      'figure flex h-6 w-6 shrink-0 items-center justify-center rounded-sm text-2xs',
                      step.done ? 'bg-surface-2 text-text-2' : 'bg-text text-surface',
                    ]"
                    aria-hidden="true"
                  >
                    <CheckCircle2 v-if="step.done" class="h-3.5 w-3.5" />
                    <template v-else>{{ index + 1 }}</template>
                  </span>
                  <div class="min-w-0 flex-1">
                    <p class="text-sm text-text">{{ step.title }}</p>
                    <p class="mt-0.5 text-xs leading-5 text-text-muted">{{ step.description }}</p>
                  </div>
                  <RouterLink
                    :to="step.to"
                    :aria-label="`${step.action}：${step.title}`"
                    class="mt-0.5 shrink-0 text-xs text-text-2 transition-colors hover:text-text"
                  >
                    {{ step.action }}
                  </RouterLink>
                </li>
              </ol>
            </Card>

            <Card>
              <h2 class="text-sm font-medium text-text">本周建议</h2>
              <p class="mt-3 text-sm leading-6 text-text-2">{{ weeklyTip }}</p>
              <p v-if="latestReport" class="mt-2 text-2xs text-text-muted">
                来自《{{ latestReport.title }}》
              </p>
              <RouterLink
                v-if="latestReport"
                :to="`/reports/${latestReport.id}`"
                class="mt-3 inline-flex items-center gap-1 text-xs text-text-2 transition-colors hover:text-text"
              >
                查看报告与改进计划
                <ArrowRight class="h-3.5 w-3.5" aria-hidden="true" />
              </RouterLink>
              <RouterLink
                v-else
                to="/interview/new"
                class="mt-3 inline-flex items-center gap-1 text-xs text-text-2 transition-colors hover:text-text"
              >
                开始第一场面试
                <ArrowRight class="h-3.5 w-3.5" aria-hidden="true" />
              </RouterLink>
            </Card>
          </aside>
        </div>
      </AsyncState>
    </div>
  </div>
</template>