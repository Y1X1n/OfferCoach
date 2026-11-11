<script setup lang="ts">
import { computed } from 'vue'
import { BookOpen } from 'lucide-vue-next'
import Card from '@/components/ui/Card.vue'
import type { ImprovementItem } from '@/types'

const props = defineProps<{ items: ImprovementItem[] }>()

const visibleItems = computed(() =>
  [...props.items].sort((a, b) => a.priority - b.priority).slice(0, 5),
)
</script>

<template>
  <Card>
    <div class="flex items-baseline justify-between gap-2">
      <h2 class="text-base font-medium text-text">改进计划</h2>
      <span class="text-xs text-text-muted">按优先级排序</span>
    </div>

    <ol v-if="visibleItems.length" class="mt-4 divide-y divide-border border-t border-border">
      <li
        v-for="item in visibleItems"
        :key="item.priority"
        class="flex gap-4 py-4"
      >
        <span class="figure w-5 shrink-0 pt-0.5 text-sm text-muted" aria-hidden="true">
          {{ item.priority }}
        </span>
        <div class="min-w-0">
          <p class="text-sm leading-6 text-text">{{ item.action }}</p>
          <p class="mt-1 text-xs leading-5 text-text-muted">{{ item.reason }}</p>
          <p class="mt-2 inline-flex items-center gap-1.5 text-xs text-text-muted">
            <BookOpen class="h-3.5 w-3.5" aria-hidden="true" />
            {{ item.resource }}
          </p>
        </div>
      </li>
    </ol>

    <p v-else class="mt-4 text-sm leading-6 text-text-muted">
      本次暂无改进计划，可以继续保持当前的答题节奏
    </p>
  </Card>
</template>
