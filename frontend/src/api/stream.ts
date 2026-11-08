import type { FeedbackDimensions, FeedbackStreamInfo } from '@/types'
import {
  API_BASE,
  ApiError,
  createRequestId,
  getAccessToken,
  refreshSession,
  toApiError,
} from './client'

export interface StreamMeta {
  answerId?: string | undefined
  model?: string | undefined
  promptVersion?: string | undefined
  degraded?: boolean | undefined
}

export interface StreamToken {
  seq: number
  delta: string
}

export interface StreamSection {
  name: string
}

export interface StreamResult {
  feedbackId?: string | undefined
  overallScore: number
  dimensions: FeedbackDimensions
}

export interface StreamHandlers {
  onMeta?: (data: StreamMeta) => void
  onToken?: (data: StreamToken) => void
  onSection?: (data: StreamSection) => void
  onResult?: (data: StreamResult) => void
  onError?: (error: ApiError) => void
  onPing?: () => void
  onReconnect?: (attempt: number) => void
}

export interface StreamFeedbackOptions {
  info: FeedbackStreamInfo
  handlers?: StreamHandlers
  /** 断线重连前重新申请 streamToken（后端返回 stream.url/token）。 */
  resolveStream?: (attempt: number) => Promise<FeedbackStreamInfo>
  signal?: AbortSignal | undefined
  /** 断线最多重连次数，默认 2。 */
  maxReconnects?: number
}

const IDLE_TIMEOUT_MS = 30_000

type ReadOutcome = 'settled' | 'disconnected' | 'aborted' | 'timeout'

/** 跨重连保留 token 序号，避免服务端重发导致重复文本。 */
interface SeqState {
  last: number
  pending: Map<number, string>
}

function streamUrl(info: FeedbackStreamInfo): string {
  const base = info.url.startsWith('http')
    ? info.url
    : info.url.startsWith(API_BASE)
      ? info.url
      : `${API_BASE}${info.url.startsWith('/') ? info.url : `/${info.url}`}`
  const url = new URL(base, window.location.origin)
  url.searchParams.set('answerId', info.answerId)
  url.searchParams.set('token', info.token)
  return url.toString()
}

/**
 * 评分 SSE：使用 fetch + ReadableStream（EventSource 无法携带 Authorization）。
 * 事件顺序 meta → token(seq) → section → result；ping 每 15s，30s 无事件视为断线。
 * 断线最多重连 2 次，每次重连前重新申请 streamToken，由调用方保证不重复扣费。
 */
export async function streamFeedback(options: StreamFeedbackOptions): Promise<void> {
  const maxReconnects = options.maxReconnects ?? 2
  let info = options.info
  let attempt = 0
  const seqState: SeqState = { last: 0, pending: new Map() }

  for (;;) {
    const outcome = await readOnce(info, options, seqState)
    if (outcome === 'settled' || outcome === 'aborted') return
    if (attempt >= maxReconnects) {
      options.handlers?.onError?.(
        new ApiError({
          code: 'STREAM_CLOSED',
          message: '反馈连接中断，请重试评分',
          status: 0,
          retryable: true,
        }),
      )
      return
    }
    if (!options.resolveStream) {
      options.handlers?.onError?.(
        new ApiError({
          code: 'STREAM_CLOSED',
          message: '反馈连接中断，请重试评分',
          status: 0,
          retryable: true,
        }),
      )
      return
    }
    attempt += 1
    options.handlers?.onReconnect?.(attempt)
    try {
      info = await options.resolveStream(attempt)
    } catch (error) {
      options.handlers?.onError?.(
        error instanceof ApiError
          ? error
          : new ApiError({
              code: 'STREAM_TOKEN_FAILED',
              message: '无法重新获取反馈流，请重试评分',
              status: 0,
            }),
      )
      return
    }
  }
}

