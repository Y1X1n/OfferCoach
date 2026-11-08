<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { Check, Copy, Lightbulb, RotateCcw, Sparkles, Wand2 } from 'lucide-vue-next'
import type { Feedback } from '@/types'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Progress from '@/components/ui/Progress.vue'
import ScoreRing from '@/components/charts/ScoreRing.vue'
import { useToast } from '@/composables/useToast'

const props = defineProps<{
  feedback: Feedback | null
  text: string
  state: 'idle' | 'connecting' | 'streaming' | 'done' | 'error'
  error?: string
}>()

const emit = defineEmits<{ stop: []; retry: [] }>()
const { toast } = useToast()
const scrollRef = ref<HTMLElement | null>(null)
const copied = ref(false)

const dimensions = computed(() =>
  props.feedback
    ? [
        { key: 'content', label: '内容相关性', score: props.feedback.dimensions.content },
        { key: 'structure', label: '结构化表达', score: props.feedback.dimensions.structure },
        { key: 'depth', label: '专业深度', score: props.feedback.dimensions.depth },
        { key: 'fit', label: '岗位匹配', score: props.feedback.dimensions.fit },
        { key: 'language', label: '语言表达', score: props.feedback.dimensions.language },
      ]
    : [],
)

watch(
  () => props.text,
  async () => {
    await nextTick()
    const element = scrollRef.value
    if (!element) return
    const nearBottom = element.scrollHeight - element.scrollTop - element.clientHeight < 120
    if (nearBottom) element.scrollTop = element.scrollHeight
  },
)

async function copyRewrite() {
  if (!props.feedback?.rewrittenAnswer) return
  try {
    await navigator.clipboard.writeText(props.feedback.rewrittenAnswer)
    copied.value = true
    toast('已复制建议改写', 'success')
    window.setTimeout(() => (copied.value = false), 1800)
  } catch {
    toast('复制失败，请手动选择文本', 'error')
  }
}
</script>

