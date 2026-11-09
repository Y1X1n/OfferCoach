import { computed, nextTick, onBeforeUnmount, reactive, ref, toValue, watch } from 'vue'
import type { MaybeRefOrGetter } from 'vue'
import type {
  Feedback,
  FinishResult,
  InterviewConfig,
  InterviewSession,
  InterviewerStyle,
  Question,
  QuestionStatus,
} from '@/types'
import { ApiError, createRequestId, errorMessage } from '@/api/client'
import * as interviewsApi from '@/api/interviews'
import type { SubmitAnswerPayload } from '@/api/interviews'
import { useToast } from '@/composables/useToast'

export type StreamState = 'idle' | 'connecting' | 'streaming' | 'done' | 'error'

const DRAFT_DEBOUNCE_MS = 3000
const DRAFT_PREFIX = 'oc_draft_'
const FEEDBACK_POLL_MS = 700
const FEEDBACK_TIMEOUT_MS = 20000

/**
 * 面试间领域逻辑：会话与题目加载、作答状态、单题计时、反馈轮询、草稿与状态机动作。
 * 反馈由后端异步生成，页面只在完成后一次性展示，不渲染 SSE token。
 */
export function useInterviewRoom(sessionId: MaybeRefOrGetter<string>) {
  const { toast } = useToast()

  const session = ref<InterviewSession | null>(null)
  const questions = ref<Question[]>([])
  const loading = ref(true)
  const error = ref<string | null>(null)
  const currentIndex = ref(0)
  const answerText = ref('')
  const remaining = ref(180)
  const questionStartedAt = ref(Date.now())
  const submitting = ref(false)
  const ending = ref(false)
  const pausing = ref(false)
  const streamState = ref<StreamState>('idle')
  const streamText = ref('')
  const streamError = ref('')
  const activeFeedback = ref<Feedback | null>(null)
  const feedbackRef = ref<HTMLElement | null>(null)
  const feedbackByQuestion = reactive<Record<string, Feedback>>({})
  const answerIds = reactive<Record<string, string>>({})
  const durations = reactive<Record<string, number>>({})

  let timerId: number | null = null
  let draftTimer: number | null = null
  let feedbackWaitGeneration = 0

  const config = computed<InterviewConfig>(
    () =>
      session.value?.config ?? {
        questionCount: 8,
        difficulty: 'MIXED',
        interviewerStyle: 'STANDARD',
        answerMode: 'TEXT',
        timeLimitSec: 180,
      },
  )
  const timeLimit = computed(() => Math.max(1, config.value.timeLimitSec))
  const jdTitle = computed(() => session.value?.jdTitle ?? '模拟面试')
  const jdCompany = computed(() => session.value?.jdCompany ?? '目标公司')
  const interviewerStyle = computed<InterviewerStyle>(() => config.value.interviewerStyle)

  const status = computed<InterviewSession['status'] | 'MISSING'>(
    () => session.value?.status ?? 'MISSING',
  )
  const isPaused = computed(() => status.value === 'PAUSED')
  const isPreparing = computed(
    () => status.value === 'CREATED' || status.value === 'PREPARING',
  )
  const isSessionActive = computed(
    () => status.value === 'IN_PROGRESS' || status.value === 'READY',
  )
  const isFinished = computed(
    () =>
      status.value === 'FINISHED' ||
      status.value === 'COMPLETED' ||
      status.value === 'REPORT_GENERATING' ||
      status.value === 'ABANDONED',
  )
  const isCompleted = computed(
    () => status.value === 'COMPLETED' || status.value === 'REPORT_GENERATING',
  )
  const completedReportId = computed(() => session.value?.reportId ?? null)

  const totalQuestions = computed(() => {
  const configured = session.value?.config.questionCount ?? 0
  return configured > 0 ? configured : questions.value.length
})
  const answeredCount = computed(
    () => questions.value.filter((question) => question.status === 'ANSWERED').length,
  )
  const skippedCount = computed(
    () => questions.value.filter((question) => question.status === 'SKIPPED').length,
  )
  const unansweredCount = computed(() =>
    Math.max(0, totalQuestions.value - answeredCount.value - skippedCount.value),
  )
  const progressPercent = computed(() =>
    totalQuestions.value === 0
      ? 0
      : Math.round(((answeredCount.value + skippedCount.value) / totalQuestions.value) * 100),
  )

  const currentQuestion = computed<Question | null>(
    () => questions.value[currentIndex.value] ?? null,
  )
  const currentQuestionId = computed(() => currentQuestion.value?.id ?? '')
  const currentStatus = computed<QuestionStatus>(() => currentQuestion.value?.status ?? 'PENDING')
  const isAnswered = computed(() => currentStatus.value === 'ANSWERED')
  const isLastQuestion = computed(
  () => (currentQuestion.value?.seq ?? 0) >= totalQuestions.value,
)
  const answerDurationText = computed(() => {
    const questionId = currentQuestionId.value
    const duration = questionId ? durations[questionId] : undefined
    if (!duration) return ''
    return formatDuration(Math.max(1, Math.round(duration / 1000)))
  })

  const trimmedLength = computed(() => answerText.value.trim().length)
  const canSubmit = computed(
    () =>
      isSessionActive.value && !isAnswered.value && trimmedLength.value >= 10 && !submitting.value,
  )
  const canAdvance = computed(
    () => isAnswered.value && (streamState.value === 'done' || streamState.value === 'error'),
  )
  const timerVisible = computed(
    () =>
      isSessionActive.value &&
      !isAnswered.value &&
      currentStatus.value !== 'SKIPPED' &&
      currentQuestion.value !== null &&
      remaining.value > 0,
  )

  function formatDuration(seconds: number): string {
    const minutes = Math.floor(seconds / 60)
    const rest = seconds % 60
    if (minutes === 0) return `${rest} 秒`
    return rest === 0 ? `${minutes} 分钟` : `${minutes} 分 ${rest} 秒`
  }

  function sessionKey(): string {
    return toValue(sessionId)
  }

  function draftKey(questionId: string): string {
    return `${DRAFT_PREFIX}${sessionKey()}_${questionId}`
  }

  function readDraft(questionId: string): string {
    try {
      return localStorage.getItem(draftKey(questionId)) ?? ''
    } catch {
      return ''
    }
  }

  function writeDraft(questionId: string, text: string): void {
    try {
      if (text.trim()) localStorage.setItem(draftKey(questionId), text)
      else localStorage.removeItem(draftKey(questionId))
    } catch {
      // 存储不可用时不阻塞作答
    }
  }

  function clearDraft(questionId: string): void {
    try {
      localStorage.removeItem(draftKey(questionId))
    } catch {
      // 忽略
    }
  }

  function clearDraftTimer(): void {
    if (draftTimer !== null) {
      window.clearTimeout(draftTimer)
      draftTimer = null
    }
  }

  /** 立即落盘当前未提交回答；切题、提交、卸载与 beforeunload 都调用。 */
  function flushDraft(): void {
    clearDraftTimer()
    const question = currentQuestion.value
    if (!question || !isSessionActive.value) return
    if (isAnswered.value || question.status === 'SKIPPED') {
      clearDraft(question.id)
      return
    }
    writeDraft(question.id, answerText.value)
  }

  function scheduleDraftSave(): void {
    const question = currentQuestion.value
    if (!question || !isSessionActive.value || isAnswered.value) return
    clearDraftTimer()
    const id = question.id
    const text = answerText.value
    draftTimer = window.setTimeout(() => {
      draftTimer = null
      writeDraft(id, text)
    }, DRAFT_DEBOUNCE_MS)
  }

  watch(answerText, scheduleDraftSave)

  function clearTimer(): void {
    if (timerId !== null) {
      window.clearInterval(timerId)
      timerId = null
    }
  }

  function startTimer(): void {
    clearTimer()
    if (!isSessionActive.value) return
    timerId = window.setInterval(() => {
      if (!isSessionActive.value) return
      if (remaining.value <= 0) return
      remaining.value -= 1
      if (remaining.value === 0) {
        clearTimer()
        handleTimeout()
      }
    }, 1000)
  }

  function abortStream(): void {
    feedbackWaitGeneration += 1
  }

  function applyFeedback(questionId: string, feedback: Feedback): void {
    feedbackByQuestion[questionId] = feedback
    if (currentQuestionId.value === questionId) {
      activeFeedback.value = feedback
      streamState.value = 'done'
      streamError.value = ''
    }
  }

  /**
   * 反馈由后端异步生成；前端只轮询结果，不读取或渲染任何 token 流。
   * 404 表示尚未生成，继续等待；其他错误立即终止。
   */
  async function waitForFeedback(questionId: string, answerId: string): Promise<void> {
    const generation = ++feedbackWaitGeneration
    if (currentQuestionId.value === questionId) {
      streamState.value = 'connecting'
      streamText.value = ''
      streamError.value = ''
    }
    const deadline = Date.now() + FEEDBACK_TIMEOUT_MS
    while (Date.now() < deadline) {
      if (generation !== feedbackWaitGeneration) return
      try {
        const feedback = await interviewsApi.getAnswerFeedback(sessionKey(), answerId)
        if (generation !== feedbackWaitGeneration) return
        applyFeedback(questionId, feedback)
        return
      } catch (cause) {
        if (generation !== feedbackWaitGeneration) return
        if (!(cause instanceof ApiError) || cause.status !== 404) {
          if (currentQuestionId.value === questionId) {
            streamState.value = 'error'
            streamError.value = errorMessage(cause, '评分结果加载失败，请重试')
          }
          return
        }
      }
      await new Promise((resolve) => window.setTimeout(resolve, FEEDBACK_POLL_MS))
    }
    if (generation === feedbackWaitGeneration && currentQuestionId.value === questionId) {
      streamState.value = 'error'
      streamError.value = '反馈生成超时，请点击重新评分'
    }
  }

  async function loadFeedback(questionId: string): Promise<void> {
    const answerId = answerIds[questionId]
    if (!answerId) return
    await waitForFeedback(questionId, answerId)
  }

  function isFeedbackDone(): boolean {
    return streamState.value === 'done'
  }

  function resetQuestionState(): void {
    clearTimer()
    abortStream()
    const question = currentQuestion.value
    if (!question || !session.value) return

    questionStartedAt.value = Date.now()
    remaining.value = timeLimit.value
    streamText.value = ''
    streamError.value = ''
    activeFeedback.value = feedbackByQuestion[question.id] ?? null
    streamState.value = activeFeedback.value ? 'done' : 'idle'

    if (isAnswered.value) {
      answerText.value = question.answerText ?? ''
      if (answerIds[question.id] && !feedbackByQuestion[question.id]) void loadFeedback(question.id)
      return
    }
    answerText.value = isSessionActive.value ? readDraft(question.id) : ''
    if (isSessionActive.value && question.status !== 'SKIPPED') startTimer()
  }

  function pickInitialIndex(): number {
    const firstOpen = questions.value.findIndex(
      (question) => question.status !== 'ANSWERED' && question.status !== 'SKIPPED',
    )
    return firstOpen >= 0 ? firstOpen : 0
  }

  async function reload(preservePosition = true, silent = false): Promise<void> {
    const id = sessionKey()
    if (!id) {
      loading.value = false
      error.value = '缺少面试会话编号'
      return
    }
    if (!silent) {
      loading.value = true
      error.value = null
    }
    try {
      const [detail, list] = await Promise.all([
        interviewsApi.getInterview(id),
        interviewsApi.getQuestions(id).catch(() => []),
      ])
      const currentId = preservePosition ? currentQuestionId.value : ''
      session.value = detail
      questions.value = list
      for (const question of list) {
        if (question.answerId) answerIds[question.id] = question.answerId
        if (typeof question.durationMs === 'number') durations[question.id] = question.durationMs
      }
      remaining.value = Math.max(1, detail.config.timeLimitSec)
      if (!preservePosition) currentIndex.value = pickInitialIndex()
      else if (currentId) {
        const index = list.findIndex((question) => question.id === currentId)
        currentIndex.value = index >= 0 ? index : pickInitialIndex()
      }
      if (detail.status === 'IN_PROGRESS' || detail.status === 'READY') startTimer()
      else clearTimer()
    } catch (cause) {
      if (!silent) error.value = errorMessage(cause, '面试会话加载失败')
    } finally {
      if (!silent) loading.value = false
    }
  }

  async function submitAnswer(): Promise<void> {
    const question = currentQuestion.value
    if (!question || submitting.value) return
    if (!isSessionActive.value) {
      toast('本场面试未在进行中，无法提交', 'warning')
      return
    }
    if (isAnswered.value) {
      if (!feedbackByQuestion[question.id]) await loadFeedback(question.id)
      return
    }
    const text = answerText.value.trim()
    if (text.length < 10) {
      toast('回答至少 10 个字才能提交', 'warning')
      return
    }

    flushDraft()
    clearTimer()
    submitting.value = true
    const payload: SubmitAnswerPayload = {
      questionId: question.id,
      answerText: text,
      durationMs: Math.max(0, Date.now() - questionStartedAt.value),
      inputMode: 'TEXT',
    }
    const key = createRequestId()
    try {
      const result = await interviewsApi.submitAnswer(sessionKey(), payload, key)
      answerIds[question.id] = result.answerId
      durations[question.id] = payload.durationMs
      question.status = 'ANSWERED'
      clearDraft(question.id)
      // 后端异步生成反馈；前端轮询结果，完成后一次性展示。
      void reload(true, true)
      await waitForFeedback(question.id, result.answerId)
      await nextTick()
      if (window.matchMedia('(max-width: 1279px)').matches) {
        const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
        feedbackRef.value?.scrollIntoView({
          behavior: reducedMotion ? 'auto' : 'smooth',
          block: 'start',
        })
      }
    } catch (cause) {
      if (cause instanceof ApiError && cause.code === 'DUPLICATE_ANSWER') {
        const existing = typeof cause.details.answerId === 'string' ? cause.details.answerId : ''
        question.status = 'ANSWERED'
        if (existing) answerIds[question.id] = existing
        clearDraft(question.id)
        toast('本题已提交，已加载已有反馈', 'info')
        await loadFeedback(question.id)
        if (!isFeedbackDone()) {
          streamState.value = 'idle'
          streamError.value = ''
        }
      } else {
        toast(errorMessage(cause, '提交失败，请稍后重试'), 'error')
        if (isSessionActive.value && !isAnswered.value) startTimer()
      }
    } finally {
      submitting.value = false
    }
  }

  async function skipCurrent(auto = false): Promise<void> {
    const question = currentQuestion.value
    if (!question || !isSessionActive.value) return
    if (isAnswered.value) {
      toast('本题已提交，无法跳过', 'warning')
      return
    }
    if (question.status === 'SKIPPED') return

    flushDraft()
    clearTimer()
    abortStream()
    try {
      await interviewsApi.skipQuestion(sessionKey(), question.id)
      question.status = 'SKIPPED'
      clearDraft(question.id)
      if (!auto) toast('已跳过本题，跳过题按 0 分计入报告', 'info')
      if (currentIndex.value < questions.value.length - 1) currentIndex.value += 1
      void reload(true, true)
    } catch (cause) {
      toast(errorMessage(cause, '跳过失败，请重试'), 'error')
      if (isSessionActive.value) startTimer()
    }
  }

  function handleTimeout(): void {
    if (!isSessionActive.value || isAnswered.value) return
    if (trimmedLength.value >= 10) {
      toast('单题计时结束，已自动提交当前回答', 'info')
      void submitAnswer()
    } else {
      toast('单题计时结束，回答不足 10 个字，已自动跳过', 'warning')
      void skipCurrent(true)
    }
  }

  function goToQuestion(id: string): void {
    const index = questions.value.findIndex((question) => question.id === id)
    if (index < 0) return
    flushDraft()
    currentIndex.value = index
  }

  function nextQuestion(): void {
    if (isLastQuestion.value) return
    flushDraft()
    currentIndex.value += 1
  }

  async function pause(): Promise<void> {
    if (pausing.value || !isSessionActive.value) return
    pausing.value = true
    try {
      await interviewsApi.pauseInterview(sessionKey())
      if (session.value) session.value.status = 'PAUSED'
      clearTimer()
      flushDraft()
      toast('已暂停，计时同步停止', 'info')
    } catch (cause) {
      toast(errorMessage(cause, '暂停失败，请稍后重试'), 'error')
    } finally {
      pausing.value = false
    }
  }

  async function resume(): Promise<void> {
    if (pausing.value || status.value !== 'PAUSED') return
    pausing.value = true
    try {
      await interviewsApi.resumeInterview(sessionKey())
      if (session.value) session.value.status = 'IN_PROGRESS'
      startTimer()
      toast('已恢复面试', 'success')
    } catch (cause) {
      toast(errorMessage(cause, '恢复失败，请稍后重试'), 'error')
    } finally {
      pausing.value = false
    }
  }

  async function abandon(): Promise<boolean> {
    if (ending.value) return false
    ending.value = true
    try {
      await interviewsApi.abandonInterview(sessionKey())
      if (session.value) session.value.status = 'ABANDONED'
      clearTimer()
      abortStream()
      toast('已放弃本场面试', 'info')
      return true
    } catch (cause) {
      toast(errorMessage(cause, '放弃失败，请稍后重试'), 'error')
      return false
    } finally {
      ending.value = false
    }
  }

  async function endInterview(): Promise<FinishResult | null> {
    if (ending.value) return null
    if (answeredCount.value === 0) {
      toast('至少完成 1 道题后才能生成报告', 'warning')
      return null
    }
    ending.value = true
    try {
      clearTimer()
      flushDraft()
      abortStream()
      const result = await interviewsApi.finishInterview(sessionKey())
      if (session.value) {
        session.value.status = 'REPORT_GENERATING'
        session.value.reportId = result.reportId
      }
      return result
    } catch (cause) {
      toast(errorMessage(cause, '结束面试失败，请稍后重试'), 'error')
      return null
    } finally {
      ending.value = false
    }
  }

  function stopStream(): void {
    if (streamState.value !== 'streaming' && streamState.value !== 'connecting') return
    abortStream()
    streamState.value = 'done'
  }

  async function retryFeedback(): Promise<void> {
    const question = currentQuestion.value
    if (!question) return
    const answerId = answerIds[question.id]
    if (!answerId) {
      await loadFeedback(question.id)
      return
    }
    streamState.value = 'connecting'
    streamError.value = ''
    try {
      const feedback = await interviewsApi.generateAnswerFeedback(sessionKey(), answerId)
      applyFeedback(question.id, feedback)
    } catch (cause) {
      streamState.value = 'error'
      streamError.value = errorMessage(cause, '重新评分失败，请稍后重试')
    }
  }

  watch(
    () => currentQuestion.value?.id,
    () => resetQuestionState(),
    { immediate: true },
  )

  watch(
    () => session.value?.status,
    (next) => {
      if (next === 'PAUSED') clearTimer()
      else if ((next === 'IN_PROGRESS' || next === 'READY') && !isAnswered.value) startTimer()
    },
  )

  function handleBeforeUnload(): void {
    flushDraft()
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('beforeunload', handleBeforeUnload)
  }

  onBeforeUnmount(() => {
    window.removeEventListener('beforeunload', handleBeforeUnload)
    flushDraft()
    clearTimer()
    abortStream()
  })

  return {
    session,
    questions,
    loading,
    error,
    status,
    config,
    totalQuestions,
    jdTitle,
    jdCompany,
    interviewerStyle,
    timeLimit,
    isSessionActive,
    isPaused,
    isPreparing,
    isFinished,
    isCompleted,
    completedReportId,
    answeredCount,
    skippedCount,
    unansweredCount,
    progressPercent,
    currentQuestion,
    currentQuestionId,
    currentStatus,
    isAnswered,
    answerDurationText,
    isLastQuestion,
    answerText,
    remaining,
    submitting,
    ending,
    pausing,
    streamState,
    streamText,
    streamError,
    activeFeedback,
    feedbackRef,
    canSubmit,
    canAdvance,
    timerVisible,
    reload,
    submitAnswer,
    skipCurrent,
    nextQuestion,
    goToQuestion,
    pause,
    resume,
    abandon,
    endInterview,
    stopStream,
    retryFeedback,
  }
}