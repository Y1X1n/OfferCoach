<script setup lang="ts">
import { computed } from 'vue'
import type { Component } from 'vue'
import type { Resume, ResumeLayout } from '@/types'
import ClassicLayout from './layouts/ClassicLayout.vue'
import CompactLayout from './layouts/CompactLayout.vue'
import MinimalLayout from './layouts/MinimalLayout.vue'
import ModernLayout from './layouts/ModernLayout.vue'

const props = withDefaults(defineProps<{ resume: Resume; layout?: ResumeLayout }>(), {
  layout: 'classic',
})

const layoutComponents: Record<ResumeLayout, Component> = {
  classic: ClassicLayout,
  modern: ModernLayout,
  compact: CompactLayout,
  minimal: MinimalLayout,
}

const activeLayout = computed<ResumeLayout>(
  () => props.layout ?? 'classic',
)
</script>

<template>
  <div
    class="overflow-hidden rounded-lg border border-border bg-surface p-6 sm:p-8"
    aria-label="简历版式预览"
  >
    <div class="min-h-[620px] min-w-0">
      <component :is="layoutComponents[activeLayout]" :resume="resume" />
    </div>
  </div>
</template>
