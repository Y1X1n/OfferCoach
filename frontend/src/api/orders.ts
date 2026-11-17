import type { Order, OrderStatus, PayChannel } from '@/types'
import { request } from './client'

/** 订单有效期兜底：后端未下发 expiresAt 时按 oc-pay 默认的 30 分钟计算。 */
export const ORDER_FALLBACK_TTL_MS = 30 * 60 * 1000

/** GET /pay-channels：可用支付渠道（登录后）。 */
export function listPayChannels(): Promise<PayChannel[]> {
  return request<PayChannel[]>('/pay-channels')
}

/** POST /orders 请求体；金额与商品名由服务端按套餐决定。 */
export interface CreateOrderInput {
  planCode: string
  channel: string
}

/** POST /orders；idempotencyKey 必填，重复调用返回首次订单。 */
export function createOrder(input: CreateOrderInput, idempotencyKey: string): Promise<Order> {
  return request<Order>('/orders', { method: 'POST', body: input, idempotencyKey })
}

/** GET /orders/{orderNo}：查询订单状态。 */
export function getOrder(orderNo: string): Promise<Order> {
  return request<Order>(`/orders/${encodeURIComponent(orderNo)}`)
}

/** POST /orders/{orderNo}/cancel：关闭未支付订单；已支付/已关闭订单幂等返回当前视图。 */
export function cancelOrder(orderNo: string): Promise<Order> {
  return request<Order>(`/orders/${encodeURIComponent(orderNo)}/cancel`, { method: 'POST' })
}

/**
 * 订单支付截止时间（毫秒）；只取首次下单返回的 expiresAt，轮询刷新不覆盖。
 * 后端未下发 expiresAt 时按 30 分钟兜底。
 */
export function orderDeadline(order: Pick<Order, 'expiresAt'>, now = Date.now()): number {
  const expiresAt = order.expiresAt ? Date.parse(order.expiresAt) : Number.NaN
  return Number.isFinite(expiresAt) ? expiresAt : now + ORDER_FALLBACK_TTL_MS
}

/** 是否已到订单支付截止时间。 */
export function isOrderExpired(deadlineAt: number, now = Date.now()): boolean {
  return deadlineAt > 0 && now >= deadlineAt
}

/** 仅未支付订单需要主动关单；已支付/已关闭/已退款状态调用无意义。 */
export function needsCancel(status: OrderStatus | null | undefined): boolean {
  return status === 'CREATED'
}
