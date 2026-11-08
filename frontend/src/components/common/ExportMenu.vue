<script setup lang="ts">
import { Braces, ChevronDown, Download, FileText, Printer } from 'lucide-vue-next'
import {
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuPortal,
  DropdownMenuRoot,
  DropdownMenuTrigger,
} from 'reka-ui'
import Button from '@/components/ui/Button.vue'

withDefaults(defineProps<{ disabled?: boolean; label?: string }>(), {
  disabled: false,
  label: '导出',
})

const emit = defineEmits<{ markdown: []; json: []; pdf: [] }>()
</script>

<template>
  <DropdownMenuRoot>
    <DropdownMenuTrigger as-child>
      <Button variant="outline" :disabled="disabled">
        <Download class="h-4 w-4" aria-hidden="true" />
        {{ label }}
        <ChevronDown class="h-3.5 w-3.5" aria-hidden="true" />
      </Button>
    </DropdownMenuTrigger>
    <DropdownMenuPortal>
      <DropdownMenuContent
        class="z-toast min-w-44 rounded-lg border border-border bg-surface p-1.5 shadow-pop"
        :side-offset="6"
        align="end"
      >
        <DropdownMenuItem
          class="flex cursor-pointer select-none items-center gap-2 rounded-md px-2.5 py-2 text-sm text-text outline-none data-[highlighted]:bg-surface-2"
          @select="emit('markdown')"
        >
          <FileText class="h-4 w-4 text-text-muted" aria-hidden="true" />
          Markdown（.md）
        </DropdownMenuItem>
        <DropdownMenuItem
          class="flex cursor-pointer select-none items-center gap-2 rounded-md px-2.5 py-2 text-sm text-text outline-none data-[highlighted]:bg-surface-2"
          @select="emit('json')"
        >
          <Braces class="h-4 w-4 text-text-muted" aria-hidden="true" />
          JSON 数据（.json）
        </DropdownMenuItem>
        <DropdownMenuItem
          class="flex cursor-pointer select-none items-center gap-2 rounded-md px-2.5 py-2 text-sm text-text outline-none data-[highlighted]:bg-surface-2"
          @select="emit('pdf')"
        >
          <Printer class="h-4 w-4 text-text-muted" aria-hidden="true" />
          打印 / 另存 PDF
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenuPortal>
  </DropdownMenuRoot>
</template>