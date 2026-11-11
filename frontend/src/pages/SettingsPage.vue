<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import {
  Check,
  Copy,
  Gift,
  LogOut,
  Monitor,
  Moon,
  Palette,
  ShieldCheck,
  Sun,
  User,
} from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import Alert from '@/components/ui/Alert.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import Dialog from '@/components/ui/Dialog.vue'
import Input from '@/components/ui/Input.vue'
import { errorMessage } from '@/api/client'
import { getInviteProfile, type InviteProfile } from '@/api/users'
import { useTheme } from '@/composables/useTheme'
import { useToast } from '@/composables/useToast'
import { useAppStore } from '@/stores/app'
import { formatDate } from '@/utils/format'

type ThemeChoice = 'light' | 'dark' | 'system'

interface ThemeOption {
  value: ThemeChoice
  label: string
  icon: Component
}

const store = useAppStore()
const router = useRouter()
const { preference: themeChoice, setTheme: applyThemePreference } = useTheme()
const { toast } = useToast()

const nickname = ref(store.user?.nickname ?? '')
const email = ref(store.user?.email ?? '')
const timezone = ref(store.user?.timezone ?? 'Asia/Shanghai')
const allowResumeText = ref(store.user?.allowRawResumeToLlm ?? true)
const saving = ref(false)
const saveError = ref('')

const nicknameError = computed(() => {
  const value = nickname.value.trim()
  if (value.length === 0) return '请输入昵称'
  if (value.length < 2 || value.length > 40) return '昵称需为 2–40 个字符'
  return ''
})
const nicknameHint = computed(
  () => `2–40 个字符，当前 ${nickname.value.trim().length}/40；显示在用户菜单与个人资料中。`,
)
const isDirty = computed(
  () =>
    nickname.value.trim() !== (store.user?.nickname ?? '') ||
    timezone.value !== (store.user?.timezone ?? 'Asia/Shanghai') ||
    allowResumeText.value !== (store.user?.allowRawResumeToLlm ?? true),
)

const timezoneOptions: { value: string; label: string }[] = [
  { value: 'Asia/Shanghai', label: 'Asia/Shanghai（UTC+8 北京 / 上海）' },
  { value: 'UTC', label: 'UTC（协调世界时）' },
  { value: 'Asia/Tokyo', label: 'Asia/Tokyo（UTC+9 东京）' },
]

const themeOptions: ThemeOption[] = [
  { value: 'light', label: '浅色', icon: Sun },
  { value: 'dark', label: '深色', icon: Moon },
  { value: 'system', label: '跟随系统', icon: Monitor },
]

const planName = computed(() => store.quota.planName)
const isPro = computed(() => store.quota.planCode === 'PRO')
const registeredAt = computed(() => (store.user?.createdAt ? formatDate(store.user.createdAt) : '—'))

async function saveProfile(): Promise<void> {
  if (nicknameError.value) return
  if (!store.user) {
    saveError.value = '登录状态已失效，请重新登录后再保存。'
    toast('无法保存', 'error', saveError.value)
    return
  }

  saving.value = true
  saveError.value = ''
  try {
    await store.updateProfile({
      nickname: nickname.value.trim(),
      timezone: timezone.value,
      allowRawResumeToLlm: allowResumeText.value,
    })
    nickname.value = store.user?.nickname ?? nickname.value.trim()
    toast('资料已保存', 'success', '昵称、时区与隐私偏好已更新。')
  } catch (cause) {
    saveError.value = errorMessage(cause, '保存失败，请稍后重试')
    toast('保存失败', 'error', '请稍后重试，你的修改仍保留在表单中。')
  } finally {
    saving.value = false
  }
}

function chooseTheme(value: ThemeChoice): void {
  applyThemePreference(value)
}

const logoutOpen = ref(false)
const invite = ref<InviteProfile | null>(null)
const inviteLoading = ref(false)
const inviteError = ref('')
const inviteCopied = ref(false)

