<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Activity, RefreshCw, Search, Stethoscope } from 'lucide-vue-next'
import AsyncState from '@/components/common/AsyncState.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import { errorMessage } from '@/api/client'
import * as adminApi from '@/api/admin'
import type { AdminDiagnosticsReports, AdminDiagnosticsSession, AdminReportTask } from '@/api/admin'
import { formatDate, formatRelative } from '@/utils/format'

const reports = ref<AdminDiagnosticsReports | null>(null)
const loading = ref(true)
const loadError = ref<string | null>(null)

const sessionId = ref('')
const session = ref<AdminDiagnosticsSession | null>(null)
const sessionLoading = ref(false)
const sessionError = ref<string | null>(null)

const stats = computed(() => {
  const value = reports.value?.stats
  if (!value) return []
  return [
    {
      label: '待生成报告',
      value: value.pendingReports,
      hint: `最老 ${formatAge(value.oldestPendingSeconds)}`,
    },
    { label: '24h 失败报告', value: value.failedReports24h, hint: '需要人工关注' },
    { label: '卡住会话', value: value.stuckSessions, hint: 'PREPARING > 10 分钟' },
    { label: '卡住评分', value: value.staleAnswers, hint: 'SCORING > 10 分钟' },
    { label: '24h AI 失败', value: value.aiFailures24h, hint: '模型/工具调用失败' },
  ]
})

async function loadReports(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    reports.value = await adminApi.getDiagnosticsReports()
  } catch (cause) {
    loadError.value = errorMessage(cause, '运行诊断加载失败')
  } finally {
    loading.value = false
  }
}

async function loadSession(): Promise<void> {
  const id = sessionId.value.trim()
  if (!id) {
    sessionError.value = '请输入会话 ID'
    return
  }
  sessionLoading.value = true
  sessionError.value = null
  try {
    session.value = await adminApi.getDiagnosticsSession(id)
  } catch (cause) {
    session.value = null
    sessionError.value = errorMessage(cause, '会话诊断加载失败')
  } finally {
    sessionLoading.value = false
  }
}

function formatAge(seconds: number): string {
  if (!seconds || seconds < 0) return '刚刚'
  if (seconds < 60) return `${Math.round(seconds)}s`
  if (seconds < 3600) return `${Math.round(seconds / 60)}m`
  return `${(seconds / 3600).toFixed(1)}h`
}

function reportTone(task: AdminReportTask): 'danger' | 'warning' | 'neutral' {
  if (task.attempts >= 3) return 'danger'
  if (task.ageSeconds >= 120) return 'warning'
  return 'neutral'
}

onMounted(() => {
  void loadReports()
})
</script>

