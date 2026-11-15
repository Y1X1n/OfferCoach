<script setup lang="ts">
import type { ConfigFormState } from '@/composables/useAiConfig'

const form = defineModel<ConfigFormState>({ required: true })

type ToolToggleKey = 'asrEnabled' | 'ttsEnabled' | 'ocrEnabled' | 'searchEnabled' | 'fetchEnabled'

const toolToggles: { key: ToolToggleKey; label: string; hint: string }[] = [
  { key: 'asrEnabled', label: '语音作答', hint: '录音转写为文字' },
  { key: 'ttsEnabled', label: '题目朗读', hint: '模拟考官朗读题干' },
  { key: 'ocrEnabled', label: '简历 OCR', hint: '扫描件与图片识别' },
  { key: 'searchEnabled', label: '岗位搜索', hint: '联网检索目标岗位' },
  { key: 'fetchEnabled', label: '链接抓取', hint: '抓取岗位链接正文' },
]
</script>

<template>
  <div class="border-t border-border pt-4">
    <p class="text-sm font-medium text-text">语音与识别能力</p>
    <p class="mt-0.5 text-xs text-text-muted">
      三项能力各由独立服务提供，可单独关闭；关闭后对应入口会提示不可用，不影响面试主流程。
    </p>
    <div class="mt-3 grid gap-3 sm:grid-cols-3">
      <label
        v-for="tool in toolToggles"
        :key="tool.key"
        class="flex items-start justify-between gap-3 rounded-md border border-border px-3 py-2.5"
      >
        <span class="min-w-0">
          <span class="block text-xs font-medium text-text">{{ tool.label }}</span>
          <span class="mt-0.5 block text-3xs leading-4 text-text-muted">{{ tool.hint }}</span>
        </span>
        <button
          type="button"
          role="switch"
          :aria-checked="form[tool.key]"
          :aria-label="tool.label"
          :class="[
            'relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors',
            form[tool.key] ? 'bg-brand' : 'bg-surface-2 ring-1 ring-inset ring-border',
          ]"
          @click="form[tool.key] = !form[tool.key]"
        >
          <span
            :class="[
              'inline-block h-3.5 w-3.5 transform rounded-full bg-surface shadow-sm transition-transform',
              form[tool.key] ? 'translate-x-4' : 'translate-x-1',
            ]"
            aria-hidden="true"
          />
        </button>
      </label>
    </div>
    <div class="mt-3 flex flex-col gap-1.5">
      <label for="ai-config-tts-voice" class="text-sm font-medium text-text">题目朗读默认音色</label>
      <select
        id="ai-config-tts-voice"
        v-model="form.ttsDefaultVoice"
        class="h-11 w-full rounded-md border border-border bg-surface px-3 text-sm text-text transition-colors focus:border-accent/60 sm:h-9"
      >
        <option value="stella">stella（女声，默认）</option>
        <option value="tide">tide（男声）</option>
      </select>
    </div>
  </div>
</template>
