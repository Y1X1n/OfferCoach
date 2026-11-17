import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearAccessToken, setAccessToken } from './client'
import { createOrder, getOrder, listPayChannels } from './orders'

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
})
