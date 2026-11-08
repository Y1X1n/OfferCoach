<script setup lang="ts">
import { computed } from 'vue'
import { Loader2 } from 'lucide-vue-next'
import { cn } from '@/utils/cn'

const props = withDefaults(
  defineProps<{ size?: 'sm' | 'md' | 'lg'; label?: string | undefined }>(),
  { size: 'md' },
)
const currentSize = computed(() => props.size ?? 'md')

const sizeClass = { sm: 'h-4 w-4', md: 'h-6 w-6', lg: 'h-9 w-9' }
</script>

<template>
  <span class="inline-flex items-center gap-2 text-text-muted" role="status">
    <Loader2 :class="cn('animate-spin', sizeClass[currentSize])" aria-hidden="true" />
    <span v-if="label" class="text-sm">{{ label }}</span>
    <span v-else class="sr-only">加载中</span>
  </span>
</template>
