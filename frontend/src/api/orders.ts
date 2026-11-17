import type { Order, PayChannel } from '@/types'
import { request } from './client'

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
