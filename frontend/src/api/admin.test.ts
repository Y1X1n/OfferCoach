import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearAccessToken } from './client'
import * as adminApi from './admin'

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
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('AI 配置', () => {
  it('容错映射字段，仅有掩码时视为已配置', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        enabled: 'true',
        baseURL: 'https://gw.example.com/v1',
        model: 'gpt-4o',
        smallModelName: 'gpt-4o-mini',
        apiKeyMasked: 'sk-****abcd',
        updatedAt: '2026-11-01T00:00:00Z',
        updatedBy: 7,
      }),
    )

    const config = await adminApi.getAiConfig()

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/admin/ai-config')
    expect(config).toEqual({
      enabled: true,
      baseUrl: 'https://gw.example.com/v1',
      model: 'gpt-4o',
      smallModel: 'gpt-4o-mini',
      hasApiKey: true,
      apiKeyMasked: 'sk-****abcd',
      asrEnabled: true,
      ttsEnabled: true,
      ocrEnabled: true,
      searchEnabled: true,
      fetchEnabled: true,
      ttsDefaultVoice: 'stella',
      updatedAt: '2026-11-01T00:00:00Z',
      updatedBy: '7',
    })
  })

  it('读取工具能力开关与音色，非法音色回退默认', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        enabled: true,
        baseUrl: 'https://gw.example.com/v1',
        model: 'gpt-4o',
        smallModel: 'gpt-4o-mini',
        hasApiKey: true,
        apiKeyMasked: 'sk-****abcd',
        tools: {
          asrEnabled: false,
          ttsEnabled: false,
          ocrEnabled: false,
          searchEnabled: false,
          fetchEnabled: false,
          ttsDefaultVoice: 'unknown',
        },
      }),
    )

    const config = await adminApi.getAiConfig()

    expect(config.asrEnabled).toBe(false)
    expect(config.ttsEnabled).toBe(false)
    expect(config.ocrEnabled).toBe(false)
    expect(config.searchEnabled).toBe(false)
    expect(config.fetchEnabled).toBe(false)
    expect(config.ttsDefaultVoice).toBe('stella')
  })

  it('保存时仅携带显式传入的 apiKey/clearApiKey', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({}))

    await adminApi.updateAiConfig({
      enabled: false,
      baseUrl: 'https://gw.example.com/v1',
      model: 'gpt-4o',
      smallModel: 'gpt-4o-mini',
      clearApiKey: true,
    })

    const init = fetchMock.mock.calls[0]?.[1]
    expect(init?.method).toBe('PUT')
    expect(JSON.parse(String(init?.body))).toEqual({
      enabled: false,
      baseUrl: 'https://gw.example.com/v1',
      model: 'gpt-4o',
      smallModel: 'gpt-4o-mini',
      clearApiKey: true,
    })
  })

  it('映射测试连接结果', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ ok: true, model: 'gpt-4o', latencyMs: '320', message: 'pong' }),
    )

    await expect(adminApi.testAiConfig()).resolves.toEqual({
      ok: true,
      model: 'gpt-4o',
      latencyMs: 320,
      message: 'pong',
    })
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe('POST')
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/admin/ai-config/test')
  })
})

describe('用户管理操作', () => {
  it('按契约路径提交角色、解锁与重置密码', async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse({}))
      .mockResolvedValueOnce(jsonResponse({}))
      .mockResolvedValueOnce(jsonResponse({}))

    await adminApi.updateUserRole('u 1', 'SUPPORT')
    await adminApi.unlockUser('u 1')
    await adminApi.resetUserPassword('u 1', 'Passw0rd!')

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/admin/users/u%201/role')
    expect(fetchMock.mock.calls[0]?.[1]?.method).toBe('PATCH')
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toEqual({ role: 'SUPPORT' })

    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/admin/users/u%201/unlock')
    expect(fetchMock.mock.calls[1]?.[1]?.method).toBe('POST')

    expect(fetchMock.mock.calls[2]?.[0]).toBe('/api/v1/admin/users/u%201/reset-password')
    expect(fetchMock.mock.calls[2]?.[1]?.method).toBe('POST')
    expect(JSON.parse(String(fetchMock.mock.calls[2]?.[1]?.body))).toEqual({
      password: 'Passw0rd!',
    })
  })
})