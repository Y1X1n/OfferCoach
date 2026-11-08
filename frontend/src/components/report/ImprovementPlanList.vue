<script setup lang="ts">
import { computed } from 'vue'
import { BookOpen, Wrench } from 'lucide-vue-next'
import Card from '@/components/ui/Card.vue'
import type { ImprovementItem } from '@/types'

const props = defineProps<{ items: ImprovementItem[] }>()

const visibleItems = computed(() =>
  [...props.items].sort((a, b) => a.priority - b.priority).slice(0, 5),
)
</script>

<template>
  <Card padding="lg" class="print:border-0 print:shadow-none">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <h2 class="flex items-center gap-2 text-base font-semibold text-text">
        <Wrench class="h-4 w-4 text-brand" aria-hidden="true" />
        改进计划
      </h2>
      <span class="text-xs text-text-muted">按优先级排序</span>
    </div>

    <ol v-if="visibleItems.length" class="mt-4 space-y-3">
      <li
        v-for="item in visibleItems"
        :key="item.priority"
        class="flex gap-3 rounded-md border border-border bg-surface-2/60 p-3 sm:p-4"
      >
        <span
          class="tabular flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-brand text-xs font-semibold text-brand-foreground"
          aria-hidden="true"
        >
          {{ item.priority }}
        </span>
        <div class="min-w-0">
          <p class="text-sm font-medium leading-6 text-text">{{ item.action }}</p>
          <p class="mt-0.5 text-xs leading-5 text-text-muted">{{ item.reason }}</p>
          <p
            class="mt-2 inline-flex items-center gap-1.5 rounded-sm bg-surface px-2 py-1 text-xs text-text-muted ring-1 ring-inset ring-border"
          >
            <BookOpen class="h-3 w-3" aria-hidden="true" />
            {{ item.resource }}
          </p>
        </div>
      </li>
    </ol>

    <p v-else class="mt-4 text-sm leading-6 text-text-muted">
      本次暂无改进计划，可以继续保持当前的答题节奏。
    </p>
  </Card>
</template>
