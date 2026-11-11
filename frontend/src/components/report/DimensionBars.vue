<script setup lang="ts">
import Progress from '@/components/ui/Progress.vue'
import type { RadarItem } from '@/types'
import { formatScore, scoreTone } from '@/utils/format'

defineProps<{ items: RadarItem[] }>()

const toneText: Record<ReturnType<typeof scoreTone>, string> = {
  success: 'text-success-strong',
  accent: 'text-accent-strong',
  warning: 'text-warning-strong',
  danger: 'text-danger-strong',
}

function tone(score: number): ReturnType<typeof scoreTone> {
  return scoreTone(score * 10)
}
</script>

<template>
  <div class="space-y-4">
    <div v-for="item in items" :key="item.dimension">
      <div class="flex items-baseline justify-between gap-3">
        <span class="text-sm text-text-2">{{ item.label }}</span>
        <span :class="['figure text-sm font-medium', toneText[tone(item.score)]]">
          {{ formatScore(item.score) }}
          <span class="text-2xs font-normal text-muted">/ 10</span>
        </span>
      </div>
      <Progress
        class="mt-1.5"
        :value="item.score"
        :max="10"
        :tone="tone(item.score)"
        :label="`${item.label} ${formatScore(item.score)} 分，满分 10 分`"
      />
    </div>
    <p v-if="items.length === 0" class="text-sm leading-6 text-text-2">本次没有生成维度分数。</p>
  </div>
</template>
