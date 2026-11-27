import { onBeforeUnmount, readonly, ref } from 'vue'

/**
 * 响应式断点监听：SSR 与非浏览器环境下退化为 `false`。
 * 仅在确实需要「按断点切换 DOM 结构」时使用；纯样式差异走 Tailwind 前缀。
 */
export function useMediaQuery(query: string) {
  const supported = typeof window !== 'undefined' && typeof window.matchMedia === 'function'
  const media = supported ? window.matchMedia(query) : null
  const matches = ref(media?.matches ?? false)

  function sync(event: MediaQueryListEvent): void {
    matches.value = event.matches
  }

  media?.addEventListener('change', sync)
  onBeforeUnmount(() => media?.removeEventListener('change', sync))

  return readonly(matches)
}