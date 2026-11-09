import type { DashboardData } from '@/types'
import { request } from './client'

export function getDashboard(): Promise<DashboardData> {
  return request<DashboardData>('/dashboard')
}