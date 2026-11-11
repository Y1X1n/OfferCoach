<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { ChevronLeft, LogOut, Menu, Moon, Settings, Sun, X } from 'lucide-vue-next'
import {
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogOverlay,
  DialogPortal,
  DialogRoot,
  DialogTitle,
} from 'reka-ui'
import SidebarContent from '@/components/common/SidebarContent.vue'
import QuotaBadge from '@/components/common/QuotaBadge.vue'
import { adminNavItem, navItems, bottomNavItems } from '@/components/common/navigation'
import { useAppStore } from '@/stores/app'
import { useTheme } from '@/composables/useTheme'
import { useToast } from '@/composables/useToast'

const route = useRoute()
const router = useRouter()
const store = useAppStore()
const { isDark, toggle } = useTheme()
const { toast } = useToast()

const mobileOpen = ref(false)
const userMenuOpen = ref(false)
const userMenuRef = ref<HTMLElement | null>(null)

const pageTitle = computed(() => {
  const metaTitle = route.meta.title
  if (typeof metaTitle === 'string') return metaTitle
  return navItems.find((item) => item.to === route.path)?.label ?? 'OfferCoach'
})
const isWide = computed(() => route.meta.wide === true)
const isAdmin = computed(() => store.user?.role === 'ADMIN')
const initials = computed(() => (store.user?.nickname ?? 'U').slice(0, 2).toUpperCase())

function isActive(to: string, exact = false) {
  return exact ? route.path === to : route.path === to || route.path.startsWith(`${to}/`)
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/')
}

async function logout() {
  userMenuOpen.value = false
  const ok = await store.logout()
  if (!ok) {
    toast('退出失败', 'error', '服务端未完成吊销，请检查网络后重试。')
    return
  }
  toast('已退出登录', 'info')
  await router.push('/login')
}

function onDocumentClick(event: MouseEvent) {
  if (!userMenuRef.value?.contains(event.target as Node)) userMenuOpen.value = false
}

function onDocumentKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') userMenuOpen.value = false
}

onMounted(() => {
  document.addEventListener('mousedown', onDocumentClick)
  document.addEventListener('keydown', onDocumentKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onDocumentClick)
  document.removeEventListener('keydown', onDocumentKeydown)
})
watch(
  () => route.fullPath,
  () => {
    mobileOpen.value = false
    userMenuOpen.value = false
  },
)
</script>

