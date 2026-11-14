<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowRight, FileText, Send, SkipForward, Timer } from 'lucide-vue-next'
import type { QuestionStatus } from '@/types'
import Button from '@/components/ui/Button.vue'
import Textarea from '@/components/ui/Textarea.vue'
import VoiceRecorder from '@/components/interview/VoiceRecorder.vue'

defineProps<{
  canSubmit: boolean
  submitting: boolean
  isAnswered: boolean
  isSessionActive: boolean
  paused?: boolean
  currentStatus: QuestionStatus
  isLastQuestion: boolean
  canAdvance: boolean
  streamState: 'idle' | 'connecting' | 'streaming' | 'done' | 'error'
  hasReport: boolean
  voiceTranscribing?: boolean
}>()

const model = defineModel<string>({ required: true })
const emit = defineEmits<{
  submit: []
  skip: []
  next: []
  'open-report': []
  'voice-recorded': [audio: Blob, durationMs: number]
  'voice-error': [message: string]
}>()

const trimmedLength = computed(() => model.value.trim().length)
const recorder = ref<InstanceType<typeof VoiceRecorder> | null>(null)

function onRecorded(audio: Blob, durationMs: number): void {
  emit('voice-recorded', audio, durationMs)
}

function onVoiceError(message: string): void {
  emit('voice-error', message)
}
</script>

<template>
  <div class="mt-4">
    <Textarea
      v-model="model"
      :label="isAnswered ? '我的回答' : '你的回答'"
      :rows="8"
      :maxlength="4000"
      show-count
      :disabled="isAnswered || !isSessionActive"
      :hint="
        isAnswered
          ? '本题已提交，不可重复评分。'
          : '至少 10 个字才能提交；也可点「语音作答」，录音后自动转写。'
      "
      placeholder="输入你的回答，先给结论，再补充关键动作与量化结果…"
    />
  </div>

  <div
    class="sticky bottom-16 z-sticky -mx-6 -mb-6 mt-5 rounded-b-lg border-t border-border bg-surface/95 px-6 py-3 backdrop-blur sm:-mx-8 sm:-mb-8 sm:px-8 lg:static lg:bottom-auto lg:mx-0 lg:mb-0 lg:rounded-none lg:border-0 lg:bg-transparent lg:px-0 lg:py-0 lg:backdrop-blur-none"
  >
    <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
      <p class="flex items-center gap-1.5 text-2xs leading-5 text-text-muted">
        <Timer class="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
        <span v-if="paused">本场面试已暂停，继续后可作答</span>
        <span v-else-if="!isSessionActive">本场面试已结束</span>
        <span v-else-if="isAnswered && streamState === 'connecting'">
          正在分析回答，完成后可进入下一题
        </span>
        <span v-else-if="isAnswered && streamState === 'error'">反馈生成失败，可重试后继续</span>
        <span v-else-if="isAnswered">本题已完成</span>
        <span v-else-if="trimmedLength < 10">还需 {{ 10 - trimmedLength }} 个字才能提交</span>
        <span v-else>先给结论，再补充证据与取舍</span>
      </p>
      <div class="flex flex-wrap items-center justify-end gap-2">
        <template v-if="isSessionActive">
          <VoiceRecorder
            v-if="!isAnswered && currentStatus !== 'SKIPPED'"
            ref="recorder"
            :disabled="submitting"
            :transcribing="voiceTranscribing ?? false"
            @recorded="onRecorded"
            @error="onVoiceError"
          />
          <Button
            v-if="!isAnswered && currentStatus === 'SKIPPED'"
            variant="outline"
            size="sm"
            @click="emit('next')"
          >
            {{ isLastQuestion ? '生成报告' : '下一题' }}
            <ArrowRight class="h-3.5 w-3.5" aria-hidden="true" />
          </Button>
          <Button
            v-if="!isAnswered && currentStatus !== 'SKIPPED'"
            variant="ghost"
            size="sm"
            :disabled="submitting || streamState === 'connecting'"
            @click="emit('skip')"
          >
            <SkipForward class="h-3.5 w-3.5" aria-hidden="true" />
            跳过
          </Button>
          <Button
            v-if="!isAnswered"
            :disabled="!canSubmit"
            :loading="submitting"
            @click="emit('submit')"
          >
            <Send class="h-4 w-4" aria-hidden="true" />
            提交回答
          </Button>
          <Button v-else :disabled="!canAdvance" @click="emit('next')">
            {{ isLastQuestion ? '生成报告' : '下一题' }}
            <ArrowRight class="h-4 w-4" aria-hidden="true" />
          </Button>
        </template>
        <Button v-else-if="hasReport" variant="secondary" @click="emit('open-report')">
          <FileText class="h-4 w-4" aria-hidden="true" />
          查看报告
        </Button>
      </div>
    </div>
  </div>
</template>
