<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RefreshCw, RotateCcw, Search, X } from 'lucide-vue-next'
import AsyncState from '@/components/common/AsyncState.vue'
import AdminPagination from '@/components/admin/AdminPagination.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import { errorMessage } from '@/api/client'
import * as adminApi from '@/api/admin'
import type { AdminAiCall, AdminAiCallStatus } from '@/api/admin'
import { formatDate, formatRelative } from '@/utils/format'
import { aiStatusMeta, optionalInt } from './meta'

const PAGE_SIZE = 20

const sceneOptions = [
  { value: 'RESUME_PARSE', label: '简历解析' },
  { value: 'JD_ANALYZE', label: 'JD 分析' },
  { value: 'QUESTION_GEN', label: '出题' },
  { value: 'FEEDBACK', label: '评分反馈' },
  { value: 'REPORT', label: '报告生成' },
  { value: 'ASR', label: '语音转写' },
  { value: 'TTS', label: '题目朗读' },
  { value: 'OCR', label: '简历识别' },
  { value: 'SEARCH', label: '岗位搜索' },
  { value: 'FETCH', label: '链接抓取' },
]

const calls = ref<AdminAiCall[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(true)
const loadError = ref<string | null>(null)

const sceneFilter = ref('')
const statusFilter = ref('')

let loadSeq = 0

const hasFilters = computed(() => sceneFilter.value !== '' || statusFilter.value !== '')
const isEmpty = computed(() => !loading.value && !loadError.value && calls.value.length === 0)

async function load(): Promise<void> {
  const seq = ++loadSeq
  loading.value = true
  loadError.value = null
  try {
    const result = await adminApi.listAiCalls({
      page: page.value,
      size: PAGE_SIZE,
      ...(sceneFilter.value ? { scene: sceneFilter.value } : {}),
      ...(statusFilter.value ? { status: statusFilter.value as AdminAiCallStatus } : {}),
    })
    if (seq !== loadSeq) return
    calls.value = result.items
    total.value = result.total
  } catch (cause) {
    if (seq !== loadSeq) return
    loadError.value = errorMessage(cause, 'AI 调用日志加载失败')
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function applyFilters(): void {
  page.value = 1
  void load()
}

function resetFilters(): void {
  sceneFilter.value = ''
  statusFilter.value = ''
  applyFilters()
}

function goPage(next: number): void {
  page.value = next
  void load()
}

function sceneLabel(value: string): string {
  return (sceneOptions.find((option) => option.value === value)?.label ?? value) || '—'
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="min-w-0">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h2 class="text-base font-semibold text-text">AI 调用日志</h2>
        <p class="mt-1 text-xs text-text-muted">
          记录每次模型调用的场景、Token 用量与首字延迟；错误码用于定位降级原因。
        </p>
      </div>
      <Button variant="ghost" size="sm" :loading="loading" @click="load">
        <RefreshCw v-if="!loading" class="h-3.5 w-3.5" aria-hidden="true" />
        刷新
      </Button>
    </div>

    <Card padding="sm" class="mt-3">
      <form
        class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4"
        role="search"
        aria-label="筛选 AI 调用日志"
        @submit.prevent="applyFilters"
      >
        <div>
          <label for="ai-scene" class="sr-only">场景</label>
          <select
            id="ai-scene"
            v-model="sceneFilter"
            class="h-11 w-full rounded-md border border-border bg-surface px-3 text-sm text-text transition-colors focus:border-accent/60 sm:h-9"
          >
            <option value="">全部场景</option>
            <option v-for="option in sceneOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </div>

        <div>
          <label for="ai-status" class="sr-only">状态</label>
          <select
            id="ai-status"
            v-model="statusFilter"
            class="h-11 w-full rounded-md border border-border bg-surface px-3 text-sm text-text transition-colors focus:border-accent/60 sm:h-9"
          >
            <option value="">全部状态</option>
            <option value="SUCCESS">成功</option>
            <option value="FAILED">失败</option>
            <option value="DEGRADED">降级</option>
          </select>
        </div>

        <div class="flex flex-wrap items-center gap-2 sm:col-span-2">
          <Button type="submit" size="sm">
            <Search class="h-3.5 w-3.5" aria-hidden="true" />
            查询
          </Button>
          <Button type="button" variant="outline" size="sm" @click="resetFilters">
            <RotateCcw class="h-3.5 w-3.5" aria-hidden="true" />
            重置
          </Button>
          <Button v-if="hasFilters" type="button" variant="ghost" size="sm" @click="resetFilters">
            <X class="h-3.5 w-3.5" aria-hidden="true" />
            清除筛选
          </Button>
        </div>
      </form>
    </Card>

    <AsyncState
      class="mt-3"
      :loading="loading"
      :error="loadError"
      error-title="AI 调用日志加载失败"
      :empty="isEmpty"
      empty-title="暂无 AI 调用记录"
      :empty-description="hasFilters ? '调整场景或状态筛选后重试。' : '模型产生调用后这里会自动记录。'"
      :empty-icon="Search"
      :loading-lines="5"
      @retry="load"
    >
      <div class="overflow-x-auto rounded-lg border border-border">
        <table class="w-full min-w-[1060px] border-collapse text-sm">
          <thead class="bg-surface-2 text-left text-xs text-text-muted">
            <tr>
              <th scope="col" class="px-3 py-2.5 font-medium">ID</th>
              <th scope="col" class="px-3 py-2.5 font-medium">用户</th>
              <th scope="col" class="px-3 py-2.5 font-medium">场景</th>
              <th scope="col" class="px-3 py-2.5 font-medium">模型</th>
              <th scope="col" class="px-3 py-2.5 font-medium">状态</th>
              <th scope="col" class="px-3 py-2.5 text-right font-medium">Token（入/出）</th>
              <th scope="col" class="px-3 py-2.5 text-right font-medium">首字 / 总耗时</th>
              <th scope="col" class="px-3 py-2.5 font-medium">错误码</th>
              <th scope="col" class="px-3 py-2.5 font-medium">时间</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-border bg-surface">
            <tr v-for="call in calls" :key="call.id">
              <td class="whitespace-nowrap px-3 py-3 tabular text-xs text-text-muted">
                #{{ call.id || '—' }}
              </td>
              <td class="px-3 py-3 text-xs text-text-muted">{{ call.userId || '—' }}</td>
              <td class="whitespace-nowrap px-3 py-3 text-text">{{ sceneLabel(call.scene) }}</td>
              <td class="max-w-56 px-3 py-3 text-xs text-text-muted">
                <p class="truncate" :title="call.provider">{{ call.provider || '—' }}</p>
                <p class="truncate" :title="call.model">{{ call.model || '—' }}</p>
              </td>
              <td class="px-3 py-3">
                <Badge :variant="aiStatusMeta(call.status).variant" dot>
                  {{ aiStatusMeta(call.status).label }}
                </Badge>
              </td>
              <td class="tabular whitespace-nowrap px-3 py-3 text-right text-xs text-text-muted">
                {{ optionalInt(call.tokensIn) }} / {{ optionalInt(call.tokensOut) }}
              </td>
              <td class="tabular whitespace-nowrap px-3 py-3 text-right text-xs text-text-muted">
                {{ optionalInt(call.firstTokenMs) }} / {{ optionalInt(call.latencyMs) }} ms
              </td>
              <td class="max-w-40 px-3 py-3">
                <span
                  :class="call.errorCode ? 'text-danger-strong' : 'text-text-muted'"
                  class="block truncate font-mono text-2xs"
                  :title="call.errorCode"
                >
                  {{ call.errorCode || '—' }}
                </span>
              </td>
              <td class="whitespace-nowrap px-3 py-3 text-xs text-text-muted">
                <span :title="formatDate(call.createdAt, true)">
                  {{ call.createdAt ? formatRelative(call.createdAt) : '—' }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <AdminPagination
        v-if="total > 0"
        class="mt-3"
        :page="page"
        :total="total"
        :size="PAGE_SIZE"
        :disabled="loading"
        @change="goPage"
      />
    </AsyncState>
  </div>
</template>