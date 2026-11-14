<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ArrowRight, CircleAlert, Globe, Loader2, X } from 'lucide-vue-next'
import type { InterviewConfig, InterviewSession, Jd, ResumeSummary } from '@/types'
import PageHeader from '@/components/common/PageHeader.vue'
import AsyncState from '@/components/common/AsyncState.vue'
import InterviewConfigPanel from '@/components/interview/setup/InterviewConfigPanel.vue'
import JdPicker from '@/components/interview/setup/JdPicker.vue'
import JdWebImportDialog from '@/components/interview/setup/JdWebImportDialog.vue'
import ResumePicker from '@/components/interview/setup/ResumePicker.vue'
import SetupSummary from '@/components/interview/setup/SetupSummary.vue'
import Alert from '@/components/ui/Alert.vue'
import Button from '@/components/ui/Button.vue'
import { ApiError, createRequestId, errorMessage } from '@/api/client'
import * as interviewsApi from '@/api/interviews'
import * as jdsApi from '@/api/jds'
import * as resumesApi from '@/api/resumes'
import { useAppStore } from '@/stores/app'
import { useToast } from '@/composables/useToast'

const store = useAppStore()
const route = useRoute()
const router = useRouter()
const { toast } = useToast()

const resumes = ref<ResumeSummary[]>([])
const jds = ref<Jd[]>([])
const loading = ref(true)
const loadError = ref<string | null>(null)

const config = ref<InterviewConfig>({
  questionCount: 8,
  difficulty: 'MIXED',
  interviewerStyle: 'STANDARD',
  answerMode: 'TEXT',
  timeLimitSec: 180,
  language: 'zh-CN',
})

const readyResumes = computed(() => resumes.value.filter((resume) => resume.status === 'READY'))
const selectedResumeId = ref('')
const selectedJdId = ref('')

const starting = ref(false)
const phase = ref<'idle' | 'creating' | 'preparing'>('idle')
const startError = ref('')
const startErrorCode = ref<string | null>(null)
const conflictSessionId = ref('')
let idempotencyKey: string | null = null
let disposed = false

const selectedResume = computed(
  () => readyResumes.value.find((resume) => resume.id === selectedResumeId.value) ?? null,
)
const selectedJd = computed(() => jds.value.find((jd) => jd.id === selectedJdId.value) ?? null)

/** 联网获取的 JD 预览；确认后随创建请求提交，不进入 JD 列表。 */
const pendingPreview = ref<{ previewToken: string; title: string; company: string; sourceUrl: string } | null>(
  null,
)
const webDialogOpen = ref(false)

const previewAsJd = computed<Jd | null>(() => {
  const current = pendingPreview.value
  if (!current) return null
  return {
    id: '',
    title: current.title,
    company: current.company || '招聘网站',
    rawText: '',
    createdAt: '',
    structured: {
      seniority: 'MID',
      responsibilities: [],
      mustHave: [],
      niceToHave: [],
      techStack: [],
      keywords: [],
    },
  }
})
const effectiveJd = computed<Jd | null>(() => selectedJd.value ?? previewAsJd.value)

function onWebImportUse(payload: {
  previewToken: string
  title: string
  siteName: string
  sourceUrl: string
}): void {
  pendingPreview.value = {
    previewToken: payload.previewToken,
    title: payload.title,
    company: payload.siteName,
    sourceUrl: payload.sourceUrl,
  }
  selectedJdId.value = ''
  idempotencyKey = null
}

function clearPreview(): void {
  pendingPreview.value = null
  idempotencyKey = null
}

const credits = computed(() => store.quota.interviewCredits)
const quotaBlocked = computed(() => credits.value <= 0)
const dailyBlocked = computed(
  () => store.quota.daily.sessionsUsed >= store.quota.daily.sessionsLimit,
)
const estimatedMinutes = computed(() => Math.max(5, Math.ceil(config.value.questionCount * 1.25)))

const missing = computed<string[]>(() => {
  const list: string[] = []
  if (readyResumes.value.length === 0) list.push('还没有已就绪的简历')
  else if (!selectedResume.value) list.push('请选择一份简历')
  if (jds.value.length === 0 && !previewAsJd.value) list.push('还没有目标岗位')
  else if (!effectiveJd.value) list.push('请选择一个目标岗位')
  if (quotaBlocked.value) list.push('本月面试额度已用完')
  if (dailyBlocked.value) list.push('今日面试场次已达上限')
  return list
})
const canStart = computed(
  () =>
    selectedResume.value !== null &&
    effectiveJd.value !== null &&
    !quotaBlocked.value &&
    !dailyBlocked.value &&
    !starting.value,
)
const phaseText = computed(() =>
  phase.value === 'creating' ? '正在创建面试会话…' : '正在生成题目，通常需要 5–15 秒…',
)

