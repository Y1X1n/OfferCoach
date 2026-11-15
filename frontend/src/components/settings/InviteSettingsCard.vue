<script setup lang="ts">
import { Check, Copy, Gift } from 'lucide-vue-next'
import Alert from '@/components/ui/Alert.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import type { InviteProfile } from '@/api/users'

defineProps<{
  invite: InviteProfile | null
  error: string
  link: string
  copied: boolean
}>()

const emit = defineEmits<{
  retry: []
  copy: []
}>()
</script>

<template>
  <Card>
    <div class="flex items-center gap-2">
      <span class="flex h-8 w-8 items-center justify-center rounded-md bg-surface-2 text-text-2">
        <Gift class="h-4 w-4" aria-hidden="true" />
      </span>
      <div>
        <h2 class="text-sm font-semibold text-text">邀请好友</h2>
        <p class="text-xs text-text-muted">好友注册成功后，你获得额外模拟面试次数，好友获得简历解析次数。</p>
      </div>
    </div>

    <div v-if="error" class="mt-4">
      <Alert variant="danger" title="加载失败" :description="error" />
      <Button variant="outline" size="sm" class="mt-3" @click="emit('retry')">重试</Button>
    </div>
    <div v-else-if="invite" class="mt-4 space-y-4">
      <div class="rounded-lg border border-border bg-surface-2 px-4 py-3">
        <p class="text-xs text-text-muted">我的邀请码</p>
        <p class="mt-1 font-mono text-2xl font-semibold tracking-[0.2em] text-text">
          {{ invite.inviteCode }}
        </p>
        <p class="mt-2 text-xs leading-5 text-text-muted">
          每邀请 1 人：你 +{{ invite.rewardInviterInterview }} 次面试，好友 +{{
            invite.rewardInviteeResume
          }}
          次简历解析；每日最多 {{ invite.dailyCap }} 人、累计 {{ invite.totalCap }} 人。
        </p>
      </div>
      <Button variant="outline" block :disabled="!link" @click="emit('copy')">
        <Check v-if="copied" class="h-4 w-4" aria-hidden="true" />
        <Copy v-else class="h-4 w-4" aria-hidden="true" />
        {{ copied ? '已复制邀请链接' : '复制邀请链接' }}
      </Button>
      <dl class="grid grid-cols-3 gap-2 text-center">
        <div class="rounded-md border border-border px-2 py-2.5">
          <dt class="text-xs text-text-muted">已邀请</dt>
          <dd class="tabular mt-1 text-lg font-semibold text-text">{{ invite.invitedCount }}</dd>
        </div>
        <div class="rounded-md border border-border px-2 py-2.5">
          <dt class="text-xs text-text-muted">今日剩余</dt>
          <dd class="tabular mt-1 text-lg font-semibold text-text">{{ invite.remainingToday }}</dd>
        </div>
        <div class="rounded-md border border-border px-2 py-2.5">
          <dt class="text-xs text-text-muted">累计剩余</dt>
          <dd class="tabular mt-1 text-lg font-semibold text-text">{{ invite.remainingTotal }}</dd>
        </div>
      </dl>
      <p class="text-xs leading-5 text-text-muted">同设备注册不计入奖励；邀请码在注册页“邀请码（可选）”中填写。</p>
    </div>
    <p v-else class="mt-4 text-xs text-text-muted">邀请信息加载中…</p>
  </Card>
</template>
