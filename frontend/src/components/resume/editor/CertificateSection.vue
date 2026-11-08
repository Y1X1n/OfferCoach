<script setup lang="ts">
import Textarea from '@/components/ui/Textarea.vue'

// 证书以“每行一条”的原始文本维护，父组件 sanitize 依赖同一份文本（split('\n')）。
const certificatesText = defineModel<string>({ required: true })
</script>

<template>
  <section class="border-t border-border pt-5">
    <h3 class="text-sm font-semibold text-text">证书</h3>
    <p class="mt-0.5 text-xs text-text-muted">每行一条，例如 CET-6。</p>
    <div class="mt-3">
      <Textarea
        label="证书（每行一条）"
        :rows="3"
        :model-value="certificatesText"
        placeholder="CET-6"
        @update:model-value="certificatesText = $event"
      />
    </div>
  </section>
</template>
