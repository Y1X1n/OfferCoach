import type { AuthResponse } from '@/types'

/**
 * 统一 API 客户端。
 * - Access Token 只存内存，刷新依赖 HttpOnly Refresh Cookie；
 * - 401 时并发去重地刷新一次并重放原请求；
 * - 错误统一抛 {@link ApiError}，UI 只按 code 分支。
 */
export const API_BASE: string = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

let accessToken: string | null = null
let refreshPromise: Promise<boolean> | null = null
let sessionExpiredHandler: (() => void) | null = null

/** 设备指纹本地键；旧键仅用于无感迁移，避免升级后设备维度风控重置。 */
const DEVICE_ID_KEY = 'oc_device_id_v1'
const LEGACY_DEVICE_ID_KEY = 'oc_device_id'
let memoryDeviceId: string | null = null

function generateDeviceId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `dev_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`
}

/**
 * 设备 ID：首次访问生成 UUID 并持久化到 localStorage。
 * 所有请求（含刷新）都会携带 X-Device-Id，服务端用于注册/登录风控。
 * localStorage 不可用时退化为当前会话内存值，保证同一会话稳定。
 */
export function getDeviceId(): string {
  if (typeof localStorage !== 'undefined') {
    try {
      const saved = localStorage.getItem(DEVICE_ID_KEY)
      if (saved) return saved
      const legacy = localStorage.getItem(LEGACY_DEVICE_ID_KEY)
      const value = legacy && legacy.trim() ? legacy : generateDeviceId()
      localStorage.setItem(DEVICE_ID_KEY, value)
      return value
    } catch {
      // 隐私模式或存储配额异常：退化为内存值
    }
  }
  if (!memoryDeviceId) memoryDeviceId = generateDeviceId()
  return memoryDeviceId
}

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function clearAccessToken(): void {
  accessToken = null
}

export function getAccessToken(): string | null {
  return accessToken
}

/** 刷新失败（会话彻底失效）时的回调；默认跳转登录页。 */
export function setSessionExpiredHandler(handler: (() => void) | null): void {
  sessionExpiredHandler = handler
}

export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly details: Record<string, unknown>
  readonly requestId: string | null
  readonly traceId: string | null
  readonly retryable: boolean

  constructor(init: {
    code: string
    message: string
    status: number
    details?: Record<string, unknown> | undefined
    requestId?: string | null | undefined
    traceId?: string | null | undefined
    retryable?: boolean | undefined
  }) {
    super(init.message)
    this.name = 'ApiError'
    this.code = init.code
    this.status = init.status
    this.details = init.details ?? {}
    this.requestId = init.requestId ?? null
    this.traceId = init.traceId ?? null
    this.retryable = init.retryable ?? (init.status >= 500 || init.status === 429)
  }
}

export function isApiError(value: unknown): value is ApiError {
  return value instanceof ApiError
}

/**
 * 面向用户的错误文案；服务端/网络错误追加请求编号，便于用户反馈时直接定位日志。
 */
export function errorMessage(value: unknown, fallback = '请求失败，请稍后重试'): string {
  if (!(value instanceof Error) || !value.message) return fallback
  if (value instanceof ApiError && value.requestId && (value.status === 0 || value.status >= 500)) {
    return `${value.message}（请求编号 ${value.requestId}）`
  }
  return value.message
}

const DEFAULT_MESSAGES: Record<number, string> = {
  400: '请求参数有误，请检查后重试',
  401: '登录状态已失效，请重新登录',
  402: '额度不足，请升级套餐或购买次数包',
  403: '没有权限执行该操作',
  404: '资源不存在或无权查看',
  409: '当前状态不允许该操作',
  413: '文件超过大小限制',
  415: '文件类型不支持',
  422: '内容无法处理，请稍后重试',
  429: '请求过于频繁，请稍后重试',
  500: '服务暂时不可用，请稍后重试',
  502: 'AI 服务返回异常，请重试',
  503: '服务暂不可用，请稍后重试',
  504: '服务响应超时，请重试',
}

