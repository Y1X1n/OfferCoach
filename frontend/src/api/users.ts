import type { Me } from '@/types'
import { request, requestBlob } from './client'

export function getMe(): Promise<Me> {
  return request<Me>('/users/me')
}

export interface UpdateMePayload {
  nickname?: string | undefined
  avatarUrl?: string | undefined
  timezone?: string | undefined
  allowRawResumeToLlm?: boolean | undefined
}

export function updateMe(payload: UpdateMePayload): Promise<Me> {
  return request<Me>('/users/me', { method: 'PATCH', body: payload })
}

/** 注销当前账号；需要再次输入密码。 */
export function deleteMyAccount(password: string): Promise<void> {
  return request<void>('/users/me/delete', { method: 'POST', body: { password } })
}

/** 导出当前账号的个人数据（JSON 文件）。 */
export function exportMyData(): Promise<Blob> {
  return requestBlob('/users/me/export').then((result) => result.blob)
}

/** 个人邀请名片：邀请码、已邀请人数、累计奖励与剩余名额。 */
export interface InviteProfile {
  inviteCode: string
  invitedCount: number
  rewardedInterviewCredits: number
  rewardedResumeCredits: number
  rewardInviterInterview: number
  rewardInviteeResume: number
  dailyCap: number
  totalCap: number
  remainingToday: number
  remainingTotal: number
}

export function getInviteProfile(): Promise<InviteProfile> {
  return request<InviteProfile>('/users/me/invite')
}
