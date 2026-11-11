<script setup lang="ts">
import type { Component } from 'vue'
import { Inbox } from 'lucide-vue-next'

withDefaults(
  defineProps<{
    icon?: Component
    title: string
    description?: string
  }>(),
  { icon: () => Inbox },
)
</script>

<template>
  <div
    class="flex flex-col items-center justify-center rounded-lg border border-dashed border-border bg-surface px-6 py-12 text-center"
  >
    <component :is="icon" class="h-5 w-5 text-muted" aria-hidden="true" />
    <h3 class="mt-4 text-sm font-medium text-text">{{ title }}</h3>
    <p v-if="description" class="mt-1.5 max-w-sm text-sm leading-6 text-text-2">
      {{ description }}
    </p>
    <div v-if="$slots.action" class="mt-5">
      <slot name="action" />
    </div>
  </div>
</template>
