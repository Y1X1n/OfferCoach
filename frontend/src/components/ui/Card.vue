<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/utils/cn'

const props = withDefaults(
  defineProps<{
    padding?: 'none' | 'sm' | 'md' | 'lg'
    hover?: boolean
    class?: string
  }>(),
  { padding: 'md', hover: false },
)

const paddingClass = computed(
  () => ({ none: '', sm: 'p-4', md: 'p-5', lg: 'p-6' })[props.padding],
)
</script>

<template>
  <div
    :class="
      cn(
        'rounded-lg border border-border bg-surface',
        paddingClass,
        hover && 'transition-colors duration-150 hover:border-text/15',
        props.class,
      )
    "
  >
    <slot />
  </div>
</template>
