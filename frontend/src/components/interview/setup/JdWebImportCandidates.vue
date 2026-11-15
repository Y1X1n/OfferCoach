<script setup lang="ts">
import { Loader2 } from 'lucide-vue-next'
import type { JdWebCandidate } from '@/api/jds'
import Badge from '@/components/ui/Badge.vue'

defineProps<{
  candidates: JdWebCandidate[]
  picking: string
}>()

const emit = defineEmits<{
  pick: [candidate: JdWebCandidate]
}>()
</script>

<template>
  <div v-if="candidates.length" class="space-y-2">
    <p class="text-xs font-medium text-text-muted">选择一个岗位</p>
    <ul class="max-h-64 space-y-1.5 overflow-y-auto pr-1">
      <li v-for="candidate in candidates" :key="candidate.resultId">
        <button
          type="button"
          class="w-full rounded-md border border-border px-3 py-2 text-left transition-colors hover:bg-surface-2 disabled:opacity-60"
          :disabled="Boolean(picking)"
          @click="emit('pick', candidate)"
        >
          <span class="flex items-start justify-between gap-2">
            <span class="min-w-0">
              <span class="block truncate text-xs font-medium text-text">{{ candidate.title }}</span>
              <span class="mt-0.5 block truncate text-3xs text-text-muted">
                {{ candidate.siteName || '招聘网站' }} · {{ candidate.url }}
              </span>
              <span v-if="candidate.snippet" class="mt-1 block line-clamp-2 text-3xs leading-4 text-text-muted">
                {{ candidate.snippet }}
              </span>
            </span>
            <span class="flex shrink-0 items-center gap-1">
              <Badge v-if="candidate.rerankScore >= 1.5" variant="success">相关</Badge>
              <Loader2
                v-if="picking === candidate.resultId"
                class="h-3.5 w-3.5 animate-spin text-text-muted"
                aria-hidden="true"
              />
            </span>
          </span>
        </button>
      </li>
    </ul>
  </div>
</template>
