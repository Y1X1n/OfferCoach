import type { Page } from '@/types'

interface RawPage<T> {
  items?: T[] | null | undefined
  total?: number | undefined
  page?: number | undefined
  size?: number | undefined
}

/** 兼容分页响应与裸数组，统一为 Page<T>。 */
export function toPage<T>(raw: unknown, defaultSize = 20): Page<T> {
  if (Array.isArray(raw)) {
    const items = raw as T[]
    return { items, total: items.length, page: 1, size: items.length || defaultSize }
  }
  const source = (raw ?? {}) as RawPage<T>
  const items = Array.isArray(source.items) ? source.items : []
  return {
    items,
    total: typeof source.total === 'number' ? source.total : items.length,
    page: typeof source.page === 'number' ? source.page : 1,
    size: typeof source.size === 'number' ? source.size : defaultSize,
  }
}