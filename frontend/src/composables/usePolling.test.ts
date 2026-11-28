import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, defineComponent, h } from 'vue'
import { usePolling, type PollingController } from './usePolling'

/**
 * 轮询任务通常读响应式状态（例如当前路由的报告 id）。start→stop→start 之后，上一轮已经
 * 发出去的请求仍会返回：旧实现只看 `running`，一返回 false 就把新一轮一起停掉——新报告
 * 会永远停在"生成中"。
 */
describe('usePolling', () => {
  let controller: PollingController | null = null
  let app: ReturnType<typeof createApp> | null = null

  beforeEach(() => {
    vi.useFakeTimers()
    controller = null
  })

  afterEach(() => {
    app?.unmount()
    app = null
    vi.useRealTimers()
  })

  function mountPolling(task: () => Promise<boolean>, intervalMs = 1000): PollingController {
    app = createApp(
      defineComponent({
        setup() {
          controller = usePolling(task, { intervalMs })
          return () => h('div')
        },
      }),
    )
    app.mount(document.createElement('div'))
    if (!controller) throw new Error('usePolling 未初始化')
    return controller
  }

  it('旧一轮的迟到结果不会停掉新一轮', async () => {
    const pending: Array<(value: boolean) => void> = []
    const task = vi.fn(() => new Promise<boolean>((resolve) => pending.push(resolve)))
    const poller = mountPolling(task)

    poller.start()
    await vi.advanceTimersByTimeAsync(1000)
    expect(task).toHaveBeenCalledTimes(1)
    expect(pending).toHaveLength(1)

    // 切换报告：停掉旧轮询，再为新的报告起一轮
    poller.stop()
    poller.start()

    // 旧请求此时返回 false（它读到的是切换前的状态），不能影响新一轮
    pending[0]?.(false)
    await vi.advanceTimersByTimeAsync(0)
    await vi.advanceTimersByTimeAsync(1000)

    expect(task).toHaveBeenCalledTimes(2)

    // 新一轮返回 true 时应继续排下一轮（确认 stop 没有被误触发）
    pending[1]?.(true)
    await vi.advanceTimersByTimeAsync(0)
    await vi.advanceTimersByTimeAsync(3000)
    expect(task.mock.calls.length).toBeGreaterThan(2)
  })

  it('任务返回 false 时停止轮询', async () => {
    const task = vi.fn(() => Promise.resolve(false))
    const poller = mountPolling(task)

    poller.start()
    await vi.advanceTimersByTimeAsync(1000)
    await vi.advanceTimersByTimeAsync(60000)

    expect(task).toHaveBeenCalledTimes(1)
  })
})
