<script setup lang="ts">
import { computed } from 'vue'
import { ScanLine } from 'lucide-vue-next'
import type { Resume } from '@/types'
import Card from '@/components/ui/Card.vue'
import Progress from '@/components/ui/Progress.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import Spinner from '@/components/ui/Spinner.vue'

const props = defineProps<{ resume: Resume }>()

const progress = computed(() => Math.min(100, Math.max(0, props.resume.progress)))
const stage = computed(() => {
  if (progress.value < 20) return '正在上传文件…'
  if (progress.value < 60) return '正在识别版式与文字…'
  if (progress.value < 90) return '正在结构化工作经历与技能…'
  return '正在生成摘要…'
})
</script>

<template>
  <Card padding="lg">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div class="flex min-w-0 items-center gap-3">
        <span
          class="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-brand/10 text-brand"
        >
          <ScanLine class="h-4 w-4" aria-hidden="true" />
        </span>
        <div class="min-w-0">
          <p class="truncate text-sm font-semibold text-text">「{{ resume.title }}」解析中</p>
          <p class="mt-0.5 text-xs text-text-muted">{{ stage }}</p>
        </div>
      </div>
      <Spinner size="sm" label="处理中" />
    </div>

    <div class="mt-4">
      <div class="flex items-center justify-between text-xs text-text-muted">
        <span>解析进度</span>
        <span class="tabular">{{ progress }}%</span>
      </div>
      <Progress :value="progress" tone="brand" size="md" class="mt-2" label="简历解析进度" />
    </div>

    <div class="mt-6 space-y-5">
      <Skeleton :lines="2" />
      <Skeleton :lines="3" />
      <Skeleton :lines="2" />
    </div>

    <p class="mt-5 text-xs text-text-muted">
      解析通常需要几秒钟，完成后会自动展示结构化结果；若长时间没有进展，可删除后重新上传。
    </p>
  </Card>
</template>
