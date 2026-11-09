<script setup lang="ts">
import Alert from '@/components/ui/Alert.vue'
import Button from '@/components/ui/Button.vue'
import Dialog from '@/components/ui/Dialog.vue'

type AlertVariant = 'info' | 'success' | 'warning' | 'danger'
type ConfirmVariant = 'primary' | 'danger'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    description?: string | undefined
    size?: 'sm' | 'md' | 'lg'
    alertVariant?: AlertVariant
    alertTitle?: string | undefined
    alertDescription?: string | undefined
    confirmLabel?: string
    cancelLabel?: string
    confirmVariant?: ConfirmVariant
    loading?: boolean
  }>(),
  {
    size: 'sm',
    alertVariant: 'warning',
    confirmLabel: '确认',
    cancelLabel: '取消',
    confirmVariant: 'primary',
    loading: false,
  },
)

const emit = defineEmits<{ 'update:open': [boolean]; confirm: [] }>()

function cancel(): void {
  emit('update:open', false)
}

function confirm(): void {
  emit('confirm')
}
</script>

<template>
  <Dialog
    :open="props.open"
    :title="props.title"
    :description="props.description"
    :size="props.size"
    @update:open="emit('update:open', $event)"
  >
    <div class="space-y-4">
      <Alert
        v-if="props.alertDescription"
        :variant="props.alertVariant"
        :title="props.alertTitle"
        :description="props.alertDescription"
      />
      <slot />
    </div>

    <template #footer>
      <Button variant="outline" :disabled="props.loading" @click="cancel">
        {{ props.cancelLabel }}
      </Button>
      <Button :variant="props.confirmVariant" :loading="props.loading" @click="confirm">
        {{ props.confirmLabel }}
      </Button>
    </template>
  </Dialog>
</template>
