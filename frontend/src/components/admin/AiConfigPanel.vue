<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Bot, PlugZap, RefreshCw, Save, Trash2 } from 'lucide-vue-next'
import AsyncState from '@/components/common/AsyncState.vue'
import Alert from '@/components/ui/Alert.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import Dialog from '@/components/ui/Dialog.vue'
import Input from '@/components/ui/Input.vue'
import { errorMessage } from '@/api/client'
import * as adminApi from '@/api/admin'
import type { AdminAiConfig, AdminAiConfigPayload, AdminAiTestResult } from '@/api/admin'
import { useToast } from '@/composables/useToast'
import { formatDate } from '@/utils/format'

const { toast } = useToast()

interface ConfigFormState {
  enabled: boolean
  baseUrl: string
  model: string
  smallModel: string
  apiKey: string
  asrEnabled: boolean
  ttsEnabled: boolean
  ocrEnabled: boolean
  searchEnabled: boolean
  fetchEnabled: boolean
  ttsDefaultVoice: 'stella' | 'tide'
}

function emptyForm(): ConfigFormState {
  return {
    enabled: false,
    baseUrl: '',
    model: '',
    smallModel: '',
    apiKey: '',
    asrEnabled: true,
    ttsEnabled: true,
    ocrEnabled: true,
    searchEnabled: true,
    fetchEnabled: true,
    ttsDefaultVoice: 'stella',
  }
}

const config = ref<AdminAiConfig | null>(null)
const form = ref<ConfigFormState>(emptyForm())
const loading = ref(true)
const loadError = ref<string | null>(null)

const saving = ref(false)
const formError = ref('')

const testing = ref(false)
const testResult = ref<AdminAiTestResult | null>(null)
const testError = ref('')

const clearOpen = ref(false)
const clearing = ref(false)
const clearError = ref('')

const isEmpty = computed(() => !loading.value && !loadError.value && config.value === null)
const hasKey = computed(() => config.value?.hasApiKey === true)

type ToolToggleKey = 'asrEnabled' | 'ttsEnabled' | 'ocrEnabled' | 'searchEnabled' | 'fetchEnabled'

const toolToggles: { key: ToolToggleKey; label: string; hint: string }[] = [
  { key: 'asrEnabled', label: '语音作答', hint: '录音转写为文字' },
  { key: 'ttsEnabled', label: '题目朗读', hint: '模拟考官朗读题干' },
  { key: 'ocrEnabled', label: '简历 OCR', hint: '扫描件与图片识别' },
  { key: 'searchEnabled', label: '岗位搜索', hint: '联网检索目标岗位' },
  { key: 'fetchEnabled', label: '链接抓取', hint: '抓取岗位链接正文' },
]

const keyPlaceholder = computed(() => {
  const masked = config.value?.apiKeyMasked
  if (masked) return `当前 Key：${masked}（留空保留）`
  return hasKey.value ? '已配置 Key（留空保留）' : '尚未配置，请输入 API Key'
})

const updatedText = computed(() => {
  const current = config.value
  if (!current) return ''
  const parts: string[] = []
  if (current.updatedAt) parts.push(formatDate(current.updatedAt, true))
  if (current.updatedBy) parts.push(`更新人 ${current.updatedBy}`)
  return parts.join(' · ')
})

const testSummary = computed(() => {
  const result = testResult.value
  if (!result) return ''
  const parts: string[] = []
  if (result.model) parts.push(`模型 ${result.model}`)
  if (result.latencyMs !== null) parts.push(`延迟 ${result.latencyMs} ms`)
  if (result.message) parts.push(result.message)
  return parts.join(' · ') || '未返回更多信息'
})

const clearSummary = computed(() => {
  const masked = config.value?.apiKeyMasked
  return masked
    ? `将清空当前 Key（${masked}）；该操作不可撤销，但会写入审计日志。`
    : '将清空已保存的 API Key；该操作不可撤销，但会写入审计日志。'
})

function syncForm(current: AdminAiConfig): void {
  config.value = current
  form.value = {
    enabled: current.enabled,
    baseUrl: current.baseUrl,
    model: current.model,
    smallModel: current.smallModel,
    apiKey: '',
    asrEnabled: current.asrEnabled,
    ttsEnabled: current.ttsEnabled,
    ocrEnabled: current.ocrEnabled,
    searchEnabled: current.searchEnabled,
    fetchEnabled: current.fetchEnabled,
    ttsDefaultVoice: current.ttsDefaultVoice,
  }
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    syncForm(await adminApi.getAiConfig())
  } catch (cause) {
    loadError.value = errorMessage(cause, 'AI 配置加载失败')
  } finally {
    loading.value = false
  }
}

