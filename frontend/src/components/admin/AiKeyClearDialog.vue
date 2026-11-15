<script setup lang="ts">
import Alert from '@/components/ui/Alert.vue'
import Button from '@/components/ui/Button.vue'
import Dialog from '@/components/ui/Dialog.vue'

const open = defineModel<boolean>('open', { required: true })

defineProps<{
  error: string
  summary: string
  clearing: boolean
}>()

const emit = defineEmits<{
  confirm: []
}>()
</script>

<template>
  <Dialog
    v-model:open="open"
    title="清除 API Key"
    description="清除后所有 AI 能力将不可用，直到重新配置 Key。"
    size="sm"
  >
    <div class="space-y-3">
      <Alert v-if="error" variant="danger" title="清除未完成" :description="error" />
      <Alert variant="warning" title="确认清除" :description="summary" />
    </div>
    <template #footer>
      <Button variant="outline" @click="open = false">取消</Button>
      <Button variant="danger" :loading="clearing" @click="emit('confirm')">确认清除</Button>
    </template>
  </Dialog>
</template>
