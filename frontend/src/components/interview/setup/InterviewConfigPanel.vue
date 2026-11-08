<script setup lang="ts">
import { computed } from 'vue'
import { MessageSquareText, MicOff } from 'lucide-vue-next'
import type { AnswerMode, DifficultyMode, InterviewConfig, InterviewerStyle } from '@/types'
import Badge from '@/components/ui/Badge.vue'
import Card from '@/components/ui/Card.vue'
import { cn } from '@/utils/cn'

const config = defineModel<InterviewConfig>({ required: true })

const questionCountOptions = [5, 8, 10, 12] as const

const difficultyOptions: { value: DifficultyMode; label: string; description: string }[] = [
  { value: 'EASY', label: '简单', description: '基础与项目主线' },
  { value: 'MIXED', label: '混合', description: '由浅入深（推荐）' },
  { value: 'HARD', label: '困难', description: '深挖与压力追问' },
]

const styleOptions: { value: InterviewerStyle; label: string; description: string }[] = [
  { value: 'GENTLE', label: '温和', description: '引导式提问' },
  { value: 'STANDARD', label: '标准', description: '真实面试节奏' },
  { value: 'PRESSURE', label: '压力', description: '追问与质疑' },
]

const timeLimitOptions: { value: number; label: string }[] = [
  { value: 60, label: '1 分钟' },
  { value: 120, label: '2 分钟' },
  { value: 180, label: '3 分钟' },
  { value: 300, label: '5 分钟' },
]

const answerModeOptions: {
  value: AnswerMode
  label: string
  description: string
  disabled: boolean
}[] = [
  { value: 'TEXT', label: '文字作答', description: '边想边写，结构更清晰', disabled: false },
  { value: 'VOICE', label: '语音作答', description: '即将开放，敬请期待', disabled: true },
]

const estimatedMinutes = computed(() => Math.max(5, Math.ceil(config.value.questionCount * 1.25)))

function patch(changes: Partial<InterviewConfig>) {
  config.value = { ...config.value, ...changes }
}

function chooseAnswerMode(option: { value: AnswerMode; disabled: boolean }) {
  if (option.disabled) return
  patch({ answerMode: option.value })
}
</script>

