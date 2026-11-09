<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { AlertTriangle, FileText, Flag, Loader2, RotateCcw } from 'lucide-vue-next'
import Alert from '@/components/ui/Alert.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import Dialog from '@/components/ui/Dialog.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import Progress from '@/components/ui/Progress.vue'
import AsyncState from '@/components/common/AsyncState.vue'
import AnswerComposer from '@/components/interview/AnswerComposer.vue'
import QuestionNav from '@/components/interview/QuestionNav.vue'
import QuestionPanel from '@/components/interview/QuestionPanel.vue'
import RoomHeader from '@/components/interview/RoomHeader.vue'
import StreamingFeedback from '@/components/interview/StreamingFeedback.vue'
import { useInterviewRoom } from '@/composables/useInterviewRoom'
import { usePolling } from '@/composables/usePolling'
import { useToast } from '@/composables/useToast'

const route = useRoute()
const router = useRouter()
const { toast } = useToast()

const sessionId = computed<string>(() => {
  const raw = route.params.id
  if (typeof raw === 'string') return raw
  return raw?.[0] ?? ''
})

const {
  session,
  questions,
  loading,
  error,
  status,
  totalQuestions,
  jdTitle,
  jdCompany,
  interviewerStyle,
  timeLimit,
  isSessionActive,
  isPaused,
  isPreparing,
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
} = useInterviewRoom(sessionId)

const endDialogOpen = ref(false)
const abandonDialogOpen = ref(false)

const poller = usePolling(async () => {
  await reload(true, true)
  return isPreparing.value
}, 2000)

function goNext(): void {
  if (isLastQuestion.value) {
    endDialogOpen.value = true
    return
  }
  nextQuestion()
}

async function handleFinishInterview(): Promise<void> {
  const result = await endInterview()
  if (!result) return
  endDialogOpen.value = false
  toast('报告生成中', 'info', '生成完成后会自动展示；也可以从报告列表查看进度')
  await router.push({ name: 'report-detail', params: { id: result.reportId } })
}

function openAbandon(): void {
  endDialogOpen.value = false
  abandonDialogOpen.value = true
}

function openReport(): void {
  const reportId = completedReportId.value
  if (!reportId) {
    toast('报告仍在生成中', 'info', '稍后可到报告列表查看')
    return
  }
  void router.push({ name: 'report-detail', params: { id: reportId } })
}

async function handleAbandon(): Promise<void> {
  const ok = await abandon()
  if (!ok) return
  abandonDialogOpen.value = false
  await router.push({ name: 'dashboard' })
}

function goSetup(): void {
  void router.push({ name: 'interview-setup' })
}

function goDashboard(): void {
  void router.push({ name: 'dashboard' })
}

function retryLoad(): void {
  void reload(false)
}

onMounted(async () => {
  await reload(false)
  if (isPreparing.value) poller.start()
})

watch(isPreparing, (preparing) => {
  if (preparing) poller.start()
  else poller.stop()
})

onBeforeUnmount(() => {
  poller.stop()
})

onBeforeRouteLeave(() => {
  if (!isSessionActive.value && !isPaused.value) return true
  return window.confirm(
    `确定离开面试间？已提交的 ${answeredCount.value} 道回答会保留，当前草稿停笔 3 秒后自动暂存。`,
  )
})
</script>

