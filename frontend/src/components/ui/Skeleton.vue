<script setup lang="ts">
import { cn } from '@/utils/cn'

withDefaults(defineProps<{ class?: string; lines?: number }>(), { lines: 3 })
</script>

<template>
  <div :class="cn('space-y-2', $props.class)" aria-hidden="true">
    <div
      v-for="line in lines"
      :key="line"
      class="relative h-3 overflow-hidden rounded bg-surface-2"
      :style="{ width: `${100 - ((line - 1) % 3) * 12}%` }"
    >
      <div
        class="absolute inset-0 -translate-x-full animate-shimmer bg-gradient-to-r from-transparent via-white/50 to-transparent dark:via-white/10"
      />
    </div>
  </div>
</template>
