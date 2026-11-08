<script setup lang="ts">
import { computed } from 'vue'
import { X } from 'lucide-vue-next'
import {
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogOverlay,
  DialogPortal,
  DialogRoot,
  DialogTitle,
} from 'reka-ui'
import { cn } from '@/utils/cn'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    description?: string | undefined
    size?: 'sm' | 'md' | 'lg'
    closeOnOverlay?: boolean
  }>(),
  { size: 'md', closeOnOverlay: true },
)

const emit = defineEmits<{ 'update:open': [boolean] }>()
const sizeClass = { sm: 'max-w-md', md: 'max-w-xl', lg: 'max-w-3xl' }
const contentClass = computed(() =>
  cn(
    'fixed left-1/2 top-1/2 z-modal max-h-[90vh] w-[calc(100%-2rem)] -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-lg border border-border bg-surface p-5 shadow-pop sm:p-6',
    'data-[state=open]:animate-fade-in',
    sizeClass[props.size],
  ),
)

function close() {
  emit('update:open', false)
}
</script>

<template>
  <DialogRoot :open="open" @update:open="emit('update:open', $event)">
    <DialogPortal>
      <DialogOverlay
        class="fixed inset-0 z-modal bg-overlay/55 backdrop-blur-sm data-[state=open]:animate-fade-in"
        @click="closeOnOverlay && close()"
      />
      <DialogContent :class="contentClass">
        <div class="flex items-start justify-between gap-4">
          <div class="min-w-0">
            <DialogTitle class="text-lg font-semibold text-text">{{ title }}</DialogTitle>
            <DialogDescription v-if="description" class="mt-1 text-sm text-text-muted">
              {{ description }}
            </DialogDescription>
            <DialogDescription v-else class="sr-only">对话框</DialogDescription>
          </div>
          <DialogClose as-child>
            <button
              type="button"
              class="flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-text-muted transition-colors hover:bg-surface-2 hover:text-text"
              aria-label="关闭"
            >
              <X class="h-4 w-4" />
            </button>
          </DialogClose>
        </div>
        <div class="mt-5">
          <slot />
        </div>
        <div
          v-if="$slots.footer"
          class="mt-6 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end"
        >
          <slot name="footer" />
        </div>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