<template>
  <div class="min-w-0">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h2 class="text-base font-semibold text-text">运行诊断</h2>
        <p class="mt-1 text-xs text-text-muted">
          报告任务积压、卡住状态与单场会话时间线；只读，不展示回答正文。
        </p>
      </div>
      <Button variant="ghost" size="sm" :loading="loading" @click="loadReports">
        <RefreshCw v-if="!loading" class="h-3.5 w-3.5" aria-hidden="true" />
        刷新
      </Button>
    </div>

    <AsyncState
      class="mt-3"
      :loading="loading"
      :error="loadError"
      error-title="运行诊断加载失败"
      :loading-lines="4"
      @retry="loadReports"
    >
      <div v-if="reports" class="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
        <Card v-for="item in stats" :key="item.label" padding="sm">
          <p class="text-xs text-text-muted">{{ item.label }}</p>
          <p class="tabular mt-1 text-2xl font-semibold text-text">{{ item.value }}</p>
          <p class="mt-1 text-2xs text-text-muted">{{ item.hint }}</p>
        </Card>
      </div>

      <div v-if="reports" class="mt-4 grid gap-4 lg:grid-cols-2">
        <Card padding="sm">
          <h3 class="flex items-center gap-2 text-sm font-semibold text-text">
            <Activity class="h-4 w-4 text-text-muted" aria-hidden="true" />
            待生成报告
          </h3>
          <p v-if="reports.pending.length === 0" class="mt-3 text-xs text-text-muted">
            当前没有积压。
          </p>
          <div v-else class="mt-3 overflow-x-auto">
            <table class="w-full min-w-[520px] text-xs">
              <thead class="text-left text-text-muted">
                <tr>
                  <th class="py-1.5 pr-3 font-medium">报告</th>
                  <th class="py-1.5 pr-3 font-medium">会话</th>
                  <th class="py-1.5 pr-3 font-medium">尝试</th>
                  <th class="py-1.5 font-medium">等待</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-border">
                <tr v-for="task in reports.pending" :key="task.id">
                  <td class="py-2 pr-3 font-mono">#{{ task.id }}</td>
                  <td class="py-2 pr-3 font-mono">#{{ task.sessionId }}</td>
                  <td class="py-2 pr-3">
                    <Badge :variant="reportTone(task)">{{ task.attempts }}</Badge>
                  </td>
                  <td class="tabular py-2 text-text-muted">{{ formatAge(task.ageSeconds) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </Card>

        <Card padding="sm">
          <h3 class="flex items-center gap-2 text-sm font-semibold text-text">
            <Stethoscope class="h-4 w-4 text-text-muted" aria-hidden="true" />
            最近失败
          </h3>
          <p v-if="reports.failed.length === 0" class="mt-3 text-xs text-text-muted">
            最近没有失败报告。
          </p>
          <div v-else class="mt-3 overflow-x-auto">
            <table class="w-full min-w-[560px] text-xs">
              <thead class="text-left text-text-muted">
                <tr>
                  <th class="py-1.5 pr-3 font-medium">报告</th>
                  <th class="py-1.5 pr-3 font-medium">会话</th>
                  <th class="py-1.5 pr-3 font-medium">尝试</th>
                  <th class="py-1.5 font-medium">原因</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-border">
                <tr v-for="task in reports.failed" :key="task.id">
                  <td class="py-2 pr-3 font-mono">#{{ task.id }}</td>
                  <td class="py-2 pr-3 font-mono">#{{ task.sessionId }}</td>
                  <td class="py-2 pr-3">
                    <Badge :variant="reportTone(task)">{{ task.attempts }}</Badge>
                  </td>
                  <td
                    class="max-w-44 truncate py-2 font-mono text-danger-strong"
                    :title="task.lastError"
                  >
                    {{ task.lastError || '—' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </Card>
      </div>
    </AsyncState>

    <Card padding="sm" class="mt-4">
      <form
        class="flex flex-wrap items-end gap-2"
        role="search"
        aria-label="按会话 ID 诊断"
        @submit.prevent="loadSession"
      >
        <div class="min-w-0 flex-1">
          <label for="diagnostics-session" class="text-xs font-medium text-text-2">会话 ID</label>
          <input
            id="diagnostics-session"
            v-model="sessionId"
            type="search"
            inputmode="numeric"
            placeholder="例如 1024"
            class="mt-1 h-9 w-full rounded-md border border-border bg-surface px-3 text-sm text-text focus:border-accent/60"
          />
        </div>
        <Button type="submit" size="sm" :loading="sessionLoading">
          <Search class="h-3.5 w-3.5" aria-hidden="true" />
          查会话
        </Button>
      </form>

      <p v-if="sessionError" class="mt-3 text-xs text-danger-strong">{{ sessionError }}</p>

      <div v-if="session" class="mt-4 space-y-4">
        <div class="flex flex-wrap items-center gap-3">
          <Badge variant="brand" dot>会话 #{{ session.session.id }}</Badge>
          <Badge variant="neutral">{{ session.session.status }}</Badge>
          <span class="text-xs text-text-muted">用户 #{{ session.session.userId }}</span>
          <span class="text-xs text-text-muted">
            题目 {{ session.session.answeredCount }}/{{ session.session.questionCount }} · 当前第
            {{ session.session.currentSeq }} 题
          </span>
          <span v-if="session.session.totalScore !== null" class="text-xs text-text-muted">
            总分 {{ session.session.totalScore }}
          </span>
        </div>
        <p class="text-xs text-text-muted">
          {{ session.session.resumeTitle || '简历已删除' }} ·
          {{ session.session.jdTitle || '岗位未命名' }} · 创建于
          {{ formatDate(session.session.createdAt, true) }}
        </p>
        <p v-if="session.session.failureReason" class="text-xs text-danger-strong">
          失败原因：{{ session.session.failureReason }}
        </p>

        <div class="grid gap-4 lg:grid-cols-2">
          <div>
            <h4 class="text-xs font-semibold text-text">题目 / 回答</h4>
            <div class="mt-2 overflow-x-auto">
              <table class="w-full min-w-[460px] text-xs">
                <thead class="text-left text-text-muted">
                  <tr>
                    <th class="py-1.5 pr-3 font-medium">#</th>
                    <th class="py-1.5 pr-3 font-medium">维度</th>
                    <th class="py-1.5 pr-3 font-medium">题目状态</th>
                    <th class="py-1.5 font-medium">回答状态</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-border">
                  <tr v-for="question in session.questions" :key="question.id">
                    <td class="tabular py-2 pr-3">{{ question.seq }}</td>
                    <td class="py-2 pr-3">{{ question.dimension }} / {{ question.difficulty }}</td>
                    <td class="py-2 pr-3">{{ question.status }} · {{ question.source }}</td>
                    <td class="py-2">
                      {{
                        session.answers.find((answer) => answer.questionId === question.id)
                          ?.status ?? '—'
                      }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div>
            <h4 class="text-xs font-semibold text-text">报告</h4>
            <div v-if="session.report" class="mt-2 space-y-1 text-xs text-text-muted">
              <p>报告 #{{ session.report.id }} · 尝试 {{ session.report.attempts }} 次</p>
              <p>等待 {{ formatAge(session.report.ageSeconds) }}</p>
              <p v-if="session.report.lastError" class="text-danger-strong">
                最后错误：{{ session.report.lastError }}
              </p>
              <p>更新于 {{ formatRelative(session.report.updatedAt) }}</p>
            </div>
            <p v-else class="mt-2 text-xs text-text-muted">这场会话还没有报告记录。</p>

            <h4 class="mt-4 text-xs font-semibold text-text">关联 AI 调用</h4>
            <p v-if="session.aiCalls.length === 0" class="mt-2 text-xs text-text-muted">
              没有按会话关联到 AI 调用（旧数据或未落 sessionId）。
            </p>
            <div v-else class="mt-2 overflow-x-auto">
              <table class="w-full min-w-[420px] text-xs">
                <thead class="text-left text-text-muted">
                  <tr>
                    <th class="py-1.5 pr-3 font-medium">场景</th>
                    <th class="py-1.5 pr-3 font-medium">状态</th>
                    <th class="py-1.5 pr-3 font-medium">耗时</th>
                    <th class="py-1.5 font-medium">请求</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-border">
                  <tr v-for="call in session.aiCalls" :key="call.id">
                    <td class="py-2 pr-3">{{ call.scene }} / {{ call.model }}</td>
                    <td class="py-2 pr-3">{{ call.status }}</td>
                    <td class="tabular py-2 pr-3">{{ call.latencyMs ?? '—' }} ms</td>
                    <td class="max-w-40 truncate py-2 font-mono text-2xs" :title="call.requestId">
                      {{ call.requestId || '—' }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </Card>
  </div>
</template>
