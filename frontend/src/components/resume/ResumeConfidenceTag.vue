<script setup lang="ts">
import { computed } from 'vue'
import { CircleAlert, ShieldCheck } from 'lucide-vue-next'
import Badge from '@/components/ui/Badge.vue'

const props = defineProps<{ value: number }>()

const percent = computed(() => Math.round(props.value * 100))
const low = computed(() => props.value < 0.8)
</script>

<template>
  <Badge
    :variant="low ? 'warning' : 'neutral'"
    :title="low ? '该区块识别把握较低，建议核对简历原文' : '该区块识别把握较高'"
  >
    <component :is="low ? CircleAlert : ShieldCheck" class="h-3 w-3" aria-hidden="true" />
    置信度 {{ percent }}%
    <template v-if="low">· 建议核对</template>
  </Badge>
</template>
