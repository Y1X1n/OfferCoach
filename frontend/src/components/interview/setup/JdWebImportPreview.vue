<script setup lang="ts">
import { ExternalLink } from 'lucide-vue-next'
import type { JdPreview } from '@/api/jds'
import Alert from '@/components/ui/Alert.vue'
import Badge from '@/components/ui/Badge.vue'

defineProps<{
  preview: JdPreview
  fallbackNotice: string
  mustHave: string[]
  techStack: string[]
}>()
</script>

<template>
  <div class="space-y-3 rounded-md border border-border p-3">
    <Alert v-if="fallbackNotice" variant="warning" title="已改用搜索获取" :description="fallbackNotice" />
    <div>
      <p class="text-sm font-medium text-text">{{ preview.title }}</p>
      <p class="mt-0.5 flex flex-wrap items-center gap-x-2 text-3xs text-text-muted">
        <span>{{ preview.siteName || '招聘网站' }}</span>
        <a
          v-if="preview.sourceUrl"
          :href="preview.sourceUrl"
          target="_blank"
          rel="noreferrer noopener"
          class="inline-flex items-center gap-1 underline-offset-2 hover:underline"
        >
          原始链接
          <ExternalLink class="h-3 w-3" aria-hidden="true" />
        </a>
      </p>
    </div>
    <div v-if="mustHave.length" class="flex flex-wrap gap-1.5">
      <span class="text-3xs text-text-muted">必备技能</span>
      <Badge v-for="skill in mustHave" :key="skill" variant="brand">{{ skill }}</Badge>
    </div>
    <div v-if="techStack.length" class="flex flex-wrap gap-1.5">
      <span class="text-3xs text-text-muted">技术栈</span>
      <Badge v-for="item in techStack" :key="item" variant="neutral">{{ item }}</Badge>
    </div>
    <details class="group">
      <summary class="w-fit cursor-pointer text-xs font-medium text-text-muted hover:text-text">查看 JD 原文</summary>
      <pre
        class="mt-2 max-h-56 overflow-y-auto whitespace-pre-wrap break-words rounded bg-surface-2 p-3 text-3xs leading-5 text-text-muted"
        >{{ preview.rawText }}</pre
      >
    </details>
  </div>
</template>
