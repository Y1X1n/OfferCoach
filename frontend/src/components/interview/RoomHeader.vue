<script setup lang="ts">
import { computed } from 'vue'
import { ChevronLeft, ExternalLink, Flag } from 'lucide-vue-next'
import { Pause, Play } from 'lucide-vue-next'
import type { InterviewerStyle } from '@/types'
import Button from '@/components/ui/Button.vue'
import Progress from '@/components/ui/Progress.vue'
import TimerRing from '@/components/interview/TimerRing.vue'

const props = defineProps<{
  jdTitle: string
  jdCompany: string
  jdSourceUrl?: string | null
  interviewerStyle: InterviewerStyle
  timeLimit: number
  remaining: number
  timerVisible: boolean
  answeredCount: number
  totalQuestions: number
  progressPercent: number
  paused?: boolean
  pausing?: boolean
  active?: boolean
}>()

const emit = defineEmits<{ end: []; pause: []; resume: [] }>()

const styleLabels: Record<InterviewerStyle, string> = {
  GENTLE: '温和',
  STANDARD: '标准',
  PRESSURE: '压力',
}

const styleLabel = computed(() => styleLabels[props.interviewerStyle])
const timeLimitText = computed(() => {
  const minutes = Math.round(props.timeLimit / 60)
  return minutes >= 1 ? `${minutes} 分钟` : `${props.timeLimit} 秒`
})
</script>

<template>
  <!-- 移动端：专注模式下外壳顶栏隐藏，这里承担返回、计时与结束，
       只保留两行信息，把高度从 ~150px 压到 ~64px。 -->
  <header
    class="sticky top-0 z-sticky -mx-3 border-b border-border bg-bg/95 px-3 py-2 backdrop-blur lg:hidden"
  >
    <div class="flex items-center gap-1.5">
      <button
        type="button"
        class="flex h-11 w-11 shrink-0 items-center justify-center rounded-md text-text-2 transition-colors hover:bg-surface-2 hover:text-text"
        aria-label="退出面试"
        @click="emit('end')"
      >
        <ChevronLeft class="h-5 w-5" aria-hidden="true" />
      </button>

      <div class="min-w-0 flex-1">
        <p class="truncate text-sm font-medium text-text">{{ jdTitle }}</p>
        <div class="mt-1 flex items-center gap-2">
          <Progress class="flex-1" :value="progressPercent" label="面试进度" />
          <span class="tabular shrink-0 text-2xs text-text-muted">
            {{ answeredCount }}/{{ totalQuestions }}
          </span>
        </div>
      </div>

      <TimerRing v-if="timerVisible" :remaining="remaining" :total="timeLimit" :size="40" />

      <button
        v-if="active !== false && !paused"
        type="button"
        class="flex h-11 w-11 shrink-0 items-center justify-center rounded-md text-text-2 transition-colors hover:bg-surface-2 hover:text-text"
        aria-label="暂停面试并停止计时"
        :disabled="pausing"
        @click="emit('pause')"
      >
        <Pause class="h-4 w-4" aria-hidden="true" />
      </button>
      <button
        v-else
        type="button"
        class="flex h-11 w-11 shrink-0 items-center justify-center rounded-md bg-surface-2 text-text transition-colors hover:bg-surface-2/80"
        aria-label="恢复面试并继续计时"
        :disabled="pausing"
        @click="emit('resume')"
      >
        <Play class="h-4 w-4" aria-hidden="true" />
      </button>

      <button
        type="button"
        class="flex h-11 shrink-0 items-center gap-1 rounded-md px-2 text-xs font-medium text-danger-strong transition-colors hover:bg-danger/10"
        @click="emit('end')"
      >
        <Flag class="h-3.5 w-3.5" aria-hidden="true" />
        结束
      </button>
    </div>
  </header>

  <header
    class="sticky top-14 z-sticky -mx-3 hidden border-b border-border bg-bg/90 px-3 py-3 backdrop-blur sm:-mx-5 sm:px-5 lg:-mx-8 lg:block lg:px-8 room:static room:mx-0 room:border-0 room:bg-transparent room:px-0 room:py-0 room:backdrop-blur-none"
  >
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="min-w-0">
        <p class="text-2xs font-medium text-text-muted">模拟面试</p>
        <h1 class="mt-0.5 truncate text-lg font-semibold text-text sm:text-xl">
          {{ jdTitle }}
        </h1>
        <p class="mt-0.5 truncate text-xs text-text-muted">
          {{ jdCompany }} · {{ styleLabel }}面试官 · 单题 {{ timeLimitText }}
          <a
            v-if="jdSourceUrl"
            :href="jdSourceUrl"
            target="_blank"
            rel="noreferrer noopener"
            class="ml-1 inline-flex items-center gap-0.5 underline-offset-2 hover:text-text hover:underline"
          >
            岗位来源
            <ExternalLink class="h-3 w-3" aria-hidden="true" />
          </a>
        </p>
      </div>
      <div class="flex shrink-0 items-center gap-2 sm:gap-3">
        <div class="text-right">
          <p class="text-2xs text-text-muted">已答</p>
          <p class="tabular text-sm font-semibold text-text">
            {{ answeredCount }} / {{ totalQuestions }}
          </p>
        </div>
        <TimerRing v-if="timerVisible" :remaining="remaining" :total="timeLimit" :size="56" />
        <Button
          v-if="active !== false && !paused"
          variant="ghost"
          size="sm"
          :loading="pausing"
          aria-label="暂停面试并停止计时"
          @click="emit('pause')"
        >
          <Pause class="h-3.5 w-3.5" aria-hidden="true" />
          暂停
        </Button>
        <Button
          v-else
          variant="secondary"
          size="sm"
          :loading="pausing"
          aria-label="恢复面试并继续计时"
          @click="emit('resume')"
        >
          <Play class="h-3.5 w-3.5" aria-hidden="true" />
          继续
        </Button>
        <Button variant="outline" size="sm" @click="emit('end')">
          <Flag class="h-3.5 w-3.5" aria-hidden="true" />
          结束面试
        </Button>
      </div>
    </div>

    <div class="mt-3 room:hidden">
      <div class="flex items-center justify-between text-2xs text-text-muted">
        <span>进度 {{ answeredCount }} / {{ totalQuestions }}</span>
        <span class="tabular">{{ progressPercent }}%</span>
      </div>
      <Progress class="mt-1" :value="progressPercent" label="面试进度" />
    </div>
  </header>
</template>
