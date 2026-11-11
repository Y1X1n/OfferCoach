<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/utils/cn'

type Variant = 'brand' | 'success' | 'warning' | 'danger' | 'neutral' | 'accent'
type Size = 'sm' | 'md'

const props = withDefaults(defineProps<{ variant?: Variant; size?: Size; dot?: boolean }>(), {
  variant: 'neutral',
  size: 'sm',
  dot: false,
})

/* Quiet tags: status reads as text plus a dot, never a tinted pill. */
const variantClass: Record<Variant, string> = {
  brand: 'text-text',
  success: 'text-success-strong',
  warning: 'text-warning-strong',
  danger: 'text-danger-strong',
  neutral: 'text-text-muted',
  accent: 'text-accent-strong',
}

const dotClass: Record<Variant, string> = {
  brand: 'bg-text',
  success: 'bg-success',
  warning: 'bg-warning',
  danger: 'bg-danger',
  neutral: 'bg-muted',
  accent: 'bg-accent',
}

const currentVariant = computed(() => props.variant ?? 'neutral')
const classes = computed(() =>
  cn(
    'inline-flex items-center gap-1.5 font-medium',
    variantClass[currentVariant.value],
    props.size === 'sm' ? 'text-2xs' : 'text-xs',
  ),
)
</script>

<template>
  <span :class="classes">
    <span
      v-if="dot"
      :class="['h-1.5 w-1.5 rounded-full', dotClass[currentVariant]]"
      aria-hidden="true"
    />
    <slot />
  </span>
</template>
