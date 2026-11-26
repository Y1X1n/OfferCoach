<script setup lang="ts">
import { onErrorCaptured, ref } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { Copy, RefreshCw, TriangleAlert } from 'lucide-vue-next'
import ToastHost from '@/components/ui/ToastHost.vue'
import { toast } from '@/composables/useToast'

interface FatalError {
  message: string
  info: string
  at: string
}

const route = useRoute()
const fatal = ref<FatalError | null>(null)

onErrorCaptured((error, _instance, info) => {
  // eslint-disable-next-line no-console -- 顶层错误边界必须留下原始堆栈，便于用户反馈与回放
  console.error(`[errorCaptured] ${info}`, error)
  fatal.value = {
    message: error instanceof Error ? error.message : String(error),
    info,
    at: new Date().toISOString(),
  }
  return false
})

async function copyDiagnostics(): Promise<void> {
  const payload = {
    at: fatal.value?.at ?? new Date().toISOString(),
    route: route.fullPath,
    info: fatal.value?.info ?? '',
    message: fatal.value?.message ?? '',
    userAgent: navigator.userAgent,
  }
  try {
    await navigator.clipboard.writeText(JSON.stringify(payload, null, 2))
    toast('诊断信息已复制', 'success')
  } catch {
    toast('复制失败', 'error', '请手动截图当前页面并反馈')
  }
}

function reload(): void {
  window.location.reload()
}
</script>

<template>
  <div v-if="fatal" class="flex min-h-screen items-center justify-center bg-bg px-4">
    <section
      class="w-full max-w-md rounded-xl border border-border bg-surface p-6 shadow-pop"
      role="alert"
      aria-live="assertive"
    >
      <span
        class="flex h-10 w-10 items-center justify-center rounded-full bg-danger/10 text-danger"
      >
        <TriangleAlert class="h-5 w-5" aria-hidden="true" />
      </span>
      <h1 class="mt-4 text-base font-semibold text-text">页面遇到未处理的异常</h1>
      <p class="mt-2 text-sm text-text-muted">
        当前页面状态已无法继续渲染。可以先刷新恢复；如果反复出现，请复制诊断信息后反馈。
      </p>
      <pre
        class="mt-4 max-h-28 overflow-auto whitespace-pre-wrap rounded-md bg-surface-2 p-3 text-xs text-text-2"
        >{{ fatal.message }}</pre>
      <div class="mt-5 flex flex-wrap gap-2">
        <button
          type="button"
          class="inline-flex h-10 items-center gap-2 rounded-md bg-brand px-4 text-sm font-medium text-brand-foreground"
          @click="reload"
        >
          <RefreshCw class="h-4 w-4" aria-hidden="true" />
          刷新页面
        </button>
        <button
          type="button"
          class="inline-flex h-10 items-center gap-2 rounded-md border border-border px-4 text-sm font-medium text-text hover:bg-surface-2"
          @click="copyDiagnostics"
        >
          <Copy class="h-4 w-4" aria-hidden="true" />
          复制诊断信息
        </button>
      </div>
    </section>
    <ToastHost />
  </div>
  <template v-else>
    <RouterView />
    <ToastHost />
  </template>
</template>
