<script setup lang="ts">
import { ref } from 'vue'
import { FileText, UploadCloud } from 'lucide-vue-next'
import Progress from '@/components/ui/Progress.vue'

const props = withDefaults(
  defineProps<{
    accept?: string
    maxSizeMb?: number
    uploading?: boolean
    progress?: number
    fileName?: string
  }>(),
  { accept: '.pdf,.docx', maxSizeMb: 10, uploading: false, progress: 0 },
)

const emit = defineEmits<{ file: [File]; error: [string] }>()
const dragging = ref(false)
const inputRef = ref<HTMLInputElement | null>(null)
const error = ref('')

function validate(file: File): boolean {
  const extensions = props.accept.split(',').map((item) => item.trim().toLowerCase())
  const ext = `.${file.name.split('.').pop()?.toLowerCase() ?? ''}`
  if (!extensions.includes(ext)) {
    error.value = `不支持 ${ext || '未知'} 格式，请上传 ${props.accept.replaceAll(',', ' / ')}`
    emit('error', error.value)
    return false
  }
  if (file.size > props.maxSizeMb * 1024 * 1024) {
    error.value = `文件大小 ${(file.size / 1024 / 1024).toFixed(1)}MB，超过 ${props.maxSizeMb}MB 限制`
    emit('error', error.value)
    return false
  }
  error.value = ''
  return true
}

function handleFiles(files: FileList | null) {
  const file = files?.[0]
  if (!file) return
  if (validate(file)) emit('file', file)
  if (inputRef.value) inputRef.value.value = ''
}

function onInputChange(event: Event) {
  handleFiles((event.target as HTMLInputElement).files)
}

function onDrop(event: DragEvent) {
  dragging.value = false
  handleFiles(event.dataTransfer?.files ?? null)
}
</script>

<template>
  <div>
    <div
      :class="[
        'flex flex-col items-center justify-center rounded-lg border-2 border-dashed px-5 py-10 text-center transition-colors',
        dragging ? 'border-text/30 bg-surface-2' : 'border-border bg-surface hover:bg-surface-2/60',
      ]"
      role="button"
      tabindex="0"
      aria-label="上传简历"
      @click="inputRef?.click()"
      @keydown.enter="inputRef?.click()"
      @keydown.space.prevent="inputRef?.click()"
      @dragover.prevent="dragging = true"
      @dragleave.prevent="dragging = false"
      @drop.prevent="onDrop"
    >
      <input ref="inputRef" type="file" class="hidden" :accept="accept" @change="onInputChange" />
      <div class="flex h-12 w-12 items-center justify-center rounded-full bg-surface-2 text-text-2">
        <UploadCloud class="h-5 w-5" aria-hidden="true" />
      </div>
      <p class="mt-4 text-sm font-medium text-text">拖拽简历到这里，或点击选择文件</p>
      <p class="mt-1 text-xs text-text-muted">支持 PDF / DOCX，单文件不超过 {{ maxSizeMb }}MB</p>
      <span
        class="mt-4 inline-flex h-8 items-center justify-center gap-1.5 rounded-md border border-border px-3 text-xs font-medium text-text"
      >
        <FileText class="h-3.5 w-3.5" />
        选择文件
      </span>
    </div>
    <div v-if="uploading" class="mt-3 rounded-md border border-border bg-surface p-3">
      <div class="flex items-center justify-between gap-3 text-xs">
        <span class="truncate text-text">{{ fileName || '正在解析…' }}</span>
        <span class="tabular text-text-muted">{{ progress }}%</span>
      </div>
      <Progress :value="progress ?? 0" class="mt-2" label="解析进度" />
    </div>
    <p v-if="error" class="mt-2 text-xs text-danger" role="alert">{{ error }}</p>
  </div>
</template>
