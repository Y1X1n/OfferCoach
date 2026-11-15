<script setup lang="ts">
import { Bot, PlugZap, RefreshCw, Save, Trash2 } from 'lucide-vue-next'
import AsyncState from '@/components/common/AsyncState.vue'
import Alert from '@/components/ui/Alert.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import AiKeyClearDialog from './AiKeyClearDialog.vue'
import AiToolsSettings from './AiToolsSettings.vue'
import { useAiConfig } from '@/composables/useAiConfig'

const {
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
} = useAiConfig()
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

          <AiToolsSettings v-model="form" />

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

          <p class="text-xs text-text-muted">测试连接使用已保存的配置，修改后请先保存再测试。</p>

          <Alert
            v-if="testResult"
            :variant="testResult.ok ? 'success' : 'danger'"
            :title="testResult.ok ? '连接正常（ok）' : '连接失败'"
            :description="testSummary"
          />
          <Alert v-if="testError" variant="danger" title="测试请求失败" :description="testError" />

          <p v-if="updatedText" class="border-t border-border pt-3 text-xs text-text-muted">
            最近更新：{{ updatedText }}
          </p>
        </form>
      </Card>
    </AsyncState>

    <AiKeyClearDialog
      v-model:open="clearOpen"
      :error="clearError"
      :summary="clearSummary"
      :clearing="clearing"
      @confirm="confirmClear"
    />
  </div>
</template>
