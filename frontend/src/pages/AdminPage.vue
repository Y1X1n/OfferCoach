<script setup lang="ts">
import { defineAsyncComponent, ref, type Component } from 'vue'
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
  | 'overview'
  | 'questions'
  | 'users'
  | 'ai-calls'
  | 'prompts'
  | 'ai-config'
  | 'settings'
  | 'audit'

interface AdminTabItem {
  key: AdminTab
  label: string
  icon: Component
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

function selectTab(tab: AdminTab): void {
  activeTab.value = tab
  if (!visited.value.includes(tab)) {
    visited.value = [...visited.value, tab]
  }
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
      >
        <button
          v-for="tab in tabs"
          :id="`admin-tab-${tab.key}`"
          :key="tab.key"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab.key"
          :class="[
            'flex min-h-11 shrink-0 items-center gap-1.5 rounded-md px-3 text-sm font-medium transition-colors sm:min-h-9',
            activeTab === tab.key
              ? 'bg-brand/10 text-brand'
              : 'text-text-muted hover:bg-surface-2 hover:text-text',
          ]"
          @click="selectTab(tab.key)"
        >
          <component :is="tab.icon" class="h-4 w-4" aria-hidden="true" />
          {{ tab.label }}
        </button>
      </div>
    </div>

    <div class="mt-5 min-w-0">
      <OverviewPanel
        v-if="visited.includes('overview')"
        v-show="activeTab === 'overview'"
        role="tabpanel"
        aria-labelledby="admin-tab-overview"
      />
      <QuestionBankPanel
        v-if="visited.includes('questions')"
        v-show="activeTab === 'questions'"
        role="tabpanel"
        aria-labelledby="admin-tab-questions"
      />
      <UserPanel
        v-if="visited.includes('users')"
        v-show="activeTab === 'users'"
        role="tabpanel"
        aria-labelledby="admin-tab-users"
      />
      <AiCallPanel
        v-if="visited.includes('ai-calls')"
        v-show="activeTab === 'ai-calls'"
        role="tabpanel"
        aria-labelledby="admin-tab-ai-calls"
      />
      <PromptPanel
        v-if="visited.includes('prompts')"
        v-show="activeTab === 'prompts'"
        role="tabpanel"
        aria-labelledby="admin-tab-prompts"
      />
      <AiConfigPanel
        v-if="visited.includes('ai-config')"
        v-show="activeTab === 'ai-config'"
        role="tabpanel"
        aria-labelledby="admin-tab-ai-config"
      />
      <SettingsPanel
        v-if="visited.includes('settings')"
        v-show="activeTab === 'settings'"
        role="tabpanel"
        aria-labelledby="admin-tab-settings"
      />
      <AuditLogPanel
        v-if="visited.includes('audit')"
        v-show="activeTab === 'audit'"
        role="tabpanel"
        aria-labelledby="admin-tab-audit"
      />
    </div>
  </div>
</template>