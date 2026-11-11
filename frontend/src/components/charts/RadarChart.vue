<script setup lang="ts">
import { computed } from 'vue'
import type { RadarItem } from '@/types'

const props = withDefaults(defineProps<{ items: RadarItem[]; size?: number }>(), { size: 280 })

const center = computed(() => props.size / 2)
const radius = computed(() => props.size * 0.3)
const labelRadius = computed(() => radius.value + 26)

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
        fill="hsl(var(--accent) / 0.1)"
        stroke="hsl(var(--accent))"
        stroke-width="1.5"
        stroke-linejoin="round"
      />
      <circle
        v-for="(item, index) in items"
        :key="`${item.dimension}-dot`"
        :cx="point(index, Math.min(1, Math.max(0, item.score / 10))).x"
        :cy="point(index, Math.min(1, Math.max(0, item.score / 10))).y"
        r="2.5"
        fill="hsl(var(--accent))"
      />
      <text
        v-for="item in labels"
        :key="`${item.dimension}-label`"
        :x="item.x"
        :y="item.y"
        :text-anchor="item.anchor"
        dominant-baseline="middle"
        fill="hsl(var(--text-2))"
        font-size="12"
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
        fill="hsl(var(--text-2))"
        font-size="11"
        font-weight="500"
      >
        {{ item.score.toFixed(1) }}
      </text>
    </g>
  </svg>
</template>
