<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { ArrowRight, ClipboardList, Zap } from 'lucide-vue-next'
import type { DifficultyMode, InterviewConfig, InterviewerStyle, Jd, ResumeSummary } from '@/types'
import Alert from '@/components/ui/Alert.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'

defineProps<{
  resume: ResumeSummary | null
  jd: Jd | null
  config: InterviewConfig
  credits: number
  quotaBlocked: boolean
  dailyBlocked: boolean
  canStart: boolean
  missing: string[]
  estimatedMinutes: number
  loading: boolean
}>()

const emit = defineEmits<{ start: [] }>()

const styleLabels: Record<InterviewerStyle, string> = {
  GENTLE: '温和',
  STANDARD: '标准',
  PRESSURE: '压力',
}

const difficultyLabels: Record<DifficultyMode, string> = {
  EASY: '简单',
  MIXED: '混合',
  HARD: '困难',
}

function timeLimitText(seconds: number): string {
  return seconds >= 60 ? `${Math.round(seconds / 60)} 分钟` : `${seconds} 秒`
}
</script>

<template>
  <Card padding="lg" class="lg:sticky lg:top-20">
    <div class="flex items-center gap-2">
      <span class="flex h-8 w-8 items-center justify-center rounded-md bg-brand/10 text-brand">
        <ClipboardList class="h-4 w-4" aria-hidden="true" />
      </span>
      <h2 class="text-sm font-semibold text-text">面试摘要</h2>
    </div>

    <dl class="mt-4 space-y-2.5 text-xs">
      <div class="flex items-start justify-between gap-3">
        <dt class="shrink-0 text-text-muted">简历</dt>
        <dd class="min-w-0 text-right">
          <span class="block truncate font-medium text-text">
            {{ resume?.title ?? '未选择' }}
          </span>
        </dd>
      </div>
      <div class="flex items-start justify-between gap-3">
        <dt class="shrink-0 text-text-muted">岗位</dt>
        <dd class="min-w-0 text-right">
          <span class="block truncate font-medium text-text">
            {{ jd?.title ?? '未选择' }}
          </span>
          <span v-if="jd" class="block truncate text-2xs text-text-muted">
            {{ jd.company }}
          </span>
        </dd>
      </div>
      <div class="flex items-center justify-between gap-3">
        <dt class="text-text-muted">题量</dt>
        <dd class="tabular font-medium text-text">{{ config.questionCount }} 道</dd>
      </div>
      <div class="flex items-center justify-between gap-3">
        <dt class="text-text-muted">难度</dt>
        <dd class="font-medium text-text">{{ difficultyLabels[config.difficulty] }}</dd>
      </div>
      <div class="flex items-center justify-between gap-3">
        <dt class="text-text-muted">面试官</dt>
        <dd class="font-medium text-text">{{ styleLabels[config.interviewerStyle] }}</dd>
      </div>
      <div class="flex items-center justify-between gap-3">
        <dt class="text-text-muted">单题限时</dt>
        <dd class="font-medium text-text">{{ timeLimitText(config.timeLimitSec) }}</dd>
      </div>
      <div class="flex items-center justify-between gap-3">
        <dt class="text-text-muted">作答方式</dt>
        <dd class="font-medium text-text">文字</dd>
      </div>
    </dl>

    <div class="my-4 border-t border-border" />

    <div class="rounded-md bg-surface-2 p-3">
      <div class="flex items-center justify-between gap-3">
        <span class="text-xs text-text-muted">预计时长</span>
        <span class="tabular text-sm font-semibold text-text">约 {{ estimatedMinutes }} 分钟</span>
      </div>
      <p class="mt-1 text-2xs leading-5 text-text-muted">
        按每题约 1 分钟作答 + 反馈生成时间估算。
      </p>
    </div>

    <div
      class="mt-3 flex items-center justify-between gap-3 rounded-md border border-border px-3 py-2.5"
    >
      <span class="flex items-center gap-1.5 text-xs text-text-muted">
        <Zap class="h-3.5 w-3.5 text-brand" aria-hidden="true" />
        额度消耗
      </span>
      <span class="text-xs font-medium text-text">
        1 次
        <span class="font-normal text-text-muted">· 剩余 {{ credits }}</span>
      </span>
    </div>

    <Alert
      v-if="quotaBlocked"
      variant="warning"
      title="面试额度不足"
      description="本月额度已用完，可查看套餐或下月再试。"
      class="mt-3"
    >
      <RouterLink
        to="/plans"
        class="mt-2 inline-flex items-center gap-1 text-xs font-medium underline underline-offset-2"
      >
        查看套餐
        <ArrowRight class="h-3 w-3" aria-hidden="true" />
      </RouterLink>
    </Alert>
    <Alert
      v-else-if="dailyBlocked"
      variant="warning"
      title="今日场次已达上限"
      description="明天再来，或升级套餐提高每日上限。"
      class="mt-3"
    />

    <Button
      class="mt-4"
      block
      size="lg"
      :loading="loading"
      :disabled="!canStart"
      @click="emit('start')"
    >
      开始模拟面试
      <ArrowRight class="h-4 w-4" aria-hidden="true" />
    </Button>
    <p
      v-if="!canStart && missing.length"
      class="mt-2 text-center text-2xs leading-5 text-text-muted"
    >
      {{ missing.join('；') }}
    </p>
    <p v-else class="mt-2 text-center text-2xs leading-5 text-text-muted">
      开始后可随时结束，至少完成 1 题即可生成报告。
    </p>
  </Card>
</template>
