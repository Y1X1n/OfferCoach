<script setup lang="ts">
import { Plus, Trash2 } from 'lucide-vue-next'
import type { ExperienceItem } from '@/types'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'
import Textarea from '@/components/ui/Textarea.vue'

const items = defineModel<ExperienceItem[]>('experiences', { required: true })

function toText(lines: string[]): string {
  return lines.join('\n')
}

function toLines(text: string): string[] {
  return text.split('\n')
}

function addExperience() {
  items.value.push({ company: '', title: '', start: '', end: '', bullets: [] })
}

function removeExperience(index: number) {
  items.value.splice(index, 1)
}
</script>

<template>
  <section class="border-t border-border pt-5">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h3 class="text-sm font-semibold text-text">工作经历</h3>
        <p class="mt-0.5 text-xs text-text-muted">职责与成果按行填写，一行一条。</p>
      </div>
      <Button size="sm" variant="outline" @click="addExperience">
        <Plus class="h-3.5 w-3.5" aria-hidden="true" />
        添加
      </Button>
    </div>
    <div v-if="items.length" class="mt-3 space-y-3">
      <div
        v-for="(item, index) in items"
        :key="`exp-${index}`"
        class="rounded-md border border-border p-3"
      >
        <div class="flex items-center justify-between gap-2">
          <p class="text-xs font-medium text-text-muted">工作 {{ index + 1 }}</p>
          <Button
            size="sm"
            variant="ghost"
            :aria-label="`删除工作经历 ${index + 1}`"
            @click="removeExperience(index)"
          >
            <Trash2 class="h-3.5 w-3.5" aria-hidden="true" />
          </Button>
        </div>
        <div class="mt-2 grid gap-3 sm:grid-cols-2">
          <Input v-model="item.company" label="公司" />
          <Input v-model="item.title" label="职位" />
          <Input v-model="item.start" label="开始时间" placeholder="2022-07" />
          <Input v-model="item.end" label="结束时间" placeholder="至今" />
        </div>
        <Textarea
          class="mt-3"
          label="职责与成果（每行一条）"
          :rows="4"
          :model-value="toText(item.bullets)"
          placeholder="负责订单履约链路重构，P99 从 680ms 降到 210ms"
          @update:model-value="item.bullets = toLines($event)"
        />
      </div>
    </div>
    <p v-else class="mt-3 text-xs text-text-muted">暂无工作经历，点击“添加”补充。</p>
  </section>
</template>
