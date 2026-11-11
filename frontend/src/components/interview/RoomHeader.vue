<script setup lang="ts">
import { computed } from 'vue'
import { Flag } from 'lucide-vue-next'
import { Pause, Play } from 'lucide-vue-next'
import type { InterviewerStyle } from '@/types'
import Button from '@/components/ui/Button.vue'
import Progress from '@/components/ui/Progress.vue'
import TimerRing from '@/components/interview/TimerRing.vue'

const props = defineProps<{
  jdTitle: string
  jdCompany: string
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
  <header
    class="sticky top-14 z-sticky -mx-3 border-b border-border bg-bg/90 px-3 py-3 backdrop-blur sm:-mx-5 sm:px-5 lg:-mx-8 lg:px-8 room:static room:mx-0 room:border-0 room:bg-transparent room:px-0 room:py-0 room:backdrop-blur-none"
  >
    <div class="flex flex-wrap items-start justify-between gap-3">
      <div class="min-w-0">
        <p class="text-2xs font-medium text-text-muted">模拟面试</p>
        <h1 class="mt-0.5 truncate text-lg font-semibold text-text sm:text-xl">
          {{ jdTitle }}
        </h1>
        <p class="mt-0.5 truncate text-xs text-text-muted">
          {{ jdCompany }} · {{ styleLabel }}面试官 · 单题 {{ timeLimitText }}
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
