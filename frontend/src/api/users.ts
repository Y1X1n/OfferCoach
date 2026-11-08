import type { Me } from '@/types'
import { request } from './client'

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