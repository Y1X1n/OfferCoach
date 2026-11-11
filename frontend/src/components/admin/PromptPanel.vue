<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { FileText, Plus, RefreshCw, Send, Sparkles } from 'lucide-vue-next'
import AsyncState from '@/components/common/AsyncState.vue'
import Alert from '@/components/ui/Alert.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import Dialog from '@/components/ui/Dialog.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import Input from '@/components/ui/Input.vue'
import Textarea from '@/components/ui/Textarea.vue'
import { errorMessage } from '@/api/client'
import * as adminApi from '@/api/admin'
import type { AdminPromptTemplate, AdminPromptVersion } from '@/api/admin'
import { useToast } from '@/composables/useToast'
import { formatDate, formatRelative } from '@/utils/format'
import { promptSceneLabel, promptStatusMeta } from './meta'

const { toast } = useToast()

const templates = ref<AdminPromptTemplate[]>([])
const templatesLoading = ref(true)
const templatesError = ref<string | null>(null)

const selectedCode = ref('')
const versions = ref<AdminPromptVersion[]>([])
const versionsLoading = ref(false)
const versionsError = ref<string | null>(null)

let versionSeq = 0

const selectedTemplate = computed(
  () => templates.value.find((template) => template.code === selectedCode.value) ?? null,
)

const sortedVersions = computed(() =>
  [...versions.value].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt)),
)

const templatesEmpty = computed(
  () => !templatesLoading.value && !templatesError.value && templates.value.length === 0,
)
const versionsEmpty = computed(
  () =>
    !versionsLoading.value &&
    !versionsError.value &&
    versions.value.length === 0 &&
    selectedCode.value !== '',
)

async function loadVersions(code: string): Promise<void> {
  const seq = ++versionSeq
  versionsLoading.value = true
  versionsError.value = null
  try {
    const list = await adminApi.listPromptVersions(code)
    if (seq !== versionSeq) return
    versions.value = list
  } catch (cause) {
    if (seq !== versionSeq) return
    versionsError.value = errorMessage(cause, '版本列表加载失败')
  } finally {
    if (seq === versionSeq) versionsLoading.value = false
  }
}

async function loadTemplates(preferredCode?: string): Promise<void> {
  templatesLoading.value = true
  templatesError.value = null
  try {
    const list = await adminApi.listPrompts()
    templates.value = list
    const wanted = preferredCode ?? selectedCode.value
    const exists = list.some((template) => template.code === wanted)
    const next = exists ? wanted : (list[0]?.code ?? '')
    selectedCode.value = next
    if (next) {
      await loadVersions(next)
    } else {
      versions.value = []
    }
  } catch (cause) {
    templatesError.value = errorMessage(cause, 'Prompt 模板加载失败')
  } finally {
    templatesLoading.value = false
  }
}

function selectTemplate(code: string): void {
  if (selectedCode.value === code) return
  selectedCode.value = code
  void loadVersions(code)
}

// ---------------------------------------------------------------------------
// 新建版本
// ---------------------------------------------------------------------------

interface VersionFormState {
  version: string
  content: string
  variablesSchema: string
  modelConfig: string
}

const versionDialogOpen = ref(false)
const versionForm = ref<VersionFormState>({
  version: '',
  content: '',
  variablesSchema: '{\n  "type": "object"\n}',
  modelConfig: '{}',
})
const versionError = ref('')
const versionSaving = ref(false)

function nextVersion(): string {
  let max = 0
  for (const item of versions.value) {
    const match = /^v(\d+)$/i.exec(item.version)
    const digits = match?.[1]
    if (!digits) continue
    const parsed = Number(digits)
    if (Number.isFinite(parsed) && parsed > max) max = parsed
  }
  return `v${max + 1}`
}

function openVersionDialog(): void {
  if (!selectedCode.value) return
  versionForm.value = {
    version: nextVersion(),
    content: '',
    variablesSchema: '{\n  "type": "object"\n}',
    modelConfig: '{}',
  }
  versionError.value = ''
  versionDialogOpen.value = true
}

function parseJsonObject(text: string, label: string): Record<string, unknown> | null {
  let parsed: unknown
  try {
    parsed = JSON.parse(text.trim() || '{}')
  } catch {
    versionError.value = `${label} 不是合法 JSON，请检查格式。`
    return null
  }
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    versionError.value = `${label} 必须是 JSON 对象。`
    return null
  }
  return parsed as Record<string, unknown>
}

async function submitVersion(): Promise<void> {
  const code = selectedCode.value
  if (!code || versionSaving.value) return
  versionError.value = ''

  const version = versionForm.value.version.trim()
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,15}$/.test(version)) {
    versionError.value = '版本号需为 1–16 位字母、数字、点、下划线或短横线，且以字母或数字开头。'
    return
  }
  if (!versionForm.value.content.trim()) {
    versionError.value = '请填写 Prompt 内容。'
    return
  }
  const variablesSchema = parseJsonObject(versionForm.value.variablesSchema, 'variablesSchema')
  if (!variablesSchema) return
  const modelConfig = parseJsonObject(versionForm.value.modelConfig, 'modelConfig')
  if (!modelConfig) return

  versionSaving.value = true
  try {
    await adminApi.createPromptVersion(code, {
      version,
      content: versionForm.value.content.trim(),
      variablesSchema,
      modelConfig,
    })
    toast('草稿版本已创建', 'success', `${code} · ${version}，需发布后才会生效。`)
    versionDialogOpen.value = false
    await loadTemplates(code)
  } catch (cause) {
    versionError.value = errorMessage(cause, '创建版本失败')
  } finally {
    versionSaving.value = false
  }
}

