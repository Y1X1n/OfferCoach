<script setup lang="ts">
import { Plus, Trash2 } from 'lucide-vue-next'
import type { EducationItem } from '@/types'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'

const items = defineModel<EducationItem[]>('education', { required: true })

function addEducation() {
  items.value.push({ school: '', degree: '', major: '', start: '', end: '' })
}

function removeEducation(index: number) {
  items.value.splice(index, 1)
}
</script>

<template>
  <section class="border-t border-border pt-5">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h3 class="text-sm font-semibold text-text">教育经历</h3>
        <p class="mt-0.5 text-xs text-text-muted">学校、学历、专业与起止时间。</p>
      </div>
      <Button size="sm" variant="outline" @click="addEducation">
        <Plus class="h-3.5 w-3.5" aria-hidden="true" />
        添加
      </Button>
    </div>
    <div v-if="items.length" class="mt-3 space-y-3">
      <div
        v-for="(item, index) in items"
        :key="`edu-${index}`"
        class="rounded-md border border-border p-3"
      >
        <div class="flex items-center justify-between gap-2">
          <p class="text-xs font-medium text-text-muted">教育 {{ index + 1 }}</p>
          <Button
            size="sm"
            variant="ghost"
            :aria-label="`删除教育经历 ${index + 1}`"
            @click="removeEducation(index)"
          >
            <Trash2 class="h-3.5 w-3.5" aria-hidden="true" />
          </Button>
        </div>
        <div class="mt-2 grid gap-3 sm:grid-cols-2">
          <Input v-model="item.school" label="学校" />
          <Input v-model="item.degree" label="学历" placeholder="本科" />
          <Input v-model="item.major" label="专业" />
          <div class="grid grid-cols-2 gap-3">
            <Input v-model="item.start" label="开始时间" placeholder="2018-09" />
            <Input v-model="item.end" label="结束时间" placeholder="2022-06" />
          </div>
        </div>
      </div>
    </div>
    <p v-else class="mt-3 text-xs text-text-muted">暂无教育经历，点击“添加”补充。</p>
  </section>
</template>
