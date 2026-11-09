<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import {
  ArrowRight,
  BarChart3,
  CheckCircle2,
  Lightbulb,
  Mic,
  Upload,
} from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import MetricStrip from '@/components/common/MetricStrip.vue'
import AsyncState from '@/components/common/AsyncState.vue'
import RecentReportItem from '@/components/dashboard/RecentReportItem.vue'
import Badge from '@/components/ui/Badge.vue'
import Card from '@/components/ui/Card.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import Progress from '@/components/ui/Progress.vue'
import { errorMessage } from '@/api/client'
import * as interviewsApi from '@/api/interviews'
import * as jdsApi from '@/api/jds'
import * as quotaApi from '@/api/quota'
import * as reportsApi from '@/api/reports'
import * as resumesApi from '@/api/resumes'
import { useAppStore } from '@/stores/app'
import type { InterviewSession, Jd, Quota, ReportSummary, ResumeSummary } from '@/types'
import { formatDate, formatScore, mean, round1 } from '@/utils/format'

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
const resumes = ref<ResumeSummary[]>([])
const jds = ref<Jd[]>([])
const sessions = ref<InterviewSession[]>([])
const reports = ref<ReportSummary[]>([])
const reportsTotal = ref(0)

const primaryLinkClass =
  'inline-flex h-10 items-center justify-center gap-2 rounded-md bg-brand px-4 text-sm font-medium text-brand-foreground shadow-sm transition-all hover:brightness-105 active:scale-98'
const secondaryLinkClass =
  'inline-flex h-10 items-center justify-center gap-2 rounded-md border border-border bg-surface px-4 text-sm font-medium text-text transition-colors hover:bg-surface-2'
const smallPrimaryLinkClass =
  'inline-flex h-8 items-center justify-center gap-1.5 rounded-md bg-brand px-3 text-xs font-medium text-brand-foreground shadow-sm transition-all hover:brightness-105 active:scale-98 max-sm:h-11'

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})
const greetingTitle = computed(() => `${greeting.value}，${store.user?.nickname ?? '同学'}`)

const scoredReports = computed(() =>
  reports.value.filter((report) => typeof report.overallScore === 'number'),
)
const averageValue = computed(() =>
  scoredReports.value.length > 0
    ? formatScore(round1(mean(scoredReports.value.map((report) => report.overallScore ?? 0))))
    : '—',
)
const averageHint = computed(() =>
  scoredReports.value.length > 0 ? `近 ${scoredReports.value.length} 场平均` : '完成面试后统计',
)

