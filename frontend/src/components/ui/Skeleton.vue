<script setup lang="ts">
import { cn } from '@/utils/cn'

withDefaults(defineProps<{ class?: string; lines?: number }>(), { lines: 3 })
</script>

<template>
  <div :class="cn('space-y-2.5', $props.class)" aria-hidden="true">
    <div
      v-for="line in lines"
      :key="line"
      class="h-3 animate-pulse-soft rounded-sm bg-surface-2"
      :style="{ width: `${100 - ((line - 1) % 3) * 12}%` }"
    />
  </div>
</template>
