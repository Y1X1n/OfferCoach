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

const variantClass: Record<Variant, string> = {
  brand: 'bg-brand/10 text-brand ring-brand/20',
  success: 'bg-success/10 text-success-strong ring-success/20',
  warning: 'bg-warning/10 text-warning-strong ring-warning/20',
  danger: 'bg-danger/10 text-danger-strong ring-danger/20',
  neutral: 'bg-surface-2 text-text-muted ring-border',
  accent: 'bg-accent/10 text-accent-strong ring-accent/20',
}

const dotClass: Record<Variant, string> = {
  brand: 'bg-brand',
  success: 'bg-success',
  warning: 'bg-warning',
  danger: 'bg-danger',
  neutral: 'bg-text-muted',
  accent: 'bg-accent',
}

const currentVariant = computed(() => props.variant ?? 'neutral')
const classes = computed(() =>
  cn(
    'inline-flex items-center gap-1.5 rounded-full font-medium ring-1 ring-inset',
    variantClass[currentVariant.value],
    props.size === 'sm' ? 'px-2 py-0.5 text-2xs' : 'px-2.5 py-1 text-xs',
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
