<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ClipboardList, RefreshCw } from 'lucide-vue-next'
import AsyncState from '@/components/common/AsyncState.vue'
import AdminPagination from '@/components/admin/AdminPagination.vue'
import Button from '@/components/ui/Button.vue'
import { errorMessage } from '@/api/client'
import * as adminApi from '@/api/admin'
import type { AdminAuditLog } from '@/api/admin'
import { formatDate, formatRelative } from '@/utils/format'
import { jsonPreview } from './meta'

const PAGE_SIZE = 20

const logs = ref<AdminAuditLog[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(true)
const loadError = ref<string | null>(null)

const isEmpty = computed(() => !loading.value && !loadError.value && logs.value.length === 0)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const result = await adminApi.listAuditLogs({ page: page.value, size: PAGE_SIZE })
    logs.value = result.items
    total.value = result.total
  } catch (cause) {
    loadError.value = errorMessage(cause, '审计日志加载失败')
  } finally {
    loading.value = false
  }
}

function goPage(next: number): void {
  page.value = next
  void load()
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="min-w-0">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h2 class="text-base font-semibold text-text">审计日志</h2>
        <p class="mt-1 text-xs text-text-muted">
          记录管理端写操作的操作者、目标与前后快照；before/after 已脱敏。
        </p>
      </div>
      <Button variant="ghost" size="sm" :loading="loading" @click="load">
        <RefreshCw v-if="!loading" class="h-3.5 w-3.5" aria-hidden="true" />
        刷新
      </Button>
    </div>

    <AsyncState
      class="mt-3"
      :loading="loading"
      :error="loadError"
      error-title="审计日志加载失败"
      :empty="isEmpty"
      empty-title="暂无审计记录"
      empty-description="管理端执行写操作后，这里会记录操作者、目标与前后快照。"
      :empty-icon="ClipboardList"
      :loading-lines="5"
      @retry="load"
    >
      <div class="overflow-x-auto rounded-lg border border-border">
        <table class="w-full min-w-[980px] border-collapse text-sm">
          <thead class="bg-surface-2 text-left text-xs text-text-muted">
            <tr>
              <th scope="col" class="px-3 py-2.5 font-medium">时间</th>
              <th scope="col" class="px-3 py-2.5 font-medium">管理员</th>
              <th scope="col" class="px-3 py-2.5 font-medium">动作</th>
              <th scope="col" class="px-3 py-2.5 font-medium">目标</th>
              <th scope="col" class="px-3 py-2.5 font-medium">变更前</th>
              <th scope="col" class="px-3 py-2.5 font-medium">变更后</th>
              <th scope="col" class="px-3 py-2.5 font-medium">请求 ID</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-border bg-surface">
            <tr v-for="log in logs" :key="log.id">
              <td class="whitespace-nowrap px-3 py-3 text-xs text-text-muted">
                <span :title="formatDate(log.createdAt, true)">
                  {{ log.createdAt ? formatRelative(log.createdAt) : '—' }}
                </span>
              </td>
              <td class="whitespace-nowrap px-3 py-3 text-xs text-text-muted">
                {{ log.adminUserId || '—' }}
              </td>
              <td class="whitespace-nowrap px-3 py-3">
                <span class="font-mono text-xs font-medium text-text">{{ log.action || '—' }}</span>
              </td>
              <td class="max-w-48 px-3 py-3 text-xs text-text-muted">
                <p class="truncate" :title="`${log.targetType} ${log.targetId}`">
                  {{ log.targetType || '—' }}
                </p>
                <p v-if="log.targetId" class="truncate font-mono text-2xs">#{{ log.targetId }}</p>
              </td>
              <td class="max-w-64 px-3 py-3">
                <span
                  class="block truncate font-mono text-2xs text-text-muted"
                  :title="jsonPreview(log.before)"
                >
                  {{ jsonPreview(log.before) }}
                </span>
              </td>
              <td class="max-w-64 px-3 py-3">
                <span
                  class="block truncate font-mono text-2xs text-text-muted"
                  :title="jsonPreview(log.after)"
                >
                  {{ jsonPreview(log.after) }}
                </span>
              </td>
              <td class="max-w-44 px-3 py-3">
                <span
                  class="block truncate font-mono text-2xs text-text-muted"
                  :title="log.requestId"
                >
                  {{ log.requestId || '—' }}
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