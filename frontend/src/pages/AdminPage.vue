<script setup lang="ts">
import { defineAsyncComponent, nextTick, ref, type Component } from 'vue'
import {
  Bot,
  ClipboardList,
  Cpu,
  FileText,
  Gauge,
  Library,
  SlidersHorizontal,
  Users,
} from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import OverviewPanel from '@/components/admin/OverviewPanel.vue'
import QuestionBankPanel from '@/components/admin/QuestionBankPanel.vue'
import UserPanel from '@/components/admin/UserPanel.vue'
import AiCallPanel from '@/components/admin/AiCallPanel.vue'
import PromptPanel from '@/components/admin/PromptPanel.vue'
import AuditLogPanel from '@/components/admin/AuditLogPanel.vue'
import Badge from '@/components/ui/Badge.vue'
import Spinner from '@/components/ui/Spinner.vue'

/** AI 配置与平台策略面板仅在首次切换时加载对应 chunk。 */
const AiConfigPanel = defineAsyncComponent({
  loader: () => import('@/components/admin/AiConfigPanel.vue'),
  loadingComponent: Spinner,
  delay: 120,
})

const SettingsPanel = defineAsyncComponent({
  loader: () => import('@/components/admin/SettingsPanel.vue'),
  loadingComponent: Spinner,
  delay: 120,
})

type AdminTab =
  'overview' | 'questions' | 'users' | 'ai-calls' | 'prompts' | 'ai-config' | 'settings' | 'audit'

interface AdminTabItem {
  key: AdminTab
  label: string
  icon: Component
}

/** tabpanel 容器始终存在（保证 aria-controls 指向有效元素），面板组件按访问懒加载。 */
const panels: Record<AdminTab, Component> = {
  overview: OverviewPanel,
  questions: QuestionBankPanel,
  users: UserPanel,
  'ai-calls': AiCallPanel,
  prompts: PromptPanel,
  'ai-config': AiConfigPanel,
  settings: SettingsPanel,
  audit: AuditLogPanel,
}

const tabs: AdminTabItem[] = [
  { key: 'overview', label: '概览', icon: Gauge },
  { key: 'questions', label: '题库', icon: Library },
  { key: 'users', label: '用户', icon: Users },
  { key: 'ai-calls', label: 'AI 日志', icon: Cpu },
  { key: 'prompts', label: 'Prompt', icon: FileText },
  { key: 'ai-config', label: 'AI 配置', icon: Bot },
  { key: 'settings', label: '平台策略', icon: SlidersHorizontal },
  { key: 'audit', label: '审计日志', icon: ClipboardList },
]

const activeTab = ref<AdminTab>('overview')
const visited = ref<AdminTab[]>(['overview'])

/** roving tabindex 需要直接操作 tab 按钮，按 key 收集元素引用。 */
const tabEls = new Map<AdminTab, HTMLButtonElement>()

function setTabEl(key: AdminTab, el: unknown): void {
  if (el instanceof HTMLButtonElement) {
    tabEls.set(key, el)
  } else {
    tabEls.delete(key)
  }
}

function selectTab(tab: AdminTab): void {
  activeTab.value = tab
  if (!visited.value.includes(tab)) {
    visited.value = [...visited.value, tab]
  }
}

/** ArrowLeft/ArrowRight 循环切换，Home/End 跳到首尾，并同步移动焦点。 */
function onTabKeydown(event: KeyboardEvent, index: number): void {
  let nextIndex: number
  switch (event.key) {
    case 'ArrowRight':
      nextIndex = (index + 1) % tabs.length
      break
    case 'ArrowLeft':
      nextIndex = (index - 1 + tabs.length) % tabs.length
      break
    case 'Home':
      nextIndex = 0
      break
    case 'End':
      nextIndex = tabs.length - 1
      break
    default:
      return
  }
  event.preventDefault()
  const nextTab = tabs[nextIndex]
  if (!nextTab) return
  selectTab(nextTab.key)
  void nextTick(() => tabEls.get(nextTab.key)?.focus())
}
</script>

<template>
  <div class="min-w-0">
    <PageHeader
      title="管理后台"
      description="题库、用户、AI 调用与 Prompt 版本集中管理；所有写操作都会写入审计日志。"
      eyebrow="管理"
    >
      <template #actions>
        <Badge variant="brand" size="md" dot>ADMIN</Badge>
        <Badge variant="neutral" size="md">写操作留痕</Badge>
      </template>
    </PageHeader>

    <div class="mt-5 overflow-x-auto pb-1">
      <div
        class="flex w-max min-w-full gap-1 rounded-lg border border-border bg-surface p-1"
        role="tablist"
        aria-label="管理功能分区"
        aria-orientation="horizontal"
      >
        <button
          v-for="(tab, index) in tabs"
          :id="`admin-tab-${tab.key}`"
          :key="tab.key"
          :ref="(el) => setTabEl(tab.key, el)"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab.key"
          :aria-controls="`admin-panel-${tab.key}`"
          :tabindex="activeTab === tab.key ? 0 : -1"
          :class="[
            'flex min-h-11 shrink-0 items-center gap-1.5 rounded-md px-3 text-sm font-medium transition-colors sm:min-h-9',
            activeTab === tab.key
              ? 'bg-brand/10 text-brand'
              : 'text-text-muted hover:bg-surface-2 hover:text-text',
          ]"
          @click="selectTab(tab.key)"
          @keydown="onTabKeydown($event, index)"
        >
          <component :is="tab.icon" class="h-4 w-4" aria-hidden="true" />
          {{ tab.label }}
        </button>
      </div>
    </div>

    <div class="mt-5 min-w-0">
      <div
        v-for="tab in tabs"
        v-show="activeTab === tab.key"
        :id="`admin-panel-${tab.key}`"
        :key="tab.key"
        role="tabpanel"
        :aria-labelledby="`admin-tab-${tab.key}`"
      >
        <component :is="panels[tab.key]" v-if="visited.includes(tab.key)" />
      </div>
    </div>
  </div>
</template>
