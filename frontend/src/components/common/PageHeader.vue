<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  eyebrow?: string
}>()
</script>

<template>
  <header class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
    <div class="min-w-0">
      <p v-if="eyebrow" class="mb-1 text-xs font-medium uppercase tracking-wider text-brand">
        {{ eyebrow }}
      </p>
      <h1 class="text-2xl font-semibold tracking-tight text-text sm:text-3xl">{{ title }}</h1>
      <p v-if="description" class="mt-1.5 max-w-2xl text-sm leading-6 text-text-muted">
        {{ description }}
      </p>
    </div>
    <div v-if="$slots.actions" class="flex flex-wrap items-center gap-2">
      <slot name="actions" />
    </div>
  </header>
</template>
