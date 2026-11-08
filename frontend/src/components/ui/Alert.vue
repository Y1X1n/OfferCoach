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

const styles: Record<Variant, { wrapper: string; icon: string }> = {
  info: { wrapper: 'border-info/30 bg-info/10 text-info-strong', icon: 'text-info-strong' },
  success: {
    wrapper: 'border-success/30 bg-success/10 text-success-strong',
    icon: 'text-success-strong',
  },
  warning: {
    wrapper: 'border-warning/30 bg-warning/10 text-warning-strong',
    icon: 'text-warning-strong',
  },
  danger: {
    wrapper: 'border-danger/30 bg-danger/10 text-danger-strong',
    icon: 'text-danger-strong',
  },
}

const icons = { info: Info, success: CircleCheck, warning: TriangleAlert, danger: CircleAlert }
const currentVariant = computed(() => props.variant ?? 'info')
const icon = computed(() => icons[currentVariant.value])
</script>

<template>
  <div
    :class="
      cn('flex gap-3 rounded-md border p-3 text-sm', styles[currentVariant].wrapper, $props.class)
    "
    :role="currentVariant === 'danger' ? 'alert' : 'status'"
  >
    <component
      :is="icon"
      :class="['mt-0.5 h-4 w-4 shrink-0', styles[currentVariant].icon]"
      aria-hidden="true"
    />
    <div class="min-w-0">
      <p v-if="title" class="font-medium">{{ title }}</p>
      <p v-if="description" class="mt-0.5 opacity-80">{{ description }}</p>
      <slot />
    </div>
  </div>
</template>
