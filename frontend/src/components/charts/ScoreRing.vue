<script setup lang="ts">
import { computed } from 'vue'
import { scoreTone } from '@/utils/format'

const props = withDefaults(
  defineProps<{
    score: number
    max?: number
    size?: number
    label?: string
    sublabel?: string | undefined
  }>(),
  { max: 100, size: 140, label: '总分' },
)

const radius = 52
const circumference = 2 * Math.PI * radius
const percent = computed(() =>
  Math.min(100, Math.max(0, (props.score / Math.max(1, props.max)) * 100)),
)
const dashOffset = computed(() => circumference * (1 - percent.value / 100))
const tone = computed(() => scoreTone(percent.value))
const toneClass = computed(
  () =>
    ({
      success: 'text-success-strong',
      accent: 'text-accent-strong',
      warning: 'text-warning-strong',
      danger: 'text-danger-strong',
    })[tone.value],
)
const displayScore = computed(() =>
  Number.isInteger(props.score) ? String(props.score) : props.score.toFixed(1),
)
const ringSize = computed(() => props.size ?? 140)
</script>

<template>
  <div class="inline-flex flex-col items-center" :style="{ width: `${ringSize}px` }">
    <div class="relative" :style="{ width: `${ringSize}px`, height: `${ringSize}px` }">
      <svg viewBox="0 0 120 120" class="h-full w-full -rotate-90" aria-hidden="true">
        <circle cx="60" cy="60" :r="radius" fill="none" stroke="hsl(var(--border))" stroke-width="6" />
        <circle
          cx="60"
          cy="60"
          :r="radius"
          fill="none"
          stroke="currentColor"
          stroke-width="6"
          stroke-linecap="butt"
          :stroke-dasharray="circumference"
          :stroke-dashoffset="dashOffset"
          :class="['transition-[stroke-dashoffset] duration-700 ease-out', toneClass]"
        />
      </svg>
      <div class="absolute inset-0 flex flex-col items-center justify-center">
        <span
          :class="['figure leading-none', toneClass]"
          :style="{ fontSize: `${ringSize * 0.26}px` }"
        >
          {{ displayScore }}
        </span>
        <span class="mt-1.5 text-2xs text-text-muted">{{ label }}</span>
      </div>
    </div>
    <p v-if="sublabel" class="mt-2 max-w-[14rem] text-center text-xs leading-5 text-text-2">
      {{ sublabel }}
    </p>
  </div>
</template>
