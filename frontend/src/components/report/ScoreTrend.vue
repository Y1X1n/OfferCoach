<script setup lang="ts">
import { computed } from 'vue'
import { TrendingUp } from 'lucide-vue-next'
import Card from '@/components/ui/Card.vue'
import type { ReportSummary } from '@/types'
import { formatScore } from '@/utils/format'

const props = defineProps<{ reports: ReportSummary[] }>()

const points = computed(() =>
  [...props.reports]
    .filter((report) => typeof report.overallScore === 'number' && report.status === 'READY')
    .sort((a, b) => Date.parse(a.createdAt) - Date.parse(b.createdAt))
    .slice(-12),
)

const chart = computed(() => {
  const items = points.value
  if (items.length < 2) return null
  const width = 320
  const height = 120
  const pad = 10
  const innerWidth = width - pad * 2
  const innerHeight = height - pad * 2
  const step = innerWidth / (items.length - 1)
  const coords = items.map((item, index) => {
    const score = item.overallScore ?? 0
    return {
      x: pad + index * step,
      y: pad + (1 - score / 100) * innerHeight,
      score,
      label: item.title || item.role,
    }
  })
  return {
    width,
    height,
    polyline: coords.map((point) => `${point.x.toFixed(1)},${point.y.toFixed(1)}`).join(' '),
    coords,
    first: items[0]?.overallScore ?? 0,
    last: items[items.length - 1]?.overallScore ?? 0,
  }
})

const delta = computed(() => {
  const value = chart.value
  if (!value) return null
  return Math.round((value.last - value.first) * 10) / 10
})
</script>

<template>
  <Card padding="lg">
    <div class="flex items-start justify-between gap-3">
      <div>
        <h2 class="flex items-center gap-2 text-sm font-semibold text-text">
          <TrendingUp class="h-4 w-4 text-text-muted" aria-hidden="true" />
          分数趋势
        </h2>
        <p class="mt-1 text-xs text-text-muted">
          最近 {{ points.length }} 份已完成报告的总分变化。
        </p>
      </div>
      <span
        v-if="delta !== null"
        class="tabular text-xs font-medium"
        :class="delta >= 0 ? 'text-success-strong' : 'text-danger-strong'"
      >
        {{ delta >= 0 ? '+' : '' }}{{ delta }} 分
      </span>
    </div>

    <div v-if="chart" class="mt-3">
      <svg
        :viewBox="`0 0 ${chart.width} ${chart.height}`"
        class="h-32 w-full text-accent"
        role="img"
        aria-label="总分趋势折线图"
        preserveAspectRatio="none"
      >
        <line
          x1="10"
          y1="10"
          x2="310"
          y2="10"
          class="stroke-border"
          stroke-width="1"
          stroke-dasharray="3 4"
        />
        <line
          x1="10"
          y1="110"
          x2="310"
          y2="110"
          class="stroke-border"
          stroke-width="1"
          stroke-dasharray="3 4"
        />
        <polyline
          :points="chart.polyline"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
        <circle
          v-for="point in chart.coords"
          :key="point.x"
          :cx="point.x"
          :cy="point.y"
          r="3"
          fill="currentColor"
        >
          <title>{{ point.label }}：{{ formatScore(point.score) }}</title>
        </circle>
      </svg>
      <div class="mt-1 flex justify-between text-2xs text-text-muted">
        <span>{{ formatScore(chart.first) }}</span>
        <span>{{ formatScore(chart.last) }}</span>
      </div>
    </div>
    <p v-else class="mt-4 text-xs text-text-muted">
      至少完成 2 场面试后，这里会显示总分趋势；当前数据还不够。
    </p>
  </Card>
</template>
