import type { AuthResponse } from '@/types'
import { ApiError, request } from './client'

export interface LoginPayload {
  account: string
  password: string
}

/** 注册入参：验证码/邀请码均按后端策略可选，设备 ID 由 client 统一附加。 */
export interface RegisterPayload {
  email: string
  password: string
  nickname: string
  verifyCode?: string | undefined
  inviteCode?: string | undefined
  captchaId?: string | undefined
  captchaAnswer?: string | undefined
}

/** 注册策略：由后端运行时策略下发；验证码与邀请码按需展示。 */
export interface RegistrationPolicy {
  registrationOpen: boolean
  requireCaptcha: boolean
  requireVerifyCode: boolean
  inviteOptional: boolean
  inviteRewardInviterInterview: number
  inviteRewardInviteeResume: number
  dailyInterviewLimit: number
  dailyResumeLimit: number
}

/** 图形算术验证码：question 形如 "3 + 5 = ?"，校验时提交 id 与 answer。 */
export interface CaptchaChallenge {
  id: string
  question: string
  /** 有效期（秒）；为 0 表示后端未下发。 */
  expiresIn: number
}

function asRecord(value: unknown): Record<string, unknown> {
  if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>
  }
  return {}
}

function asBoolean(value: unknown, fallback = false): boolean {
  if (typeof value === 'boolean') return value
  if (value === 1 || value === '1' || value === 'true') return true
  if (value === 0 || value === '0' || value === 'false') return false
  return fallback
}

function asString(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

function asNumber(value: unknown, fallback = 0): number {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    if (Number.isFinite(parsed)) return parsed
  }
  return fallback
}

/** 策略缺失时默认开放，避免字段命名差异导致注册入口被误判关闭。 */
export async function getRegistrationPolicy(): Promise<RegistrationPolicy> {
  const raw = await request<unknown>('/auth/registration-policy', {
    refreshOnUnauthorized: false,
  })
  const dto = asRecord(asRecord(raw).policy ?? raw)
  return {
    registrationOpen: asBoolean(dto.registrationOpen, true),
    requireCaptcha: asBoolean(dto.requireCaptcha),
    requireVerifyCode: asBoolean(dto.requireVerifyCode),
    inviteOptional: asBoolean(dto.inviteOptional, true),
    inviteRewardInviterInterview: asNumber(dto.inviteRewardInviterInterview),
    inviteRewardInviteeResume: asNumber(dto.inviteRewardInviteeResume),
    dailyInterviewLimit: asNumber(dto.dailyInterviewLimit),
    dailyResumeLimit: asNumber(dto.dailyResumeLimit),
  }
}

export async function getCaptcha(): Promise<CaptchaChallenge> {
  const raw = await request<unknown>('/auth/captcha', {
    method: 'POST',
    refreshOnUnauthorized: false,
  })
  const dto = asRecord(asRecord(raw).captcha ?? raw)
  const id = asString(dto.captchaId)
  const question = asString(dto.question)
  if (!id || !question) {
    throw new ApiError({
      code: 'CAPTCHA_UNAVAILABLE',
      message: '验证码加载失败，请点击刷新重试',
      status: 503,
      retryable: true,
    })
  }
  return { id, question, expiresIn: asNumber(dto.expiresIn) }
}

export function login(payload: LoginPayload): Promise<AuthResponse> {
  return request<AuthResponse>('/auth/login', {
    method: 'POST',
    body: payload,
    refreshOnUnauthorized: false,
  })
}

export function register(payload: RegisterPayload): Promise<AuthResponse> {
  return request<AuthResponse>('/auth/register', {
    method: 'POST',
    body: payload,
    refreshOnUnauthorized: false,
  })
}

/** 由 Cookie 驱动的刷新；401 由调用方决定是否跳转。 */
export function refresh(): Promise<AuthResponse> {
  return request<AuthResponse>('/auth/refresh', {
    method: 'POST',
    refreshOnUnauthorized: false,
  })
}

export function logout(): Promise<void> {
  return request<void>('/auth/logout', { method: 'POST', refreshOnUnauthorized: false })
}