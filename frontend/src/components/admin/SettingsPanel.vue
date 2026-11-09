<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RefreshCw, Save, ShieldCheck, SlidersHorizontal } from 'lucide-vue-next'
import AsyncState from '@/components/common/AsyncState.vue'
import Alert from '@/components/ui/Alert.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue'
import Input from '@/components/ui/Input.vue'
import { errorMessage } from '@/api/client'
import * as adminApi from '@/api/admin'
import type { AdminPlatformSettings, AdminPlatformSettingsPayload } from '@/api/admin'
import { useToast } from '@/composables/useToast'
import { formatDate } from '@/utils/format'

const { toast } = useToast()

interface SettingsForm {
  registrationOpen: boolean
  dailyInterviewLimit: string
  dailyResumeLimit: string
  freeMonthlyInterviews: string
  freeMonthlyResumes: string
  inviteRewardInviterInterview: string
  inviteRewardInviteeResume: string
  inviteDailyCap: string
  inviteTotalCap: string
  registrationIpDailyCap: string
  registrationDeviceDailyCap: string
}

function emptyForm(): SettingsForm {
  return {
    registrationOpen: true,
    dailyInterviewLimit: '3',
    dailyResumeLimit: '3',
    freeMonthlyInterviews: '3',
    freeMonthlyResumes: '3',
    inviteRewardInviterInterview: '1',
    inviteRewardInviteeResume: '1',
    inviteDailyCap: '3',
    inviteTotalCap: '20',
    registrationIpDailyCap: '20',
    registrationDeviceDailyCap: '3',
  }
}

const settings = ref<AdminPlatformSettings | null>(null)
const form = ref<SettingsForm>(emptyForm())
const loading = ref(true)
const loadError = ref<string | null>(null)
const saving = ref(false)
const formError = ref('')
const confirmOpen = ref(false)
/** 待确认保存的载荷，确认后才会真正提交。 */
const pendingPayload = ref<AdminPlatformSettingsPayload | null>(null)

const isEmpty = computed(() => !loading.value && !loadError.value && settings.value === null)

const updatedText = computed(() => {
  const current = settings.value
  if (!current) return ''
  const parts: string[] = []
  if (current.updatedAt) parts.push(formatDate(current.updatedAt, true))
  if (current.updatedBy) parts.push(`更新人 ${current.updatedBy}`)
  return parts.join(' · ')
})

function syncForm(current: AdminPlatformSettings): void {
  settings.value = current
  form.value = {
    registrationOpen: current.registrationOpen,
    dailyInterviewLimit: String(current.dailyInterviewLimit),
    dailyResumeLimit: String(current.dailyResumeLimit),
    freeMonthlyInterviews: String(current.freeMonthlyInterviews),
    freeMonthlyResumes: String(current.freeMonthlyResumes),
    inviteRewardInviterInterview: String(current.inviteRewardInviterInterview),
    inviteRewardInviteeResume: String(current.inviteRewardInviteeResume),
    inviteDailyCap: String(current.inviteDailyCap),
    inviteTotalCap: String(current.inviteTotalCap),
    registrationIpDailyCap: String(current.registrationIpDailyCap),
    registrationDeviceDailyCap: String(current.registrationDeviceDailyCap),
  }
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    syncForm(await adminApi.getPlatformSettings())
  } catch (cause) {
    loadError.value = errorMessage(cause, '平台策略加载失败')
  } finally {
    loading.value = false
  }
}

const LIMITS: Record<keyof Omit<SettingsForm, 'registrationOpen'>, [number, number, string]> = {
  dailyInterviewLimit: [0, 10000, '每日模拟面试上限'],
  dailyResumeLimit: [0, 10000, '每日简历解析上限'],
  freeMonthlyInterviews: [0, 1000, '每月免费面试次数'],
  freeMonthlyResumes: [0, 1000, '每月免费解析次数'],
  inviteRewardInviterInterview: [0, 100, '邀请人奖励'],
  inviteRewardInviteeResume: [0, 100, '被邀请人奖励'],
  inviteDailyCap: [0, 1000, '邀请每日上限'],
  inviteTotalCap: [0, 10000, '邀请累计上限'],
  registrationIpDailyCap: [0, 10000, '单 IP 每日注册上限'],
  registrationDeviceDailyCap: [0, 1000, '单设备每日注册上限'],
}

