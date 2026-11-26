<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { User } from 'lucide-vue-next'
import PageHeader from '@/components/common/PageHeader.vue'
import Alert from '@/components/ui/Alert.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import Dialog from '@/components/ui/Dialog.vue'
import Input from '@/components/ui/Input.vue'
import AccountSettingsCard from '@/components/settings/AccountSettingsCard.vue'
import AppearanceSettingsCard from '@/components/settings/AppearanceSettingsCard.vue'
import InviteSettingsCard from '@/components/settings/InviteSettingsCard.vue'
import PrivacySettingsCard from '@/components/settings/PrivacySettingsCard.vue'
import SpeechSettingsCard from '@/components/settings/SpeechSettingsCard.vue'
import { errorMessage } from '@/api/client'
import { deleteMyAccount, exportMyData, getInviteProfile, type InviteProfile } from '@/api/users'
import { useToast } from '@/composables/useToast'
import { useAppStore } from '@/stores/app'
import { formatDate } from '@/utils/format'

const store = useAppStore()
const router = useRouter()
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

const planName = computed(() => store.quota.planName)
const isPro = computed(() => store.quota.planCode === 'PRO')
const registeredAt = computed(() =>
  store.user?.createdAt ? formatDate(store.user.createdAt) : '—',
)

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

const logoutOpen = ref(false)
const invite = ref<InviteProfile | null>(null)
const inviteLoading = ref(false)
const inviteError = ref('')
const inviteCopied = ref(false)

const deleteOpen = ref(false)
const deletePassword = ref('')
const deleteError = ref('')
const deleting = ref(false)
const exporting = ref(false)

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

async function exportData(): Promise<void> {
  if (exporting.value) return
  exporting.value = true
  try {
    const blob = await exportMyData()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `offercoach-export-${store.user?.id ?? 'me'}.json`
    link.click()
    URL.revokeObjectURL(url)
    toast('数据已开始下载', 'success', '导出文件包含资料、简历、JD、会话、回答与报告。')
  } catch (cause) {
    toast('导出失败', 'error', errorMessage(cause, '请稍后重试'))
  } finally {
    exporting.value = false
  }
}

function openDeleteAccount(): void {
  deletePassword.value = ''
  deleteError.value = ''
  deleteOpen.value = true
}

async function confirmDeleteAccount(): Promise<void> {
  if (!deletePassword.value) {
    deleteError.value = '请输入当前密码'
    return
  }
  deleting.value = true
  deleteError.value = ''
  try {
    await deleteMyAccount(deletePassword.value)
    deleteOpen.value = false
    toast('账号已注销', 'success', '联系邮箱与昵称已清空，全部登录态已吊销。')
    store.clearSession()
    await router.push('/login')
  } catch (cause) {
    deleteError.value = errorMessage(cause, '注销失败，请稍后重试')
  } finally {
    deleting.value = false
  }
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
              <Button type="submit" :loading="saving" :disabled="!isDirty || !!nicknameError"
                >保存资料</Button
              >
            </div>
          </form>
        </Card>

        <AppearanceSettingsCard />
        <SpeechSettingsCard />
      </div>

      <div class="space-y-5">
        <PrivacySettingsCard v-model="allowResumeText" />
        <AccountSettingsCard
          :account-id="store.user?.id ?? ''"
          :email="email"
          :registered-at="registeredAt"
          :plan-name="planName"
          :is-pro="isPro"
          @logout="logoutOpen = true"
          @export-data="exportData"
          @delete-account="openDeleteAccount"
        />
        <InviteSettingsCard
          :invite="invite"
          :error="inviteError"
          :link="inviteLink"
          :copied="inviteCopied"
          @retry="loadInvite"
          @copy="copyInviteLink"
        />
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

    <Dialog
      :open="deleteOpen"
      title="注销账号？"
      description="注销后无法恢复，当前邮箱可重新注册；额度流水与审计记录按合规要求保留。"
      size="sm"
      @update:open="deleteOpen = $event"
    >
      <div class="space-y-3">
        <Alert
          variant="danger"
          title="请确认已导出需要保留的数据"
          description="注销会清空邮箱、昵称与头像，并立即吊销全部登录态。"
        />
        <div>
          <label for="delete-password" class="text-xs font-medium text-text-2">当前密码</label>
          <Input
            id="delete-password"
            v-model="deletePassword"
            type="password"
            autocomplete="current-password"
            placeholder="输入当前密码以确认"
            class="mt-1"
          />
        </div>
        <p v-if="deleteError" class="text-xs text-danger-strong">{{ deleteError }}</p>
      </div>
      <template #footer>
        <Button variant="outline" :disabled="deleting" @click="deleteOpen = false">取消</Button>
        <Button variant="danger" :loading="deleting" @click="confirmDeleteAccount">确认注销</Button>
      </template>
    </Dialog>
  </div>
</template>
