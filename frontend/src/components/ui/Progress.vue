<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/utils/cn'

type Tone = 'brand' | 'success' | 'warning' | 'danger' | 'accent'

const props = withDefaults(
  defineProps<{
    value: number
    max?: number
    tone?: Tone
    size?: 'sm' | 'md'
    label?: string | undefined
  }>(),
  { max: 100, tone: 'brand', size: 'sm' },
)

const percent = computed(() => Math.min(100, Math.max(0, (props.value / props.max) * 100)))
const toneClass: Record<Tone, string> = {
  brand: 'bg-text',
  success: 'bg-success',
  warning: 'bg-warning',
  danger: 'bg-danger',
  accent: 'bg-accent',
}
const classes = computed(() =>
  cn('h-1 w-full overflow-hidden rounded-sm bg-surface-2', props.size === 'md' && 'h-1.5'),
)
const currentTone = computed(() => props.tone ?? 'brand')
</script>

<template>
  <div>
    <div
      :class="classes"
      role="progressbar"
      :aria-valuenow="Math.round(percent)"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-label="label"
    >
      <div
        :class="[
          'h-full rounded-sm transition-[width] duration-500 ease-out',
          toneClass[currentTone],
        ]"
        :style="{ width: `${percent}%` }"
      />
    </div>
  </div>
</template>
