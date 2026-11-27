<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { useAppStore } from '@/stores/app'

const store = useAppStore()
const remaining = computed(() => store.quota.interviewCredits)
const low = computed(() => remaining.value <= 2)
</script>

<template>
  <RouterLink
    to="/plans"
    class="inline-flex h-8 min-w-8 items-center justify-center gap-1.5 rounded-md px-2 text-xs text-text-muted transition-colors hover:bg-surface-2 hover:text-text max-sm:min-h-11 max-sm:min-w-11 max-sm:bg-surface-2"
    :aria-label="`剩余 ${remaining} 次面试额度，查看套餐`"
  >
    <!-- 手机端只留「额度」二字：裸数字看不出含义，也容易被误读成未读角标。 -->
    <span class="text-2xs sm:text-xs">
      <span class="sm:hidden">额度</span>
      <span class="hidden sm:inline">面试额度</span>
    </span>
    <span :class="['figure text-sm', low ? 'text-warning-strong' : 'text-text']">
      {{ remaining }}
    </span>
  </RouterLink>
</template>
