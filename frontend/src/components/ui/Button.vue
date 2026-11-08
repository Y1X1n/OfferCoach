<script setup lang="ts">
import { computed } from 'vue'
import { Loader2 } from 'lucide-vue-next'
import { cn } from '@/utils/cn'

type Variant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger'
type Size = 'sm' | 'md' | 'lg'

const props = withDefaults(
  defineProps<{
    variant?: Variant
    size?: Size
    loading?: boolean | undefined
    disabled?: boolean | undefined
    type?: 'button' | 'submit' | 'reset'
    block?: boolean
  }>(),
  {
    variant: 'primary',
    size: 'md',
    loading: false,
    disabled: false,
    type: 'button',
    block: false,
  },
)

const emit = defineEmits<{ click: [MouseEvent] }>()

const variantClass: Record<Variant, string> = {
  primary: 'bg-brand text-brand-foreground shadow-sm hover:brightness-105',
  secondary: 'border border-border bg-surface text-text hover:bg-surface-2',
  outline: 'border border-border bg-transparent text-text hover:bg-surface-2',
  ghost: 'bg-transparent text-text-muted hover:bg-surface-2 hover:text-text',
  danger: 'bg-danger-solid text-white shadow-sm hover:brightness-105',
}

const sizeClass: Record<Size, string> = {
  sm: 'h-8 px-3 text-xs max-sm:h-11',
  md: 'h-10 px-4 text-sm max-sm:h-11',
  lg: 'h-12 px-5 text-base',
}

const classes = computed(() =>
  cn(
    'inline-flex select-none items-center justify-center gap-2 rounded-md font-medium transition-all duration-150 active:scale-98 disabled:cursor-not-allowed disabled:opacity-50',
    variantClass[props.variant],
    sizeClass[props.size],
    props.block && 'w-full',
  ),
)
</script>

<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    :class="classes"
    @click="emit('click', $event)"
  >
    <Loader2 v-if="loading" class="h-4 w-4 animate-spin" aria-hidden="true" />
    <slot />
  </button>
</template>
