import { ref } from 'vue'
import type { ToastItem, ToastVariant } from '@/types'

const MAX_TOASTS = 3
const toasts = ref<ToastItem[]>([])
let nextId = 1

export function toast(title: string, variant: ToastVariant = 'success', description?: string) {
  const id = nextId++
  toasts.value.push({ id, title, variant, description })
  if (toasts.value.length > MAX_TOASTS) toasts.value.shift()
  window.setTimeout(() => dismissToast(id), variant === 'error' ? 6000 : 3600)
}

export function dismissToast(id: number) {
  toasts.value = toasts.value.filter((item) => item.id !== id)
}

export function useToast() {
  return { toasts, toast, dismissToast }
}
