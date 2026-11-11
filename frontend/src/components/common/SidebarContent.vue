<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
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
  <aside class="flex h-full w-60 shrink-0 flex-col border-r border-border bg-surface max-lg:w-full">
    <RouterLink
      to="/"
      class="flex h-14 items-center gap-2.5 px-5"
      aria-label="OfferCoach 工作台"
      @click="emit('navigate')"
    >
      <span
        class="flex h-7 w-7 items-center justify-center rounded-sm bg-text text-sm font-semibold leading-none text-surface"
        aria-hidden="true"
      >
        O
      </span>
      <span class="text-sm font-medium tracking-tight text-text">OfferCoach</span>
    </RouterLink>

    <div class="flex-1 overflow-y-auto px-3 pb-4">
      <p class="px-2 pb-1.5 pt-4 text-2xs text-muted">练习</p>
      <nav class="space-y-0.5" aria-label="主导航">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          :aria-current="active(item.to, item.exact) ? 'page' : undefined"
          :class="[
            'flex h-8 items-center gap-2.5 rounded-md px-2 text-sm transition-colors duration-150 max-lg:min-h-11',
            active(item.to, item.exact)
              ? 'bg-surface-2 font-medium text-text'
              : 'text-text-2 hover:bg-surface-2 hover:text-text',
          ]"
          @click="emit('navigate')"
        >
          <component :is="item.icon" class="h-4 w-4 shrink-0" aria-hidden="true" />
          <span class="truncate">{{ item.label }}</span>
        </RouterLink>
      </nav>

      <template v-if="isAdmin">
        <p class="px-2 pb-1.5 pt-6 text-2xs text-muted">管理</p>
        <nav aria-label="管理导航">
          <RouterLink
            :to="adminNavItem.to"
            :aria-current="active(adminNavItem.to) ? 'page' : undefined"
            :class="[
              'flex h-8 items-center gap-2.5 rounded-md px-2 text-sm transition-colors duration-150 max-lg:min-h-11',
              active(adminNavItem.to)
                ? 'bg-surface-2 font-medium text-text'
                : 'text-text-2 hover:bg-surface-2 hover:text-text',
            ]"
            @click="emit('navigate')"
          >
            <component :is="adminNavItem.icon" class="h-4 w-4 shrink-0" aria-hidden="true" />
            <span class="truncate">{{ adminNavItem.label }}</span>
          </RouterLink>
        </nav>
      </template>
    </div>

    <div class="border-t border-border px-5 py-4">
      <p v-if="isPro" class="text-2xs text-text-2">
        专业版 · 已解锁建议改写
      </p>
      <RouterLink
        v-else
        to="/plans"
        class="flex items-center justify-between gap-2 text-2xs text-text-muted transition-colors hover:text-text"
        @click="emit('navigate')"
      >
        <span>升级专业版解锁建议改写</span>
        <span aria-hidden="true">→</span>
      </RouterLink>
    </div>
  </aside>
</template>