async function readOnce(
  info: FeedbackStreamInfo,
  options: StreamFeedbackOptions,
  seqState: SeqState,
): Promise<ReadOutcome> {
  if (options.signal?.aborted) return 'aborted'
  const controller = new AbortController()
  let timedOut = false
  let watchdog: ReturnType<typeof setTimeout> | null = null

  const touch = () => {
    if (watchdog !== null) clearTimeout(watchdog)
    watchdog = setTimeout(() => {
      timedOut = true
      controller.abort()
    }, IDLE_TIMEOUT_MS)
  }
  const onExternalAbort = () => controller.abort()
  options.signal?.addEventListener('abort', onExternalAbort)
  touch()

  let settled = false
  const handlers = options.handlers

  const emitToken = (seq: number, delta: string) => {
    seqState.last = seq
    handlers?.onToken?.({ seq, delta })
  }
  const flushPending = () => {
    const seqs = [...seqState.pending.keys()].sort((a, b) => a - b)
    for (const seq of seqs) {
      const delta = seqState.pending.get(seq)
      if (delta !== undefined) emitToken(seq, delta)
    }
    seqState.pending.clear()
  }

  function dispatch(event: string, raw: string): void {
    if (!raw) return
    let data: unknown
    try {
      data = JSON.parse(raw)
    } catch {
      return
    }
    if (!data || typeof data !== 'object') return
    const payload = data as Record<string, unknown>
    switch (event) {
      case 'meta':
        handlers?.onMeta?.({
          answerId: typeof payload.answerId === 'string' ? payload.answerId : undefined,
          model: typeof payload.model === 'string' ? payload.model : undefined,
          promptVersion:
            typeof payload.promptVersion === 'string' ? payload.promptVersion : undefined,
          degraded: payload.degraded === true,
        })
        break
      case 'token': {
        const seq = typeof payload.seq === 'number' ? payload.seq : 0
        const delta = typeof payload.delta === 'string' ? payload.delta : ''
        if (!seq || !delta || seq <= seqState.last || seqState.pending.has(seq)) break
        if (seq === seqState.last + 1) {
          emitToken(seq, delta)
          while (seqState.pending.has(seqState.last + 1)) {
            const next = seqState.pending.get(seqState.last + 1)
            seqState.pending.delete(seqState.last + 1)
            if (next !== undefined) emitToken(seqState.last + 1, next)
          }
        } else {
          seqState.pending.set(seq, delta)
        }
        break
      }
      case 'section':
        if (typeof payload.name === 'string') handlers?.onSection?.({ name: payload.name })
        break
      case 'result': {
        flushPending()
        const dimensions = (payload.dimensions ?? {}) as Partial<FeedbackDimensions>
        handlers?.onResult?.({
          feedbackId: typeof payload.feedbackId === 'string' ? payload.feedbackId : undefined,
          overallScore: typeof payload.overallScore === 'number' ? payload.overallScore : 0,
          dimensions: {
            content: dimensions.content ?? 0,
            structure: dimensions.structure ?? 0,
            depth: dimensions.depth ?? 0,
            fit: dimensions.fit ?? 0,
            language: dimensions.language ?? 0,
          },
        })
        settled = true
        break
      }
      case 'error': {
        const code = typeof payload.code === 'string' ? payload.code : 'AI_OUTPUT_INVALID'
        const message =
          typeof payload.message === 'string' ? payload.message : '反馈生成失败，请重试评分'
        handlers?.onError?.(
          new ApiError({
            code,
            message,
            status: 502,
            retryable: payload.retryable === true,
          }),
        )
        settled = true
        break
      }
      case 'ping':
        handlers?.onPing?.()
        break
      default:
        break
    }
  }

  try {
    let res = await fetchWithAuth(streamUrl(info), controller.signal)
    if (res.status === 401) {
      const refreshed = await refreshSession()
      if (!refreshed) {
        handlers?.onError?.(await toApiError(res))
        return 'settled'
      }
      res = await fetchWithAuth(streamUrl(info), controller.signal)
    }
    if (!res.ok || !res.body) {
      handlers?.onError?.(await toApiError(res))
      return 'settled'
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let event = 'message'
    let dataLines: string[] = []

    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      touch()
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n').replace(/\r/g, '\n')
      let index = buffer.indexOf('\n')
      while (index >= 0) {
        const line = buffer.slice(0, index)
        buffer = buffer.slice(index + 1)
        index = buffer.indexOf('\n')
        if (line === '') {
          dispatch(event, dataLines.join('\n'))
          event = 'message'
          dataLines = []
          if (settled) {
            controller.abort()
            return 'settled'
          }
        } else if (line.startsWith('event:')) {
          event = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trimStart())
        }
      }
    }
    flushPending()
    if (options.signal?.aborted) return 'aborted'
    if (settled) return 'settled'
    return timedOut ? 'timeout' : 'disconnected'
  } catch {
    if (options.signal?.aborted) return 'aborted'
    return timedOut ? 'timeout' : 'disconnected'
  } finally {
    options.signal?.removeEventListener('abort', onExternalAbort)
    if (watchdog !== null) clearTimeout(watchdog)
  }
}

async function fetchWithAuth(url: string, signal: AbortSignal): Promise<Response> {
  const token = getAccessToken()
  const headers: Record<string, string> = {
    Accept: 'text/event-stream',
    'X-Request-Id': createRequestId(),
  }
  if (token) headers.Authorization = `Bearer ${token}`
  return fetch(url, { headers, credentials: 'include', signal })
}