<script setup lang="ts">
import { computed } from 'vue'
import type { Component } from 'vue'
import { Inbox } from 'lucide-vue-next'
import Alert from '@/components/ui/Alert.vue'
import Button from '@/components/ui/Button.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import Skeleton from '@/components/ui/Skeleton.vue'
import Spinner from '@/components/ui/Spinner.vue'

const props = defineProps<{
  loading?: boolean | undefined
  error?: string | null | undefined
  errorTitle?: string | undefined
  empty?: boolean | undefined
  emptyTitle?: string | undefined
  emptyDescription?: string | undefined
  emptyIcon?: Component | undefined
  loadingLines?: number | undefined
}>()

const emit = defineEmits<{ retry: [] }>()

const isLoading = computed(() => props.loading === true)
const errorText = computed(() => props.error ?? '')
const errorHeading = computed(() => props.errorTitle ?? '加载失败')
const isEmpty = computed(() => props.empty === true)
const emptyHeading = computed(() => props.emptyTitle ?? '暂无内容')
const emptyDescription = computed(() => props.emptyDescription ?? '')
const emptyIcon = computed<Component>(() => props.emptyIcon ?? Inbox)
const loadingLines = computed(() => props.loadingLines ?? 4)
</script>

<template>
  <div v-if="isLoading" class="space-y-3" role="status" aria-live="polite">
    <slot name="loading">
      <div class="rounded-lg border border-border bg-surface p-5 shadow-card">
        <div class="flex items-center justify-between gap-3">
          <Skeleton class="w-40" :lines="1" />
          <Spinner size="sm" label="加载中" />
        </div>
        <Skeleton class="mt-4" :lines="loadingLines" />
      </div>
    </slot>
  </div>

  <Alert v-else-if="errorText" variant="danger" :title="errorHeading" :description="errorText">
    <div class="mt-3">
      <Button variant="outline" size="sm" @click="emit('retry')">重试</Button>
    </div>
  </Alert>

  <EmptyState
    v-else-if="isEmpty"
    :icon="emptyIcon"
    :title="emptyHeading"
    :description="emptyDescription"
  >
    <template v-if="$slots.action" #action>
      <slot name="action" />
    </template>
  </EmptyState>

  <slot v-else />
</template>