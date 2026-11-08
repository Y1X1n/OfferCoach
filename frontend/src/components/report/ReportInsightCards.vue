<script setup lang="ts">
import { CheckCircle2, TriangleAlert } from 'lucide-vue-next'
import Card from '@/components/ui/Card.vue'

defineProps<{ highlights: string[]; risks: string[] }>()
</script>

<template>
  <div class="space-y-4">
    <Card padding="md" class="print:border-0 print:shadow-none">
      <h3 class="flex items-center gap-2 text-sm font-semibold text-text">
        <CheckCircle2 class="h-4 w-4 text-success" aria-hidden="true" />
        表现亮点
        <span class="tabular ml-auto text-xs font-normal text-text-muted"
          >{{ highlights.length }} 条</span
        >
      </h3>
      <ul v-if="highlights.length" class="mt-3 space-y-2">
        <li
          v-for="item in highlights"
          :key="item"
          class="flex gap-2.5 text-sm leading-6 text-text-muted"
        >
          <span class="mt-2.5 h-1.5 w-1.5 shrink-0 rounded-full bg-success" aria-hidden="true" />
          <span>{{ item }}</span>
        </li>
      </ul>
      <p v-else class="mt-3 text-sm leading-6 text-text-muted">
        本次没有记录到突出亮点。下一轮练习可以先保证结论明确、结构完整。
      </p>
    </Card>

    <Card padding="md" class="print:border-0 print:shadow-none">
      <h3 class="flex items-center gap-2 text-sm font-semibold text-text">
        <TriangleAlert class="h-4 w-4 text-warning" aria-hidden="true" />
        风险与短板
        <span class="tabular ml-auto text-xs font-normal text-text-muted"
          >{{ risks.length }} 条</span
        >
      </h3>
      <ul v-if="risks.length" class="mt-3 space-y-2">
        <li
          v-for="item in risks"
          :key="item"
          class="flex gap-2.5 text-sm leading-6 text-text-muted"
        >
          <span class="mt-2.5 h-1.5 w-1.5 shrink-0 rounded-full bg-warning" aria-hidden="true" />
          <span>{{ item }}</span>
        </li>
      </ul>
      <p v-else class="mt-3 text-sm leading-6 text-text-muted">
        本次没有发现明显风险，可以继续保持当前的答题节奏。
      </p>
    </Card>
  </div>
</template>
