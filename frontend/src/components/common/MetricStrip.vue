<script setup lang="ts">
import { computed } from 'vue'

interface MetricItem {
  label: string
  value: string | number
  hint?: string
}

const props = defineProps<{ items: MetricItem[]; label?: string }>()

const gridClass = computed(() => {
  if (props.items.length === 3) return 'grid-cols-3'
  if (props.items.length === 2) return 'grid-cols-2'
  return 'grid-cols-2 sm:grid-cols-4'
})
</script>

<template>
  <section
    class="overflow-hidden rounded-lg border border-border bg-border"
    :aria-label="label ?? '概览数据'"
  >
    <div :class="['grid gap-px', gridClass]">
      <div v-for="item in items" :key="item.label" class="bg-surface px-5 py-4">
        <p class="text-2xs text-text-muted">{{ item.label }}</p>
        <p class="figure mt-1 text-2xl leading-8 text-text">{{ item.value }}</p>
        <p v-if="item.hint" class="mt-0.5 text-2xs text-text-muted">{{ item.hint }}</p>
      </div>
    </div>
  </section>
</template>