// ---------------------------------------------------------------------------
// 发布
// ---------------------------------------------------------------------------

const publishTarget = ref<AdminPromptVersion | null>(null)
const publishOpen = ref(false)
const publishing = ref(false)

function openPublish(version: AdminPromptVersion): void {
  publishTarget.value = version
  publishOpen.value = true
}

async function confirmPublish(): Promise<void> {
  const target = publishTarget.value
  const code = selectedCode.value
  if (!target || !code || publishing.value) return
  publishing.value = true
  try {
    await adminApi.publishPromptVersion(code, target.version)
    toast('版本已发布', 'success', `${code} · ${target.version} 已生效，旧版本自动归档。`)
    publishOpen.value = false
    publishTarget.value = null
    await loadTemplates(code)
  } catch (cause) {
    toast('发布失败', 'error', errorMessage(cause))
  } finally {
    publishing.value = false
  }
}

onMounted(() => {
  void loadTemplates()
})
</script>

<template>
  <div class="min-w-0">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h2 class="text-base font-semibold text-text">Prompt 模板</h2>
        <p class="mt-1 text-xs text-text-muted">
          版本内容不可变；发布新版本会自动归档旧版本，运行时会缓存当前版本约 60 秒。
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <Button variant="outline" size="sm" :loading="templatesLoading" @click="loadTemplates()">
          <RefreshCw v-if="!templatesLoading" class="h-3.5 w-3.5" aria-hidden="true" />
          刷新
        </Button>
        <Button size="sm" :disabled="!selectedCode" @click="openVersionDialog">
          <Plus class="h-3.5 w-3.5" aria-hidden="true" />
          新建版本
        </Button>
      </div>
    </div>

    <div class="mt-3 grid gap-3 lg:grid-cols-[minmax(0,18rem)_minmax(0,1fr)] lg:items-start">
      <Card padding="sm" class="min-w-0">
        <h3 class="px-1 pb-2 text-xs font-semibold text-text-muted">
          模板列表
        </h3>
        <AsyncState
          :loading="templatesLoading"
          :error="templatesError"
          error-title="模板加载失败"
          :empty="templatesEmpty"
          empty-title="暂无 Prompt 模板"
          empty-description="模板由后端初始化；如为空请联系后端确认迁移是否执行。"
          :empty-icon="FileText"
          :loading-lines="4"
          @retry="loadTemplates()"
        >
          <ul class="max-h-[28rem] space-y-1 overflow-y-auto" role="list">
            <li v-for="template in templates" :key="template.code">
              <button
                type="button"
                :aria-pressed="selectedCode === template.code"
                :class="[
                  'w-full rounded-md px-3 py-2.5 text-left transition-colors min-h-11',
                  selectedCode === template.code
                    ? 'bg-surface-2 text-text-2'
                    : 'text-text hover:bg-surface-2',
                ]"
                @click="selectTemplate(template.code)"
              >
                <div class="flex items-center justify-between gap-2">
                  <span class="truncate text-sm font-medium">{{ template.name }}</span>
                  <Badge v-if="template.activeVersion" variant="success" dot>
                    {{ template.activeVersion }}
                  </Badge>
                  <Badge v-else variant="warning">未发布</Badge>
                </div>
                <p class="mt-0.5 truncate text-xs text-text-muted">
                  {{ template.code }} · {{ promptSceneLabel(template.scene) }} ·
                  {{ template.versionCount }} 个版本
                </p>
              </button>
            </li>
          </ul>
        </AsyncState>
      </Card>

      <Card padding="sm" class="min-w-0">
        <div class="flex flex-wrap items-center justify-between gap-2 px-1 pb-2">
          <div class="min-w-0">
            <h3 class="truncate text-sm font-semibold text-text">
              {{ selectedTemplate ? selectedTemplate.name : '版本列表' }}
            </h3>
            <p v-if="selectedTemplate" class="mt-0.5 truncate text-xs text-text-muted">
              {{ selectedTemplate.code }} · 当前发布
              {{ selectedTemplate.activeVersion ?? '无' }}
            </p>
          </div>
          <Badge v-if="selectedTemplate?.activeVersion" variant="brand">
            生效 {{ selectedTemplate.activeVersion }}
          </Badge>
        </div>

        <EmptyState
          v-if="!selectedCode && !templatesLoading"
          :icon="Sparkles"
          title="选择一个模板"
          description="从左侧选择 Prompt 模板，查看版本历史并创建草稿版本。"
        />

        <AsyncState
          v-else
          :loading="versionsLoading"
          :error="versionsError"
          error-title="版本列表加载失败"
          :empty="versionsEmpty"
          empty-title="暂无版本"
          empty-description="创建第一个草稿版本，联调评测通过后再发布。"
          :loading-lines="5"
          @retry="selectedCode ? loadVersions(selectedCode) : loadTemplates()"
        >
          <div class="overflow-x-auto rounded-lg border border-border">
            <table class="w-full min-w-[640px] border-collapse text-sm">
              <thead class="bg-surface-2 text-left text-xs text-text-muted">
                <tr>
                  <th scope="col" class="px-3 py-2.5 font-medium">版本</th>
                  <th scope="col" class="px-3 py-2.5 font-medium">状态</th>
                  <th scope="col" class="px-3 py-2.5 font-medium">内容哈希</th>
                  <th scope="col" class="px-3 py-2.5 font-medium">创建时间</th>
                  <th scope="col" class="px-3 py-2.5 font-medium">发布时间</th>
                  <th scope="col" class="px-3 py-2.5 text-right font-medium">操作</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-border bg-surface">
                <tr v-for="version in sortedVersions" :key="version.version">
                  <td class="whitespace-nowrap px-3 py-3">
                    <span class="font-mono text-sm font-medium text-text">{{ version.version }}</span>
                  </td>
                  <td class="px-3 py-3">
                    <Badge :variant="promptStatusMeta(version.status).variant" dot>
                      {{ promptStatusMeta(version.status).label }}
                    </Badge>
                  </td>
                  <td class="max-w-48 px-3 py-3">
                    <span
                      class="block truncate font-mono text-2xs text-text-muted"
                      :title="version.contentHash"
                    >
                      {{ version.contentHash || '—' }}
                    </span>
                  </td>
                  <td class="whitespace-nowrap px-3 py-3 text-xs text-text-muted">
                    <span :title="formatDate(version.createdAt, true)">
                      {{ version.createdAt ? formatRelative(version.createdAt) : '—' }}
                    </span>
                  </td>
                  <td class="whitespace-nowrap px-3 py-3 text-xs text-text-muted">
                    <span :title="version.publishedAt ? formatDate(version.publishedAt, true) : ''">
                      {{ version.publishedAt ? formatRelative(version.publishedAt) : '—' }}
                    </span>
                  </td>
                  <td class="px-3 py-3">
                    <div class="flex justify-end">
                      <Button
                        v-if="version.status !== 'PUBLISHED'"
                        variant="secondary"
                        size="sm"
                        :loading="publishing && publishTarget?.version === version.version"
                        @click="openPublish(version)"
                      >
                        <Send v-if="!publishing" class="h-3.5 w-3.5" aria-hidden="true" />
                        发布
                      </Button>
                      <span v-else class="px-2 text-xs text-text-muted">当前版本</span>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </AsyncState>
      </Card>
    </div>

    <Dialog v-model:open="versionDialogOpen" title="新建 Prompt 版本" size="lg">
      <form id="prompt-version-form" class="space-y-4" novalidate @submit.prevent="submitVersion">
        <Alert
          v-if="versionError"
          variant="danger"
          title="版本未创建"
          :description="versionError"
        />
        <Input
          v-model="versionForm.version"
          label="版本号"
          required
          placeholder="如：v4"
          hint="1–16 位字母、数字、点、下划线或短横线；创建后内容不可修改。"
        />
        <Textarea
          v-model="versionForm.content"
          label="Prompt 内容"
          required
          :rows="12"
          placeholder="输入完整 Prompt 文本"
        />
        <div class="grid gap-4 lg:grid-cols-2">
          <Textarea
            v-model="versionForm.variablesSchema"
            label="variablesSchema（JSON）"
            :rows="5"
            hint="描述模板变量结构，默认 {}。"
          />
          <Textarea
            v-model="versionForm.modelConfig"
            label="modelConfig（JSON）"
            :rows="5"
            hint="模型与采样参数，默认 {}。"
          />
        </div>
      </form>

      <template #footer>
        <Button variant="outline" @click="versionDialogOpen = false">取消</Button>
        <Button type="submit" form="prompt-version-form" :loading="versionSaving">
          创建草稿
        </Button>
      </template>
    </Dialog>

    <Dialog
      :open="publishOpen"
      title="发布 Prompt 版本"
      description="发布后该版本立即生效，当前发布版本会自动归档。"
      size="sm"
      @update:open="publishOpen = $event"
    >
      <Alert
        v-if="publishTarget && selectedTemplate"
        variant="warning"
        title="确认发布"
        :description="`${selectedTemplate.code} · ${publishTarget.version} 将替换 ${selectedTemplate.activeVersion ?? '当前无发布版本'}，运行时缓存约 60 秒后全部生效。`"
      />
      <template #footer>
        <Button variant="outline" @click="publishOpen = false">取消</Button>
        <Button :loading="publishing" @click="confirmPublish">确认发布</Button>
      </template>
    </Dialog>
  </div>
</template>