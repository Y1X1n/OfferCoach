<script setup lang="ts">
import { LogOut, User } from 'lucide-vue-next'
import { RouterLink } from 'vue-router'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'

defineProps<{
  accountId: string
  email: string
  registeredAt: string
  planName: string
  isPro: boolean
}>()

const emit = defineEmits<{
  logout: []
}>()
</script>

<template>
  <Card>
    <div class="flex items-center gap-2">
      <span class="flex h-8 w-8 items-center justify-center rounded-md bg-surface-2 text-text-muted">
        <User class="h-4 w-4" aria-hidden="true" />
      </span>
      <div>
        <h2 class="text-sm font-semibold text-text">账号</h2>
        <p class="text-xs text-text-muted">账号信息与登录状态。</p>
      </div>
    </div>

    <dl class="mt-4 space-y-3 text-sm">
      <div class="flex items-center justify-between gap-3">
        <dt class="text-text-muted">账号 ID</dt>
        <dd class="truncate font-mono text-xs text-text">{{ accountId || '—' }}</dd>
      </div>
      <div class="flex items-center justify-between gap-3">
        <dt class="text-text-muted">邮箱</dt>
        <dd class="truncate text-text">{{ email || '—' }}</dd>
      </div>
      <div class="flex items-center justify-between gap-3">
        <dt class="text-text-muted">注册时间</dt>
        <dd class="tabular text-text">{{ registeredAt }}</dd>
      </div>
      <div class="flex items-center justify-between gap-3">
        <dt class="text-text-muted">套餐</dt>
        <dd class="flex items-center gap-2">
          <Badge :variant="isPro ? 'brand' : 'neutral'" dot>{{ planName }}</Badge>
          <RouterLink to="/plans" class="text-xs font-medium text-text-2 hover:text-text">查看套餐</RouterLink>
        </dd>
      </div>
    </dl>

    <Button variant="outline" block class="mt-4" @click="emit('logout')">
      <LogOut class="h-4 w-4" aria-hidden="true" />
      退出登录
    </Button>
  </Card>
</template>
