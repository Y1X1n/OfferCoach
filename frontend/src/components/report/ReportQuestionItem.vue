<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import {
  CheckCircle2,
  ChevronDown,
  Lightbulb,
  Lock,
  MessagesSquare,
  Quote,
  TriangleAlert,
} from 'lucide-vue-next'
import { useAppStore } from '@/stores/app'
import type { QuestionDimension, ReportBreakdownItem } from '@/types'
import { formatScore, scoreTone } from '@/utils/format'

const props = withDefaults(defineProps<{ item: ReportBreakdownItem; open?: boolean }>(), {
  open: false,
})

const store = useAppStore()

const dimensionMeta: Record<QuestionDimension, string> = {
  PROJECT: '项目经历',
  DEPTH: '专业深度',
  BEHAVIOR: '行为面试',
  FIT: '岗位匹配',
}

const toneText: Record<ReturnType<typeof scoreTone>, string> = {
  success: 'text-success-strong',
  accent: 'text-accent-strong',
  warning: 'text-warning-strong',
  danger: 'text-danger-strong',
}

const meta = computed(() => dimensionMeta[props.item.dimension])
const scoreText = computed(() => (props.item.score === null ? '—' : formatScore(props.item.score)))
const tone = computed(() => (props.item.score === null ? null : scoreTone(props.item.score * 10)))
const question = computed(() => props.item.question?.trim() || `第 ${props.item.seq} 题`)
const feedback = computed(() => props.item.feedback ?? null)
const evidence = computed(() => feedback.value?.evidence ?? [])
const rewrittenAnswer = computed(() => feedback.value?.rewrittenAnswer ?? null)
</script>

<template>
  <details
    :open="open"
    class="group rounded-md border border-border bg-surface transition-colors open:border-text/30 print:break-inside-avoid print:border-border"
  >
    <summary
      class="flex cursor-pointer list-none items-start gap-3 rounded-md p-3.5 transition-colors hover:bg-surface-2/60 focus-visible:bg-surface-2/60 sm:items-center sm:p-4 [&::-webkit-details-marker]:hidden"
    >
      <span
        class="tabular flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-surface-2 text-xs font-medium text-text-2"
        aria-hidden="true"
      >
        {{ item.seq }}
      </span>
      <span class="min-w-0 flex-1">
        <span class="block truncate text-sm font-medium text-text" :title="question">{{
          question
        }}</span>
        <span class="mt-1 flex flex-wrap items-center gap-2">
          <span class="text-2xs text-text-muted">{{ meta }}</span>
          <span class="text-2xs text-muted">满分 10 分</span>
        </span>
      </span>
      <span :class="['figure shrink-0 text-lg', tone ? toneText[tone] : 'text-text']">
        {{ scoreText }}
      </span>
      <ChevronDown
        class="mt-1.5 h-4 w-4 shrink-0 text-text-muted transition-transform duration-200 group-open:rotate-180 sm:mt-0"
        aria-hidden="true"
      />
    </summary>

    <div class="space-y-5 border-t border-border p-3.5 sm:p-5">
      <section v-if="item.summary">
        <h4
          class="flex items-center gap-1.5 text-xs text-text-muted"
        >
          <Lightbulb class="h-3.5 w-3.5" aria-hidden="true" />
          本题结论
        </h4>
        <p class="mt-2 whitespace-pre-line rounded-md bg-surface-2/70 p-3 text-sm leading-6 text-text">
          {{ item.summary }}
        </p>
      </section>

      <section v-if="item.answer">
        <h4
          class="flex items-center gap-1.5 text-xs text-text-muted"
        >
          <MessagesSquare class="h-3.5 w-3.5" aria-hidden="true" />
          我的回答
        </h4>
        <p
          class="mt-2 whitespace-pre-line rounded-md bg-surface-2/70 p-3 text-sm leading-6 text-text"
        >
          {{ item.answer }}
        </p>
      </section>

      <div v-if="feedback" class="grid gap-4 sm:grid-cols-2">
        <section>
          <h4
            class="flex items-center gap-1.5 text-xs font-medium text-success-strong"
          >
            <CheckCircle2 class="h-3.5 w-3.5" aria-hidden="true" />
            做得好的地方
          </h4>
          <ul v-if="feedback.strengths.length" class="mt-2 space-y-1.5">
            <li
              v-for="strength in feedback.strengths"
              :key="strength"
              class="flex gap-2 text-sm leading-6 text-text-muted"
            >
              <span
                class="mt-2.5 h-1.5 w-1.5 shrink-0 rounded-full bg-success"
                aria-hidden="true"
              />
              <span>{{ strength }}</span>
            </li>
          </ul>
          <p v-else class="mt-2 text-sm text-text-muted">暂无记录。</p>
        </section>

        <section>
          <h4
            class="flex items-center gap-1.5 text-xs font-medium text-warning-strong"
          >
            <TriangleAlert class="h-3.5 w-3.5" aria-hidden="true" />
            建议改进
          </h4>
          <ul v-if="feedback.improvements.length" class="mt-2 space-y-1.5">
            <li
              v-for="improvement in feedback.improvements"
              :key="improvement"
              class="flex gap-2 text-sm leading-6 text-text-muted"
            >
              <span
                class="mt-2.5 h-1.5 w-1.5 shrink-0 rounded-full bg-warning"
                aria-hidden="true"
              />
              <span>{{ improvement }}</span>
            </li>
          </ul>
          <p v-else class="mt-2 text-sm text-text-muted">暂无记录。</p>
        </section>
      </div>

      <section v-if="feedback">
        <h4
          class="flex items-center gap-1.5 text-xs text-text-muted"
        >
          <Quote class="h-3.5 w-3.5" aria-hidden="true" />
          证据引用
        </h4>
        <ul v-if="evidence.length" class="mt-2 space-y-2">
          <li
            v-for="entry in evidence"
            :key="entry.quote"
            class="rounded-md border-l-2 border-text/30 bg-surface-2/50 py-2 pl-3 pr-2"
          >
            <p class="text-sm leading-6 text-text">「{{ entry.quote }}」</p>
            <p class="mt-0.5 text-xs leading-5 text-text-muted">{{ entry.reason }}</p>
          </li>
        </ul>
        <p v-else class="mt-2 text-sm text-text-muted">本题没有记录证据引用。</p>
      </section>

      <section v-if="feedback">
        <h4
          class="flex items-center gap-1.5 text-xs text-text-muted"
        >
          <Lightbulb class="h-3.5 w-3.5" aria-hidden="true" />
          建议改写
        </h4>
        <p
          v-if="rewrittenAnswer"
          class="mt-2 rounded-md border border-text/15 bg-surface-2 p-3 text-sm leading-6 text-text"
        >
          {{ rewrittenAnswer }}
        </p>
        <p
          v-else-if="store.isPro"
          class="mt-2 rounded-md border border-dashed border-border bg-surface-2/60 p-3 text-sm text-text-muted"
        >
          本题没有生成建议改写。
        </p>
        <div
          v-else
          class="mt-2 flex flex-wrap items-center justify-between gap-3 rounded-md border border-dashed border-border bg-surface-2/60 p-3"
        >
          <p class="flex items-center gap-2 text-sm text-text-muted">
            <Lock class="h-4 w-4 shrink-0" aria-hidden="true" />
            升级 PRO 解锁本题的建议改写
          </p>
          <RouterLink
            to="/plans"
            class="text-sm font-medium text-text-2 hover:text-text"
          >
            查看套餐
          </RouterLink>
        </div>
      </section>
    </div>
  </details>
</template>
