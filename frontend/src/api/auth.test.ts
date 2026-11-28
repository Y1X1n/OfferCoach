import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, clearAccessToken, setAccessToken } from './client'
import { logout } from './auth'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const AUTH_BODY = {
  userId: '1',
  accessToken: 'fresh',
  expiresIn: 900,
  user: { id: '1', nickname: 'n', role: 'USER', planCode: 'FREE' },
}

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  clearAccessToken()
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('logout', () => {
  /**
   * Access Token 过期时不能把 401 当成功：Refresh Cookie 是 HttpOnly，前端删不掉，
   * 服务端没收到登出请求就不会吊销家族，用户刷新页面会被静默重新登录。
   */
  it('Access 过期时先刷新再重试，让服务端真正吊销 Refresh 家族', async () => {
    setAccessToken('expired')
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ code: 'TOKEN_EXPIRED', message: '过期' }, 401))
      .mockResolvedValueOnce(jsonResponse(AUTH_BODY))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))

    await expect(logout()).resolves.toBeUndefined()

    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/auth/logout')
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/auth/refresh')
    expect(fetchMock.mock.calls[2]?.[0]).toBe('/api/v1/auth/logout')
    const retryHeaders = fetchMock.mock.calls[2]?.[1]?.headers as Record<string, string>
    expect(retryHeaders.Authorization).toBe('Bearer fresh')
  })

  it('Refresh 也失效时仍以 401 失败，交给调用方清本地会话', async () => {
    setAccessToken('expired')
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ code: 'TOKEN_EXPIRED' }, 401))
      .mockResolvedValueOnce(jsonResponse({ code: 'REFRESH_INVALID' }, 401))

    await expect(logout()).rejects.toBeInstanceOf(ApiError)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})
