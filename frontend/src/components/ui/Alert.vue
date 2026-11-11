<script setup lang="ts">
import { computed } from 'vue'
import { CircleAlert, CircleCheck, Info, TriangleAlert } from 'lucide-vue-next'
import { cn } from '@/utils/cn'

type Variant = 'info' | 'success' | 'warning' | 'danger'

const props = withDefaults(
  defineProps<{
    variant?: Variant
    title?: string | undefined
    description?: string | undefined
    class?: string
  }>(),
  { variant: 'info' },
)

/* Alerts keep the ramp and only tint the hairline and the icon, never a filled block. */
const styles: Record<Variant, { wrapper: string; icon: string }> = {
  info: { wrapper: 'border-info/40 bg-surface text-text', icon: 'text-info-strong' },
  success: { wrapper: 'border-success/40 bg-surface text-text', icon: 'text-success-strong' },
  warning: { wrapper: 'border-warning/45 bg-surface text-text', icon: 'text-warning-strong' },
  danger: { wrapper: 'border-danger/45 bg-surface text-text', icon: 'text-danger-strong' },
}

const icons = { info: Info, success: CircleCheck, warning: TriangleAlert, danger: CircleAlert }
const currentVariant = computed(() => props.variant ?? 'info')
const icon = computed(() => icons[currentVariant.value])
</script>

<template>
  <div
    :class="cn('flex gap-3 rounded-md border p-4 text-sm', styles[currentVariant].wrapper, $props.class)"
    :role="currentVariant === 'danger' ? 'alert' : 'status'"
  >
    <component
      :is="icon"
      :class="['mt-0.5 h-4 w-4 shrink-0', styles[currentVariant].icon]"
      aria-hidden="true"
    />
    <div class="min-w-0 flex-1">
      <p v-if="title" class="font-medium text-text">{{ title }}</p>
      <p v-if="description" class="mt-1 leading-6 text-text-2">{{ description }}</p>
      <slot />
    </div>
  </div>
</template>