/** 保存成功后静默拉取最新掩码；失败不覆盖当前表单。 */
async function refreshQuietly(): Promise<void> {
  try {
    syncForm(await adminApi.getAiConfig())
  } catch {
    // 保存已成功，保留本地表单，等待手动刷新
  }
}

function validate(): string {
  const baseUrl = form.value.baseUrl.trim()
  if (!baseUrl) return '请填写 Base URL。'
  try {
    const parsed = new URL(baseUrl)
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      return 'Base URL 仅支持 http 或 https。'
    }
  } catch {
    return 'Base URL 不是合法的 URL，请包含 http:// 或 https://。'
  }
  if (!form.value.model.trim()) return '请填写主模型名称。'
  if (!form.value.smallModel.trim()) return '请填写小模型名称。'
  return ''
}

function basePayload(): AdminAiConfigPayload {
  return {
    enabled: form.value.enabled,
    baseUrl: form.value.baseUrl.trim().replace(/\/+$/, ''),
    model: form.value.model.trim(),
    smallModel: form.value.smallModel.trim(),
    asrEnabled: form.value.asrEnabled,
    ttsEnabled: form.value.ttsEnabled,
    ocrEnabled: form.value.ocrEnabled,
    searchEnabled: form.value.searchEnabled,
    fetchEnabled: form.value.fetchEnabled,
    ttsDefaultVoice: form.value.ttsDefaultVoice,
  }
}

/** 清空 Key 使用已保存的配置，避免把未保存的表单改动一并提交。 */
function savedPayload(): AdminAiConfigPayload {
  const current = config.value
  return {
    enabled: current?.enabled ?? false,
    baseUrl: current?.baseUrl ?? '',
    model: current?.model ?? '',
    smallModel: current?.smallModel ?? '',
    asrEnabled: current?.asrEnabled ?? true,
    ttsEnabled: current?.ttsEnabled ?? true,
    ocrEnabled: current?.ocrEnabled ?? true,
    searchEnabled: current?.searchEnabled ?? true,
    fetchEnabled: current?.fetchEnabled ?? true,
    ttsDefaultVoice: current?.ttsDefaultVoice ?? 'stella',
  }
}

async function save(): Promise<void> {
  if (saving.value) return
  formError.value = validate()
  if (formError.value) return

  saving.value = true
  try {
    const key = form.value.apiKey.trim()
    await adminApi.updateAiConfig({
      ...basePayload(),
      ...(key ? { apiKey: key } : {}),
    })
    form.value.apiKey = ''
    testResult.value = null
    testError.value = ''
    toast('AI 配置已保存', 'success', '约 30 秒内生效。')
    await refreshQuietly()
  } catch (cause) {
    formError.value = errorMessage(cause, 'AI 配置保存失败')
  } finally {
    saving.value = false
  }
}

async function runTest(): Promise<void> {
  if (testing.value) return
  testing.value = true
  testResult.value = null
  testError.value = ''
  try {
    const result = await adminApi.testAiConfig()
    testResult.value = result
    if (result.ok) toast('连接测试通过', 'success', result.message || '模型响应正常')
  } catch (cause) {
    testError.value = errorMessage(cause, '测试连接请求失败')
  } finally {
    testing.value = false
  }
}

function openClear(): void {
  if (!hasKey.value) return
  clearError.value = ''
  clearOpen.value = true
}

