<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { ChevronRight } from 'lucide-vue-next'
import ScoreRing from '@/components/charts/ScoreRing.vue'
import Badge from '@/components/ui/Badge.vue'
import type { ReportSummary } from '@/types'
import { formatDate } from '@/utils/format'

defineProps<{ report: ReportSummary }>()
</script>

<template>
  <RouterLink
    :to="`/reports/${report.id}`"
    class="group flex items-center gap-4 rounded-md p-2.5 transition-colors hover:bg-surface-2"
    :aria-label="`查看报告：${report.title}，总分 ${report.overallScore ?? '生成中'}`"
  >
    <ScoreRing :score="report.overallScore ?? 0" :size="72" label="总分" />

    <div class="min-w-0 flex-1">
      <p class="truncate text-sm font-medium text-text">{{ report.title }}</p>
      <p class="mt-0.5 truncate text-xs text-text-muted">
        {{ report.company }} · {{ formatDate(report.createdAt) }}
      </p>
      <div class="mt-1.5 flex flex-wrap items-center gap-2">
        <Badge v-if="report.status === 'READY'" variant="success" size="sm">已生成</Badge>
        <Badge v-else-if="report.status === 'GENERATING'" variant="brand" size="sm" dot>
          生成中
        </Badge>
        <Badge v-else variant="danger" size="sm">生成失败</Badge>
        <span class="text-2xs text-text-muted">共 {{ report.questionCount }} 题</span>
      </div>
    </div>

    <ChevronRight
      class="h-4 w-4 shrink-0 text-text-muted transition-transform group-hover:translate-x-0.5"
      aria-hidden="true"
    />
  </RouterLink>
</template>