function parseForm(): { payload: AdminPlatformSettingsPayload } | { error: string } {
  const fields = Object.keys(LIMITS) as (keyof typeof LIMITS)[]
  for (const field of fields) {
    const [min, max, label] = LIMITS[field]
    const raw = form.value[field].trim()
    if (!/^\d+$/.test(raw)) {
      return { error: `${label}需为非负整数。` }
    }
    const value = Number(raw)
    if (value < min || value > max) {
      return { error: `${label}需在 ${min}–${max} 之间。` }
    }
  }
  return {
    payload: {
      registrationOpen: form.value.registrationOpen,
      dailyInterviewLimit: Number(form.value.dailyInterviewLimit),
      dailyResumeLimit: Number(form.value.dailyResumeLimit),
      freeMonthlyInterviews: Number(form.value.freeMonthlyInterviews),
      freeMonthlyResumes: Number(form.value.freeMonthlyResumes),
      inviteRewardInviterInterview: Number(form.value.inviteRewardInviterInterview),
      inviteRewardInviteeResume: Number(form.value.inviteRewardInviteeResume),
      inviteDailyCap: Number(form.value.inviteDailyCap),
      inviteTotalCap: Number(form.value.inviteTotalCap),
      registrationIpDailyCap: Number(form.value.registrationIpDailyCap),
      registrationDeviceDailyCap: Number(form.value.registrationDeviceDailyCap),
    },
  }
}

interface SummaryItem {
  label: string
  value: string
}

function dailyText(value: number): string {
  return value === 0 ? '0（不限制）' : `${value} 次`
}

function rewardText(value: number): string {
  return value === 0 ? '0（暂停发放）' : `${value} 次`
}

/** 确认对话框展示的关键数值摘要，取自待提交载荷而非表单原始字符串。 */
const summary = computed<SummaryItem[]>(() => {
  const payload = pendingPayload.value
  if (!payload) return []
  return [
    { label: '开放注册', value: payload.registrationOpen ? '开放' : '关闭' },
    { label: '每日模拟面试上限', value: dailyText(payload.dailyInterviewLimit) },
    { label: '每日简历解析上限', value: dailyText(payload.dailyResumeLimit) },
    { label: '每月免费面试次数', value: `${payload.freeMonthlyInterviews} 次` },
    { label: '每月免费解析次数', value: `${payload.freeMonthlyResumes} 次` },
    { label: '邀请人奖励', value: rewardText(payload.inviteRewardInviterInterview) },
    { label: '被邀请人奖励', value: rewardText(payload.inviteRewardInviteeResume) },
    { label: '每人每日邀请上限', value: `${payload.inviteDailyCap} 次` },
    { label: '每人累计邀请上限', value: `${payload.inviteTotalCap} 次` },
    { label: '单 IP 每日注册上限', value: `${payload.registrationIpDailyCap} 个` },
    { label: '单设备每日注册上限', value: `${payload.registrationDeviceDailyCap} 个` },
  ]
})

function requestSave(): void {
  if (saving.value) return
  const parsed = parseForm()
  if ('error' in parsed) {
    formError.value = parsed.error
    return
  }
  formError.value = ''
  pendingPayload.value = parsed.payload
  confirmOpen.value = true
}

