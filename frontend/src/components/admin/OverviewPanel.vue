<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import {
  Activity,
  BarChart3,
  CircleAlert,
  ClipboardCheck,
  FileText,
  MessagesSquare,
  RefreshCw,
  Sparkles,
  UserPlus,
  Users,
} from 'lucide-vue-next'
import AsyncState from '@/components/common/AsyncState.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import { errorMessage } from '@/api/client'
import * as adminApi from '@/api/admin'
import type { AdminOverview, AdminRange } from '@/api/admin'
import { formatScore } from '@/utils/format'

interface RangeOption {
  value: AdminRange
  label: string
}

interface OverviewMetric {
  label: string
  value: string
  hint: string
  icon: Component
}

const ranges: RangeOption[] = [
  { value: '1d', label: '今日' },
  { value: '7d', label: '近 7 天' },
  { value: '30d', label: '近 30 天' },
]

const range = ref<AdminRange>('7d')
const overview = ref<AdminOverview | null>(null)
const loading = ref(true)
const loadError = ref<string | null>(null)

const rangeLabel = computed(
  () => ranges.find((option) => option.value === range.value)?.label ?? '近 7 天',
)

const errorRate = computed(() => {
  const data = overview.value
  if (!data || data.aiCalls <= 0) return '—'
  return `${((data.aiErrors / data.aiCalls) * 100).toFixed(1)}%`
})

const metrics = computed<OverviewMetric[]>(() => {
  const data = overview.value
  if (!data) return []
  return [
    { label: '用户总数', value: String(data.users), hint: '累计注册账号', icon: Users },
    {
      label: '新增用户',
      value: `+${data.newUsers}`,
      hint: `${rangeLabel.value}新增`,
      icon: UserPlus,
    },
    { label: '面试会话', value: String(data.sessions), hint: '区间内会话数', icon: MessagesSquare },
    {
      label: '完成面试',
      value: String(data.completedSessions),
      hint: '区间内完成会话',
      icon: ClipboardCheck,
    },
    { label: '面试报告', value: String(data.reports), hint: '区间内生成报告', icon: FileText },
    {
      label: '平均分',
      value: data.avgScore === null ? '—' : formatScore(data.avgScore),
      hint: '报告总分均值',
      icon: BarChart3,
    },
    { label: 'AI 调用', value: String(data.aiCalls), hint: '区间内总次数', icon: Sparkles },
    {
      label: 'AI 失败',
      value: String(data.aiErrors),
      hint: `错误率 ${errorRate.value}`,
      icon: CircleAlert,
    },
    {
      label: '今日活跃',
      value: String(data.activeToday),
      hint: '今日活跃用户数',
      icon: Activity,
    },
  ]
})

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    overview.value = await adminApi.getOverview(range.value)
  } catch (cause) {
    loadError.value = errorMessage(cause, '指标加载失败')
  } finally {
    loading.value = false
  }
}

function selectRange(next: AdminRange): void {
  if (range.value === next) return
  range.value = next
  void load()
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="min-w-0">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div class="flex items-center gap-2">
        <h2 class="text-base font-semibold text-text">指标概览</h2>
        <Badge variant="neutral">{{ rangeLabel }}</Badge>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <div
          class="flex items-center gap-1 rounded-md bg-surface-2 p-1"
          role="group"
          aria-label="指标时间范围"
        >
          <button
            v-for="option in ranges"
            :key="option.value"
            type="button"
            :aria-pressed="range === option.value"
            :class="[
              'h-11 rounded-sm px-3 text-xs font-medium transition-colors sm:h-8',
              range === option.value
                ? 'bg-surface text-text shadow-card'
                : 'text-text-muted hover:text-text',
            ]"
            @click="selectRange(option.value)"
          >
            {{ option.label }}
          </button>
        </div>
        <Button variant="outline" size="sm" :loading="loading" @click="load">
          <RefreshCw v-if="!loading" class="h-3.5 w-3.5" aria-hidden="true" />
          刷新
        </Button>
      </div>
    </div>

    <AsyncState
      class="mt-4"
      :loading="loading"
      :error="loadError"
      error-title="指标加载失败"
      :loading-lines="5"
      @retry="load"
    >
      <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <Card v-for="metric in metrics" :key="metric.label" padding="sm" class="min-w-0">
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <p class="text-xs text-text-muted">{{ metric.label }}</p>
              <p class="tabular mt-1.5 text-2xl font-semibold tracking-tight text-text">
                {{ metric.value }}
              </p>
              <p class="mt-1 truncate text-xs text-text-muted">{{ metric.hint }}</p>
            </div>
            <span
              class="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-surface-2 text-text-muted"
            >
              <component :is="metric.icon" class="h-4 w-4" aria-hidden="true" />
            </span>
          </div>
        </Card>
      </div>
    </AsyncState>
  </div>
</template>