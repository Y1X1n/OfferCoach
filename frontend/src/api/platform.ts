import { request } from './client'

export interface PlatformToolStatus {
  asrEnabled: boolean
  ttsEnabled: boolean
  ocrEnabled: boolean
  searchEnabled: boolean
  fetchEnabled: boolean
}

export interface PlatformStatus {
  aiEnabled: boolean
  aiConfigured: boolean
  degraded: boolean
  tools: PlatformToolStatus
}

export function getPlatformStatus(): Promise<PlatformStatus> {
  return request<PlatformStatus>('/platform/status')
}
