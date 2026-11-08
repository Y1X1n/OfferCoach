<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ArrowLeft, Compass, House, LogIn, Moon, Sun } from 'lucide-vue-next'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import { useAppStore } from '@/stores/app'
import { useTheme } from '@/composables/useTheme'

const router = useRouter()
const store = useAppStore()
const { isDark, toggle: toggleTheme } = useTheme()

const primary = computed(() =>
  store.isAuthenticated
    ? { label: '返回工作台', to: { name: 'dashboard' }, icon: House }
    : { label: '去登录', to: { name: 'login' }, icon: LogIn },
)
</script>

<template>
  <div class="relative flex min-h-screen flex-col bg-bg">
    <div class="grid-pattern pointer-events-none absolute inset-0 opacity-50" aria-hidden="true" />
    <div
      class="pointer-events-none absolute inset-x-0 top-0 h-64 bg-gradient-to-b from-brand/10 to-transparent"
      aria-hidden="true"
    />

    <header class="relative flex items-center justify-between gap-4 px-5 py-5 sm:px-8">
      <span class="flex items-center gap-2.5">
        <span
          class="flex h-9 w-9 items-center justify-center rounded-lg bg-brand text-brand-foreground"
        >
          <Compass class="h-4 w-4" aria-hidden="true" />
        </span>
        <span class="text-sm font-semibold">OfferCoach</span>
      </span>
      <button
        type="button"
        class="inline-flex h-10 w-10 items-center justify-center rounded-md border border-border bg-surface text-text-muted transition-colors hover:bg-surface-2 hover:text-text"
        :aria-label="isDark ? '切换到浅色模式' : '切换到深色模式'"
        @click="toggleTheme"
      >
        <Sun v-if="isDark" class="h-4 w-4" />
        <Moon v-else class="h-4 w-4" />
      </button>
    </header>

    <main class="relative flex flex-1 items-center justify-center px-5 py-10 sm:px-8">
      <Card padding="lg" class="w-full max-w-md animate-fade-up text-center">
        <span
          class="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl border border-border bg-surface-2 text-brand"
        >
          <Compass class="h-7 w-7" aria-hidden="true" />
        </span>
        <p class="tabular mt-6 text-5xl font-semibold tracking-tight text-text">404</p>
        <h1 class="mt-3 text-xl font-semibold text-text">这个页面不在地图上</h1>
        <p class="mt-2 text-sm leading-relaxed text-text-muted">
          链接可能已失效或地址输入有误，请从工作台重新进入。
        </p>
        <div class="mt-7 flex flex-col gap-3 sm:flex-row">
          <RouterLink v-slot="{ navigate }" :to="primary.to" custom>
            <Button size="lg" block @click="navigate">
              <component :is="primary.icon" class="h-4 w-4" aria-hidden="true" />
              {{ primary.label }}
            </Button>
          </RouterLink>
          <Button variant="secondary" size="lg" block @click="router.back()">
            <ArrowLeft class="h-4 w-4" aria-hidden="true" />
            返回上一页
          </Button>
        </div>
        <p class="mt-6 text-xs text-text-muted">错误码 404 · 页面不存在</p>
      </Card>
    </main>
  </div>
</template>
