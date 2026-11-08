import type { Plan } from '@/types'
import { request } from './client'

interface PlanListResponse {
  items?: Plan[] | undefined
}

/** GET /plans 为公开接口；兼容数组与 { items } 两种返回形态。 */
export async function listPlans(): Promise<Plan[]> {
  const res = await request<Plan[] | PlanListResponse>('/plans')
  if (Array.isArray(res)) return res
  return Array.isArray(res.items) ? res.items : []
}