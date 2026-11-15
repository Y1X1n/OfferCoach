<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Loader2 } from 'lucide-vue-next'
import type { JdPreview, JdWebCandidate } from '@/api/jds'
import * as jdsApi from '@/api/jds'
import { errorMessage } from '@/api/client'
import Alert from '@/components/ui/Alert.vue'
import Button from '@/components/ui/Button.vue'
import Dialog from '@/components/ui/Dialog.vue'
import Input from '@/components/ui/Input.vue'
import JdImportModeTabs from './JdImportModeTabs.vue'
import JdWebImportCandidates from './JdWebImportCandidates.vue'
import JdWebImportPreview from './JdWebImportPreview.vue'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{
  'update:open': [value: boolean]
  use: [payload: { previewToken: string; title: string; siteName: string; sourceUrl: string }]
}>()

type Mode = 'URL' | 'SEARCH'

const mode = ref<Mode>('URL')
const url = ref('')
const keywords = ref('')
const query = ref('')
const location = ref('')
const loading = ref(false)
const error = ref('')
const preview = ref<JdPreview | null>(null)
const candidates = ref<JdWebCandidate[]>([])
const searchToken = ref('')
const picking = ref('')

const visible = computed({
  get: () => props.open,
  set: (value: boolean) => emit('update:open', value),
})

const fallbackNotice = computed(() => {
  const current = preview.value
  if (!current || current.resolvedBy !== 'SEARCH_FALLBACK') return ''
  if (current.fallbackReason === 'FETCH_NO_CONTENT') return '该链接没有可提取的正文，已改用搜索获取同一岗位。'
  if (current.fallbackReason === 'FETCH_TOO_SHORT') return '该链接正文过短（可能需要登录），已改用搜索获取同一岗位。'
  return '该链接无法抓取（站点可能拒绝访问），已改用搜索获取同一岗位。'
})

const mustHave = computed(() => preview.value?.structured.mustHave.slice(0, 8) ?? [])
const techStack = computed(() => preview.value?.structured.techStack.slice(0, 8) ?? [])

function reset(): void {
  loading.value = false
  error.value = ''
  preview.value = null
  candidates.value = []
  searchToken.value = ''
  picking.value = ''
}

watch(
  () => props.open,
  (open) => {
    if (open) reset()
  },
)

watch(mode, () => {
  error.value = ''
})

function onError(cause: unknown, fallback: string): void {
  error.value = errorMessage(cause, fallback)
}

async function fetchFromUrl(): Promise<void> {
  const link = url.value.trim()
  if (!/^https?:\/\//i.test(link)) {
    error.value = '请填写以 http:// 或 https:// 开头的岗位链接。'
    return
  }
  loading.value = true
  error.value = ''
  preview.value = null
  candidates.value = []
  try {
    const result = await jdsApi.previewJd({
      url: link,
      ...(keywords.value.trim() ? { keywords: keywords.value.trim() } : {}),
    })
    applyPreview(result)
  } catch (cause) {
    onError(cause, '获取岗位链接失败')
  } finally {
    loading.value = false
  }
}

async function runSearch(): Promise<void> {
  const text = query.value.trim()
  if (text.length < 2) {
    error.value = '请输入至少 2 个字的岗位关键词。'
    return
  }
  loading.value = true
  error.value = ''
  preview.value = null
  candidates.value = []
  try {
    const result = await jdsApi.searchJds({
      query: text,
      ...(location.value.trim() ? { location: location.value.trim() } : {}),
      count: 8,
    })
    searchToken.value = result.searchToken
    candidates.value = result.results
    if (result.results.length === 0) {
      error.value = '未找到匹配岗位，换个关键词或手动粘贴 JD。'
    }
  } catch (cause) {
    onError(cause, '搜索岗位失败')
  } finally {
    loading.value = false
  }
}

async function pickCandidate(candidate: JdWebCandidate): Promise<void> {
  if (picking.value) return
  picking.value = candidate.resultId
  error.value = ''
  try {
    const result = await jdsApi.previewJd({
      searchToken: searchToken.value,
      resultId: candidate.resultId,
    })
    applyPreview(result)
  } catch (cause) {
    onError(cause, '读取该岗位失败')
  } finally {
    picking.value = ''
  }
}

function applyPreview(result: JdPreview): void {
  if (result.needsChoice) {
    searchToken.value = result.searchToken
    candidates.value = result.candidates
    error.value = candidates.value.length === 0 ? '没有找到可用岗位，请换关键词或手动粘贴。' : ''
    return
  }
  preview.value = result
}

function usePreview(): void {
  const current = preview.value
  if (!current) return
  emit('use', {
    previewToken: current.previewToken,
    title: current.title,
    siteName: current.siteName ?? '',
    sourceUrl: current.sourceUrl ?? '',
  })
  visible.value = false
}

function close(): void {
  visible.value = false
}
</script>

<template>
  <Dialog
    v-model:open="visible"
    title="从招聘网站获取目标岗位"
    description="粘贴岗位链接优先抓取；抓不到会自动改用搜索。确认后用于定制题目。"
    size="lg"
  >
    <div class="space-y-4">
      <JdImportModeTabs v-model="mode" />

      <div v-if="mode === 'URL'" class="space-y-3">
        <Input
          v-model="url"
          label="岗位链接"
          placeholder="https://www.example.com/job/123456"
          hint="招聘网站的岗位详情页链接。"
        />
        <Input
          v-model="keywords"
          label="关键词（可选）"
          placeholder="如：高级后端工程师 上海"
          hint="链接抓不到正文时，用这些关键词搜索兜底。"
        />
        <Button :loading="loading" :disabled="loading" @click="fetchFromUrl">
          <Loader2 v-if="loading" class="h-4 w-4 animate-spin" aria-hidden="true" />
          获取岗位 JD
        </Button>
      </div>

      <div v-else class="space-y-3">
        <Input
          v-model="query"
          label="岗位关键词"
          placeholder="如：高级后端工程师"
          hint="建议带上岗位方向；可在下方补充城市。"
        />
        <Input v-model="location" label="城市（可选）" placeholder="如：上海" />
        <Button :loading="loading" :disabled="loading" @click="runSearch">
          <Loader2 v-if="loading" class="h-4 w-4 animate-spin" aria-hidden="true" />
          搜索岗位
        </Button>
      </div>

      <Alert v-if="error" variant="danger" title="没有取到岗位信息" :description="error" />

      <JdWebImportCandidates :candidates="candidates" :picking="picking" @pick="pickCandidate" />

      <JdWebImportPreview
        v-if="preview"
        :preview="preview"
        :fallback-notice="fallbackNotice"
        :must-have="mustHave"
        :tech-stack="techStack"
      />
    </div>

    <template #footer>
      <div class="flex items-center justify-end gap-2">
        <Button variant="ghost" size="sm" @click="close">取消</Button>
        <Button size="sm" :disabled="!preview" @click="usePreview">使用这份 JD</Button>
      </div>
    </template>
  </Dialog>
</template>
