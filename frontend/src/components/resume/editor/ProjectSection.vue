<script setup lang="ts">
import { Plus, Trash2 } from 'lucide-vue-next'
import type { ProjectItem } from '@/types'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'
import Textarea from '@/components/ui/Textarea.vue'

const items = defineModel<ProjectItem[]>('projects', { required: true })

function toText(lines: string[]): string {
  return lines.join('\n')
}

function toLines(text: string): string[] {
  return text.split('\n')
}

function stackToText(stack: string[]): string {
  return stack.join(', ')
}

function textToStack(text: string): string[] {
  return text.split(/[,，]/)
}

function addProject() {
  items.value.push({ name: '', role: '', stack: [], highlights: [], metrics: [] })
}

function removeProject(index: number) {
  items.value.splice(index, 1)
}
</script>

<template>
  <section class="border-t border-border pt-5">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h3 class="text-sm font-semibold text-text">项目经历</h3>
        <p class="mt-0.5 text-xs text-text-muted">技术栈用逗号分隔，亮点与指标每行一条。</p>
      </div>
      <Button size="sm" variant="outline" @click="addProject">
        <Plus class="h-3.5 w-3.5" aria-hidden="true" />
        添加
      </Button>
    </div>
    <div v-if="items.length" class="mt-3 space-y-3">
      <div
        v-for="(project, index) in items"
        :key="`project-${index}`"
        class="rounded-md border border-border p-3"
      >
        <div class="flex items-center justify-between gap-2">
          <p class="text-xs font-medium text-text-muted">项目 {{ index + 1 }}</p>
          <Button
            size="sm"
            variant="ghost"
            :aria-label="`删除项目经历 ${index + 1}`"
            @click="removeProject(index)"
          >
            <Trash2 class="h-3.5 w-3.5" aria-hidden="true" />
          </Button>
        </div>
        <div class="mt-2 grid gap-3 sm:grid-cols-2">
          <Input v-model="project.name" label="项目名称" />
          <Input v-model="project.role" label="担任角色" />
          <div class="sm:col-span-2">
            <Input
              label="技术栈（逗号分隔）"
              :model-value="stackToText(project.stack)"
              placeholder="Java, Spring Boot, Redis"
              @update:model-value="project.stack = textToStack($event)"
            />
          </div>
        </div>
        <div class="mt-3 grid gap-3">
          <Textarea
            label="关键亮点（每行一条）"
            :rows="3"
            :model-value="toText(project.highlights)"
            placeholder="拆分履约编排与库存预占，减少跨服务同步调用"
            @update:model-value="project.highlights = toLines($event)"
          />
          <Textarea
            label="量化指标（每行一条）"
            :rows="2"
            :model-value="toText(project.metrics)"
            placeholder="QPS 从 800 提升到 2400"
            @update:model-value="project.metrics = toLines($event)"
          />
        </div>
      </div>
    </div>
    <p v-else class="mt-3 text-xs text-text-muted">暂无项目经历，点击“添加”补充。</p>
  </section>
</template>
