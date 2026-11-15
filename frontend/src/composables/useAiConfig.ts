import { computed, onMounted, ref } from 'vue'
import { errorMessage } from '@/api/client'
import * as adminApi from '@/api/admin'
import type { AdminAiConfig, AdminAiConfigPayload, AdminAiTestResult } from '@/api/admin'
import { useToast } from '@/composables/useToast'
import { formatDate } from '@/utils/format'

export interface ConfigFormState {
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

/**
 * AI 配置面板的状态与动作：加载、保存、连接测试与清除 Key。
 */
export function useAiConfig() {
  const { toast } = useToast()

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

  return {
    config,
    form,
    loading,
    loadError,
    saving,
    formError,
    testing,
    testResult,
    testError,
    clearOpen,
    clearing,
    clearError,
    isEmpty,
    hasKey,
    keyPlaceholder,
    updatedText,
    testSummary,
    clearSummary,
    load,
    save,
    runTest,
    openClear,
    confirmClear,
  }
}
