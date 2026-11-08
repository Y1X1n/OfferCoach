<script setup lang="ts">
import { computed, useId } from 'vue'
import type { RadarItem } from '@/types'

const props = withDefaults(defineProps<{ items: RadarItem[]; size?: number }>(), { size: 280 })

const center = computed(() => props.size / 2)
const radius = computed(() => props.size * 0.3)
const labelRadius = computed(() => radius.value + 24)
const gradientId = `radar-fill-${useId()}`

function point(index: number, ratio: number): { x: number; y: number } {
  const count = Math.max(3, props.items.length)
  const angle = -Math.PI / 2 + (index * 2 * Math.PI) / count
  return {
    x: center.value + Math.cos(angle) * radius.value * ratio,
    y: center.value + Math.sin(angle) * radius.value * ratio,
  }
}

function polygon(ratio: number): string {
  return props.items
    .map((_, index) => {
      const p = point(index, ratio)
      return `${p.x},${p.y}`
    })
    .join(' ')
}

const levels = [0.25, 0.5, 0.75, 1]
const valuePolygon = computed(() =>
  props.items
    .map((item, index) => {
      const p = point(index, Math.min(1, Math.max(0, item.score / 10)))
      return `${p.x},${p.y}`
    })
    .join(' '),
)

const labels = computed(() =>
  props.items.map((item, index) => {
    const count = Math.max(3, props.items.length)
    const angle = -Math.PI / 2 + (index * 2 * Math.PI) / count
    const x = center.value + Math.cos(angle) * labelRadius.value
    const y = center.value + Math.sin(angle) * labelRadius.value
    const anchor =
      Math.abs(Math.cos(angle)) < 0.25 ? 'middle' : Math.cos(angle) > 0 ? 'start' : 'end'
    return { ...item, x, y, anchor }
  }),
)
</script>

<template>
  <svg
    :viewBox="`0 0 ${size} ${size}`"
    class="h-auto w-full max-w-full"
    role="img"
    aria-label="五维能力雷达图"
  >
    <defs>
      <linearGradient :id="gradientId" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0%" stop-color="hsl(var(--brand))" stop-opacity="0.28" />
        <stop offset="100%" stop-color="hsl(var(--accent))" stop-opacity="0.12" />
      </linearGradient>
    </defs>
    <g>
      <polygon
        v-for="level in levels"
        :key="level"
        :points="polygon(level)"
        fill="none"
        stroke="hsl(var(--border))"
        stroke-width="1"
      />
      <line
        v-for="(item, index) in items"
        :key="item.dimension"
        :x1="center"
        :y1="center"
        :x2="point(index, 1).x"
        :y2="point(index, 1).y"
        stroke="hsl(var(--border))"
        stroke-width="1"
      />
      <polygon
        :points="valuePolygon"
        :fill="`url(#${gradientId})`"
        stroke="hsl(var(--brand))"
        stroke-width="2"
        stroke-linejoin="round"
      />
      <circle
        v-for="(item, index) in items"
        :key="`${item.dimension}-dot`"
        :cx="point(index, Math.min(1, Math.max(0, item.score / 10))).x"
        :cy="point(index, Math.min(1, Math.max(0, item.score / 10))).y"
        r="3"
        fill="hsl(var(--brand))"
      />
      <text
        v-for="item in labels"
        :key="`${item.dimension}-label`"
        :x="item.x"
        :y="item.y"
        :text-anchor="item.anchor"
        dominant-baseline="middle"
        fill="hsl(var(--muted))"
        font-size="11"
      >
        {{ item.label }}
      </text>
      <text
        v-for="item in labels"
        :key="`${item.dimension}-score`"
        :x="item.x"
        :y="item.y + 13"
        :text-anchor="item.anchor"
        dominant-baseline="middle"
        fill="hsl(var(--text))"
        font-size="10"
        font-weight="600"
      >
        {{ item.score.toFixed(1) }}
      </text>
    </g>
  </svg>
</template>