<template>
  <div class="space-y-4">
    <AsyncState
      v-if="error && !session"
      :error="error"
      error-title="面试会话加载失败"
      :loading-lines="6"
      @retry="retryLoad"
    >
      <div class="flex flex-wrap gap-2">
        <Button @click="retryLoad">重试</Button>
        <Button variant="outline" @click="goDashboard">返回工作台</Button>
      </div>
    </AsyncState>

    <AsyncState v-else-if="loading && !session" :loading="true" :loading-lines="6" />

    <div v-else-if="!session" class="py-8">
      <EmptyState
        title="面试会话不存在"
        description="链接可能已失效，或该会话已被清理。你可以重新创建一场模拟面试。"
      >
        <template #action>
          <div class="flex flex-wrap items-center justify-center gap-2">
            <Button @click="goSetup">重新创建面试</Button>
            <Button variant="outline" @click="goDashboard">返回工作台</Button>
          </div>
        </template>
      </EmptyState>
    </div>

    <Card v-else-if="status === 'FAILED'" padding="lg">
      <Alert
        variant="danger"
        title="本场面试出题失败"
        description="额度已按规则返还。可以调整配置后重新创建，或稍后再试。"
      />
      <div class="mt-4 flex flex-wrap justify-end gap-2">
        <Button variant="outline" @click="goDashboard">返回工作台</Button>
        <Button @click="goSetup">重新创建面试</Button>
      </div>
    </Card>

    <Card v-else-if="status === 'ABANDONED'" padding="lg">
      <EmptyState
        :icon="AlertTriangle"
        title="本场面试已放弃"
        description="未提交回答的额度已按规则返还；已作答内容不再计入报告。"
      >
        <template #action>
          <div class="flex flex-wrap items-center justify-center gap-2">
            <Button @click="goSetup">重新创建面试</Button>
            <Button variant="outline" @click="goDashboard">返回工作台</Button>
          </div>
        </template>
      </EmptyState>
    </Card>

    <Card v-else-if="isPreparing" padding="lg">
      <div class="flex flex-col items-center justify-center py-10 text-center">
        <span
          class="flex h-12 w-12 items-center justify-center rounded-full bg-brand/10 text-brand"
        >
          <Loader2 class="h-5 w-5 animate-spin" aria-hidden="true" />
        </span>
        <h2 class="mt-4 text-base font-semibold text-text">正在生成面试题目</h2>
        <p class="mt-1 max-w-md text-sm leading-6 text-text-muted">
          结合你的简历与目标岗位出题，通常需要 5–15 秒；题目就绪后会自动展示。
        </p>
        <p class="mt-3 text-xs text-text-muted">已等待，请保持页面打开。</p>
        <div class="mt-5 flex flex-wrap items-center justify-center gap-2">
          <Button variant="outline" @click="retryLoad">
            <RotateCcw class="h-4 w-4" aria-hidden="true" />
            刷新状态
          </Button>
          <Button variant="ghost" @click="abandonDialogOpen = true">放弃本场</Button>
        </div>
      </div>
    </Card>

    <template v-else>
      <RoomHeader
        :jd-title="jdTitle"
        :jd-company="jdCompany"
        :interviewer-style="interviewerStyle"
        :time-limit="timeLimit"
        :remaining="remaining"
        :timer-visible="timerVisible"
        :answered-count="answeredCount"
        :total-questions="totalQuestions"
        :progress-percent="progressPercent"
        :paused="isPaused"
        :pausing="pausing"
        :active="isSessionActive"
        @end="endDialogOpen = true"
        @pause="pause"
        @resume="resume"
      />

      <Alert v-if="isPaused" variant="warning" title="本场面试已暂停">
        <p class="mt-1 text-xs">计时已停止，草稿会保留；继续后从当前题目接着作答。</p>
        <Button variant="outline" size="sm" class="mt-2" :loading="pausing" @click="resume">
          继续面试
        </Button>
      </Alert>

      <Alert
        v-else-if="isCompleted"
        variant="success"
        title="本场面试已结束"
        description="报告正在生成或已就绪，可以查看逐题评分与改进建议。"
      >
        <Button v-if="completedReportId" variant="outline" size="sm" class="mt-2" @click="openReport">
          <FileText class="h-3.5 w-3.5" aria-hidden="true" />
          查看报告
        </Button>
      </Alert>

      <p class="sr-only" aria-live="polite">
        当前已完成 {{ answeredCount }} 道题，共 {{ totalQuestions }} 道。
      </p>

      <div class="grid grid-cols-1 items-start gap-4 room:grid-cols-[240px_minmax(0,1fr)_400px]">
        <!-- 题目导航 -->
        <div
          class="room-nav scrollbar-thin min-w-0 room:sticky room:top-20 room:max-h-[calc(100vh-6rem)] room:overflow-y-auto"
        >
          <Card padding="sm">
            <div class="mb-2 hidden items-center justify-between px-1 room:flex">
              <h2 class="text-xs font-semibold text-text">题目导航</h2>
              <span class="tabular text-2xs text-text-muted">
                {{ answeredCount }}/{{ totalQuestions }}
              </span>
            </div>
            <QuestionNav
              :questions="questions"
              :current-id="currentQuestionId"
              @select="goToQuestion"
            />
            <div class="mt-3 hidden border-t border-border pt-3 room:block">
              <div class="flex items-center justify-between text-2xs text-text-muted">
                <span>完成进度</span>
                <span class="tabular">{{ progressPercent }}%</span>
              </div>
              <Progress class="mt-1.5" :value="progressPercent" label="面试进度" />
              <p class="mt-2 text-2xs leading-5 text-text-muted">
                已跳过 {{ skippedCount }} 题，未答 {{ unansweredCount }} 题
              </p>
            </div>
          </Card>
        </div>

        <!-- 题目与作答 -->
        <div class="min-w-0">
          <QuestionPanel
            v-if="currentQuestion"
            :question="currentQuestion"
            :total-questions="totalQuestions"
            :is-answered="isAnswered"
            :answer-duration-text="answerDurationText"
          >
            <AnswerComposer
              v-model="answerText"
              :can-submit="canSubmit"
              :submitting="submitting"
              :is-answered="isAnswered"
              :is-session-active="isSessionActive"
              :paused="isPaused"
              :current-status="currentStatus"
              :is-last-question="isLastQuestion"
              :can-advance="canAdvance"
              :stream-state="streamState"
              :has-report="Boolean(completedReportId)"
              @submit="submitAnswer"
              @skip="skipCurrent(false)"
              @next="goNext"
              @open-report="openReport"
            />
          </QuestionPanel>

          <EmptyState
            v-else
            title="本场面试暂无题目"
            description="题目可能仍在生成，或会话中没有可用题目。可刷新状态或返回重新创建面试。"
          >
            <template #action>
              <div class="flex flex-wrap items-center justify-center gap-2">
                <Button variant="outline" @click="retryLoad">刷新题目</Button>
                <Button @click="goSetup">重新创建面试</Button>
              </div>
            </template>
          </EmptyState>
        </div>

        <!-- 反馈 -->
        <div ref="feedbackRef" class="min-w-0 room:sticky room:top-20 room:h-[calc(100vh-6rem)]">
          <StreamingFeedback
            :feedback="activeFeedback"
            :text="streamText"
            :state="streamState"
            :error="streamError"
            @stop="stopStream"
            @retry="retryFeedback"
          />
        </div>
      </div>

      <Dialog
        v-model:open="endDialogOpen"
        title="结束本场面试？"
        description="结束后将根据已作答题目生成报告，未作答题目视为跳过。"
      >
        <div class="space-y-3">
          <div class="grid grid-cols-3 gap-2 text-center">
            <div class="rounded-md bg-surface-2 p-3">
              <p class="tabular text-lg font-semibold text-text">{{ answeredCount }}</p>
              <p class="text-2xs text-text-muted">已作答</p>
            </div>
            <div class="rounded-md bg-surface-2 p-3">
              <p class="tabular text-lg font-semibold text-text">{{ skippedCount }}</p>
              <p class="text-2xs text-text-muted">已跳过</p>
            </div>
            <div class="rounded-md bg-surface-2 p-3">
              <p class="tabular text-lg font-semibold text-text">{{ unansweredCount }}</p>
              <p class="text-2xs text-text-muted">未作答</p>
            </div>
          </div>
          <Alert
            v-if="answeredCount === 0"
            variant="warning"
            title="至少完成 1 道题"
            description="当前没有可评分的回答，暂时无法生成报告。"
          />
          <p class="text-xs leading-5 text-text-muted">
            未作答题目按跳过计入报告（0 分）；生成后可在报告页查看逐题评分与改进建议。
          </p>
        </div>
        <template #footer>
          <Button variant="ghost" @click="endDialogOpen = false">继续作答</Button>
          <Button variant="outline" @click="openAbandon">
            <AlertTriangle class="h-4 w-4" aria-hidden="true" />
            放弃本场
          </Button>
          <Button
            variant="danger"
            :loading="ending"
            :disabled="answeredCount === 0"
            @click="handleFinishInterview"
          >
            <Flag class="h-4 w-4" aria-hidden="true" />
            结束并生成报告
          </Button>
        </template>
      </Dialog>

      <Dialog
        v-model:open="abandonDialogOpen"
        title="放弃本场面试？"
        description="未提交任何回答时会返还面试额度；已作答部分不再返还，也不会生成报告。"
        size="sm"
      >
        <Alert
          variant="warning"
          title="影响范围"
          description="放弃后本场无法恢复，需要重新创建面试。已提交的回答不会进入报告。"
        />
        <template #footer>
          <Button variant="outline" @click="abandonDialogOpen = false">继续作答</Button>
          <Button variant="danger" :loading="ending" @click="handleAbandon">确认放弃</Button>
        </template>
      </Dialog>
    </template>
  </div>
</template>

<style scoped>
@media (max-width: 1399px) {
  .room-nav :deep(nav) {
    flex-direction: row;
    overflow-x: auto;
    padding-bottom: 0.25rem;
  }

  .room-nav :deep(nav > button) {
    width: auto;
    min-width: 9.75rem;
  }
}
</style>