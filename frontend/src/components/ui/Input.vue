<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import { Eye, EyeOff } from 'lucide-vue-next'
import { cn } from '@/utils/cn'

const props = withDefaults(
  defineProps<{
    label?: string | undefined
    error?: string | undefined
    hint?: string | undefined
    type?: 'text' | 'email' | 'password' | 'number' | undefined
    placeholder?: string | undefined
    autocomplete?: string | undefined
    disabled?: boolean | undefined
    required?: boolean | undefined
    id?: string | undefined
    inputClass?: string | undefined
  }>(),
  { type: 'text', required: false, disabled: false },
)

const model = defineModel<string>({ default: '' })
const generatedId = useId()
const inputId = computed(() => props.id ?? `input-${generatedId}`)
const errorId = computed(() => `${inputId.value}-error`)
const hintId = computed(() => `${inputId.value}-hint`)
const showPassword = ref(false)
const inputType = computed(() =>
  props.type === 'password' ? (showPassword.value ? 'text' : 'password') : props.type,
)
</script>

<template>
  <div class="flex flex-col gap-1.5">
    <label v-if="label" :for="inputId" class="text-sm font-medium text-text">
      {{ label }}
      <span v-if="required" class="text-danger" aria-hidden="true">*</span>
    </label>
    <div class="relative">
      <input
        :id="inputId"
        v-model="model"
        :type="inputType"
        :placeholder="placeholder"
        :autocomplete="autocomplete"
        :disabled="disabled"
        :required="required"
        :aria-invalid="!!error"
        :aria-describedby="error ? errorId : hint ? hintId : undefined"
        :class="
          cn(
            'h-10 w-full rounded-md border bg-surface px-3 text-sm text-text outline-none transition-colors placeholder:text-text-muted/70 focus:border-brand focus:ring-2 focus:ring-brand/20 disabled:cursor-not-allowed disabled:bg-surface-2',
            error ? 'border-danger' : 'border-border',
            props.type === 'password' && 'pr-10',
            inputClass,
          )
        "
      />
      <button
        v-if="type === 'password'"
        type="button"
        class="absolute right-1 top-1 flex h-8 w-8 items-center justify-center rounded text-text-muted hover:bg-surface-2 hover:text-text"
        :aria-label="showPassword ? '隐藏密码' : '显示密码'"
        @click="showPassword = !showPassword"
      >
        <EyeOff v-if="showPassword" class="h-4 w-4" />
        <Eye v-else class="h-4 w-4" />
      </button>
    </div>
    <p v-if="error" :id="errorId" class="text-xs text-danger-strong">{{ error }}</p>
    <p v-else-if="hint" :id="hintId" class="text-xs text-text-muted">{{ hint }}</p>
  </div>
</template>