const inviteLink = computed(() => {
  if (!invite.value) return ''
  const origin = typeof window === 'undefined' ? '' : window.location.origin
  return `${origin}/register?invite=${encodeURIComponent(invite.value.inviteCode)}`
})

async function loadInvite(): Promise<void> {
  if (inviteLoading.value) return
  inviteLoading.value = true
  inviteError.value = ''
  try {
    invite.value = await getInviteProfile()
  } catch (cause) {
    inviteError.value = errorMessage(cause, '邀请信息加载失败')
  } finally {
    inviteLoading.value = false
  }
}

async function copyInviteLink(): Promise<void> {
  if (!inviteLink.value) return
  try {
    await navigator.clipboard.writeText(inviteLink.value)
    inviteCopied.value = true
    toast('邀请链接已复制', 'success', '发给好友注册即可获得额外额度。')
    window.setTimeout(() => {
      inviteCopied.value = false
    }, 2000)
  } catch {
    toast('复制失败', 'error', '请手动选择并复制邀请码。')
  }
}

async function confirmLogout(): Promise<void> {
  logoutOpen.value = false
  const ok = await store.logout()
  if (!ok) {
    toast('退出失败', 'error', '服务端未完成吊销，请检查网络后重试。')
    return
  }
  toast('已退出登录', 'info')
  await router.push('/login')
}

onMounted(async () => {
  if (!store.user) await store.loadMe()
  nickname.value = store.user?.nickname ?? ''
  email.value = store.user?.email ?? ''
  timezone.value = store.user?.timezone ?? 'Asia/Shanghai'
  allowResumeText.value = store.user?.allowRawResumeToLlm ?? true
  await loadInvite()
})
</script>

