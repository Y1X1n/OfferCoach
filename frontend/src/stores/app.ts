import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { AuthResponse, Me, Quota, UserSummary } from '@/types'
import { ApiError, clearAccessToken, getAccessToken, setAccessToken } from '@/api/client'
import * as authApi from '@/api/auth'
import * as quotaApi from '@/api/quota'
import * as usersApi from '@/api/users'

const EMPTY_QUOTA: Quota = {
  planCode: 'FREE',
  planName: '免费版',
  interviewCredits: 0,
  resumeCredits: 0,
  periodEnd: '',
  daily: { date: '', sessionsUsed: 0, sessionsLimit: 0 },
  features: {},
}

function fallbackMe(summary: UserSummary): Me {
  return {
    id: summary.id,
    email: '',
    nickname: summary.nickname,
    avatarUrl: null,
    role: summary.role,
    timezone: 'Asia/Shanghai',
    planCode: summary.planCode,
    createdAt: '',
  }
}

/** 认证与当前用户态：Access Token 只在内存，Refresh 依赖 HttpOnly Cookie。 */
export const useAppStore = defineStore('app', () => {
  const user = ref<Me | null>(null)
  const quota = ref<Quota>({ ...EMPTY_QUOTA })
  const quotaLoaded = ref(false)
  const tokenPresent = ref(false)
  const restoreAttempted = ref(false)
  const booting = ref(false)

  const isAuthenticated = computed(() => tokenPresent.value && user.value !== null)
  const isPro = computed(() => quota.value.planCode === 'PRO')

  function applySession(res: AuthResponse): void {
    setAccessToken(res.accessToken)
    tokenPresent.value = true
  }

  function clearSession(): void {
    clearAccessToken()
    tokenPresent.value = false
    user.value = null
    quota.value = { ...EMPTY_QUOTA }
    quotaLoaded.value = false
    restoreAttempted.value = true
    clearDrafts()
  }

  function clearDrafts(): void {
    try {
      for (let index = localStorage.length - 1; index >= 0; index -= 1) {
        const key = localStorage.key(index)
        if (key?.startsWith('oc_draft_')) localStorage.removeItem(key)
      }
    } catch {
      // 本地存储不可用时忽略
    }
  }

  async function loadMe(): Promise<Me | null> {
    try {
      const me = await usersApi.getMe()
      user.value = me
      return me
    } catch {
      return null
    }
  }

  async function loadQuota(): Promise<void> {
    try {
      quota.value = await quotaApi.getQuota()
      quotaLoaded.value = true
    } catch {
      // 额度加载失败不阻断页面；需要额度的页面会自行展示错误
    }
  }

  function setQuota(value: Quota): void {
    quota.value = value
    quotaLoaded.value = true
  }

  async function login(account: string, password: string): Promise<Me> {
    const res = await authApi.login({ account: account.trim(), password })
    applySession(res)
    const me = (await loadMe()) ?? fallbackMe(res.user)
    user.value = me
    await loadQuota()
    return me
  }

  async function register(input: {
    email: string
    password: string
    nickname: string
    verifyCode?: string | undefined
    inviteCode?: string | undefined
    captchaId?: string | undefined
    captchaAnswer?: string | undefined
  }): Promise<Me> {
    const res = await authApi.register({
      email: input.email.trim(),
      password: input.password,
      nickname: input.nickname.trim(),
      ...(input.verifyCode?.trim() ? { verifyCode: input.verifyCode.trim() } : {}),
      ...(input.inviteCode?.trim() ? { inviteCode: input.inviteCode.trim() } : {}),
      ...(input.captchaId ? { captchaId: input.captchaId } : {}),
      ...(input.captchaAnswer?.trim() ? { captchaAnswer: input.captchaAnswer.trim() } : {}),
    })
    applySession(res)
    const me = (await loadMe()) ?? fallbackMe(res.user)
    user.value = me
    await loadQuota()
    return me
  }

  /** 用 Refresh Cookie 静默恢复会话；失败即视为未登录，不跳转。 */
  async function refresh(): Promise<boolean> {
    try {
      const res = await authApi.refresh()
      applySession(res)
      const me = (await loadMe()) ?? fallbackMe(res.user)
      user.value = me
      await loadQuota()
      return true
    } catch {
      clearSession()
      return false
    }
  }

  /** 首次进入路由前执行一次；之后由 isAuthenticated 判定。 */
  async function ensureSession(): Promise<void> {
    if (restoreAttempted.value) return
    restoreAttempted.value = true
    booting.value = true
    try {
      await refresh()
    } finally {
      booting.value = false
    }
  }

  async function logout(): Promise<boolean> {
    try {
      await authApi.logout()
    } catch (cause) {
      if (cause instanceof ApiError && (cause.status === 401 || cause.status === 403)) {
        // Access/Refresh 均已失效，本地清理即可，避免卡在无法登出的状态。
        clearSession()
        return true
      }
      // 服务端吊销失败时保留本地会话，避免“看似登出、刷新后自动重登”。
      return false
    }
    clearSession()
    return true
  }

  async function updateProfile(input: {
    nickname?: string | undefined
    timezone?: string | undefined
    allowRawResumeToLlm?: boolean | undefined
  }): Promise<void> {
    const me = await usersApi.updateMe(input)
    user.value = {
      ...me,
      allowRawResumeToLlm: me.allowRawResumeToLlm ?? input.allowRawResumeToLlm,
    }
  }

  function hasAccessToken(): boolean {
    return getAccessToken() !== null
  }

  return {
    user,
    quota,
    quotaLoaded,
    booting,
    isAuthenticated,
    isPro,
    restoreAttempted,
    login,
    register,
    refresh,
    ensureSession,
    loadMe,
    loadQuota,
    setQuota,
    logout,
    clearSession,
    updateProfile,
    hasAccessToken,
  }
})
