<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  Coins,
  KeyRound,
  Power,
  RefreshCw,
  RotateCcw,
  Search,
  ShieldCheck,
  Unlock,
  X,
} from 'lucide-vue-next'
import AsyncState from '@/components/common/AsyncState.vue'
import AdminPagination from '@/components/admin/AdminPagination.vue'
import Alert from '@/components/ui/Alert.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import Dialog from '@/components/ui/Dialog.vue'
import Input from '@/components/ui/Input.vue'
import { createRequestId, errorMessage } from '@/api/client'
import * as adminApi from '@/api/admin'
import type { AdminQuotaType, AdminRole, AdminUser, AdminUserStatus } from '@/api/admin'
import { useToast } from '@/composables/useToast'
import { useAppStore } from '@/stores/app'
import { formatDate, formatRelative } from '@/utils/format'
import { roleMeta, userStatusMeta } from './meta'

const PAGE_SIZE = 20

const { toast } = useToast()
const appStore = useAppStore()

const users = ref<AdminUser[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(true)
const loadError = ref<string | null>(null)

const keyword = ref('')
const statusFilter = ref('')

let loadSeq = 0

const hasFilters = computed(() => keyword.value.trim() !== '' || statusFilter.value !== '')
const isEmpty = computed(() => !loading.value && !loadError.value && users.value.length === 0)

async function load(): Promise<void> {
  const seq = ++loadSeq
  loading.value = true
  loadError.value = null
  try {
    const result = await adminApi.listUsers({
      page: page.value,
      size: PAGE_SIZE,
      ...(keyword.value.trim() ? { keyword: keyword.value.trim() } : {}),
      ...(statusFilter.value ? { status: statusFilter.value as AdminUserStatus } : {}),
    })
    if (seq !== loadSeq) return
    users.value = result.items
    total.value = result.total
  } catch (cause) {
    if (seq !== loadSeq) return
    loadError.value = errorMessage(cause, '用户列表加载失败')
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function applyFilters(): void {
  page.value = 1
  void load()
}

function resetFilters(): void {
  keyword.value = ''
  statusFilter.value = ''
  applyFilters()
}

function goPage(next: number): void {
  page.value = next
  void load()
}

function creditsText(user: AdminUser): string {
  if (user.interviewCredits === null && user.resumeCredits === null) return '—'
  const interview = user.interviewCredits === null ? '—' : String(user.interviewCredits)
  const resume = user.resumeCredits === null ? '—' : String(user.resumeCredits)
  return `面试 ${interview} · 解析 ${resume}`
}

function isSelf(user: AdminUser): boolean {
  return appStore.user?.id === user.id
}

// ---------------------------------------------------------------------------
// 启用 / 停用
// ---------------------------------------------------------------------------

const statusTarget = ref<AdminUser | null>(null)
const statusOpen = ref(false)
const statusSaving = ref(false)

const statusConfirmText = computed(() => {
  const target = statusTarget.value
  if (!target) return ''
  const name = target.email || target.nickname
  return target.role === 'ADMIN'
    ? `${name} 是管理员，停用后其管理后台登录与全部写权限会立即失效，重新启用后可恢复。`
    : `${name} 将立即无法登录，重新启用后可恢复。`
})

function requestToggle(user: AdminUser): void {
  if (user.status === 'ACTIVE') {
    statusTarget.value = user
    statusOpen.value = true
    return
  }
  void applyStatus(user, 'ACTIVE')
}

async function applyStatus(user: AdminUser, status: AdminUserStatus): Promise<void> {
  if (statusSaving.value) return
  statusSaving.value = true
  try {
    await adminApi.updateUserStatus(user.id, status)
    toast(status === 'ACTIVE' ? '账号已启用' : '账号已停用', 'success', user.email || user.nickname)
    statusOpen.value = false
    statusTarget.value = null
    await load()
  } catch (cause) {
    toast(status === 'ACTIVE' ? '启用失败' : '停用失败', 'error', errorMessage(cause))
  } finally {
    statusSaving.value = false
  }
}

async function confirmDisable(): Promise<void> {
  const target = statusTarget.value
  if (!target) return
  await applyStatus(target, 'DISABLED')
}

// ---------------------------------------------------------------------------
// 角色调整
// ---------------------------------------------------------------------------

interface RoleOption {
  value: AdminRole
  label: string
  description: string
}

const roleOptions: RoleOption[] = [
  { value: 'USER', label: '用户', description: '仅能使用前台面试、简历与报告功能。' },
  { value: 'SUPPORT', label: '客服', description: '用于日常问题排查与用户支持。' },
  { value: 'ADMIN', label: '管理员', description: '可访问管理后台全部功能与写操作。' },
]

const roleTarget = ref<AdminUser | null>(null)
const roleOpen = ref(false)
const roleValue = ref<AdminRole>('USER')
const roleError = ref('')
const roleSaving = ref(false)
/** 提升为 ADMIN 前的二次确认。 */
const adminConfirmOpen = ref(false)

const roleChanged = computed(
  () => roleTarget.value !== null && roleValue.value !== roleTarget.value.role,
)

function openRole(user: AdminUser): void {
  roleTarget.value = user
  roleValue.value = user.role
  roleError.value = ''
  roleOpen.value = true
}

function selectRole(value: AdminRole): void {
  roleValue.value = value
  roleError.value = ''
}

const adminConfirmText = computed(() => {
  const target = roleTarget.value
  if (!target) return ''
  const name = target.email || target.nickname
  return `${name} 将立即获得管理后台全部权限：用户与角色管理、题库、Prompt、AI 配置与平台策略的读写操作，变更会写入审计日志。`
})

/** 目标角色为 ADMIN 时先二次确认，其余角色直接提交。 */
function requestRoleSubmit(): void {
  if (!roleChanged.value || roleSaving.value) return
  if (roleValue.value === 'ADMIN') {
    adminConfirmOpen.value = true
    return
  }
  void submitRole()
}

function confirmAdminRole(): void {
  adminConfirmOpen.value = false
  void submitRole()
}

async function submitRole(): Promise<void> {
  const target = roleTarget.value
  if (!target || roleSaving.value || !roleChanged.value) return
  roleSaving.value = true
  roleError.value = ''
  try {
    await adminApi.updateUserRole(target.id, roleValue.value)
    toast(
      '角色已更新',
      'success',
      `${target.email || target.nickname} → ${roleMeta(roleValue.value).label}`,
    )
    roleOpen.value = false
    roleTarget.value = null
    await load()
  } catch (cause) {
    roleError.value = errorMessage(cause, '角色调整失败')
  } finally {
    roleSaving.value = false
  }
}

// ---------------------------------------------------------------------------
// 解除登录锁定
// ---------------------------------------------------------------------------

const unlockingId = ref<string | null>(null)

async function unlock(user: AdminUser): Promise<void> {
  if (unlockingId.value !== null) return
  unlockingId.value = user.id
  try {
    await adminApi.unlockUser(user.id)
    toast('账号已解锁', 'success', `${user.email || user.nickname} 的失败计数与锁定已清空。`)
    await load()
  } catch (cause) {
    toast('解锁失败', 'error', errorMessage(cause))
  } finally {
    unlockingId.value = null
  }
}

// ---------------------------------------------------------------------------
// 重置密码
// ---------------------------------------------------------------------------

const resetTarget = ref<AdminUser | null>(null)
const resetOpen = ref(false)
const resetPassword = ref('')
const resetConfirm = ref('')
const resetError = ref('')
const resetSaving = ref(false)

function passwordIssue(value: string): string {
  if (value.length < 8 || value.length > 64) return '密码长度需为 8–64 位。'
  if (!/[A-Za-z]/.test(value)) return '密码需至少包含一个字母。'
  if (!/\d/.test(value)) return '密码需至少包含一个数字。'
  return ''
}

function openReset(user: AdminUser): void {
  resetTarget.value = user
  resetPassword.value = ''
  resetConfirm.value = ''
  resetError.value = ''
  resetOpen.value = true
}

async function submitReset(): Promise<void> {
  const target = resetTarget.value
  if (!target || resetSaving.value) return
  const issue = passwordIssue(resetPassword.value)
  if (issue) {
    resetError.value = issue
    return
  }
  if (resetPassword.value !== resetConfirm.value) {
    resetError.value = '两次输入的密码不一致。'
    return
  }
  resetSaving.value = true
  resetError.value = ''
  try {
    await adminApi.resetUserPassword(target.id, resetPassword.value)
    resetOpen.value = false
    toast('密码已重置', 'success', '请通知用户使用新密码登录；旧会话可能已失效。')
    resetTarget.value = null
    resetPassword.value = ''
    resetConfirm.value = ''
  } catch (cause) {
    resetError.value = errorMessage(cause, '重置密码失败')
  } finally {
    resetSaving.value = false
  }
}

// ---------------------------------------------------------------------------
// 额度调整
// ---------------------------------------------------------------------------

const quotaOpen = ref(false)
const quotaUser = ref<AdminUser | null>(null)
const quotaType = ref<AdminQuotaType>('INTERVIEW')
const quotaDelta = ref('1')
const quotaReason = ref('')
const quotaKey = ref('')
const quotaError = ref('')
const quotaSaving = ref(false)

function openQuota(user: AdminUser): void {
  quotaUser.value = user
  quotaType.value = 'INTERVIEW'
  quotaDelta.value = '1'
  quotaReason.value = ''
  quotaKey.value = `admin_quota_${createRequestId()}`
  quotaError.value = ''
  quotaOpen.value = true
}

async function submitQuota(): Promise<void> {
  const user = quotaUser.value
  if (!user || quotaSaving.value) return
  quotaError.value = ''

  const delta = Number(quotaDelta.value)
  if (!Number.isInteger(delta) || delta === 0) {
    quotaError.value = '调整数量必须是非零整数（正数增加，负数扣减）。'
    return
  }
  if (!quotaReason.value.trim()) {
    quotaError.value = '请填写调整原因，原因会写入额度流水与审计日志。'
    return
  }
  if (!quotaKey.value.trim()) {
    quotaError.value = '请填写幂等键，重复提交同一幂等键不会重复入账。'
    return
  }

  quotaSaving.value = true
  try {
    await adminApi.adjustUserQuota(user.id, {
      type: quotaType.value,
      delta,
      reason: quotaReason.value.trim(),
      idempotencyKey: quotaKey.value.trim(),
    })
    toast(
      '额度已调整',
      'success',
      `${user.email || user.nickname} · ${delta > 0 ? '+' : ''}${delta} 次`,
    )
    quotaOpen.value = false
    quotaUser.value = null
    await load()
  } catch (cause) {
    quotaError.value = errorMessage(cause, '额度调整失败')
  } finally {
    quotaSaving.value = false
  }
}

onMounted(() => {
  void load()
})

// 关闭重置密码对话框后立即丢弃明文密码。
watch(resetOpen, (open) => {
  if (!open) {
    resetPassword.value = ''
    resetConfirm.value = ''
  }
})
</script>

<template>
  <div class="min-w-0">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h2 class="text-base font-semibold text-text">用户管理</h2>
        <p class="mt-1 text-xs text-text-muted">
          停用后用户无法登录；角色、解锁、密码重置与额度调整都会写入审计日志。
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
        aria-label="筛选用户"
        @submit.prevent="applyFilters"
      >
        <div class="relative lg:col-span-2">
          <label for="user-keyword" class="sr-only">搜索用户</label>
          <Search
            class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted"
            aria-hidden="true"
          />
          <input
            id="user-keyword"
            v-model="keyword"
            type="search"
            placeholder="搜索邮箱或昵称"
            class="h-11 w-full rounded-md border border-border bg-surface pl-9 pr-3 text-sm text-text transition-colors placeholder:text-muted focus:border-accent/60 sm:h-9"
          />
        </div>

        <div>
          <label for="user-status" class="sr-only">状态</label>
          <select
            id="user-status"
            v-model="statusFilter"
            class="h-11 w-full rounded-md border border-border bg-surface px-3 text-sm text-text transition-colors focus:border-accent/60 sm:h-9"
          >
            <option value="">全部状态</option>
            <option value="ACTIVE">正常</option>
            <option value="DISABLED">已停用</option>
            <option value="LOCKED">已锁定</option>
          </select>
        </div>

        <div class="flex flex-wrap items-center gap-2">
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
            清除
          </Button>
        </div>
      </form>
    </Card>

    <AsyncState
      class="mt-3"
      :loading="loading"
      :error="loadError"
      error-title="用户列表加载失败"
      :empty="isEmpty"
      empty-title="没有匹配的用户"
      :empty-description="hasFilters ? '调整关键词或状态筛选后重试。' : '暂无注册用户。'"
      :empty-icon="Search"
      :loading-lines="5"
      @retry="load"
    >
      <div class="overflow-x-auto rounded-lg border border-border">
        <table class="w-full min-w-[980px] border-collapse text-sm">
          <thead class="bg-surface-2 text-left text-xs text-text-muted">
            <tr>
              <th scope="col" class="px-3 py-2.5 font-medium">用户</th>
              <th scope="col" class="px-3 py-2.5 font-medium">角色</th>
              <th scope="col" class="px-3 py-2.5 font-medium">状态</th>
              <th scope="col" class="px-3 py-2.5 font-medium">套餐</th>
              <th scope="col" class="px-3 py-2.5 font-medium">剩余额度</th>
              <th scope="col" class="px-3 py-2.5 font-medium">注册时间</th>
              <th scope="col" class="px-3 py-2.5 font-medium">最近登录</th>
              <th scope="col" class="px-3 py-2.5 text-right font-medium">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-border bg-surface">
            <tr v-for="user in users" :key="user.id">
              <td class="max-w-64 px-3 py-3">
                <p class="truncate font-medium text-text" :title="user.nickname">
                  {{ user.nickname || '未命名用户' }}
                  <span v-if="isSelf(user)" class="text-xs font-normal text-text-muted">
                    （本人）
                  </span>
                </p>
                <p class="truncate text-xs text-text-muted" :title="user.email">
                  {{ user.email || '—' }}
                </p>
              </td>
              <td class="px-3 py-3">
                <Badge :variant="roleMeta(user.role).variant">{{
                  roleMeta(user.role).label
                }}</Badge>
              </td>
              <td class="px-3 py-3">
                <Badge :variant="userStatusMeta(user.status).variant" dot>
                  {{ userStatusMeta(user.status).label }}
                </Badge>
              </td>
              <td class="px-3 py-3 text-text-muted">{{ user.planCode || '—' }}</td>
              <td class="tabular whitespace-nowrap px-3 py-3 text-xs text-text-muted">
                {{ creditsText(user) }}
              </td>
              <td class="whitespace-nowrap px-3 py-3 text-xs text-text-muted">
                <span :title="formatDate(user.createdAt, true)">
                  {{ user.createdAt ? formatRelative(user.createdAt) : '—' }}
                </span>
              </td>
              <td class="whitespace-nowrap px-3 py-3 text-xs text-text-muted">
                <span :title="formatDate(user.lastLoginAt, true)">
                  {{ user.lastLoginAt ? formatRelative(user.lastLoginAt) : '—' }}
                </span>
              </td>
              <td class="px-3 py-3">
                <div class="flex flex-wrap justify-end gap-1.5">
                  <Button
                    v-if="user.status !== 'DELETED'"
                    :variant="user.status === 'ACTIVE' ? 'outline' : 'secondary'"
                    size="sm"
                    :class="user.status === 'ACTIVE' ? 'text-danger-strong' : ''"
                    :loading="statusSaving && statusTarget?.id === user.id"
                    :disabled="statusSaving"
                    @click="requestToggle(user)"
                  >
                    <Power class="h-3.5 w-3.5" aria-hidden="true" />
                    {{ user.status === 'ACTIVE' ? '停用' : '启用' }}
                  </Button>
                  <Button
                    v-if="user.status === 'LOCKED'"
                    variant="secondary"
                    size="sm"
                    :loading="unlockingId === user.id"
                    :disabled="unlockingId !== null && unlockingId !== user.id"
                    @click="unlock(user)"
                  >
                    <Unlock class="h-3.5 w-3.5" aria-hidden="true" />
                    解锁
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    :disabled="user.status === 'DELETED'"
                    @click="openRole(user)"
                  >
                    <ShieldCheck class="h-3.5 w-3.5" aria-hidden="true" />
                    角色
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    :disabled="user.status === 'DELETED'"
                    @click="openReset(user)"
                  >
                    <KeyRound class="h-3.5 w-3.5" aria-hidden="true" />
                    重置密码
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    :disabled="user.status === 'DELETED'"
                    @click="openQuota(user)"
                  >
                    <Coins class="h-3.5 w-3.5" aria-hidden="true" />
                    调整额度
                  </Button>
                </div>
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

    <ConfirmDialog
      :open="statusOpen"
      title="停用账号"
      description="停用后该用户无法登录，已生成的报告仍会保留。"
      size="sm"
      alert-variant="warning"
      alert-title="确认停用"
      :alert-description="statusConfirmText"
      confirm-label="确认停用"
      confirm-variant="danger"
      :loading="statusSaving"
      @update:open="statusOpen = $event"
      @confirm="confirmDisable"
    />

    <Dialog
      v-model:open="quotaOpen"
      title="调整额度"
      :description="
        quotaUser ? `${quotaUser.nickname}（${quotaUser.email || '无邮箱'}）` : '手动补扣额度'
      "
      size="md"
    >
      <form id="quota-form" class="space-y-4" novalidate @submit.prevent="submitQuota">
        <Alert v-if="quotaError" variant="danger" title="调整未提交" :description="quotaError" />
        <div class="grid gap-4 sm:grid-cols-2">
          <div class="flex flex-col gap-1.5">
            <label for="quota-type" class="text-sm font-medium text-text">额度类型</label>
            <select
              id="quota-type"
              v-model="quotaType"
              class="h-11 w-full rounded-md border border-border bg-surface px-3 text-sm text-text transition-colors focus:border-accent/60 sm:h-9"
            >
              <option value="INTERVIEW">面试次数</option>
              <option value="RESUME">简历解析次数</option>
            </select>
          </div>
          <Input
            v-model="quotaDelta"
            type="number"
            label="调整数量"
            required
            hint="正数增加，负数扣减，不能为 0。"
          />
        </div>
        <Input
          v-model="quotaReason"
          label="调整原因"
          required
          placeholder="如：客服补偿：报告生成失败"
          hint="会写入额度流水与审计日志，请写明来源。"
        />
        <Input
          v-model="quotaKey"
          label="幂等键"
          required
          hint="重复提交同一幂等键不会重复入账；请勿复用历史键。"
        />
      </form>

      <template #footer>
        <Button variant="outline" @click="quotaOpen = false">取消</Button>
        <Button type="submit" form="quota-form" :loading="quotaSaving">确认调整</Button>
      </template>
    </Dialog>

    <Dialog
      v-model:open="roleOpen"
      title="调整角色"
      :description="
        roleTarget ? `${roleTarget.nickname}（${roleTarget.email || '无邮箱'}）` : '调整用户角色'
      "
      size="md"
    >
      <div class="space-y-4">
        <Alert v-if="roleError" variant="danger" title="角色未更新" :description="roleError" />
        <Alert
          v-if="roleTarget && isSelf(roleTarget)"
          variant="warning"
          title="当前登录账号"
          description="该账号是当前登录的管理员，服务端可能拒绝此操作；如被拒绝会在上方展示具体原因。"
        />
        <div class="space-y-2" role="radiogroup" aria-label="选择角色">
          <button
            v-for="option in roleOptions"
            :key="option.value"
            type="button"
            role="radio"
            :aria-checked="roleValue === option.value"
            :class="[
              'flex min-h-11 w-full items-center justify-between gap-3 rounded-md border px-3 py-2.5 text-left transition-colors',
              roleValue === option.value
                ? 'border-text/30 bg-surface-2'
                : 'border-border hover:bg-surface-2',
            ]"
            @click="selectRole(option.value)"
          >
            <span class="flex min-w-0 flex-col">
              <span class="text-sm font-medium text-text">{{ option.label }}</span>
              <span class="text-xs text-text-muted">{{ option.description }}</span>
            </span>
            <Badge :variant="roleMeta(option.value).variant">{{ option.value }}</Badge>
          </button>
        </div>
        <p class="text-xs text-text-muted">调整后立即生效；所有角色变更都会写入审计日志。</p>
      </div>

      <template #footer>
        <Button variant="outline" @click="roleOpen = false">取消</Button>
        <Button :loading="roleSaving" :disabled="!roleChanged" @click="requestRoleSubmit">
          确认调整
        </Button>
      </template>
    </Dialog>

    <ConfirmDialog
      v-model:open="adminConfirmOpen"
      title="提升为管理员"
      description="管理员可访问管理后台全部功能，包括用户、题库、Prompt、AI 配置与平台策略的写操作。"
      size="sm"
      alert-variant="danger"
      alert-title="授予全部管理权限"
      :alert-description="adminConfirmText"
      confirm-label="确认授予 ADMIN"
      confirm-variant="danger"
      :loading="roleSaving"
      @confirm="confirmAdminRole"
    />

    <Dialog
      v-model:open="resetOpen"
      title="重置密码"
      :description="
        resetTarget
          ? `${resetTarget.nickname}（${resetTarget.email || '无邮箱'}）`
          : '为指定用户设置新密码'
      "
      size="md"
    >
      <form id="reset-password-form" class="space-y-4" novalidate @submit.prevent="submitReset">
        <Alert v-if="resetError" variant="danger" title="密码未重置" :description="resetError" />
        <Alert
          v-if="resetTarget && isSelf(resetTarget)"
          variant="warning"
          title="当前登录账号"
          description="管理员不能重置自己的密码，服务端会拒绝该操作；如失败会在上方展示具体原因。"
        />
        <Alert
          variant="info"
          title="重置说明"
          description="重置后该用户的旧会话可能失效，请通过安全渠道通知用户新密码。"
        />
        <Input
          v-model="resetPassword"
          type="password"
          label="新密码"
          required
          autocomplete="new-password"
          hint="8–64 位，至少包含一个字母和一个数字。"
        />
        <Input
          v-model="resetConfirm"
          type="password"
          label="确认新密码"
          required
          autocomplete="new-password"
          hint="请再次输入新密码以确认。"
        />
      </form>

      <template #footer>
        <Button variant="outline" @click="resetOpen = false">取消</Button>
        <Button type="submit" form="reset-password-form" :loading="resetSaving">确认重置</Button>
      </template>
    </Dialog>
  </div>
</template>