async function loadAll(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const [resumePage, jdPage] = await Promise.all([
      resumesApi.listResumes({ page: 1, size: 50 }),
      jdsApi.listJds({ page: 1, size: 50 }),
    ])
    resumes.value = resumePage.items
    jds.value = jdPage.items
    const queryResumeId = typeof route.query.resumeId === 'string' ? route.query.resumeId : ''
    const queryJdId = typeof route.query.jdId === 'string' ? route.query.jdId : ''
    selectedResumeId.value = readyResumes.value.some((item) => item.id === queryResumeId)
      ? queryResumeId
      : ''
    selectedJdId.value = jds.value.some((item) => item.id === queryJdId) ? queryJdId : ''
    await store.loadQuota()
  } catch (cause) {
    loadError.value = errorMessage(cause, '面试配置加载失败')
  } finally {
    loading.value = false
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms)
  })
}

async function waitUntilReady(id: string): Promise<InterviewSession> {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const detail = await interviewsApi.getInterview(id)
    if (detail.status === 'READY' || detail.status === 'IN_PROGRESS') return detail
    if (detail.status === 'FAILED' || detail.status === 'ABANDONED') {
      throw new ApiError({
        code: 'QUESTION_GEN_FAILED',
        message: '出题失败，额度已返还；可以调整配置后重新创建',
        status: 502,
      })
    }
    if (detail.status !== 'PREPARING' && detail.status !== 'CREATED') return detail
    if (disposed) return detail
    await sleep(2000)
  }
  throw new ApiError({
    code: 'AI_TIMEOUT',
    message: '出题耗时较长，可稍后从工作台继续；本次不会重复扣额度',
    status: 504,
  })
}

async function startInterview(): Promise<void> {
  if (starting.value) return
  const resume = selectedResume.value
  const jd = effectiveJd.value
  if (!resume || !jd) {
    toast(missing.value[0] ?? '请先完善面试配置', 'warning')
    return
  }
  starting.value = true
  startError.value = ''
  startErrorCode.value = null
  conflictSessionId.value = ''
  phase.value = 'creating'
  if (!idempotencyKey) idempotencyKey = createRequestId()
  try {
    const preview = pendingPreview.value
    const session = await interviewsApi.createInterview(
      {
        resumeId: resume.id,
        ...(preview ? { previewToken: preview.previewToken } : { jdId: selectedJd.value?.id ?? jd.id }),
        config: { ...config.value },
      },
      idempotencyKey,
    )
    phase.value = 'preparing'
    await interviewsApi.startInterview(session.id)
    const ready = await waitUntilReady(session.id)
    idempotencyKey = null
    await store.loadQuota()
    if (disposed) return
    toast('面试已就绪，开始作答', 'success', `${jd.title} · ${ready.questionCount ?? config.value.questionCount} 道题`)
    await router.push({ name: 'interview-room', params: { id: session.id } })
  } catch (cause) {
    phase.value = 'idle'
    startError.value = errorMessage(cause, '创建面试失败，请稍后重试')
    if (cause instanceof ApiError) {
      startErrorCode.value = cause.code
      if (cause.status === 409) {
        const sessionId = cause.details.sessionId ?? cause.details.session_id
        conflictSessionId.value = typeof sessionId === 'string' ? sessionId : ''
        if (!conflictSessionId.value) {
          try {
            const page = await interviewsApi.listInterviews({ page: 1, size: 20 })
            const open = page.items.find(
              (item) =>
                item.status === 'IN_PROGRESS' ||
                item.status === 'PAUSED' ||
                item.status === 'READY' ||
                item.status === 'CREATED' ||
                item.status === 'PREPARING',
            )
            if (open) conflictSessionId.value = open.id
          } catch {
            // 找不到可继续的会话时只展示文字提示
          }
        }
      }
      if (cause.status === 402) {
        startError.value = '面试额度不足，请升级套餐或购买次数包后再开始。'
      }
    }
    toast('创建面试失败', 'error', startError.value)
  } finally {
    starting.value = false
    if (!startError.value) phase.value = 'idle'
  }
}

