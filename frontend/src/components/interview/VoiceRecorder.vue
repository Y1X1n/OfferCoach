<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { Mic, RotateCcw, Square, Loader2 } from 'lucide-vue-next'
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

const WAVE_BARS = 24

const maxSeconds = computed(() => props.maxSeconds ?? 120)
const supported = isRecordingSupported()
const recording = ref(false)
const elapsedMs = ref(0)
const requesting = ref(false)
const levels = ref<number[]>(Array.from({ length: WAVE_BARS }, () => 0))
let handle: RecorderHandle | null = null
let latestLevel = 0
let waveTimer = 0

const elapsedText = computed(() => {
  const total = Math.floor(elapsedMs.value / 1000)
  const minutes = Math.floor(total / 60)
  const seconds = total % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
})

const busy = computed(() => props.transcribing || requesting.value)

function resetLevels(): void {
  latestLevel = 0
  levels.value = Array.from({ length: WAVE_BARS }, () => 0)
}

function pushBar(): void {
  const next = levels.value.slice(1)
  next.push(Math.min(1, latestLevel * 4))
  levels.value = next
}

function startWave(): void {
  resetLevels()
  waveTimer = window.setInterval(pushBar, 60)
}

function stopWave(): void {
  window.clearInterval(waveTimer)
  waveTimer = 0
}

function barHeight(level: number): string {
  return `${Math.max(2, Math.round(level * 16))}px`
}

async function begin(): Promise<void> {
  try {
    requesting.value = true
    handle = await startRecording(
      (tick) => {
        elapsedMs.value = tick
        if (tick >= maxSeconds.value * 1000) void finish()
      },
      maxSeconds.value,
      (level) => {
        latestLevel = level
      },
    )
    elapsedMs.value = 0
    recording.value = true
    startWave()
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

async function toggle(): Promise<void> {
  if (props.disabled || busy.value) return
  if (recording.value) {
    await finish()
    return
  }
  await begin()
}

async function finish(): Promise<void> {
  if (!handle) return
  const current = handle
  handle = null
  recording.value = false
  stopWave()
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

/** 重录：放弃当前录音并立即重新开始，不入库、不产生费用。 */
async function restart(): Promise<void> {
  if (!recording.value || busy.value) return
  cancel()
  await begin()
}

function cancel(): void {
  handle?.cancel()
  handle = null
  recording.value = false
  stopWave()
  resetLevels()
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
      <span class="max-sm:hidden">{{ transcribing ? '转写中' : '语音作答' }}</span>
    </Button>
    <template v-else>
      <span class="flex h-4 items-end gap-px" aria-hidden="true">
        <span
          v-for="(level, index) in levels"
          :key="index"
          class="w-0.5 rounded-full bg-brand transition-[height] duration-75"
          :style="{ height: barHeight(level) }"
        />
      </span>
      <Button variant="outline" size="sm" aria-label="停止录音并提交" @click="finish">
        <Square class="h-3.5 w-3.5 fill-current" aria-hidden="true" />
        <span class="max-sm:hidden">停止 {{ elapsedText }}</span>
        <span class="tabular sm:hidden">{{ elapsedText }}</span>
      </Button>
      <Button variant="ghost" size="sm" aria-label="重录" @click="restart">
        <RotateCcw class="h-3.5 w-3.5" aria-hidden="true" />
        重录
      </Button>
    </template>
    <span v-if="recording" class="text-2xs text-text-muted" aria-live="polite">
      正在录音，最长 {{ Math.floor(maxSeconds / 60) }} 分钟
    </span>
  </div>
</template>