async function save(): Promise<void> {
  const payload = pendingPayload.value
  if (!payload || saving.value) return
  saving.value = true
  try {
    syncForm(await adminApi.updatePlatformSettings(payload))
    toast('平台策略已保存', 'success', '约 30 秒内对注册与额度生效。')
  } catch (cause) {
    formError.value = errorMessage(cause, '平台策略保存失败')
  } finally {
    confirmOpen.value = false
    saving.value = false
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
        <h2 class="text-base font-semibold text-text">平台策略</h2>
        <p class="mt-1 text-xs text-text-muted">
          在线调节每日模拟面试/简历解析次数、月度免费额度、邀请奖励与注册风控阈值。
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
      title="生效说明"
      description="保存后约 30 秒内生效；每日上限为 0 表示不限制，邀请奖励为 0 表示暂停发放但仍记录邀请关系。修改会写入审计日志。"
    />

    <AsyncState
      class="mt-3"
      :loading="loading"
      :error="loadError"
      error-title="平台策略加载失败"
      :empty="isEmpty"
      empty-title="暂无平台策略"
      empty-description="后端尚未返回策略，请刷新重试。"
      :empty-icon="SlidersHorizontal"
      :loading-lines="6"
      @retry="load"
    >
      <Card padding="md" class="min-w-0">
        <form class="space-y-6" novalidate @submit.prevent="requestSave">
          <Alert v-if="formError" variant="danger" title="策略未保存" :description="formError" />

          <section>
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div class="min-w-0">
                <p class="text-sm font-medium text-text">开放注册</p>
                <p class="mt-0.5 text-xs text-text-muted">
                  关闭后新用户无法注册；已登录用户不受影响。运维访问码仍可绕过。
                </p>
              </div>
              <div class="flex items-center gap-2">
                <Badge :variant="form.registrationOpen ? 'success' : 'warning'" dot>
                  {{ form.registrationOpen ? '开放' : '关闭' }}
                </Badge>
                <button
                  type="button"
                  role="switch"
                  :aria-checked="form.registrationOpen"
                  aria-label="开放注册"
                  :class="[
                    'relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors',
                    form.registrationOpen
                      ? 'bg-brand'
                      : 'bg-surface-2 ring-1 ring-inset ring-border',
                  ]"
                  @click="form.registrationOpen = !form.registrationOpen"
                >
                  <span
                    :class="[
                      'inline-block h-4 w-4 transform rounded-full bg-surface shadow-sm transition-transform',
                      form.registrationOpen ? 'translate-x-6' : 'translate-x-1',
                    ]"
                    aria-hidden="true"
                  />
                </button>
              </div>
            </div>
          </section>

          <section class="border-t border-border pt-5">
            <h3 class="text-sm font-medium text-text">每日次数</h3>
            <p class="mt-0.5 text-xs text-text-muted">
              单用户每日最多创建模拟面试与解析简历的次数；0 表示不限制。
            </p>
            <div class="mt-3 grid gap-4 sm:grid-cols-2">
              <Input
                v-model="form.dailyInterviewLimit"
                label="每日模拟面试上限"
                type="number"
                hint="默认 3 次"
              />
              <Input
                v-model="form.dailyResumeLimit"
                label="每日简历解析上限"
                type="number"
                hint="默认 3 次"
              />
            </div>
          </section>

          <section class="border-t border-border pt-5">
            <h3 class="text-sm font-medium text-text">月度免费额度</h3>
            <p class="mt-0.5 text-xs text-text-muted">
              免费用户每月 1 日续发；到期套餐会降级为 FREE 并按此处额度续发。
            </p>
            <div class="mt-3 grid gap-4 sm:grid-cols-2">
              <Input v-model="form.freeMonthlyInterviews" label="每月免费面试次数" type="number" />
              <Input v-model="form.freeMonthlyResumes" label="每月免费解析次数" type="number" />
            </div>
          </section>

          <section class="border-t border-border pt-5">
            <h3 class="flex items-center gap-1.5 text-sm font-medium text-text">
              <ShieldCheck class="h-4 w-4 text-brand" aria-hidden="true" />
              邀请奖励
            </h3>
            <p class="mt-0.5 text-xs text-text-muted">
              好友注册成功后发放；同一设备注册不计入奖励，超出上限仍记录邀请关系但不发额度。
            </p>
            <div class="mt-3 grid gap-4 sm:grid-cols-2">
              <Input
                v-model="form.inviteRewardInviterInterview"
                label="邀请人奖励（面试次数）"
                type="number"
              />
              <Input
                v-model="form.inviteRewardInviteeResume"
                label="被邀请人奖励（解析次数）"
                type="number"
              />
              <Input v-model="form.inviteDailyCap" label="每人每日邀请上限" type="number" />
              <Input v-model="form.inviteTotalCap" label="每人累计邀请上限" type="number" />
            </div>
          </section>

          <section class="border-t border-border pt-5">
            <h3 class="text-sm font-medium text-text">注册风控</h3>
            <p class="mt-0.5 text-xs text-text-muted">
              限制单 IP / 单设备每日成功注册数，配合算术验证码与每分钟限流防止脚本批量注册。
            </p>
            <div class="mt-3 grid gap-4 sm:grid-cols-2">
              <Input
                v-model="form.registrationIpDailyCap"
                label="单 IP 每日注册上限"
                type="number"
              />
              <Input
                v-model="form.registrationDeviceDailyCap"
                label="单设备每日注册上限"
                type="number"
              />
            </div>
          </section>

          <div class="flex flex-wrap items-center gap-2 border-t border-border pt-5">
            <Button type="submit" :loading="saving">
              <Save v-if="!saving" class="h-4 w-4" aria-hidden="true" />
              保存策略
            </Button>
            <p v-if="updatedText" class="text-xs text-text-muted">最近更新：{{ updatedText }}</p>
          </div>
        </form>
      </Card>
    </AsyncState>

    <ConfirmDialog
      v-model:open="confirmOpen"
      title="确认保存平台策略"
      description="请核对将要写入的数值；保存后约 30 秒内对注册与额度生效，修改会写入审计日志。"
      size="md"
      alert-variant="warning"
      alert-title="影响全站注册与额度"
      alert-description="数值错误会立即影响新用户注册与所有用户的额度发放，取消不会提交任何修改。"
      confirm-label="确认保存"
      :loading="saving"
      @confirm="save"
    >
      <dl class="grid gap-x-4 gap-y-2 text-sm sm:grid-cols-2">
        <div
          v-for="item in summary"
          :key="item.label"
          class="flex items-baseline justify-between gap-3 border-b border-border pb-1.5"
        >
          <dt class="text-text-muted">{{ item.label }}</dt>
          <dd class="tabular shrink-0 font-medium text-text">{{ item.value }}</dd>
        </div>
      </dl>
    </ConfirmDialog>
  </div>
</template>
