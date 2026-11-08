<script setup lang="ts">
import { Plus, Trash2 } from 'lucide-vue-next'
import type { SkillItem } from '@/types'
import Button from '@/components/ui/Button.vue'
import Input from '@/components/ui/Input.vue'

const items = defineModel<SkillItem[]>('skills', { required: true })

function onLevelChange(skill: SkillItem, event: Event) {
  const value = (event.target as HTMLSelectElement).value
  if (value === 'BEGINNER' || value === 'INTERMEDIATE' || value === 'ADVANCED') {
    skill.level = value
  }
}

function onSkillYears(skill: SkillItem, event: Event) {
  const value = Number((event.target as HTMLInputElement).value)
  skill.years = Number.isFinite(value) && value >= 0 ? value : 0
}

function addSkill() {
  items.value.push({ name: '', level: 'INTERMEDIATE', years: 1 })
}

function removeSkill(index: number) {
  items.value.splice(index, 1)
}
</script>

<template>
  <section class="border-t border-border pt-5">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h3 class="text-sm font-semibold text-text">技能</h3>
        <p class="mt-0.5 text-xs text-text-muted">熟练度分为了解 / 熟练 / 精通三档。</p>
      </div>
      <Button size="sm" variant="outline" @click="addSkill">
        <Plus class="h-3.5 w-3.5" aria-hidden="true" />
        添加
      </Button>
    </div>
    <div v-if="items.length" class="mt-3 space-y-3">
      <div
        v-for="(skill, index) in items"
        :key="`skill-${index}`"
        class="rounded-md border border-border p-3"
      >
        <div class="grid gap-3 sm:grid-cols-[minmax(0,1fr)_7rem_5.5rem_auto] sm:items-end">
          <Input v-model="skill.name" label="技能名称" />
          <div class="flex flex-col gap-1.5">
            <label :for="`skill-level-${index}`" class="text-sm font-medium text-text"
              >熟练度</label
            >
            <select
              :id="`skill-level-${index}`"
              :value="skill.level"
              class="h-10 w-full rounded-md border border-border bg-surface px-2 text-sm text-text outline-none transition-colors focus:border-brand focus:ring-2 focus:ring-brand/20"
              @change="onLevelChange(skill, $event)"
            >
              <option value="BEGINNER">了解</option>
              <option value="INTERMEDIATE">熟练</option>
              <option value="ADVANCED">精通</option>
            </select>
          </div>
          <div class="flex flex-col gap-1.5">
            <label :for="`skill-years-${index}`" class="text-sm font-medium text-text">年限</label>
            <input
              :id="`skill-years-${index}`"
              type="number"
              min="0"
              step="1"
              :value="skill.years"
              class="tabular h-10 w-full rounded-md border border-border bg-surface px-3 text-sm text-text outline-none transition-colors focus:border-brand focus:ring-2 focus:ring-brand/20"
              @input="onSkillYears(skill, $event)"
            />
          </div>
          <Button
            size="sm"
            variant="ghost"
            :aria-label="`删除技能 ${index + 1}`"
            @click="removeSkill(index)"
          >
            <Trash2 class="h-3.5 w-3.5" aria-hidden="true" />
          </Button>
        </div>
      </div>
    </div>
    <p v-else class="mt-3 text-xs text-text-muted">暂无技能，点击“添加”补充。</p>
  </section>
</template>
