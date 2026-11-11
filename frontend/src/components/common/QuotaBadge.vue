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
    class="inline-flex h-8 items-center gap-2 rounded-md px-2 text-xs text-text-muted transition-colors hover:bg-surface-2 hover:text-text max-sm:min-h-11"
    :aria-label="`剩余 ${remaining} 次面试额度，查看套餐`"
  >
    <span>面试额度</span>
    <span :class="['figure text-sm', low ? 'text-warning-strong' : 'text-text']">
      {{ remaining }}
    </span>
  </RouterLink>
</template>
