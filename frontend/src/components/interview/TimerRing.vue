<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{ remaining: number; total: number; size?: number }>(), {
  size: 64,
})
const ringSize = computed(() => props.size ?? 64)

const radius = 26
const circumference = 2 * Math.PI * radius
const ratio = computed(() => Math.min(1, Math.max(0, props.remaining / Math.max(1, props.total))))
const dashOffset = computed(() => circumference * (1 - ratio.value))
const tone = computed(() =>
  ratio.value <= 0.1 ? 'danger' : ratio.value <= 0.3 ? 'warning' : 'brand',
)
const toneClass = { brand: 'text-brand', warning: 'text-warning', danger: 'text-danger' }
const label = computed(() => {
  const minutes = Math.floor(props.remaining / 60)
  const seconds = props.remaining % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
})
</script>

<template>
  <div class="inline-flex items-center gap-2" :aria-label="`剩余时间 ${label}`">
    <div class="relative" :style="{ width: `${ringSize}px`, height: `${ringSize}px` }">
      <svg viewBox="0 0 64 64" class="h-full w-full -rotate-90" aria-hidden="true">
        <circle
          cx="32"
          cy="32"
          :r="radius"
          fill="none"
          stroke="hsl(var(--border))"
          stroke-width="5"
        />
        <circle
          cx="32"
          cy="32"
          :r="radius"
          fill="none"
          stroke="currentColor"
          stroke-width="5"
          stroke-linecap="round"
          :stroke-dasharray="circumference"
          :stroke-dashoffset="dashOffset"
          :class="['transition-all duration-500', toneClass[tone]]"
        />
      </svg>
      <span
        class="tabular absolute inset-0 flex items-center justify-center text-3xs font-semibold text-text"
      >
        {{ label }}
      </span>
    </div>
    <span class="hidden text-xs text-text-muted sm:inline">单题限时</span>
  </div>
</template>