/**
 * 按业务错误码覆盖状态码文案：503 既可能是 AI 也可能是支付服务，
 * 只按状态码兜底会把支付故障误报成「AI 服务暂不可用」。
 */
const CODE_MESSAGES: Record<string, string> = {
  AI_UNAVAILABLE: 'AI 服务暂不可用',
  AI_TIMEOUT: 'AI 服务超时，请重试',
  PAY_UNAVAILABLE: '支付服务暂不可用，请稍后重试',
}

function statusCodeToCode(status: number): string {
  switch (status) {
    case 400:
      return 'VALIDATION_FAILED'
    case 401:
      return 'UNAUTHENTICATED'
    case 402:
      return 'QUOTA_EXCEEDED'
    case 403:
      return 'FORBIDDEN'
    case 404:
      return 'RESOURCE_NOT_FOUND'
    case 409:
      return 'STATE_CONFLICT'
    case 413:
      return 'FILE_TOO_LARGE'
    case 415:
      return 'UNSUPPORTED_FILE'
    case 429:
      return 'RATE_LIMITED'
    default:
      return status >= 500 ? 'INTERNAL_ERROR' : 'REQUEST_FAILED'
  }
}

export function createRequestId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `req_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`
}

/** 解析失败响应为 ApiError；响应体不是 JSON 时仍返回可读错误。 */
export async function toApiError(res: Response): Promise<ApiError> {
  let body: Record<string, unknown> = {}
  try {
    const parsed: unknown = await res.json()
    if (parsed && typeof parsed === 'object') body = parsed as Record<string, unknown>
  } catch {
    // 非 JSON 错误体：使用状态码兜底
  }
  const code = typeof body.code === 'string' ? body.code : statusCodeToCode(res.status)
  const message =
    typeof body.message === 'string' && body.message.trim()
      ? body.message
      : (CODE_MESSAGES[code] ?? DEFAULT_MESSAGES[res.status] ?? `请求失败（${res.status}）`)
  const details =
    body.details && typeof body.details === 'object'
      ? (body.details as Record<string, unknown>)
      : {}
  const requestId = textOrNull(body.requestId) ?? textOrNull(res.headers.get('X-Request-Id'))
  const traceId = textOrNull(body.traceId) ?? textOrNull(res.headers.get('X-Trace-Id'))
  return new ApiError({ code, message, status: res.status, details, requestId, traceId })
}

function textOrNull(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value : null
}

export type QueryValue = string | number | boolean | null | undefined

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  /** JSON 请求体；FormData 直接通过 body 传入并自动跳过 Content-Type。 */
  body?: unknown
  query?: Record<string, QueryValue> | undefined
  headers?: Record<string, string> | undefined
  signal?: AbortSignal | undefined
  /** 单次请求超时，默认 JSON 30s、表单上传 120s。 */
  timeoutMs?: number | undefined
  idempotencyKey?: string | undefined
  /** 401 时是否尝试刷新（默认 true，/auth/* 除外）。 */
  refreshOnUnauthorized?: boolean | undefined
}

export function buildUrl(path: string, query?: Record<string, QueryValue> | undefined): string {
  const base = `${API_BASE}${path.startsWith('/') ? path : `/${path}`}`
  if (!query) return base
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue
    params.set(key, String(value))
  }
  const search = params.toString()
  return search ? `${base}?${search}` : base
}

function notifySessionExpired(): void {
  if (sessionExpiredHandler) {
    sessionExpiredHandler()
    return
  }
  if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
    window.location.assign('/login')
  }
}

/**
 * 刷新 Access Token（并发去重）。失败时清空内存 Token 并触发会话失效回调。
 * 返回值表示是否拿到新 Token。
 */