const overviewItems = computed(() => [
  {
    label: '面试额度',
    value: quota.value?.interviewCredits ?? '—',
    hint: quota.value ? `${quota.value.planName} · 本月剩余` : '额度加载中',
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
const activeResumeTitle = computed(() => {
  const session = activeSession.value
  if (!session) return ''
  return (
    session.resumeTitle ??
    resumes.value.find((resume) => resume.id === session.resumeId)?.title ??
    '未选择简历'
  )
})
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

const resumeReady = computed(() => resumes.value.some((resume) => resume.status === 'READY'))
const jdReady = computed(() => jds.value.length > 0)
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
    description: jdReady.value ? `已添加 ${jds.value.length} 个目标岗位` : '粘贴目标岗位 JD 并分析',
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

async function loadAll(): Promise<void> {
  loading.value = true
  loadError.value = null
  const [quotaResult, resumeResult, jdResult, interviewResult, reportResult] =
    await Promise.allSettled([
      quotaApi.getQuota(),
      resumesApi.listResumes({ page: 1, size: 20 }),
      jdsApi.listJds({ page: 1, size: 20 }),
      interviewsApi.listInterviews({ page: 1, size: 20 }),
      reportsApi.listReports({ page: 1, size: 20 }),
    ])

  if (quotaResult.status === 'fulfilled') quota.value = quotaResult.value
  if (resumeResult.status === 'fulfilled') resumes.value = resumeResult.value.items
  if (jdResult.status === 'fulfilled') jds.value = jdResult.value.items
  if (interviewResult.status === 'fulfilled') sessions.value = interviewResult.value.items
  if (reportResult.status === 'fulfilled') {
    reports.value = reportResult.value.items
    reportsTotal.value = reportResult.value.total
  }

  const failures = [quotaResult, resumeResult, jdResult, interviewResult, reportResult].filter(
    (result): result is PromiseRejectedResult => result.status === 'rejected',
  )
  if (failures.length === 5) {
    loadError.value = errorMessage(failures[0]?.reason, '工作台数据加载失败')
  } else if (failures.length > 0) {
    loadError.value = null
  }
  loading.value = false
}

onMounted(() => {
  void loadAll()
  void store.loadQuota()
})
</script>

<template>
  <div class="flex min-h-[calc(100vh-8rem)] flex-col">
    <PageHeader
      :title="greetingTitle"
      description="今天练哪一场？先确认简历与目标岗位，再进行一场限时模拟面试。"
      eyebrow="工作台"
    >
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

      <div class="mt-5 grid min-w-0 flex-1 gap-5 lg:grid-cols-3">
        <div class="flex min-h-0 min-w-0 flex-col gap-5 lg:col-span-2">
          <Card>
            <div class="flex items-center justify-between gap-3">
              <p class="text-xs font-medium uppercase tracking-wider text-brand">继续上次面试</p>
              <Badge v-if="activeSession" variant="warning" dot>
                {{ activeSession.status === 'PAUSED' ? '已暂停' : '进行中' }}
              </Badge>
            </div>

            <template v-if="activeSession">
              <div class="mt-3 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                <div class="min-w-0 flex-1">
                  <div class="flex items-start gap-3">
                    <span
                      class="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-brand/10 text-brand"
                    >
                      <Mic class="h-5 w-5" aria-hidden="true" />
                    </span>
                    <div class="min-w-0">
                      <p class="truncate text-base font-semibold text-text">
                        {{ activeSession.jdTitle ?? '模拟面试' }}
                      </p>
                      <p class="mt-0.5 truncate text-xs text-text-muted">
                        {{ activeSession.jdCompany ?? '目标公司' }} · {{ activeResumeTitle }}
                      </p>
                    </div>
                  </div>

                  <div class="mt-4 max-w-md">
                    <div class="flex items-center justify-between text-xs">
                      <span class="text-text-muted"
                        >已答 {{ answeredCount }}/{{ questionTotal }} 题</span
                      >
                      <span class="tabular font-medium text-text">{{ sessionProgress }}%</span>
                    </div>
                    <Progress class="mt-1.5" :value="sessionProgress" label="面试进度" />
                  </div>
                </div>

                <RouterLink :to="continueLink" :class="`${primaryLinkClass} shrink-0`">
                  继续作答
                  <ArrowRight class="h-4 w-4" aria-hidden="true" />
                </RouterLink>
              </div>
            </template>

            <template v-else>
              <div class="mt-3 flex items-start gap-3">
                <span
                  class="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-surface-2 text-text-muted"
                >
                  <Mic class="h-5 w-5" aria-hidden="true" />
                </span>
                <div class="min-w-0">
                  <p class="text-sm font-semibold text-text">还没有进行中的面试</p>
                  <p class="mt-0.5 text-xs leading-5 text-text-muted">
                    选好简历与目标岗位，完成一场面试后即可查看逐题反馈与改进计划。
                  </p>
                </div>
              </div>
              <div class="mt-4 flex flex-wrap gap-2">
                <RouterLink to="/interview/new" :class="smallPrimaryLinkClass">
                  开始新面试
                  <ArrowRight class="h-3.5 w-3.5" aria-hidden="true" />
                </RouterLink>
                <RouterLink to="/resumes" :class="secondaryLinkClass">上传简历</RouterLink>
              </div>
            </template>
          </Card>

          <Card class="flex flex-1 flex-col">
            <div class="flex items-center justify-between gap-3">
              <div class="flex items-center gap-2">
                <span
                  class="flex h-8 w-8 items-center justify-center rounded-md bg-surface-2 text-text-muted"
                >
                  <BarChart3 class="h-4 w-4" aria-hidden="true" />
                </span>
                <h2 class="text-sm font-semibold text-text">最近报告</h2>
              </div>
              <RouterLink to="/reports" class="text-xs font-medium text-brand hover:underline">
                查看全部
              </RouterLink>
            </div>

            <div v-if="recentReports.length > 0" class="mt-3 flex-1 divide-y divide-border">
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
              description="完成一场模拟面试后，这里会生成逐题反馈与改进计划。"
            >
              <template #action>
                <RouterLink to="/interview/new" :class="smallPrimaryLinkClass">
                  开始模拟面试
                </RouterLink>
              </template>
            </EmptyState>
          </Card>
        </div>

        <aside class="min-w-0 space-y-5" aria-label="额度与快捷入口">
          <Card>
            <div class="flex items-center justify-between gap-2">
              <div>
                <h2 class="text-sm font-semibold text-text">本月额度</h2>
                <p class="mt-0.5 text-xs text-text-muted">
                  {{ quota?.planName ?? '额度加载中' }}
                </p>
              </div>
              <Badge :variant="isPro ? 'brand' : 'neutral'" dot>
                {{ isPro ? 'PRO' : (quota?.planCode ?? 'FREE') }}
              </Badge>
            </div>

            <div class="mt-4 grid grid-cols-2 gap-3">
              <div class="rounded-md bg-surface-2 p-3">
                <p class="text-xs text-text-muted">面试剩余</p>
                <p class="tabular mt-1 text-xl font-semibold text-text">
                  {{ quota?.interviewCredits ?? '—'
                  }}<span class="ml-1 text-xs font-normal text-text-muted">次</span>
                </p>
              </div>
              <div class="rounded-md bg-surface-2 p-3">
                <p class="text-xs text-text-muted">解析剩余</p>
                <p class="tabular mt-1 text-xl font-semibold text-text">
                  {{ quota?.resumeCredits ?? '—'
                  }}<span class="ml-1 text-xs font-normal text-text-muted">次</span>
                </p>
              </div>
            </div>

            <div class="mt-4">
              <div class="flex items-center justify-between text-xs text-text-muted">
                <span>今日面试用量</span>
                <span class="tabular">
                  {{ quota?.daily.sessionsUsed ?? 0 }}/{{ quota?.daily.sessionsLimit ?? 0 }}
                </span>
              </div>
              <Progress
                class="mt-1.5"
                :value="dailyPercent"
                :tone="dailyPercent >= 100 ? 'warning' : 'brand'"
                label="今日面试用量"
              />
            </div>

            <p class="mt-3 text-xs text-text-muted">
              额度周期至 {{ quota?.periodEnd ? formatDate(quota.periodEnd) : '—' }}
            </p>

            <p v-if="isPro" class="mt-3 flex items-start gap-1.5 text-xs text-success-strong">
              <CheckCircle2 class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              已解锁建议改写与优先生成队列
            </p>
            <RouterLink
              v-else
              to="/plans"
              class="mt-3 inline-flex items-center gap-1 text-xs font-medium text-brand hover:underline"
            >
              升级 PRO 解锁建议改写与优先生成队列
              <ArrowRight class="h-3.5 w-3.5" aria-hidden="true" />
            </RouterLink>
          </Card>

          <Card>
            <div class="flex items-center justify-between gap-2">
              <h2 class="text-sm font-semibold text-text">快速开始</h2>
              <span class="text-2xs text-text-muted">3 步进入面试</span>
            </div>

            <ol class="mt-4 space-y-3">
              <li v-for="(step, index) in quickSteps" :key="step.key" class="flex items-start gap-3">
                <span
                  :class="[
                    'flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold',
                    step.done ? 'bg-success/10 text-success-strong' : 'bg-surface-2 text-text-muted',
                  ]"
                  aria-hidden="true"
                >
                  <CheckCircle2 v-if="step.done" class="h-4 w-4" />
                  <template v-else>{{ index + 1 }}</template>
                </span>
                <div class="min-w-0 flex-1">
                  <p class="text-sm font-medium text-text">{{ step.title }}</p>
                  <p class="mt-0.5 text-xs leading-5 text-text-muted">{{ step.description }}</p>
                </div>
                <RouterLink
                  :to="step.to"
                  :aria-label="`${step.action}：${step.title}`"
                  class="mt-0.5 inline-flex shrink-0 items-center gap-1 text-xs font-medium text-brand hover:underline"
                >
                  {{ step.action }}
                  <ArrowRight class="h-3.5 w-3.5" aria-hidden="true" />
                </RouterLink>
              </li>
            </ol>
          </Card>

          <Card class="border-brand/30 bg-brand/5">
            <div class="flex items-center gap-2">
              <span
                class="flex h-8 w-8 items-center justify-center rounded-md bg-brand/10 text-brand"
              >
                <Lightbulb class="h-4 w-4" aria-hidden="true" />
              </span>
              <h2 class="text-sm font-semibold text-text">本周建议</h2>
            </div>
            <p class="mt-3 text-sm leading-6 text-text">{{ weeklyTip }}</p>
            <template v-if="latestReport">
              <p class="mt-2 text-2xs text-text-muted">来自《{{ latestReport.title }}》</p>
              <RouterLink
                :to="`/reports/${latestReport.id}`"
                class="mt-2 inline-flex items-center gap-1 text-xs font-medium text-brand hover:underline"
              >
                查看报告与改进计划
                <ArrowRight class="h-3.5 w-3.5" aria-hidden="true" />
              </RouterLink>
            </template>
            <RouterLink
              v-else
              to="/interview/new"
              class="mt-2 inline-flex items-center gap-1 text-xs font-medium text-brand hover:underline"
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