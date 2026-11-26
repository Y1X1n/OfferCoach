import { onBeforeUnmount, ref, type Ref } from 'vue'

export interface PollingOptions {
  /** 首次间隔，默认 2000ms。 */
  intervalMs?: number
  /** 退避上限，默认 intervalMs * 5。 */
  maxIntervalMs?: number
  /** 每次继续轮询的间隔增长倍数，默认 1.5。 */
  factor?: number
  /** 超过该时长后把 slow 置为 true，默认不启用。 */
  slowAfterMs?: number
  /** 页面隐藏时暂停，恢复可见后立即补一次；默认 false。 */
  pauseWhenHidden?: boolean
}

export interface PollingController {
  start: () => void
  stop: () => void
  /** 轮询已超过 slowAfterMs；用于展示“生成较慢”提示。 */
  slow: Ref<boolean>
}

/**
 * 间隔轮询；task 返回 true 表示任务仍进行、继续轮询。
 * 支持指数退避、页面隐藏暂停与慢任务标记；组件卸载时自动停止，避免泄漏定时器。
 */
export function usePolling(
  task: () => Promise<boolean>,
  intervalOrOptions: number | PollingOptions = 2000,
): PollingController {
  const options =
    typeof intervalOrOptions === 'number' ? { intervalMs: intervalOrOptions } : intervalOrOptions
  const baseInterval = Math.max(250, options.intervalMs ?? 2000)
  const maxInterval = Math.max(baseInterval, options.maxIntervalMs ?? baseInterval * 5)
  const factor = Math.max(1, options.factor ?? 1.5)
  const pauseWhenHidden = options.pauseWhenHidden ?? false
  const slowAfterMs = options.slowAfterMs ?? 0

  const slow = ref(false)
  let timer: ReturnType<typeof setTimeout> | null = null
  let running = false
  let paused = false
  let currentInterval = baseInterval
  let startedAt = 0

  function clearTimer(): void {
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
  }

  function schedule(delay: number): void {
    if (pauseWhenHidden && typeof document !== 'undefined' && document.hidden) {
      paused = true
      return
    }
    timer = setTimeout(() => {
      void tick()
    }, delay)
  }

  async function tick(): Promise<void> {
    if (!running) return
    if (pauseWhenHidden && typeof document !== 'undefined' && document.hidden) {
      paused = true
      return
    }
    let keepGoing: boolean
    try {
      keepGoing = await task()
    } catch {
      keepGoing = false
    }
    if (!running) return
    if (!keepGoing) {
      stop()
      return
    }
    if (slowAfterMs > 0 && Date.now() - startedAt >= slowAfterMs) {
      slow.value = true
    }
    currentInterval = Math.min(maxInterval, Math.round(currentInterval * factor))
    schedule(currentInterval)
  }

  function onVisibilityChange(): void {
    if (!running) return
    if (typeof document !== 'undefined' && document.hidden) {
      clearTimer()
      paused = true
      return
    }
    if (paused) {
      paused = false
      schedule(0)
    }
  }

  function stop(): void {
    running = false
    paused = false
    clearTimer()
  }

  function start(): void {
    stop()
    running = true
    currentInterval = baseInterval
    startedAt = Date.now()
    slow.value = false
    schedule(baseInterval)
  }

  if (pauseWhenHidden && typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', onVisibilityChange)
  }

  onBeforeUnmount(() => {
    stop()
    if (pauseWhenHidden && typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  })

  return { start, stop, slow }
}
