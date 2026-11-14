<script setup lang="ts">
import { computed } from 'vue'
import { ChevronDown, Lock, PauseCircle, Sparkles, Volume2 } from 'lucide-vue-next'
import type { Difficulty, Question, QuestionDimension } from '@/types'
import Alert from '@/components/ui/Alert.vue'
import Badge from '@/components/ui/Badge.vue'
import Card from '@/components/ui/Card.vue'

const props = defineProps<{
  question: Question
  totalQuestions: number
  isAnswered: boolean
  answerDurationText: string
  speaking?: boolean
  speechError?: string
}>()

const emit = defineEmits<{ 'toggle-speech': [] }>()

const dimensionLabels: Record<QuestionDimension, string> = {
  DEPTH: '专业深度',
  PROJECT: '项目经历',
  BEHAVIOR: '行为面试',
  FIT: '岗位匹配',
}

const difficultyLabels: Record<Difficulty, string> = {
  EASY: '简单',
  MEDIUM: '中等',
  HARD: '困难',
}

const difficultyVariants: Record<Difficulty, 'success' | 'warning' | 'danger'> = {
  EASY: 'success',
  MEDIUM: 'warning',
  HARD: 'danger',
}

const dimensionLabel = computed(() => dimensionLabels[props.question.dimension])
const difficultyLabel = computed(() => difficultyLabels[props.question.difficulty])
const difficultyVariant = computed(() => difficultyVariants[props.question.difficulty])
</script>

<template>
  <Card padding="lg">
    <section :aria-label="`第 ${question.seq} 题`">
      <div class="flex flex-wrap items-center gap-2">
        <span
          class="inline-flex items-center rounded-full bg-surface-2 px-2.5 py-1 text-2xs font-medium text-text-muted"
        >
          第 {{ question.seq }} 题 / 共 {{ totalQuestions }} 题
        </span>
        <Badge variant="brand">{{ dimensionLabel }}</Badge>
        <Badge :variant="difficultyVariant">{{ difficultyLabel }}</Badge>
        <Badge v-if="isAnswered" variant="success" dot class="ml-auto">
          已作答<template v-if="answerDurationText"> · {{ answerDurationText }}</template>
        </Badge>
        <Badge v-else-if="question.status === 'SKIPPED'" variant="neutral" class="ml-auto">
          已跳过
        </Badge>
      </div>

      <h2 class="mt-4 text-xl font-semibold leading-9 text-text sm:text-2xl sm:leading-10">
        {{ question.text }}
      </h2>

      <div class="mt-3 flex flex-wrap items-center gap-3">
        <button
          type="button"
          class="inline-flex items-center gap-1.5 rounded-md border border-border px-2.5 py-1.5 text-xs font-medium text-text-muted transition-colors hover:border-accent/50 hover:text-text"
          :aria-label="speaking ? '停止朗读题目' : '朗读题目'"
          :aria-pressed="speaking ?? false"
          @click="emit('toggle-speech')"
        >
          <PauseCircle v-if="speaking" class="h-3.5 w-3.5" aria-hidden="true" />
          <Volume2 v-else class="h-3.5 w-3.5" aria-hidden="true" />
          {{ speaking ? '停止朗读' : '朗读题目' }}
        </button>
        <span v-if="speechError" class="text-2xs text-danger" role="alert" aria-live="polite">
          {{ speechError }}
        </span>
      </div>

      <details v-if="question.whyThisQuestion" class="group mt-3">
        <summary
          class="flex w-fit cursor-pointer list-none items-center gap-1.5 text-xs font-medium text-text-muted transition-colors hover:text-text [&::-webkit-details-marker]:hidden"
        >
          <Sparkles class="h-3.5 w-3.5" aria-hidden="true" />
          为什么问这题
          <ChevronDown
            class="h-3.5 w-3.5 transition-transform duration-200 group-open:rotate-180"
            aria-hidden="true"
          />
        </summary>
        <p class="mt-2 rounded-md bg-surface-2 px-3 py-2 text-xs leading-5 text-text-muted">
          {{ question.whyThisQuestion }}
        </p>
      </details>

      <details v-if="question.referencePoints.length" class="group mt-2">
        <summary
          class="flex w-fit cursor-pointer list-none items-center gap-1.5 text-xs font-medium text-text-muted transition-colors hover:text-text [&::-webkit-details-marker]:hidden"
        >
          <Lock class="h-3.5 w-3.5" aria-hidden="true" />
          参考要点
          <span class="text-3xs font-normal text-text-muted/80">（建议先独立作答）</span>
          <ChevronDown
            class="h-3.5 w-3.5 transition-transform duration-200 group-open:rotate-180"
            aria-hidden="true"
          />
        </summary>
        <ul class="mt-2 flex flex-wrap gap-1.5">
          <li
            v-for="point in question.referencePoints"
            :key="point"
            class="rounded-full bg-surface-2 px-2.5 py-1 text-2xs text-text-muted"
          >
            {{ point }}
          </li>
        </ul>
      </details>

      <div class="mt-6 border-t border-border pt-5">
        <Alert v-if="isAnswered" variant="success" title="本题已提交">
          <p class="text-current/80 mt-0.5 text-xs">
            以下为提交原文，本题不可重复评分。用时 {{ answerDurationText }}。
          </p>
        </Alert>
        <Alert
          v-else-if="question.status === 'SKIPPED'"
          variant="warning"
          title="本题已跳过"
          description="跳过题按 0 分计入报告；你仍可补答后提交。"
        />

        <slot />
      </div>
    </section>
  </Card>
</template>