<template>
  <div class="min-h-screen bg-bg">
    <div class="flex min-h-screen">
      <SidebarContent class="sticky top-0 hidden h-screen lg:flex print:hidden" />

      <div class="flex min-w-0 flex-1 flex-col">
        <header class="sticky top-0 z-sticky border-b border-border bg-bg print:hidden">
          <div class="flex h-14 items-center gap-1 px-3 sm:px-5 lg:px-8">
            <button
              type="button"
              class="flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-text-muted transition-colors hover:bg-surface-2 hover:text-text max-sm:h-11 max-sm:w-11"
              aria-label="返回上一页"
              title="返回"
              @click="goBack"
            >
              <ChevronLeft class="h-4 w-4" />
            </button>
            <button
              type="button"
              class="flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-text-muted transition-colors hover:bg-surface-2 hover:text-text max-sm:h-11 max-sm:w-11 lg:hidden"
              aria-label="打开导航"
              @click="mobileOpen = true"
            >
              <Menu class="h-4 w-4" />
            </button>

            <div class="min-w-0 flex-1 px-1">
              <p class="truncate text-sm text-text-2">{{ pageTitle }}</p>
            </div>

            <QuotaBadge />

            <button
              type="button"
              class="flex h-8 w-8 items-center justify-center rounded-md text-text-muted transition-colors hover:bg-surface-2 hover:text-text max-sm:h-11 max-sm:w-11"
              :aria-label="isDark ? '切换到浅色模式' : '切换到深色模式'"
              @click="toggle"
            >
              <Sun v-if="isDark" class="h-4 w-4" />
              <Moon v-else class="h-4 w-4" />
            </button>

            <div ref="userMenuRef" class="relative">
              <button
                type="button"
                class="flex h-8 items-center gap-2 rounded-md px-1.5 text-sm text-text-2 transition-colors hover:bg-surface-2 hover:text-text max-sm:h-11"
                aria-label="打开用户菜单"
                :aria-expanded="userMenuOpen"
                @click="userMenuOpen = !userMenuOpen"
              >
                <span
                  class="flex h-6 w-6 items-center justify-center rounded-full bg-surface-2 text-2xs font-medium text-text-2"
                >
                  {{ initials }}
                </span>
                <span class="hidden max-w-24 truncate sm:inline">{{ store.user?.nickname }}</span>
              </button>

              <Transition
                enter-active-class="duration-150 ease-out"
                enter-from-class="translate-y-1 opacity-0"
                enter-to-class="translate-y-0 opacity-100"
                leave-active-class="duration-100 ease-in"
                leave-from-class="opacity-100"
                leave-to-class="opacity-0"
              >
                <div
                  v-if="userMenuOpen"
                  class="absolute right-0 top-10 z-dropdown w-56 rounded-lg border border-border bg-surface p-1 shadow-pop"
                  role="menu"
                >
                  <div class="border-b border-border px-2.5 py-2.5">
                    <p class="truncate text-sm font-medium text-text">{{ store.user?.nickname }}</p>
                    <p class="truncate text-xs text-text-muted">{{ store.user?.email }}</p>
                  </div>
                  <RouterLink
                    to="/settings"
                    class="mt-1 flex h-8 items-center gap-2 rounded-md px-2 text-sm text-text-2 transition-colors hover:bg-surface-2 hover:text-text max-sm:min-h-11"
                    role="menuitem"
                  >
                    <Settings class="h-4 w-4" /> 设置
                  </RouterLink>
                  <button
                    type="button"
                    class="flex h-8 w-full items-center gap-2 rounded-md px-2 text-left text-sm text-danger transition-colors hover:bg-danger/10 max-sm:min-h-11"
                    role="menuitem"
                    @click="logout"
                  >
                    <LogOut class="h-4 w-4" /> 退出登录
                  </button>
                </div>
              </Transition>
            </div>
          </div>
        </header>

        <main
          :class="[
            'flex-1 pb-24 pt-6 sm:px-6 lg:px-8 lg:pb-10 print:px-0 print:pb-0 print:pt-0',
            isWide
              ? 'mx-auto w-full max-w-screen-2xl px-3'
              : 'mx-auto w-full max-w-6xl px-3 sm:px-0',
          ]"
        >
          <RouterView v-slot="{ Component }">
            <Transition name="page" mode="out-in">
              <component :is="Component" />
            </Transition>
          </RouterView>
        </main>

        <footer
          class="hidden border-t border-border px-8 py-4 text-center text-2xs text-muted lg:block"
        >
          OfferCoach · 面试练习与复盘
        </footer>
      </div>
    </div>

    <DialogRoot :open="mobileOpen" @update:open="mobileOpen = $event">
      <DialogPortal>
        <DialogOverlay
          class="fixed inset-0 z-drawer bg-overlay/40 data-[state=open]:animate-fade-in lg:hidden"
        />
        <DialogContent
          class="fixed inset-y-0 left-0 z-drawer w-64 outline-none data-[state=open]:animate-fade-in lg:hidden"
          aria-describedby="mobile-nav-description"
        >
          <DialogTitle class="sr-only">导航菜单</DialogTitle>
          <DialogDescription id="mobile-nav-description" class="sr-only">
            选择要前往的页面
          </DialogDescription>
          <SidebarContent class="h-full" @navigate="mobileOpen = false" />
          <DialogClose as-child>
            <button
              type="button"
              class="absolute -right-12 top-3 flex h-11 w-11 items-center justify-center rounded-md bg-surface text-text shadow-pop"
              aria-label="关闭导航"
            >
              <X class="h-4 w-4" aria-hidden="true" />
            </button>
          </DialogClose>
        </DialogContent>
      </DialogPortal>
    </DialogRoot>

    <nav
      class="fixed inset-x-0 bottom-0 z-sticky border-t border-border bg-surface lg:hidden print:hidden"
      aria-label="移动端主导航"
    >
      <div :class="['grid', isAdmin ? 'grid-cols-6' : 'grid-cols-5']">
        <RouterLink
          v-for="item in bottomNavItems"
          :key="item.to"
          :to="item.to"
          :class="[
            'flex min-h-11 flex-col items-center justify-center gap-1 py-2 text-3xs transition-colors',
            isActive(item.to, item.exact) ? 'text-text' : 'text-muted',
          ]"
        >
          <component :is="item.icon" class="h-4 w-4" aria-hidden="true" />
          <span>{{ item.label }}</span>
        </RouterLink>
        <RouterLink
          v-if="isAdmin"
          :to="adminNavItem.to"
          :class="[
            'flex min-h-11 flex-col items-center justify-center gap-1 py-2 text-3xs transition-colors',
            isActive(adminNavItem.to) ? 'text-text' : 'text-muted',
          ]"
        >
          <component :is="adminNavItem.icon" class="h-4 w-4" aria-hidden="true" />
          <span>{{ adminNavItem.label }}</span>
        </RouterLink>
      </div>
    </nav>
  </div>
</template>

<style scoped>
.page-enter-active,
.page-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}
.page-enter-from {
  opacity: 0;
  transform: translateY(4px);
}
.page-leave-to {
  opacity: 0;
  transform: translateY(-2px);
}
</style>