<template>
  <section
    class="flex h-full min-h-[420px] flex-col overflow-hidden rounded-lg border border-border bg-surface shadow-card"
  >
    <header class="flex items-center justify-between gap-3 border-b border-border px-4 py-3">
      <div class="flex items-center gap-2">
        <span class="flex h-7 w-7 items-center justify-center rounded-md bg-brand/10 text-brand">
          <Sparkles class="h-3.5 w-3.5" aria-hidden="true" />
        </span>
        <div>
          <h2 class="text-sm font-semibold text-text">本题反馈</h2>
          <p class="text-2xs text-text-muted">AI 评分与改进建议</p>
        </div>
      </div>
      <Badge v-if="state === 'connecting'" variant="brand" dot>分析中</Badge>
      <Badge v-else-if="state === 'done'" variant="success">已完成</Badge>
      <Badge v-else-if="state === 'error'" variant="danger">失败</Badge>
      <Badge v-else variant="neutral">待作答</Badge>
    </header>

    <div ref="scrollRef" class="scrollbar-thin flex-1 overflow-y-auto p-4">
      <div
        v-if="state === 'idle'"
        class="flex h-full min-h-[320px] flex-col items-center justify-center text-center"
      >
        <div
          class="flex h-12 w-12 items-center justify-center rounded-full bg-surface-2 text-text-muted"
        >
          <Sparkles class="h-5 w-5" aria-hidden="true" />
        </div>
        <h3 class="mt-4 text-sm font-medium text-text">提交回答后，AI 会完成分析并一次性展示反馈</h3>
        <p class="mt-1 max-w-xs text-xs leading-5 text-text-muted">
          评分包含五个维度、原文证据与一条可执行的改进建议。
        </p>
      </div>

      <div
        v-else-if="state === 'connecting'"
        class="flex h-full min-h-[320px] flex-col items-center justify-center text-center"
      >
        <div
          class="flex h-12 w-12 items-center justify-center rounded-full bg-brand/10 text-brand"
        >
          <span class="flex items-center gap-1" aria-hidden="true">
            <span class="h-1.5 w-1.5 animate-pulse-soft rounded-full bg-brand" />
            <span class="h-1.5 w-1.5 animate-pulse-soft rounded-full bg-brand [animation-delay:120ms]" />
            <span class="h-1.5 w-1.5 animate-pulse-soft rounded-full bg-brand [animation-delay:240ms]" />
          </span>
        </div>
        <h3 class="mt-4 text-sm font-medium text-text">正在分析你的回答…</h3>
        <p class="mt-1 max-w-xs text-xs leading-5 text-text-muted">
          结果会在完成后一次性展示，通常只需几秒钟。
        </p>
      </div>

      <div v-else-if="state === 'error'">
        <div class="rounded-md border border-danger/30 bg-danger/5 p-4">
          <p class="text-sm font-medium text-danger">反馈生成失败</p>
          <p class="mt-1 text-xs text-text-muted">{{ error || '网络或服务异常，请重试。' }}</p>
          <Button variant="outline" size="sm" class="mt-4" @click="emit('retry')">
            <RotateCcw class="h-3.5 w-3.5" />
            重新评分
          </Button>
        </div>
      </div>

      <div v-else-if="state === 'done' && feedback" class="animate-fade-in space-y-5">
        <div class="flex items-center gap-4 rounded-lg bg-surface-2/60 p-4">
          <ScoreRing :score="feedback.overallScore" :max="10" :size="108" label="本题" />
          <div class="min-w-0">
            <p class="text-sm font-medium text-text">
              {{
                feedback.overallScore >= 8.5
                  ? '回答质量不错'
                  : feedback.overallScore >= 7
                    ? '有亮点，也有提升空间'
                    : '建议按结构重写'
              }}
            </p>
            <p class="mt-1 text-xs leading-5 text-text-muted">
              本题得分 {{ feedback.overallScore.toFixed(1) }} / 10，重点看下方的证据与改进建议。
            </p>
          </div>
        </div>

        <div class="space-y-2.5">
          <div
            v-for="item in dimensions"
            :key="item.key"
            class="grid grid-cols-[88px_1fr_40px] items-center gap-3"
          >
            <span class="text-xs text-text-muted">{{ item.label }}</span>
            <Progress
              :value="item.score * 10"
              :tone="item.score >= 8 ? 'success' : item.score >= 6.5 ? 'brand' : 'warning'"
            />
            <span class="tabular text-right text-xs font-medium text-text">{{
              item.score.toFixed(1)
            }}</span>
          </div>
        </div>

        <div>
          <h3 class="flex items-center gap-1.5 text-xs font-semibold text-text">
            <Check class="h-3.5 w-3.5 text-success-strong" /> 优点
          </h3>
          <ul class="mt-2 space-y-1.5">
            <li
              v-for="item in feedback.strengths"
              :key="item"
              class="flex gap-2 text-xs leading-5 text-text-muted"
            >
              <span class="mt-2 h-1 w-1 shrink-0 rounded-full bg-success" />
              {{ item }}
            </li>
          </ul>
        </div>

        <div>
          <h3 class="flex items-center gap-1.5 text-xs font-semibold text-text">
            <Lightbulb class="h-3.5 w-3.5 text-warning-strong" /> 改进
          </h3>
          <ul class="mt-2 space-y-1.5">
            <li
              v-for="item in feedback.improvements"
              :key="item"
              class="flex gap-2 text-xs leading-5 text-text-muted"
            >
              <span class="mt-2 h-1 w-1 shrink-0 rounded-full bg-warning" />
              {{ item }}
            </li>
          </ul>
        </div>

        <div>
          <h3 class="text-xs font-semibold text-text">证据</h3>
          <blockquote
            class="mt-2 rounded-md border-l-2 border-brand/40 bg-brand/5 px-3 py-2 text-xs leading-5 text-text-muted"
          >
            <p>“{{ feedback.evidence[0]?.quote }}”</p>
            <p class="mt-1 text-2xs text-text-muted/80">{{ feedback.evidence[0]?.reason }}</p>
          </blockquote>
        </div>

        <div v-if="feedback.rewrittenAnswer">
          <div class="flex items-center justify-between gap-2">
            <h3 class="flex items-center gap-1.5 text-xs font-semibold text-text">
              <Wand2 class="h-3.5 w-3.5 text-brand" /> 建议改写
            </h3>
            <button
              type="button"
              class="-m-1 inline-flex min-h-8 items-center gap-1 rounded p-1 text-2xs text-text-muted hover:text-brand max-sm:min-h-11"
              @click="copyRewrite"
            >
              <Check v-if="copied" class="h-3 w-3" />
              <Copy v-else class="h-3 w-3" />
              {{ copied ? '已复制' : '复制' }}
            </button>
          </div>
          <p class="mt-2 rounded-md bg-surface-2 p-3 text-xs leading-6 text-text-muted">
            {{ feedback.rewrittenAnswer }}
          </p>
        </div>

        <p class="sr-only" aria-live="polite">
          反馈已生成，本题得分 {{ feedback.overallScore.toFixed(1) }} 分。
        </p>
      </div>
    </div>
  </section>
</template>
