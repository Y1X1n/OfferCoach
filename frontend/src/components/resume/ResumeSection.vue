<script setup lang="ts">
import type { Component } from 'vue'
import Card from '@/components/ui/Card.vue'
import ResumeConfidenceTag from './ResumeConfidenceTag.vue'

defineProps<{
  title: string
  icon?: Component
  confidence?: number
}>()
</script>

<template>
  <Card padding="lg" class="min-w-0">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <h3 class="flex items-center gap-2 text-sm font-semibold text-text">
        <component :is="icon" v-if="icon" class="h-4 w-4 text-text-muted" aria-hidden="true" />
        {{ title }}
      </h3>
      <ResumeConfidenceTag v-if="confidence !== undefined" :value="confidence" />
    </div>
    <div class="mt-4">
      <slot />
    </div>
  </Card>
</template>