<template>
  <div>
    <PageHeader
      title="设置"
      description="管理个人资料、外观与隐私偏好；账号与偏好均保存在服务端。"
    />

    <div class="mt-6 grid gap-5 lg:grid-cols-2 lg:items-start">
      <div class="space-y-5">
        <Card>
          <div class="flex items-center gap-2">
            <span
              class="flex h-8 w-8 items-center justify-center rounded-md bg-surface-2 text-text-muted"
            >
              <User class="h-4 w-4" aria-hidden="true" />
            </span>
            <div>
              <h2 class="text-sm font-semibold text-text">个人资料</h2>
              <p class="text-xs text-text-muted">昵称会显示在用户菜单与个人资料中</p>
            </div>
          </div>

          <form class="mt-4 space-y-4" novalidate @submit.prevent="saveProfile">
            <Input
              v-model="nickname"
              label="昵称"
              autocomplete="nickname"
              required
              :error="nicknameError || undefined"
              :hint="nicknameHint"
            />

            <Input
              v-model="email"
              label="邮箱"
              type="email"
              disabled
              hint="邮箱用于登录，暂不支持修改。"
            />

            <div class="flex flex-col gap-1.5">
              <label for="settings-timezone" class="text-sm font-medium text-text">时区</label>
              <select
                id="settings-timezone"
                v-model="timezone"
                class="h-10 w-full rounded-md border border-border bg-surface px-3 text-sm text-text transition-colors focus:border-accent/60"
              >
                <option v-for="option in timezoneOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
              <p class="text-xs text-text-muted">用于报告中“相对 + 绝对”时间的展示。</p>
            </div>

            <Alert v-if="saveError" variant="danger" title="保存失败" :description="saveError" />

            <div class="flex flex-wrap items-center justify-between gap-3">
              <p class="text-xs text-text-muted">保存后立即同步到服务端。</p>
              <Button type="submit" :loading="saving" :disabled="!isDirty || !!nicknameError">
                保存资料
              </Button>
            </div>
          </form>
        </Card>

        <Card>
          <div class="flex items-center gap-2">
            <span
              class="flex h-8 w-8 items-center justify-center rounded-md bg-surface-2 text-text-muted"
            >
              <Palette class="h-4 w-4" aria-hidden="true" />
            </span>
            <div>
              <h2 class="text-sm font-semibold text-text">外观</h2>
              <p class="text-xs text-text-muted">选择界面主题，偏好保存在本机。</p>
            </div>
          </div>

          <div role="group" aria-label="外观主题" class="mt-4 grid grid-cols-3 gap-2">
            <button
              v-for="option in themeOptions"
              :key="option.value"
              type="button"
              :aria-pressed="themeChoice === option.value"
              :class="[
                'flex flex-col items-center gap-2 rounded-md border px-2 py-3 text-xs font-medium transition-colors',
                themeChoice === option.value
                  ? 'border-text/30 bg-surface-2 text-text'
                  : 'border-border bg-surface text-text-muted hover:bg-surface-2 hover:text-text',
              ]"
              @click="chooseTheme(option.value)"
            >
              <component :is="option.icon" class="h-4 w-4" aria-hidden="true" />
              {{ option.label }}
            </button>
          </div>

          <p class="mt-3 text-xs leading-5 text-text-muted">
            跟随系统会实时响应系统深浅色变化；主题偏好仅保存在本机。
          </p>
        </Card>
      </div>

      <div class="space-y-5">
        <Card>
          <div class="flex items-center gap-2">
            <span
              class="flex h-8 w-8 items-center justify-center rounded-md bg-surface-2 text-text-muted"
            >
              <ShieldCheck class="h-4 w-4" aria-hidden="true" />
            </span>
            <div>
              <h2 class="text-sm font-semibold text-text">隐私</h2>
              <p class="text-xs text-text-muted">控制简历原文是否可用于反馈引用。</p>
            </div>
          </div>

          <div class="mt-4">
            <div class="flex items-start justify-between gap-4">
              <div class="min-w-0">
                <p id="allow-resume-text-label" class="text-sm font-medium text-text">
                  允许使用简历原文
                </p>
                <p class="mt-1 text-xs leading-5 text-text-muted">
                  用于在反馈中引用你的回答原文与简历片段，关闭后仅保留结构化摘要。
                </p>
              </div>
              <button
                type="button"
                role="switch"
                :aria-checked="allowResumeText"
                aria-labelledby="allow-resume-text-label"
                :class="[
                  'relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors',
                  allowResumeText ? 'bg-brand' : 'bg-text-muted/30',
                ]"
                @click="allowResumeText = !allowResumeText"
              >
                <span class="absolute -inset-2.5" aria-hidden="true" />
                <span
                  :class="[
                    'inline-block h-5 w-5 rounded-full bg-surface shadow-sm transition-transform',
                    allowResumeText ? 'translate-x-5' : 'translate-x-0.5',
                  ]"
                  aria-hidden="true"
                />
              </button>
            </div>
            <p class="mt-3 text-xs leading-5 text-text-muted">
              修改后随“保存资料”一起提交到 PATCH /users/me；服务端未下发该字段时默认开启。
            </p>
          </div>
        </Card>

        <Card>
          <div class="flex items-center gap-2">
            <span
              class="flex h-8 w-8 items-center justify-center rounded-md bg-surface-2 text-text-muted"
            >
              <User class="h-4 w-4" aria-hidden="true" />
            </span>
            <div>
              <h2 class="text-sm font-semibold text-text">账号</h2>
              <p class="text-xs text-text-muted">账号信息与登录状态。</p>
            </div>
          </div>

          <dl class="mt-4 space-y-3 text-sm">
            <div class="flex items-center justify-between gap-3">
              <dt class="text-text-muted">账号 ID</dt>
              <dd class="truncate font-mono text-xs text-text">{{ store.user?.id ?? '—' }}</dd>
            </div>
            <div class="flex items-center justify-between gap-3">
              <dt class="text-text-muted">邮箱</dt>
              <dd class="truncate text-text">{{ email || '—' }}</dd>
            </div>
            <div class="flex items-center justify-between gap-3">
              <dt class="text-text-muted">注册时间</dt>
              <dd class="tabular text-text">{{ registeredAt }}</dd>
            </div>
            <div class="flex items-center justify-between gap-3">
              <dt class="text-text-muted">套餐</dt>
              <dd class="flex items-center gap-2">
                <Badge :variant="isPro ? 'brand' : 'neutral'" dot>{{ planName }}</Badge>
                <RouterLink to="/plans" class="text-xs font-medium text-text-2 hover:text-text">
                  查看套餐
                </RouterLink>
              </dd>
            </div>
          </dl>

          <Button variant="outline" block class="mt-4" @click="logoutOpen = true">
            <LogOut class="h-4 w-4" aria-hidden="true" />
            退出登录
          </Button>
        </Card>

        <Card>
          <div class="flex items-center gap-2">
            <span
              class="flex h-8 w-8 items-center justify-center rounded-md bg-surface-2 text-text-2"
            >
              <Gift class="h-4 w-4" aria-hidden="true" />
            </span>
            <div>
              <h2 class="text-sm font-semibold text-text">邀请好友</h2>
              <p class="text-xs text-text-muted">
                好友注册成功后，你获得额外模拟面试次数，好友获得简历解析次数。
              </p>
            </div>
          </div>

          <div v-if="inviteError" class="mt-4">
            <Alert variant="danger" title="加载失败" :description="inviteError" />
            <Button variant="outline" size="sm" class="mt-3" @click="loadInvite">
              重试
            </Button>
          </div>
          <div v-else-if="invite" class="mt-4 space-y-4">
            <div class="rounded-lg border border-border bg-surface-2 px-4 py-3">
              <p class="text-xs text-text-muted">我的邀请码</p>
              <p class="mt-1 font-mono text-2xl font-semibold tracking-[0.2em] text-text">
                {{ invite.inviteCode }}
              </p>
              <p class="mt-2 text-xs leading-5 text-text-muted">
                每邀请 1 人：你 +{{ invite.rewardInviterInterview }} 次面试，好友 +{{
                  invite.rewardInviteeResume
                }}
                次简历解析；每日最多 {{ invite.dailyCap }} 人、累计 {{ invite.totalCap }} 人。
              </p>
            </div>
            <Button variant="outline" block :disabled="!inviteLink" @click="copyInviteLink">
              <Check v-if="inviteCopied" class="h-4 w-4" aria-hidden="true" />
              <Copy v-else class="h-4 w-4" aria-hidden="true" />
              {{ inviteCopied ? '已复制邀请链接' : '复制邀请链接' }}
            </Button>
            <dl class="grid grid-cols-3 gap-2 text-center">
              <div class="rounded-md border border-border px-2 py-2.5">
                <dt class="text-xs text-text-muted">已邀请</dt>
                <dd class="tabular mt-1 text-lg font-semibold text-text">
                  {{ invite.invitedCount }}
                </dd>
              </div>
              <div class="rounded-md border border-border px-2 py-2.5">
                <dt class="text-xs text-text-muted">今日剩余</dt>
                <dd class="tabular mt-1 text-lg font-semibold text-text">
                  {{ invite.remainingToday }}
                </dd>
              </div>
              <div class="rounded-md border border-border px-2 py-2.5">
                <dt class="text-xs text-text-muted">累计剩余</dt>
                <dd class="tabular mt-1 text-lg font-semibold text-text">
                  {{ invite.remainingTotal }}
                </dd>
              </div>
            </dl>
            <p class="text-xs leading-5 text-text-muted">
              同设备注册不计入奖励；邀请码在注册页“邀请码（可选）”中填写。
            </p>
          </div>
          <p v-else class="mt-4 text-xs text-text-muted">邀请信息加载中…</p>
        </Card>
      </div>
    </div>

    <Dialog
      :open="logoutOpen"
      title="退出登录？"
      description="退出后需要重新登录；服务端会吊销当前 Refresh 会话。"
      size="sm"
      @update:open="logoutOpen = $event"
    >
      <template #footer>
        <Button variant="outline" @click="logoutOpen = false">取消</Button>
        <Button variant="danger" @click="confirmLogout">退出登录</Button>
      </template>
    </Dialog>
  </div>
</template>