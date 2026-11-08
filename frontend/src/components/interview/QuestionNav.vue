<script setup lang="ts">
import { CheckCircle2, Circle, CircleDot, MinusCircle } from 'lucide-vue-next'
import type { Question } from '@/types'
import { cn } from '@/utils/cn'

defineProps<{ questions: Question[]; currentId: string }>()
const emit = defineEmits<{ select: [string] }>()

const dimensionLabels = {
  DEPTH: '专业深度',
  PROJECT: '项目经历',
  BEHAVIOR: '行为面试',
  FIT: '岗位匹配',
}

const statusIcon = {
  ANSWERED: CheckCircle2,
  SKIPPED: MinusCircle,
  ASKED: CircleDot,
  PENDING: Circle,
}

const statusClass = {
  ANSWERED: 'text-success-strong',
  SKIPPED: 'text-text-muted',
  ASKED: 'text-brand',
  PENDING: 'text-text-muted/50',
}

const statusLabels: Record<Question['status'], string> = {
  ANSWERED: '已答',
  SKIPPED: '已跳过',
  ASKED: '进行中',
  PENDING: '未开始',
}
</script>

<template>
  <nav
    aria-label="题目导航"
    class="flex gap-2 overflow-x-auto pb-1 contain-layout lg:flex-col lg:overflow-visible lg:pb-0"
  >
    <button
      v-for="question in questions"
      :key="question.id"
      type="button"
      :aria-current="question.id === currentId ? 'step' : undefined"
      :class="
        cn(
          'group flex min-w-[156px] items-center gap-2 rounded-md border p-2.5 text-left transition-colors lg:w-full lg:min-w-0',
          question.id === currentId
            ? 'border-brand/40 bg-brand/10'
            : 'border-border bg-surface hover:bg-surface-2',
        )
      "
      @click="emit('select', question.id)"
    >
      <span
        :class="
          cn(
            'tabular flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-2xs font-semibold',
            question.id === currentId
              ? 'bg-brand text-brand-foreground'
              : 'bg-surface-2 text-text-muted',
          )
        "
      >
        {{ question.seq }}
      </span>
      <span class="min-w-0 flex-1">
        <span class="block truncate text-xs font-medium text-text">{{
          dimensionLabels[question.dimension]
        }}</span>
        <span class="block text-2xs text-text-muted">
          {{
            question.difficulty === 'EASY'
              ? '简单'
              : question.difficulty === 'MEDIUM'
                ? '中等'
                : '困难'
          }}
        </span>
      </span>
      <component
        :is="statusIcon[question.status]"
        :class="['h-4 w-4 shrink-0', statusClass[question.status]]"
        aria-hidden="true"
      />
      <span class="sr-only">{{ statusLabels[question.status] }}</span>
    </button>
  </nav>
</template>
