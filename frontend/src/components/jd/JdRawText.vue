<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChevronDown, ChevronUp } from 'lucide-vue-next'

const props = defineProps<{ text: string }>()

const expanded = ref(false)
const canCollapse = computed(() => props.text.length > 240)
</script>

<template>
  <div>
    <p
      :class="[
        'whitespace-pre-line break-words text-sm leading-6 text-text-muted',
        !expanded && canCollapse ? 'line-clamp-4' : '',
      ]"
    >
      {{ text }}
    </p>
    <button
      v-if="canCollapse"
      type="button"
      class="mt-2 inline-flex items-center gap-1 text-xs font-medium text-text-2 hover:text-text"
      :aria-expanded="expanded"
      @click="expanded = !expanded"
    >
      <component :is="expanded ? ChevronUp : ChevronDown" class="h-3.5 w-3.5" aria-hidden="true" />
      {{ expanded ? '收起原文' : '展开全文' }}
    </button>
  </div>
</template>
