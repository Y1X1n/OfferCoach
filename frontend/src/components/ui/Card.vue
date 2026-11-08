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
  () => ({ none: '', sm: 'p-3', md: 'p-5', lg: 'p-6 sm:p-8' })[props.padding],
)
</script>

<template>
  <div
    :class="
      cn(
        'rounded-lg border border-border bg-surface shadow-card',
        paddingClass,
        hover && 'transition-shadow duration-200 hover:shadow-pop',
        props.class,
      )
    "
  >
    <slot />
  </div>
</template>