export function refreshSession(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = performRefresh().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

async function performRefresh(): Promise<boolean> {
  let res: Response
  try {
    res = await fetch(buildUrl('/auth/refresh'), {
      method: 'POST',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'X-Request-Id': createRequestId(),
        'X-Device-Id': getDeviceId(),
      },
      signal: AbortSignal.timeout(10_000),
    })
  } catch {
    // 网络异常或超时不等于会话失效：保留内存 Token，由调用方提示重试。
    return false
  }
  if (!res.ok) {
    if (res.status === 401 || res.status === 403) {
      clearAccessToken()
      notifySessionExpired()
    }
    return false
  }
  try {
    const data = (await res.json()) as AuthResponse
    if (typeof data.accessToken !== 'string' || !data.accessToken) {
      return false
    }
    setAccessToken(data.accessToken)
    return true
  } catch {
    return false
  }
}

/**
 * 发送请求并在 401 时刷新重放一次；刷新失败则清空会话并交由回调跳转。
 */
async function sendWithRefreshRetry(path: string, options: RequestOptions): Promise<Response> {
  const response = await send(path, options)
  if (
    response.status === 401 &&
    options.refreshOnUnauthorized !== false &&
    (!path.startsWith('/auth/') || path === '/auth/logout')
  ) {
    const refreshed = await refreshSession()
    if (!refreshed) throw await toApiError(response)
    return send(path, options)
  }
  return response
}

/**
 * 发起 API 请求并返回解析后的 JSON。
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return parseResponse<T>(await sendWithRefreshRetry(path, options))
}

/**
 * 发起 API 请求并返回二进制内容（音频 / 文件下载）；401 处理与 JSON 请求一致。
 */
export async function requestBlob(
  path: string,
  options: RequestOptions = {},
): Promise<{ blob: Blob; headers: Headers }> {
  const response = await sendWithRefreshRetry(path, {
    ...options,
    headers: { ...options.headers, Accept: '*/*' },
  })
  if (!response.ok) throw await toApiError(response)
  return { blob: await response.blob(), headers: response.headers }
}

async function send(path: string, options: RequestOptions): Promise<Response> {
  const body = options.body
  const isFormData = typeof FormData !== 'undefined' && body instanceof FormData
  const headers: Record<string, string> = {
    Accept: 'application/json',
    'X-Request-Id': createRequestId(),
    'X-Device-Id': getDeviceId(),
    ...options.headers,
  }
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`
  if (options.idempotencyKey) headers['Idempotency-Key'] = options.idempotencyKey
  let payload: BodyInit | undefined
  if (isFormData) {
    payload = body as FormData
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json; charset=utf-8'
    payload = JSON.stringify(body)
  }
  const timeoutMs = options.timeoutMs ?? (isFormData ? 120_000 : 30_000)
  const controller = new AbortController()
  const timeoutId = window.setTimeout(() => controller.abort(), timeoutMs)
  if (options.signal) {
    if (options.signal.aborted) controller.abort()
    else options.signal.addEventListener('abort', () => controller.abort(), { once: true })
  }
  try {
    return await fetch(buildUrl(path, options.query), {
      method: options.method ?? 'GET',
      credentials: 'include',
      headers,
      ...(payload === undefined ? {} : { body: payload }),
      signal: controller.signal,
    })
  } catch (error) {
    if (options.signal?.aborted) throw error
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new ApiError({
        code: 'NETWORK_ERROR',
        message: '请求超时，请检查网络后重试',
        status: 0,
        retryable: true,
      })
    }
    throw new ApiError({
      code: 'NETWORK_ERROR',
      message: '网络连接失败，请检查网络后重试',
      status: 0,
      retryable: true,
    })
  } finally {
    window.clearTimeout(timeoutId)
  }
}

async function parseResponse<T>(res: Response): Promise<T> {
  if (!res.ok) throw await toApiError(res)
  if (res.status === 204 || res.status === 205) return undefined as T
  const text = await res.text()
  if (!text.trim()) return undefined as T
  try {
    return JSON.parse(text) as T
  } catch {
    throw new ApiError({
      code: 'INTERNAL_ERROR',
      message: '服务返回了无法解析的数据',
      status: res.status,
    })
  }
}
