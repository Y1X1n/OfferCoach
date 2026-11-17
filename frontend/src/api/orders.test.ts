import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearAccessToken, setAccessToken } from './client'
import {
  ORDER_FALLBACK_TTL_MS,
  cancelOrder,
  createOrder,
  getOrder,
  isOrderExpired,
  listPayChannels,
  needsCancel,
  orderDeadline,
} from './orders'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  clearAccessToken()
  setAccessToken('token-1')
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('orders api', () => {
  it('createOrder 携带 Idempotency-Key 与请求体', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ orderNo: 'OC1', status: 'CREATED' }, 201))

    await createOrder({ planCode: 'PRO', channel: 'wechat' }, 'key-1')

    const init = fetchMock.mock.calls[0]?.[1]
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/orders')
    expect(init?.method).toBe('POST')
    expect(init?.body).toBe(JSON.stringify({ planCode: 'PRO', channel: 'wechat' }))
    expect((init?.headers as Record<string, string>)['Idempotency-Key']).toBe('key-1')
  })

  it('listPayChannels 命中 /pay-channels', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse([]))

    await listPayChannels()

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/pay-channels')
  })

  it('getOrder 对订单号做 URL 编码', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ orderNo: 'OC1' }))

    await getOrder('OC 1')

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/orders/OC%201')
  })

  it('cancelOrder 向 /orders/{orderNo}/cancel 发 POST', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ orderNo: 'OC1', status: 'CLOSED' }))

    await cancelOrder('OC 1')

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/orders/OC%201/cancel')
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe('POST')
  })
})

describe('orders lifecycle helpers', () => {
  it('orderDeadline 使用订单 expiresAt', () => {
    const expiresAt = '2026-01-01T00:30:00Z'

    expect(orderDeadline({ expiresAt })).toBe(Date.parse(expiresAt))
  })

  it('orderDeadline 在缺失或非法 expiresAt 时回退 30 分钟', () => {
    const now = Date.parse('2026-01-01T00:00:00Z')

    expect(orderDeadline({ expiresAt: null }, now)).toBe(now + ORDER_FALLBACK_TTL_MS)
    expect(orderDeadline({ expiresAt: 'not-a-date' }, now)).toBe(now + ORDER_FALLBACK_TTL_MS)
  })

  it('isOrderExpired 在截止时间前不误报', () => {
    const deadlineAt = Date.parse('2026-01-01T00:30:00Z')

    expect(isOrderExpired(deadlineAt, deadlineAt - 1)).toBe(false)
    expect(isOrderExpired(deadlineAt, deadlineAt)).toBe(true)
    expect(isOrderExpired(0)).toBe(false)
  })

  it('needsCancel 只对未支付订单返回 true', () => {
    expect(needsCancel('CREATED')).toBe(true)
    expect(needsCancel('PAID')).toBe(false)
    expect(needsCancel('CLOSED')).toBe(false)
    expect(needsCancel('REFUNDED')).toBe(false)
    expect(needsCancel(null)).toBe(false)
  })
})
