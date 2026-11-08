import { onBeforeUnmount } from 'vue'

export interface PollingController {
  start: () => void
  stop: () => void
}

/**
 * 固定间隔轮询；task 返回 true 表示任务仍进行、继续轮询。
 * 组件卸载时自动停止，避免泄漏定时器。
 */
export function usePolling(task: () => Promise<boolean>, intervalMs = 2000): PollingController {
  let timer: ReturnType<typeof setTimeout> | null = null
  let running = false

  function stop(): void {
    running = false
    if (timer !== null) {
      clearTimeout(timer)
      timer = null
    }
  }

  async function tick(): Promise<void> {
    if (!running) return
    let keepGoing: boolean
    try {
      keepGoing = await task()
    } catch {
      keepGoing = false
    }
    if (!running || !keepGoing) {
      stop()
      return
    }
    timer = setTimeout(() => {
      void tick()
    }, intervalMs)
  }

  function start(): void {
    stop()
    running = true
    timer = setTimeout(() => {
      void tick()
    }, intervalMs)
  }

  onBeforeUnmount(stop)
  return { start, stop }
}