<template>
  <Card padding="lg">
    <section aria-labelledby="setup-config">
      <div class="flex items-start gap-3">
        <span
          class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-brand/10 text-sm font-semibold text-brand"
        >
          3
        </span>
        <div class="min-w-0 flex-1">
          <h2 id="setup-config" class="text-base font-semibold text-text">面试配置</h2>
          <p class="mt-0.5 text-xs leading-5 text-text-muted">
            配置会影响题目数量、难度梯度与追问方式。
          </p>
        </div>
        <Badge variant="neutral">可调整</Badge>
      </div>

      <div class="mt-5 space-y-5">
        <div>
          <div class="flex items-center justify-between gap-3">
            <h3 class="text-sm font-medium text-text">题量</h3>
            <span class="tabular text-2xs text-text-muted">约 {{ estimatedMinutes }} 分钟</span>
          </div>
          <div class="mt-2 grid grid-cols-4 gap-2" role="group" aria-label="题量">
            <button
              v-for="count in questionCountOptions"
              :key="count"
              type="button"
              :aria-pressed="config.questionCount === count"
              :class="
                cn(
                  'rounded-md border px-2 py-2 text-center transition-colors',
                  config.questionCount === count
                    ? 'border-brand bg-brand/10'
                    : 'border-border bg-surface hover:bg-surface-2',
                )
              "
              @click="patch({ questionCount: count })"
            >
              <span
                :class="[
                  'tabular block text-sm font-semibold',
                  config.questionCount === count ? 'text-brand' : 'text-text',
                ]"
              >
                {{ count }}
              </span>
              <span class="block text-3xs text-text-muted">道题</span>
            </button>
          </div>
        </div>

        <div class="grid gap-5 sm:grid-cols-2">
          <div>
            <h3 class="text-sm font-medium text-text">难度</h3>
            <div class="mt-2 grid grid-cols-3 gap-2" role="group" aria-label="难度">
              <button
                v-for="option in difficultyOptions"
                :key="option.value"
                type="button"
                :aria-pressed="config.difficulty === option.value"
                :class="
                  cn(
                    'rounded-md border px-2 py-2 text-center transition-colors',
                    config.difficulty === option.value
                      ? 'border-brand bg-brand/10'
                      : 'border-border bg-surface hover:bg-surface-2',
                  )
                "
                @click="patch({ difficulty: option.value })"
              >
                <span
                  :class="[
                    'block text-xs font-medium',
                    config.difficulty === option.value ? 'text-brand' : 'text-text',
                  ]"
                >
                  {{ option.label }}
                </span>
                <span class="mt-0.5 block text-3xs leading-4 text-text-muted">
                  {{ option.description }}
                </span>
              </button>
            </div>
          </div>

          <div>
            <h3 class="text-sm font-medium text-text">面试官风格</h3>
            <div class="mt-2 grid grid-cols-3 gap-2" role="group" aria-label="面试官风格">
              <button
                v-for="option in styleOptions"
                :key="option.value"
                type="button"
                :aria-pressed="config.interviewerStyle === option.value"
                :class="
                  cn(
                    'rounded-md border px-2 py-2 text-center transition-colors',
                    config.interviewerStyle === option.value
                      ? 'border-brand bg-brand/10'
                      : 'border-border bg-surface hover:bg-surface-2',
                  )
                "
                @click="patch({ interviewerStyle: option.value })"
              >
                <span
                  :class="[
                    'block text-xs font-medium',
                    config.interviewerStyle === option.value ? 'text-brand' : 'text-text',
                  ]"
                >
                  {{ option.label }}
                </span>
                <span class="mt-0.5 block text-3xs leading-4 text-text-muted">
                  {{ option.description }}
                </span>
              </button>
            </div>
          </div>
        </div>

        <div class="grid gap-5 sm:grid-cols-2">
          <div>
            <h3 class="text-sm font-medium text-text">单题时限</h3>
            <div class="mt-2 grid grid-cols-4 gap-2" role="group" aria-label="单题时限">
              <button
                v-for="option in timeLimitOptions"
                :key="option.value"
                type="button"
                :aria-pressed="config.timeLimitSec === option.value"
                :class="
                  cn(
                    'rounded-md border px-1 py-2 text-xs font-medium transition-colors',
                    config.timeLimitSec === option.value
                      ? 'border-brand bg-brand/10 text-brand'
                      : 'border-border bg-surface text-text hover:bg-surface-2',
                  )
                "
                @click="patch({ timeLimitSec: option.value })"
              >
                {{ option.label }}
              </button>
            </div>
          </div>

          <div>
            <h3 class="text-sm font-medium text-text">作答方式</h3>
            <div class="mt-2 grid gap-2 sm:grid-cols-2">
              <button
                v-for="option in answerModeOptions"
                :key="option.value"
                type="button"
                :disabled="option.disabled"
                :aria-pressed="config.answerMode === option.value"
                :class="
                  cn(
                    'flex items-start gap-2 rounded-md border p-2.5 text-left transition-colors',
                    config.answerMode === option.value
                      ? 'border-brand bg-brand/10'
                      : 'border-border bg-surface',
                    option.disabled ? 'cursor-not-allowed opacity-60' : 'hover:bg-surface-2',
                  )
                "
                @click="chooseAnswerMode(option)"
              >
                <component
                  :is="option.value === 'TEXT' ? MessageSquareText : MicOff"
                  :class="[
                    'mt-0.5 h-4 w-4 shrink-0',
                    config.answerMode === option.value ? 'text-brand' : 'text-text-muted',
                  ]"
                  aria-hidden="true"
                />
                <span class="min-w-0 flex-1">
                  <span class="flex flex-wrap items-center gap-1.5 text-xs font-medium text-text">
                    {{ option.label }}
                    <Badge v-if="option.disabled" variant="neutral">即将开放</Badge>
                  </span>
                  <span class="mt-0.5 block text-3xs leading-4 text-text-muted">
                    {{ option.description }}
                  </span>
                </span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>
  </Card>
</template>
