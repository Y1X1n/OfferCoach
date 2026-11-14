<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ExternalLink, Link2, Loader2, Search } from 'lucide-vue-next'
import type { JdPreview, JdWebCandidate } from '@/api/jds'
import * as jdsApi from '@/api/jds'
import { errorMessage } from '@/api/client'
import Alert from '@/components/ui/Alert.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Dialog from '@/components/ui/Dialog.vue'
import Input from '@/components/ui/Input.vue'

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
      <div class="inline-flex rounded-md border border-border p-0.5" role="tablist">
        <button
          type="button"
          role="tab"
          :aria-selected="mode === 'URL'"
          :class="[
            'inline-flex items-center gap-1.5 rounded px-3 py-1.5 text-xs font-medium transition-colors',
            mode === 'URL' ? 'bg-surface-2 text-text' : 'text-text-muted hover:text-text',
          ]"
          @click="mode = 'URL'; error = ''"
        >
          <Link2 class="h-3.5 w-3.5" aria-hidden="true" />
          粘贴链接
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="mode === 'SEARCH'"
          :class="[
            'inline-flex items-center gap-1.5 rounded px-3 py-1.5 text-xs font-medium transition-colors',
            mode === 'SEARCH' ? 'bg-surface-2 text-text' : 'text-text-muted hover:text-text',
          ]"
          @click="mode = 'SEARCH'; error = ''"
        >
          <Search class="h-3.5 w-3.5" aria-hidden="true" />
          搜索岗位
        </button>
      </div>

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

      <div v-if="candidates.length" class="space-y-2">
        <p class="text-xs font-medium text-text-muted">选择一个岗位</p>
        <ul class="max-h-64 space-y-1.5 overflow-y-auto pr-1">
          <li v-for="candidate in candidates" :key="candidate.resultId">
            <button
              type="button"
              class="w-full rounded-md border border-border px-3 py-2 text-left transition-colors hover:bg-surface-2 disabled:opacity-60"
              :disabled="Boolean(picking)"
              @click="pickCandidate(candidate)"
            >
              <span class="flex items-start justify-between gap-2">
                <span class="min-w-0">
                  <span class="block truncate text-xs font-medium text-text">{{ candidate.title }}</span>
                  <span class="mt-0.5 block truncate text-3xs text-text-muted">
                    {{ candidate.siteName || '招聘网站' }} · {{ candidate.url }}
                  </span>
                  <span v-if="candidate.snippet" class="mt-1 block line-clamp-2 text-3xs leading-4 text-text-muted">
                    {{ candidate.snippet }}
                  </span>
                </span>
                <span class="flex shrink-0 items-center gap-1">
                  <Badge v-if="candidate.rerankScore >= 1.5" variant="success">相关</Badge>
                  <Loader2
                    v-if="picking === candidate.resultId"
                    class="h-3.5 w-3.5 animate-spin text-text-muted"
                    aria-hidden="true"
                  />
                </span>
              </span>
            </button>
          </li>
        </ul>
      </div>

      <div v-if="preview" class="space-y-3 rounded-md border border-border p-3">
        <Alert
          v-if="fallbackNotice"
          variant="warning"
          title="已改用搜索获取"
          :description="fallbackNotice"
        />
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
          <summary class="w-fit cursor-pointer text-xs font-medium text-text-muted hover:text-text">
            查看 JD 原文
          </summary>
          <pre class="mt-2 max-h-56 overflow-y-auto whitespace-pre-wrap break-words rounded bg-surface-2 p-3 text-3xs leading-5 text-text-muted">{{ preview.rawText }}</pre>
        </details>
      </div>
    </div>

    <template #footer>
      <div class="flex items-center justify-end gap-2">
        <Button variant="ghost" size="sm" @click="close">取消</Button>
        <Button size="sm" :disabled="!preview" @click="usePreview">使用这份 JD</Button>
      </div>
    </template>
  </Dialog>
</template>