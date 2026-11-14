<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { Mic, Square, Loader2 } from 'lucide-vue-next'
import { isRecordingSupported, startRecording } from '@/utils/audioRecorder'
import type { RecorderHandle } from '@/utils/audioRecorder'
import Button from '@/components/ui/Button.vue'

const props = defineProps<{
  disabled: boolean
  transcribing: boolean
  maxSeconds?: number
}>()

const emit = defineEmits<{
  recorded: [audio: Blob, durationMs: number]
  error: [message: string]
}>()

const MAX_SECONDS = props.maxSeconds ?? 120
const supported = isRecordingSupported()
const recording = ref(false)
const elapsedMs = ref(0)
const requesting = ref(false)
let handle: RecorderHandle | null = null

const elapsedText = computed(() => {
  const total = Math.floor(elapsedMs.value / 1000)
  const minutes = Math.floor(total / 60)
  const seconds = total % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
})

const busy = computed(() => props.transcribing || requesting.value)

async function toggle(): Promise<void> {
  if (props.disabled || busy.value) return
  if (recording.value) {
    await finish()
    return
  }
  try {
    requesting.value = true
    handle = await startRecording((tick) => {
      elapsedMs.value = tick
      if (tick >= MAX_SECONDS * 1000) void finish()
    }, MAX_SECONDS)
    elapsedMs.value = 0
    recording.value = true
  } catch (cause) {
    const message =
      cause instanceof DOMException && cause.name === 'NotAllowedError'
        ? '麦克风权限被拒绝，请在浏览器设置中允许后重试'
        : '无法启动录音，请检查麦克风设备'
    emit('error', message)
  } finally {
    requesting.value = false
  }
}

async function finish(): Promise<void> {
  if (!handle) return
  const current = handle
  handle = null
  recording.value = false
  try {
    const audio = await current.stop()
    if (audio.blob.size <= 44) {
      emit('error', '录音过短，请重新录制')
      return
    }
    emit('recorded', audio.blob, audio.durationMs)
  } catch {
    emit('error', '录音处理失败，请重试')
  }
}

function cancel(): void {
  handle?.cancel()
  handle = null
  recording.value = false
}

onBeforeUnmount(cancel)

defineExpose({ cancel })
</script>

<template>
  <div v-if="supported" class="flex items-center gap-2">
    <Button
      v-if="!recording"
      variant="ghost"
      size="sm"
      :disabled="disabled || busy"
      aria-label="语音作答"
      @click="toggle"
    >
      <Loader2 v-if="busy" class="h-4 w-4 animate-spin" aria-hidden="true" />
      <Mic v-else class="h-4 w-4" aria-hidden="true" />
      {{ transcribing ? '转写中' : '语音作答' }}
    </Button>
    <Button v-else variant="outline" size="sm" aria-label="停止录音" @click="finish">
      <Square class="h-3.5 w-3.5 fill-current" aria-hidden="true" />
      停止 {{ elapsedText }}
    </Button>
    <span v-if="recording" class="text-2xs text-text-muted" aria-live="polite">
      正在录音，最长 {{ Math.floor(MAX_SECONDS / 60) }} 分钟
    </span>
  </div>
</template>
