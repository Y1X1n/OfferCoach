<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  /** Optional quiet context line; sentence case, never a tracked uppercase label. */
  eyebrow?: string
}>()
</script>

<template>
  <header class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
    <div class="min-w-0">
      <p v-if="eyebrow" class="mb-1.5 text-2xs text-text-muted">{{ eyebrow }}</p>
      <h1 class="text-2xl font-medium tracking-tight text-text">{{ title }}</h1>
      <p v-if="description" class="mt-2 max-w-2xl text-sm leading-6 text-text-2">
        {{ description }}
      </p>
    </div>
    <div v-if="$slots.actions" class="flex flex-wrap items-center gap-2">
      <slot name="actions" />
    </div>
  </header>
</template>
