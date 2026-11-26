import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  buildUrl,
  clearAccessToken,
  errorMessage,
  request,
  refreshSession,
  setAccessToken,
} from './client'

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

describe('buildUrl', () => {
  it('拼接 base 与查询参数并跳过空值', () => {
    expect(buildUrl('/resumes', { page: 1, size: 20, keyword: undefined, empty: '' })).toBe(
      '/api/v1/resumes?page=1&size=20',
    )
  })
})

describe('request', () => {
  it('自动携带 Authorization、X-Request-Id 与 credentials', async () => {
    setAccessToken('token-1')
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: '1' }))

    const result = await request<{ id: string }>('/resumes/1')

    expect(result.id).toBe('1')
    const init = fetchMock.mock.calls[0]?.[1]
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/resumes/1')
    expect(init?.credentials).toBe('include')
    expect(init?.headers).toMatchObject({ Authorization: 'Bearer token-1' })
    expect((init?.headers as Record<string, string>)['X-Request-Id']).toBeTruthy()
  })

  it('204 返回 undefined 且不解析响应体', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))
    await expect(request<void>('/resumes/1')).resolves.toBeUndefined()
  })

  it('把后端错误体解析为 ApiError', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        {
          code: 'QUOTA_EXCEEDED',
          message: '额度不足',
          requestId: 'req_1',
          details: { balance: 0 },
        },
        402,
      ),
    )

    const error = await request('/interviews').catch((cause: unknown) => cause)
    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.code).toBe('QUOTA_EXCEEDED')
    expect(apiError.status).toBe(402)
    expect(apiError.requestId).toBe('req_1')
    expect(apiError.details).toEqual({ balance: 0 })
    expect(apiError.retryable).toBe(false)
  })

  it('网络错误转为可重试的 NETWORK_ERROR', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))

    const error = await request('/quota').catch((cause: unknown) => cause)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).code).toBe('NETWORK_ERROR')
    expect((error as ApiError).retryable).toBe(true)
  })

  it('错误体缺少 ID 时回退到响应头，且 5xx 文案带请求编号', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ code: 'INTERNAL_ERROR', message: '服务错误' }), {
        status: 500,
        headers: {
          'Content-Type': 'application/json',
          'X-Request-Id': 'req_header',
          'X-Trace-Id': 'trace_header',
        },
      }),
    )

    const error = await request('/reports/9').catch((cause: unknown) => cause)
    expect(error).toBeInstanceOf(ApiError)
    const apiError = error as ApiError
    expect(apiError.requestId).toBe('req_header')
    expect(apiError.traceId).toBe('trace_header')
    expect(errorMessage(apiError)).toBe('服务错误（请求编号 req_header）')
  })

  it('4xx 错误文案不追加请求编号', () => {
    const error = new ApiError({
      code: 'QUOTA_EXCEEDED',
      message: '额度不足',
      status: 402,
      requestId: 'req_1',
    })
    expect(errorMessage(error)).toBe('额度不足')
  })

  it('503 按业务错误码区分 AI 与支付', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ code: 'PAY_UNAVAILABLE' }), {
        status: 503,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    const payError = (await request('/orders/1').catch((cause: unknown) => cause)) as ApiError
    expect(payError.message).toBe('支付服务暂不可用，请稍后重试')

    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ code: 'AI_UNAVAILABLE' }), {
        status: 503,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    const aiError = (await request('/resumes/1/parse').catch((cause: unknown) => cause)) as ApiError
    expect(aiError.message).toBe('AI 服务暂不可用')
  })
})

describe('401 刷新', () => {
  it('刷新成功后重放原请求一次', async () => {
    setAccessToken('expired')
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ code: 'TOKEN_EXPIRED', message: '过期' }, 401))
      .mockResolvedValueOnce(
        jsonResponse({
          userId: '1',
          accessToken: 'fresh',
          expiresIn: 900,
          user: { id: '1', nickname: 'n', role: 'USER', planCode: 'FREE' },
        }),
      )
      .mockResolvedValueOnce(jsonResponse({ id: '9' }))

    const result = await request<{ id: string }>('/resumes/9')

    expect(result.id).toBe('9')
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/v1/auth/refresh')
    const replayHeaders = fetchMock.mock.calls[2]?.[1]?.headers as Record<string, string>
    expect(replayHeaders.Authorization).toBe('Bearer fresh')
  })

  it('并发刷新只发起一次请求', async () => {
    let resolveRefresh: ((value: Response) => void) | undefined
    fetchMock.mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          resolveRefresh = resolve
        }),
    )

    const first = refreshSession()
    const second = refreshSession()
    expect(fetchMock).toHaveBeenCalledTimes(1)

    resolveRefresh?.(
      jsonResponse({
        userId: '1',
        accessToken: 'shared',
        expiresIn: 900,
        user: { id: '1', nickname: 'n', role: 'USER', planCode: 'FREE' },
      }),
    )
    await expect(Promise.all([first, second])).resolves.toEqual([true, true])
  })

  it('刷新失败清空会话并返回 false', async () => {
    setAccessToken('expired')
    fetchMock.mockResolvedValueOnce(jsonResponse({ code: 'REFRESH_INVALID' }, 401))

    await expect(refreshSession()).resolves.toBe(false)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
