<script setup lang="ts">
import { computed } from 'vue'
import type { ResumeLayout } from '@/types'
import { resumeLayouts } from '@/constants/resumeLayouts'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Dialog from '@/components/ui/Dialog.vue'
import { cn } from '@/utils/cn'

const props = defineProps<{ open: boolean; current: ResumeLayout }>()
const emit = defineEmits<{ 'update:open': [boolean]; select: [ResumeLayout] }>()

const layouts = computed(() => resumeLayouts)

function choose(layout: ResumeLayout) {
  emit('select', layout)
}
</script>

<template>
  <Dialog
    :open="open"
    title="选择简历版式"
    description="只改变排版，不修改内容。切换后预览会立即使用新样式。"
    size="lg"
    @update:open="emit('update:open', $event)"
  >
    <div class="grid gap-3 sm:grid-cols-2">
      <div
        v-for="layout in layouts"
        :key="layout.id"
        role="button"
        tabindex="0"
        :aria-pressed="layout.id === props.current"
        :aria-label="`应用版式：${layout.name}`"
        :class="
          cn(
            'flex cursor-pointer flex-col rounded-lg border p-3 text-left transition-colors focus-visible:border-text/40',
            layout.id === props.current
              ? 'border-text/30 bg-surface-2'
              : 'border-border bg-surface hover:border-text/20 hover:bg-surface-2',
          )
        "
        @click="choose(layout.id)"
        @keydown.enter.prevent="choose(layout.id)"
        @keydown.space.prevent="choose(layout.id)"
      >
        <div class="flex items-start justify-between gap-2">
          <div class="min-w-0">
            <p class="text-sm font-semibold text-text">{{ layout.name }}</p>
            <Badge variant="neutral" class="mt-1.5">{{ layout.tag }}</Badge>
          </div>
          <Badge v-if="layout.id === props.current" variant="brand" dot>当前</Badge>
        </div>

        <div
          class="mt-3 aspect-[4/3] overflow-hidden rounded-md border border-border bg-surface-2/50 p-2.5"
        >
          <div v-if="layout.id === 'classic'" class="flex h-full flex-col gap-1.5">
            <div class="mx-auto h-1.5 w-12 rounded-sm bg-text-muted/50" />
            <div class="mx-auto h-1 w-20 rounded-sm bg-text-muted/20" />
            <div v-for="group in 3" :key="`classic-${group}`" class="mt-1 space-y-1">
              <div class="h-1 w-8 rounded-sm bg-text-muted/45" />
              <div class="h-px w-full bg-border" />
              <div class="h-1 w-full rounded-sm bg-text-muted/15" />
              <div class="h-1 w-5/6 rounded-sm bg-text-muted/15" />
            </div>
          </div>

          <div v-else-if="layout.id === 'modern'" class="flex h-full flex-col gap-1.5">
            <div class="h-1.5 w-14 rounded-sm bg-text-muted/50" />
            <div class="h-1 w-10 rounded-sm bg-text-muted/20" />
            <div class="grid flex-1 grid-cols-[1fr_0.5fr] gap-1.5">
              <div class="space-y-1">
                <div class="h-1 w-6 rounded-sm bg-text-muted/45" />
                <div class="h-1 w-full rounded-sm bg-text-muted/15" />
                <div class="h-1 w-full rounded-sm bg-text-muted/15" />
                <div class="mt-1.5 h-1 w-6 rounded-sm bg-text-muted/45" />
                <div class="h-1 w-11/12 rounded-sm bg-text-muted/15" />
                <div class="h-1 w-4/5 rounded-sm bg-text-muted/15" />
              </div>
              <div class="space-y-1 border-l border-border pl-1.5">
                <div class="h-1 w-full rounded-sm bg-text-muted/30" />
                <div class="h-1 w-4/5 rounded-sm bg-text-muted/20" />
                <div class="mt-1.5 h-1 w-full rounded-sm bg-text-muted/30" />
                <div class="h-1 w-3/4 rounded-sm bg-text-muted/20" />
                <div class="h-1 w-4/5 rounded-sm bg-text-muted/20" />
              </div>
            </div>
          </div>

          <div v-else-if="layout.id === 'compact'" class="flex h-full flex-col gap-1">
            <div class="flex items-baseline justify-between gap-1">
              <div class="h-1.5 w-9 rounded-sm bg-text-muted/50" />
              <div class="h-1 w-10 rounded-sm bg-text-muted/20" />
            </div>
            <div class="h-1 w-full rounded-sm bg-text-muted/15" />
            <div v-for="group in 3" :key="`compact-${group}`" class="mt-0.5 space-y-0.5">
              <div class="h-1 w-6 rounded-sm bg-text-muted/45" />
              <div class="h-px w-full bg-border" />
              <div class="h-1 w-full rounded-sm bg-text-muted/15" />
              <div class="h-1 w-full rounded-sm bg-text-muted/15" />
            </div>
            <div class="mt-auto grid grid-cols-2 gap-x-1.5 gap-y-0.5">
              <div class="h-1 rounded-sm bg-text-muted/20" />
              <div class="h-1 rounded-sm bg-text-muted/20" />
              <div class="h-1 rounded-sm bg-text-muted/20" />
              <div class="h-1 rounded-sm bg-text-muted/20" />
            </div>
          </div>

          <div v-else class="flex h-full flex-col">
            <div class="h-2.5 w-16 rounded-sm bg-text-muted/50" />
            <div class="mt-1.5 h-1 w-12 rounded-sm bg-text-muted/20" />
            <div class="mt-5 border-t border-border pt-1.5">
              <div class="h-1 w-5 rounded-sm bg-text-muted/35" />
              <div class="mt-1.5 h-1 w-11/12 rounded-sm bg-text-muted/15" />
            </div>
            <div class="mt-4 border-t border-border pt-1.5">
              <div class="h-1 w-7 rounded-sm bg-text-muted/35" />
              <div class="mt-1.5 h-1 w-full rounded-sm bg-text-muted/15" />
              <div class="mt-1 h-1 w-3/4 rounded-sm bg-text-muted/15" />
            </div>
          </div>
        </div>

        <p class="mt-2.5 text-xs leading-5 text-text-muted">{{ layout.description }}</p>

        <div class="mt-3 flex justify-end">
          <Button size="sm" variant="outline" @click.stop="choose(layout.id)"> 应用此版式 </Button>
        </div>
      </div>
    </div>

    <template #footer>
      <Button variant="outline" @click="emit('update:open', false)">取消</Button>
    </template>
  </Dialog>
</template>
