<script setup lang="ts">
import { ShieldCheck } from 'lucide-vue-next'
import Card from '@/components/ui/Card.vue'

const allowResumeText = defineModel<boolean>({ required: true })
</script>

<template>
  <Card>
    <div class="flex items-center gap-2">
      <span class="flex h-8 w-8 items-center justify-center rounded-md bg-surface-2 text-text-muted">
        <ShieldCheck class="h-4 w-4" aria-hidden="true" />
      </span>
      <div>
        <h2 class="text-sm font-semibold text-text">隐私</h2>
        <p class="text-xs text-text-muted">控制简历原文是否可用于反馈引用。</p>
      </div>
    </div>

    <div class="mt-4">
      <div class="flex items-start justify-between gap-4">
        <div class="min-w-0">
          <p id="allow-resume-text-label" class="text-sm font-medium text-text">允许使用简历原文</p>
          <p class="mt-1 text-xs leading-5 text-text-muted">
            用于在反馈中引用你的回答原文与简历片段，关闭后仅保留结构化摘要。
          </p>
        </div>
        <button
          type="button"
          role="switch"
          :aria-checked="allowResumeText"
          aria-labelledby="allow-resume-text-label"
          :class="[
            'relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors',
            allowResumeText ? 'bg-brand' : 'bg-text-muted/30',
          ]"
          @click="allowResumeText = !allowResumeText"
        >
          <span class="absolute -inset-2.5" aria-hidden="true" />
          <span
            :class="[
              'inline-block h-5 w-5 rounded-full bg-surface shadow-sm transition-transform',
              allowResumeText ? 'translate-x-5' : 'translate-x-0.5',
            ]"
            aria-hidden="true"
          />
        </button>
      </div>
      <p class="mt-3 text-xs leading-5 text-text-muted">
        修改后随“保存资料”一起提交到 PATCH /users/me；服务端未下发该字段时默认开启。
      </p>
    </div>
  </Card>
</template>
