<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { Sparkles } from 'lucide-vue-next'
import { adminNavItem, navItems } from '@/components/common/navigation'
import { useAppStore } from '@/stores/app'

const emit = defineEmits<{ navigate: [] }>()
const route = useRoute()
const store = useAppStore()

const isPro = computed(() => store.quota.planCode === 'PRO')
const isAdmin = computed(() => store.user?.role === 'ADMIN')

function active(to: string, exact = false) {
  return exact ? route.path === to : route.path === to || route.path.startsWith(`${to}/`)
}
</script>

<template>
  <aside class="flex h-full w-64 shrink-0 flex-col border-r border-border bg-surface">
    <RouterLink to="/" class="flex items-center gap-2.5 px-5 py-5" @click="emit('navigate')">
      <span
        class="flex h-9 w-9 items-center justify-center rounded-lg bg-brand text-brand-foreground shadow-sm"
      >
        <Sparkles class="h-5 w-5" aria-hidden="true" />
      </span>
      <span class="min-w-0">
        <span class="block text-sm font-semibold tracking-tight text-text">OfferCoach</span>
        <span class="block text-2xs text-text-muted">AI 模拟面试</span>
      </span>
    </RouterLink>

    <div class="flex-1 overflow-y-auto px-3 pb-4">
      <p class="px-2 pb-2 pt-2 text-3xs font-semibold uppercase tracking-wider text-text-muted">
        功能
      </p>
      <nav class="space-y-1" aria-label="主导航">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          :class="[
            'flex items-center gap-2.5 rounded-md px-2.5 py-2 text-sm transition-colors',
            active(item.to, item.exact)
              ? 'bg-brand/10 font-medium text-brand'
              : 'text-text-muted hover:bg-surface-2 hover:text-text',
          ]"
          @click="emit('navigate')"
        >
          <component :is="item.icon" class="h-4 w-4 shrink-0" aria-hidden="true" />
          <span class="truncate">{{ item.label }}</span>
        </RouterLink>
      </nav>

      <div v-if="isAdmin" class="mt-4 border-t border-border pt-3">
        <p class="px-2 pb-2 text-3xs font-semibold uppercase tracking-wider text-text-muted">
          管理员
        </p>
        <nav class="space-y-1" aria-label="管理导航">
          <RouterLink
            :to="adminNavItem.to"
            :class="[
              'flex min-h-11 items-center gap-2.5 rounded-md px-2.5 py-2 text-sm transition-colors',
              active(adminNavItem.to)
                ? 'bg-brand/10 font-medium text-brand'
                : 'text-text-muted hover:bg-surface-2 hover:text-text',
            ]"
            @click="emit('navigate')"
          >
            <component :is="adminNavItem.icon" class="h-4 w-4 shrink-0" aria-hidden="true" />
            <span class="truncate">{{ adminNavItem.label }}</span>
          </RouterLink>
        </nav>
      </div>
    </div>

    <div class="border-t border-border p-4">
      <div v-if="isPro" class="flex items-center gap-2 text-xs text-text-muted">
        <span
          class="inline-flex h-5 items-center rounded-full bg-success/10 px-2 font-medium text-success-strong"
          >PRO</span
        >
        已解锁建议改写
      </div>
      <RouterLink
        v-else
        to="/plans"
        class="flex items-center justify-between text-xs text-text-muted hover:text-brand"
        @click="emit('navigate')"
      >
        <span>升级 PRO 解锁建议改写</span>
        <span aria-hidden="true">→</span>
      </RouterLink>
    </div>
  </aside>
</template>
