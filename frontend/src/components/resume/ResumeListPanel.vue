<script setup lang="ts">
import { FileText, Trash2 } from 'lucide-vue-next'
import type { ResumeParseStatus, ResumeSummary } from '@/types'
import Badge from '@/components/ui/Badge.vue'
import { cn } from '@/utils/cn'
import { formatDate, formatRelative } from '@/utils/format'

defineProps<{ resumes: ResumeSummary[]; selectedId: string }>()
const emit = defineEmits<{ select: [string]; delete: [ResumeSummary] }>()

const statusMeta: Record<
  ResumeParseStatus,
  { label: string; variant: 'success' | 'brand' | 'danger' }
> = {
  READY: { label: '已解析', variant: 'success' },
  PARSING: { label: '解析中', variant: 'brand' },
  FAILED: { label: '解析失败', variant: 'danger' },
}

function selectedClass(selected: boolean): string {
  return selected ? 'border-text/30 bg-surface-2' : 'border-border bg-surface hover:bg-surface-2'
}
</script>

<template>
  <div class="rounded-lg border border-border bg-surface p-3 shadow-card">
    <div class="flex items-center justify-between gap-2 px-1 pb-2">
      <h2 class="text-sm font-semibold text-text">我的简历</h2>
      <span class="tabular text-2xs text-text-muted">{{ resumes.length }} 份</span>
    </div>

    <ul class="space-y-2">
      <li v-for="resume in resumes" :key="resume.id" class="relative">
        <button
          type="button"
          :aria-pressed="resume.id === selectedId"
          :title="`右键可删除 · 更新于 ${formatDate(resume.updatedAt, true)}`"
          :class="
            cn(
              'w-full rounded-md border p-3 pr-11 text-left transition-colors focus-visible:border-text/40',
              selectedClass(resume.id === selectedId),
            )
          "
          @click="emit('select', resume.id)"
          @contextmenu.prevent="emit('delete', resume)"
        >
          <div class="flex items-start gap-2.5">
            <span
              :class="
                cn(
                  'mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-md',
                  resume.status === 'FAILED'
                    ? 'bg-danger/10 text-danger'
                    : resume.status === 'PARSING'
                      ? 'bg-surface-2 text-text-2'
                      : 'bg-surface-2 text-text-muted',
                )
              "
            >
              <FileText class="h-4 w-4" aria-hidden="true" />
            </span>
            <span class="min-w-0 flex-1">
              <span class="block truncate text-sm font-medium text-text">{{ resume.title }}</span>
              <span class="mt-0.5 block truncate text-2xs text-text-muted">{{
                resume.fileName
              }}</span>
              <span class="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1">
                <Badge :variant="statusMeta[resume.status].variant" dot>
                  {{ statusMeta[resume.status].label }}
                </Badge>
                <span class="text-2xs text-text-muted">{{ formatRelative(resume.updatedAt) }}</span>
                <span class="text-2xs text-text-muted">·</span>
                <span class="text-2xs text-text-muted">{{ resume.skillCount }} 项技能</span>
              </span>
            </span>
          </div>
        </button>

        <button
          type="button"
          class="absolute right-2 top-2 flex h-8 w-8 items-center justify-center rounded-md text-text-muted opacity-70 transition-colors hover:bg-danger/10 hover:text-danger focus-visible:opacity-100"
          :aria-label="`删除简历 ${resume.title}`"
          @click="emit('delete', resume)"
        >
          <Trash2 class="h-4 w-4" aria-hidden="true" />
        </button>
      </li>
    </ul>
  </div>
</template>
