<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { Zap } from 'lucide-vue-next'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
const remaining = computed(() => store.quota.interviewCredits)
const low = computed(() => remaining.value <= 2)
</script>

<template>
  <RouterLink
    to="/plans"
    class="inline-flex items-center gap-2 rounded-full border border-border bg-surface px-3 py-1.5 text-xs font-medium transition-colors hover:border-brand/40 hover:bg-brand/5"
    :aria-label="`剩余 ${remaining} 次面试额度，查看套餐`"
  >
    <Zap :class="['h-3.5 w-3.5', low ? 'text-warning-strong' : 'text-brand']" aria-hidden="true" />
    <span class="text-text-muted">面试额度</span>
    <span :class="['tabular', low ? 'text-warning-strong' : 'text-text']">{{ remaining }}</span>
  </RouterLink>
</template>
