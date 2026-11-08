import type { Quota } from '@/types'
import { request } from './client'

export function getQuota(): Promise<Quota> {
  return request<Quota>('/quota')
}