<script setup lang="ts">
import { ref } from 'vue'
import { Volume2 } from 'lucide-vue-next'
import Card from '@/components/ui/Card.vue'
import { getAutoReadSpeech, getSpeechVoice, setAutoReadSpeech, setSpeechVoice } from '@/utils/speechPrefs'
import type { SpeechVoice } from '@/utils/speechPrefs'

const autoReadSpeech = ref(getAutoReadSpeech())
const speechVoice = ref<SpeechVoice>(getSpeechVoice())
const speechVoiceOptions: { value: SpeechVoice; label: string }[] = [
  { value: 'stella', label: '女声（stella）' },
  { value: 'tide', label: '男声（tide）' },
]

function toggleAutoReadSpeech(): void {
  autoReadSpeech.value = !autoReadSpeech.value
  setAutoReadSpeech(autoReadSpeech.value)
}

function chooseSpeechVoice(voice: SpeechVoice): void {
  speechVoice.value = voice
  setSpeechVoice(voice)
}
</script>

<template>
  <Card>
    <div class="flex items-center gap-2">
      <span class="flex h-8 w-8 items-center justify-center rounded-md bg-surface-2 text-text-muted">
        <Volume2 class="h-4 w-4" aria-hidden="true" />
      </span>
      <div>
        <h2 class="text-sm font-semibold text-text">语音朗读</h2>
        <p class="text-xs text-text-muted">模拟考官朗读题目的偏好，仅保存在本机。</p>
      </div>
    </div>

    <div class="mt-4 flex items-center justify-between gap-3">
      <div class="min-w-0">
        <p class="text-xs font-medium text-text">自动朗读题目</p>
        <p class="mt-0.5 text-3xs text-text-muted">进入新题时自动播放；浏览器可能要求先与页面交互一次。</p>
      </div>
      <button
        type="button"
        role="switch"
        :aria-checked="autoReadSpeech"
        aria-label="自动朗读题目"
        :class="[
          'relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors',
          autoReadSpeech ? 'bg-brand' : 'bg-surface-2 ring-1 ring-inset ring-border',
        ]"
        @click="toggleAutoReadSpeech"
      >
        <span
          :class="[
            'inline-block h-4 w-4 transform rounded-full bg-surface shadow-sm transition-transform',
            autoReadSpeech ? 'translate-x-6' : 'translate-x-1',
          ]"
          aria-hidden="true"
        />
      </button>
    </div>

    <div role="group" aria-label="朗读音色" class="mt-4 grid grid-cols-2 gap-2">
      <button
        v-for="option in speechVoiceOptions"
        :key="option.value"
        type="button"
        :aria-pressed="speechVoice === option.value"
        :class="[
          'rounded-md border px-3 py-2 text-xs font-medium transition-colors',
          speechVoice === option.value
            ? 'border-text/30 bg-surface-2 text-text'
            : 'border-border bg-surface text-text-muted hover:bg-surface-2 hover:text-text',
        ]"
        @click="chooseSpeechVoice(option.value)"
      >
        {{ option.label }}
      </button>
    </div>
  </Card>
</template>
