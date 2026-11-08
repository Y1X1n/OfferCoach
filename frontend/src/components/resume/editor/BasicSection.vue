<script setup lang="ts">
import { computed } from 'vue'
import type { ResumeBasic } from '@/types'
import Input from '@/components/ui/Input.vue'

defineModel<ResumeBasic>('basic', { required: true })
const yearsRaw = defineModel<string>('yearsRaw', { required: true })
defineProps<{ yearsError?: string | undefined }>()

const yearsText = computed({
  get: () => String(yearsRaw.value ?? ''),
  set: (value: string) => {
    yearsRaw.value = String(value ?? '')
  },
})
</script>

<template>
  <section class="border-t border-border pt-5">
    <h3 class="text-sm font-semibold text-text">基本信息</h3>
    <p class="mt-0.5 text-xs text-text-muted">联系方式请填写脱敏后的文本，界面只展示脱敏值。</p>
    <div class="mt-3 grid gap-3 sm:grid-cols-2">
      <Input v-model="basic.name" label="姓名" placeholder="张三" />
      <Input v-model="basic.city" label="所在城市" placeholder="上海" />
      <Input
        v-model="yearsText"
        label="工作年限"
        type="number"
        :error="yearsError"
        hint="单位：年，需不小于 0"
      />
      <Input v-model="basic.phoneMasked" label="手机（脱敏）" placeholder="138****6721" />
      <Input v-model="basic.emailMasked" label="邮箱（脱敏）" placeholder="zhang***@example.com" />
    </div>
  </section>
</template>
