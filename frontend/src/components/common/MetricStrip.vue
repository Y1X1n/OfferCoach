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
      <div v-for="item in items" :key="item.label" class="bg-surface p-4 sm:p-5">
        <p class="text-xs text-text-muted">{{ item.label }}</p>
        <p class="tabular mt-2 text-2xl font-semibold tracking-tight text-text">{{ item.value }}</p>
        <p v-if="item.hint" class="mt-1 text-xs text-text-muted">{{ item.hint }}</p>
      </div>
    </div>
  </section>
</template>