async function confirmClear(): Promise<void> {
  if (clearing.value) return
  clearing.value = true
  clearError.value = ''
  try {
    await adminApi.updateAiConfig({ ...savedPayload(), clearApiKey: true })
    clearOpen.value = false
    testResult.value = null
    testError.value = ''
    toast('API Key 已清除', 'success', '重新配置 Key 前 AI 服务不可用。')
    await refreshQuietly()
  } catch (cause) {
    clearError.value = errorMessage(cause, '清除 API Key 失败')
  } finally {
    clearing.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="min-w-0">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h2 class="text-base font-semibold text-text">AI 配置</h2>
        <p class="mt-1 text-xs text-text-muted">
          统一管理模型网关地址、主/小模型与 API Key；修改会写入审计日志。
        </p>
      </div>
      <Button variant="ghost" size="sm" :loading="loading" @click="load">
        <RefreshCw v-if="!loading" class="h-3.5 w-3.5" aria-hidden="true" />
        刷新
      </Button>
    </div>

    <Alert
      class="mt-3"
      variant="info"
      title="生效与安全说明"
      description="保存后约 30 秒内生效；API Key 加密存储且不回显，仅在本次输入时短暂存在于内存中。"
    />

    <AsyncState
      class="mt-3"
      :loading="loading"
      :error="loadError"
      error-title="AI 配置加载失败"
      :empty="isEmpty"
      empty-title="暂无 AI 配置"
      empty-description="后端尚未返回配置，请刷新重试；如持续为空请联系后端确认配置表是否初始化。"
      :empty-icon="Bot"
      :loading-lines="6"
      @retry="load"
    >
      <Card padding="md" class="min-w-0">
        <form id="ai-config-form" class="space-y-5" novalidate @submit.prevent="save">
          <Alert v-if="formError" variant="danger" title="配置未保存" :description="formError" />

          <div class="flex flex-wrap items-start justify-between gap-3">
            <div class="min-w-0">
              <p class="text-sm font-medium text-text">启用 AI 服务</p>
              <p class="mt-0.5 text-xs text-text-muted">
                关闭后解析、出题、评分与报告生成都会降级，仅供维护时使用。
              </p>
            </div>
            <button
              type="button"
              role="switch"
              :aria-checked="form.enabled"
              aria-label="启用 AI 服务"
              :class="[
                'relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors',
                form.enabled ? 'bg-brand' : 'bg-surface-2 ring-1 ring-inset ring-border',
              ]"
              @click="form.enabled = !form.enabled"
            >
              <span
                :class="[
                  'inline-block h-4 w-4 transform rounded-full bg-surface shadow-sm transition-transform',
                  form.enabled ? 'translate-x-6' : 'translate-x-1',
                ]"
                aria-hidden="true"
              />
            </button>
          </div>

          <div class="grid gap-4 lg:grid-cols-2">
            <div class="lg:col-span-2">
              <Input
                v-model="form.baseUrl"
                label="Base URL"
                required
                placeholder="https://api.example.com/v1"
                hint="OpenAI 兼容网关地址；保存时会去掉末尾的斜杠。"
                input-class="font-mono"
              />
            </div>
            <Input
              v-model="form.model"
              label="主模型"
              required
              placeholder="如：gpt-4o"
              hint="用于出题、评分与报告生成等复杂场景。"
              input-class="font-mono"
            />
            <Input
              v-model="form.smallModel"
              label="小模型"
              required
              placeholder="如：gpt-4o-mini"
              hint="用于解析、摘要等轻量场景，降低延迟与成本。"
              input-class="font-mono"
            />
          </div>

          <div class="flex flex-col gap-1.5">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <label for="ai-config-api-key" class="text-sm font-medium text-text">API Key</label>
              <Badge :variant="hasKey ? 'success' : 'warning'" dot>
                {{ hasKey ? '已配置' : '未配置' }}
              </Badge>
            </div>
            <Input
              id="ai-config-api-key"
              v-model="form.apiKey"
              type="password"
              autocomplete="new-password"
              :placeholder="keyPlaceholder"
              hint="留空保留现有 Key；保存后输入框即清空，明文不会被缓存或回显。"
            />
          </div>

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
              <label for="ai-config-tts-voice" class="text-sm font-medium text-text">
                题目朗读默认音色
              </label>
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

          <div class="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
            <Button type="submit" :loading="saving">
              <Save v-if="!saving" class="h-4 w-4" aria-hidden="true" />
              保存配置
            </Button>
            <Button variant="outline" :loading="testing" :disabled="saving" @click="runTest">
              <PlugZap v-if="!testing" class="h-4 w-4" aria-hidden="true" />
              测试连接
            </Button>
            <Button
              variant="outline"
              class="text-danger-strong"
              :disabled="!hasKey || saving || clearing"
              @click="openClear"
            >
              <Trash2 class="h-4 w-4" aria-hidden="true" />
              清除 Key
            </Button>
          </div>

          <p class="text-xs text-text-muted">
            测试连接使用已保存的配置，修改后请先保存再测试。
          </p>

          <Alert
            v-if="testResult"
            :variant="testResult.ok ? 'success' : 'danger'"
            :title="testResult.ok ? '连接正常（ok）' : '连接失败'"
            :description="testSummary"
          />
          <Alert
            v-if="testError"
            variant="danger"
            title="测试请求失败"
            :description="testError"
          />

          <p v-if="updatedText" class="border-t border-border pt-3 text-xs text-text-muted">
            最近更新：{{ updatedText }}
          </p>
        </form>
      </Card>
    </AsyncState>

    <Dialog
      v-model:open="clearOpen"
      title="清除 API Key"
      description="清除后所有 AI 能力将不可用，直到重新配置 Key。"
      size="sm"
    >
      <div class="space-y-3">
        <Alert
          v-if="clearError"
          variant="danger"
          title="清除未完成"
          :description="clearError"
        />
        <Alert
          variant="warning"
          title="确认清除"
          :description="clearSummary"
        />
      </div>
      <template #footer>
        <Button variant="outline" @click="clearOpen = false">取消</Button>
        <Button variant="danger" :loading="clearing" @click="confirmClear">确认清除</Button>
      </template>
    </Dialog>
  </div>
</template>