<script setup lang="ts">
import { computed, useId } from 'vue'
import { cn } from '@/utils/cn'

const props = withDefaults(
  defineProps<{
    label?: string | undefined
    error?: string | undefined
    hint?: string | undefined
    placeholder?: string | undefined
    rows?: number
    maxlength?: number | undefined
    disabled?: boolean
    required?: boolean
    showCount?: boolean
  }>(),
  { rows: 6, showCount: false, required: false, disabled: false },
)

const model = defineModel<string>({ default: '' })
const generatedId = useId()
const textareaId = `textarea-${generatedId}`
const errorId = `${textareaId}-error`
const hintId = `${textareaId}-hint`
const count = computed(() => model.value.length)
const nearLimit = computed(() => (props.maxlength ? count.value / props.maxlength > 0.85 : false))
</script>

<template>
  <div class="flex flex-col gap-1.5">
    <div class="flex items-end justify-between gap-3">
      <label v-if="label" :for="textareaId" class="text-sm font-medium text-text">
        {{ label }}
        <span v-if="required" class="text-danger" aria-hidden="true">*</span>
      </label>
      <span
        v-if="showCount && maxlength"
        :class="['tabular text-xs', nearLimit ? 'text-warning-strong' : 'text-text-muted']"
      >
        {{ count }} / {{ maxlength }}
      </span>
    </div>
    <textarea
      :id="textareaId"
      v-model="model"
      :rows="rows"
      :placeholder="placeholder"
      :maxlength="maxlength"
      :disabled="disabled"
      :required="required"
      :aria-invalid="!!error"
      :aria-describedby="error ? errorId : hint ? hintId : undefined"
      :class="
        cn(
          'w-full resize-y rounded-md border bg-surface px-3 py-2.5 text-sm leading-6 text-text transition-colors placeholder:text-muted focus:border-accent/60 disabled:cursor-not-allowed disabled:bg-surface-2',
          error ? 'border-danger' : 'border-border',
        )
      "
    />
    <p v-if="error" :id="errorId" class="text-xs text-danger-strong">{{ error }}</p>
    <p v-else-if="hint" :id="hintId" class="text-xs text-text-muted">{{ hint }}</p>
  </div>
</template>
