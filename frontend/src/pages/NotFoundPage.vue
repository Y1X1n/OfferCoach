<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { ArrowLeft, House, LogIn, Moon, Sun } from 'lucide-vue-next'
import Button from '@/components/ui/Button.vue'
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
    <header class="flex items-center justify-between gap-4 px-5 py-5 sm:px-8">
      <span class="flex items-center gap-2.5">
        <span
          class="flex h-7 w-7 items-center justify-center rounded-sm bg-text text-sm font-semibold leading-none text-surface"
          aria-hidden="true"
        >
          O
        </span>
        <span class="text-sm font-medium">OfferCoach</span>
      </span>
      <button
        type="button"
        class="inline-flex h-8 w-8 items-center justify-center rounded-md text-text-muted transition-colors hover:bg-surface-2 hover:text-text max-sm:h-11 max-sm:w-11"
        :aria-label="isDark ? '切换到浅色模式' : '切换到深色模式'"
        @click="toggleTheme"
      >
        <Sun v-if="isDark" class="h-4 w-4" />
        <Moon v-else class="h-4 w-4" />
      </button>
    </header>

    <main class="flex flex-1 items-center justify-center px-5 py-10 sm:px-8">
      <div class="w-full max-w-sm animate-fade-up">
        <p class="figure text-5xl leading-none text-text">404</p>
        <h1 class="mt-4 text-xl font-medium text-text">页面不存在</h1>
        <p class="mt-2 text-sm leading-6 text-text-2">
          链接可能已失效或地址输入有误，请从工作台重新进入
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
      </div>
    </main>
  </div>
</template>
