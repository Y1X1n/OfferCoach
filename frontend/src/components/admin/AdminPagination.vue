<script setup lang="ts">
import { computed } from 'vue'
import { ChevronLeft, ChevronRight } from 'lucide-vue-next'

const props = defineProps<{
  page: number
  total: number
  size: number
  disabled?: boolean | undefined
}>()

const emit = defineEmits<{ change: [page: number] }>()

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / Math.max(1, props.size))))
const atFirst = computed(() => props.page <= 1)
const atLast = computed(() => props.page >= totalPages.value)
const buttonClass =
  'flex h-11 w-11 items-center justify-center rounded-md border border-border bg-surface text-text transition-colors hover:bg-surface-2 disabled:cursor-not-allowed disabled:opacity-40 sm:h-8 sm:w-8'

function go(target: number): void {
  if (props.disabled === true) return
  const next = Math.min(totalPages.value, Math.max(1, target))
  if (next !== props.page) emit('change', next)
}
</script>

<template>
  <nav class="flex flex-wrap items-center justify-between gap-3" aria-label="分页">
    <p class="tabular text-xs text-text-muted">
      共 {{ total }} 条 · 第 {{ page }} / {{ totalPages }} 页
    </p>
    <div class="flex items-center gap-1.5">
      <button
        type="button"
        :class="buttonClass"
        :disabled="disabled === true || atFirst"
        aria-label="上一页"
        @click="go(page - 1)"
      >
        <ChevronLeft class="h-4 w-4" aria-hidden="true" />
      </button>
      <button
        type="button"
        :class="buttonClass"
        :disabled="disabled === true || atLast"
        aria-label="下一页"
        @click="go(page + 1)"
      >
        <ChevronRight class="h-4 w-4" aria-hidden="true" />
      </button>
    </div>
  </nav>
</template>