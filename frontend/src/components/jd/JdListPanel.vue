<script setup lang="ts">
import { BriefcaseBusiness, Trash2 } from 'lucide-vue-next'
import type { Jd } from '@/types'
import Badge from '@/components/ui/Badge.vue'
import { cn } from '@/utils/cn'
import { formatDate, formatRelative, formatScore, scoreTone } from '@/utils/format'

defineProps<{ jds: Jd[]; selectedId: string }>()
const emit = defineEmits<{ select: [string]; delete: [Jd] }>()

const seniorityLabel: Record<Jd['structured']['seniority'], string> = {
  JUNIOR: '初级',
  MID: '中级',
  SENIOR: '高级',
}
</script>

<template>
  <div class="rounded-lg border border-border bg-surface p-3 shadow-card">
    <div class="flex items-center justify-between gap-2 px-1 pb-2">
      <h2 class="text-sm font-semibold text-text">已保存岗位</h2>
      <span class="tabular text-2xs text-text-muted">{{ jds.length }} 个</span>
    </div>

    <ul class="space-y-2">
      <li v-for="jd in jds" :key="jd.id" class="relative">
        <button
          type="button"
          :aria-pressed="jd.id === selectedId"
          :class="
            cn(
              'w-full rounded-md border p-3 pr-11 text-left transition-colors focus-visible:border-text/40',
              jd.id === selectedId
                ? 'border-text/30 bg-surface-2'
                : 'border-border bg-surface hover:bg-surface-2',
            )
          "
          @click="emit('select', jd.id)"
          @contextmenu.prevent="emit('delete', jd)"
        >
          <div class="flex items-start gap-2.5">
            <span
              class="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-surface-2 text-text-muted"
            >
              <BriefcaseBusiness class="h-4 w-4" aria-hidden="true" />
            </span>
            <span class="min-w-0 flex-1">
              <span class="block truncate text-sm font-medium text-text">{{ jd.title }}</span>
              <span class="mt-0.5 block truncate text-2xs text-text-muted">{{ jd.company }}</span>
              <span class="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1">
                <Badge v-if="jd.match" :variant="scoreTone(jd.match.score)" dot>
                  匹配 {{ formatScore(jd.match.score) }}
                </Badge>
                <Badge v-else variant="neutral">待分析</Badge>
                <Badge variant="neutral">{{ seniorityLabel[jd.structured.seniority] }}</Badge>
              </span>
              <span
                class="mt-1 block text-2xs text-text-muted"
                :title="formatDate(jd.createdAt, true)"
              >
                {{ formatRelative(jd.createdAt) }}
              </span>
            </span>
          </div>
        </button>

        <button
          type="button"
          class="absolute right-2 top-2 flex h-8 w-8 items-center justify-center rounded-md text-text-muted opacity-70 transition-colors hover:bg-danger/10 hover:text-danger focus-visible:opacity-100"
          :aria-label="`删除岗位 ${jd.title}`"
          @click="emit('delete', jd)"
        >
          <Trash2 class="h-4 w-4" aria-hidden="true" />
        </button>
      </li>
    </ul>
  </div>
</template>
