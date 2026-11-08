<script setup lang="ts">
import { CircleAlert, CircleCheck, Info, TriangleAlert, X } from 'lucide-vue-next'
import { useToast } from '@/composables/useToast'
import type { ToastVariant } from '@/types'

const { toasts, dismissToast } = useToast()

const icons = { success: CircleCheck, error: CircleAlert, warning: TriangleAlert, info: Info }
const styles: Record<ToastVariant, string> = {
  success: 'border-success/30 text-success-strong',
  error: 'border-danger/30 text-danger-strong',
  warning: 'border-warning/30 text-warning-strong',
  info: 'border-info/30 text-info-strong',
}
</script>

<template>
  <Teleport to="body">
    <div
      class="pointer-events-none fixed bottom-20 right-4 z-toast flex w-[min(360px,calc(100vw-2rem))] flex-col gap-2 lg:bottom-4"
      aria-live="polite"
    >
      <TransitionGroup
        enter-active-class="duration-200 ease-out"
        enter-from-class="translate-y-2 opacity-0"
        enter-to-class="translate-y-0 opacity-100"
        leave-active-class="duration-150 ease-in"
        leave-from-class="opacity-100"
        leave-to-class="translate-y-1 opacity-0"
      >
        <div
          v-for="item in toasts"
          :key="item.id"
          :class="[
            'pointer-events-auto flex items-start gap-3 rounded-lg border bg-surface p-3 shadow-pop',
            styles[item.variant],
          ]"
        >
          <component :is="icons[item.variant]" class="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <div class="min-w-0 flex-1">
            <p class="text-sm font-medium text-text">{{ item.title }}</p>
            <p v-if="item.description" class="mt-0.5 text-xs text-text-muted">
              {{ item.description }}
            </p>
          </div>
          <button
            type="button"
            class="-m-1 flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-text-muted hover:bg-surface-2 hover:text-text max-sm:h-11 max-sm:w-11"
            aria-label="关闭提示"
            @click="dismissToast(item.id)"
          >
            <X class="h-3.5 w-3.5" />
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>