watch(
  [selectedResumeId, selectedJdId, config],
  () => {
    idempotencyKey = null
    if (!starting.value) {
      startError.value = ''
      startErrorCode.value = null
      conflictSessionId.value = ''
    }
  },
  { deep: true },
)

watch(selectedJdId, (id) => {
  if (id) pendingPreview.value = null
})

onMounted(() => {
  void loadAll()
})

onBeforeUnmount(() => {
  disposed = true
})
</script>

<template>
  <div class="space-y-6">
    <PageHeader
      title="创建模拟面试"
      description="基于简历与目标岗位生成针对性题目。默认 8 道题约 10 分钟，可按需调整题量、难度与面试官风格。"
    />

    <AsyncState
      :loading="loading"
      :error="loadError"
      error-title="面试配置加载失败"
      :loading-lines="6"
      @retry="loadAll"
    >
      <div class="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div class="space-y-4">
          <ResumePicker
            v-model="selectedResumeId"
            :resumes="readyResumes"
            :total-count="resumes.length"
          />

          <JdPicker v-model="selectedJdId" :jds="jds" />

          <div class="space-y-2">
            <div class="flex flex-wrap items-center gap-2">
              <Button variant="outline" size="sm" @click="webDialogOpen = true">
                <Globe class="h-3.5 w-3.5" aria-hidden="true" />
                从招聘网站获取目标岗位
              </Button>
              <span class="text-3xs text-text-muted">粘贴岗位链接，抓不到会自动改用搜索</span>
            </div>

            <div
              v-if="pendingPreview"
              class="flex flex-wrap items-center justify-between gap-2 rounded-md border border-border bg-surface-2 px-3 py-2"
            >
              <div class="min-w-0">
                <p class="truncate text-xs font-medium text-text">
                  {{ pendingPreview.title }}
                </p>
                <p class="mt-0.5 truncate text-3xs text-text-muted">
                  {{ pendingPreview.company || '招聘网站' }} · 联网获取，创建面试时保存
                </p>
              </div>
              <Button variant="ghost" size="sm" aria-label="移除已获取的岗位" @click="clearPreview">
                <X class="h-3.5 w-3.5" aria-hidden="true" />
                移除
              </Button>
            </div>
          </div>

          <InterviewConfigPanel v-model="config" />

          <Alert
            v-if="starting"
            variant="info"
            :title="phaseText"
            description="请保持页面打开；题目就绪后会自动进入面试间。若中途失败，额度会按规则返还。"
          >
            <p class="mt-2 flex items-center gap-2 text-xs text-text-muted">
              <Loader2 class="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
              {{ phaseText }}
            </p>
          </Alert>

          <Alert
            v-if="startError"
            variant="danger"
            title="面试没有创建成功"
            :description="startError"
          >
            <div class="mt-3 flex flex-wrap items-center gap-2">
              <RouterLink
                v-if="startErrorCode === 'QUOTA_EXCEEDED'"
                to="/plans"
                class="inline-flex h-9 items-center gap-1 rounded-md bg-brand px-3 text-xs font-medium text-brand-foreground"
              >
                查看套餐
                <ArrowRight class="h-3.5 w-3.5" aria-hidden="true" />
              </RouterLink>
              <RouterLink
                v-if="conflictSessionId"
                :to="`/interview/${conflictSessionId}`"
                class="inline-flex h-9 items-center gap-1 rounded-md border border-border px-3 text-xs font-medium text-text hover:bg-surface-2"
              >
                继续未结束的面试
                <ArrowRight class="h-3.5 w-3.5" aria-hidden="true" />
              </RouterLink>
              <Button v-else variant="outline" size="sm" :disabled="starting" @click="startInterview">
                重试创建
              </Button>
            </div>
          </Alert>

          <p v-if="startErrorCode === 'STATE_CONFLICT' && !conflictSessionId" class="flex items-start gap-1.5 text-xs leading-5 text-text-muted">
            <CircleAlert class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            已有一场未结束的面试。可先结束或放弃该会话，再创建新的面试。
          </p>
        </div>

        <SetupSummary
          :resume="selectedResume"
          :jd="effectiveJd"
          :config="config"
          :credits="credits"
          :quota-blocked="quotaBlocked"
          :daily-blocked="dailyBlocked"
          :can-start="canStart"
          :missing="missing"
          :estimated-minutes="estimatedMinutes"
          :loading="starting"
          @start="startInterview"
        />
      </div>
    </AsyncState>

    <JdWebImportDialog v-model:open="webDialogOpen" @use="onWebImportUse" />
  </div>
</template>