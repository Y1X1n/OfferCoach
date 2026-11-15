<script setup lang="ts">
import { Link2, Search } from 'lucide-vue-next'

type Mode = 'URL' | 'SEARCH'

const mode = defineModel<Mode>({ required: true })
</script>

<template>
  <div class="inline-flex rounded-md border border-border p-0.5" role="tablist">
    <button
      type="button"
      role="tab"
      :aria-selected="mode === 'URL'"
      :class="[
        'inline-flex items-center gap-1.5 rounded px-3 py-1.5 text-xs font-medium transition-colors',
        mode === 'URL' ? 'bg-surface-2 text-text' : 'text-text-muted hover:text-text',
      ]"
      @click="mode = 'URL'"
    >
      <Link2 class="h-3.5 w-3.5" aria-hidden="true" />
      粘贴链接
    </button>
    <button
      type="button"
      role="tab"
      :aria-selected="mode === 'SEARCH'"
      :class="[
        'inline-flex items-center gap-1.5 rounded px-3 py-1.5 text-xs font-medium transition-colors',
        mode === 'SEARCH' ? 'bg-surface-2 text-text' : 'text-text-muted hover:text-text',
      ]"
      @click="mode = 'SEARCH'"
    >
      <Search class="h-3.5 w-3.5" aria-hidden="true" />
      搜索岗位
    </button>
  </div>
</template>
