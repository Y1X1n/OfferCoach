<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { ChevronRight } from 'lucide-vue-next'
import type { ReportSummary } from '@/types'
import { formatDate, formatScore, scoreTone } from '@/utils/format'

defineProps<{ report: ReportSummary }>()

const toneText: Record<ReturnType<typeof scoreTone>, string> = {
  success: 'text-success-strong',
  accent: 'text-accent-strong',
  warning: 'text-warning-strong',
  danger: 'text-danger-strong',
}
</script>

<template>
  <RouterLink
    :to="`/reports/${report.id}`"
    class="flex items-center gap-4 py-3 transition-colors hover:bg-surface-2/60"
    :aria-label="`查看报告：${report.title}，总分 ${report.overallScore ?? '生成中'}`"
  >
    <span class="w-10 shrink-0 text-right">
      <span
        :class="[
          'figure block text-lg leading-6',
          report.overallScore === null ? 'text-text' : toneText[scoreTone(report.overallScore)],
        ]"
      >
        {{ report.overallScore === null ? '—' : formatScore(report.overallScore) }}
      </span>
      <span class="block text-3xs text-muted">总分</span>
    </span>

    <div class="min-w-0 flex-1">
      <p class="truncate text-sm text-text">{{ report.title }}</p>
      <p class="mt-0.5 truncate text-xs text-text-muted">
        {{ report.company }} · {{ formatDate(report.createdAt) }} ·
        {{ report.questionCount }} 题
        <template v-if="report.status === 'GENERATING'"> · 生成中</template>
        <span v-else-if="report.status === 'FAILED'" class="text-danger-strong"> · 生成失败</span>
      </p>
    </div>

    <ChevronRight class="h-4 w-4 shrink-0 text-muted" aria-hidden="true" />
  </RouterLink>
</template>
