import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { clearAccessToken, setAccessToken, setSessionExpiredHandler } from '@/api/client'
import { useAppStore } from './app'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  clearAccessToken()
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  setActivePinia(createPinia())
  // 会话失效默认会 window.location.assign('/login')，测试里换成空实现。
  setSessionExpiredHandler(() => {})
})

afterEach(() => {
  vi.unstubAllGlobals()
  setSessionExpiredHandler(null)
})

describe('登出', () => {
  it('Refresh 明确失效时视为已登出，清本地会话', async () => {
    setAccessToken('expired')
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ code: 'TOKEN_EXPIRED' }, 401))
      .mockResolvedValueOnce(jsonResponse({ code: 'REFRESH_INVALID' }, 401))

    await expect(useAppStore().logout()).resolves.toBe(true)
    expect(useAppStore().hasAccessToken()).toBe(false)
  })

  /**
   * 关键分支：Refresh 只是网络/5xx 失败时，`performRefresh` 不会清 Token，Cookie 与服务端
   * 家族都还活着。此时把登出的 401 当成功，等于"什么都没做却告诉用户已登出"。
   */
  it('Refresh 只是暂时失败时返回失败，不当成已登出', async () => {
    setAccessToken('expired')
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ code: 'TOKEN_EXPIRED' }, 401))
      .mockResolvedValueOnce(new Response('bad gateway', { status: 502 }))

    await expect(useAppStore().logout()).resolves.toBe(false)
    expect(useAppStore().hasAccessToken()).toBe(true)
  })

  /**
   * 403 常来自网关 / WAF（限流页、源站封禁），不代表 Refresh 家族被吊销。此时清掉内存
   * Token 是对的，但不能告诉用户"已登出"——Cookie 还在，刷新页面就回来了。
   */
  it('Refresh 返回 403 时不当成已登出', async () => {
    setAccessToken('expired')
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ code: 'TOKEN_EXPIRED' }, 401))
      .mockResolvedValueOnce(new Response('forbidden by waf', { status: 403 }))

    await expect(useAppStore().logout()).resolves.toBe(false)
  })

  it('登出成功后清空会话', async () => {
    setAccessToken('valid')
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await expect(useAppStore().logout()).resolves.toBe(true)
    expect(useAppStore().hasAccessToken()).toBe(false)
  })
})
