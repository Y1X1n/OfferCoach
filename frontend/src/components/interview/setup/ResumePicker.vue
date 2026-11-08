<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowRight, CheckCircle2, Clock, FileText, Layers } from 'lucide-vue-next'
import type { ResumeSummary } from '@/types'
import Alert from '@/components/ui/Alert.vue'
import Badge from '@/components/ui/Badge.vue'
import Card from '@/components/ui/Card.vue'
import { cn } from '@/utils/cn'
import { formatDate, formatRelative } from '@/utils/format'

const props = defineProps<{
  resumes: ResumeSummary[]
  totalCount: number
}>()

const selectedId = defineModel<string>({ required: true })

const emptyTitle = computed(() => (props.totalCount > 0 ? '简历正在解析中' : '还没有可用的简历'))
const emptyDescription = computed(() =>
  props.totalCount > 0
    ? '解析完成后即可选择；若长时间未完成，请到简历页查看解析状态。'
    : '上传 PDF 或 DOCX 简历，解析完成后即可用于生成面试题。',
)
</script>

<template>
  <Card padding="lg">
    <section aria-labelledby="setup-resume">
      <div class="flex items-start gap-3">
        <span
          class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-brand/10 text-sm font-semibold text-brand"
        >
          1
        </span>
        <div class="min-w-0 flex-1">
          <h2 id="setup-resume" class="text-base font-semibold text-text">选择简历</h2>
          <p class="mt-0.5 text-xs leading-5 text-text-muted">
            题目会引用简历中的项目、技能与量化结果。
          </p>
        </div>
        <Badge variant="brand">必选</Badge>
      </div>

      <div class="mt-4">
        <Alert
          v-if="resumes.length === 0"
          variant="warning"
          :title="emptyTitle"
          :description="emptyDescription"
        >
          <RouterLink
            to="/resumes"
            class="mt-2 inline-flex items-center gap-1 text-xs font-medium underline underline-offset-2"
          >
            去上传简历
            <ArrowRight class="h-3 w-3" aria-hidden="true" />
          </RouterLink>
        </Alert>

        <div
          v-else
          class="grid gap-3 sm:grid-cols-2"
          role="radiogroup"
          aria-labelledby="setup-resume"
        >
          <button
            v-for="resume in resumes"
            :key="resume.id"
            type="button"
            role="radio"
            :aria-checked="selectedId === resume.id"
            :class="
              cn(
                'flex h-full flex-col rounded-lg border p-4 text-left transition-all duration-150',
                selectedId === resume.id
                  ? 'border-brand bg-brand/5 shadow-sm'
                  : 'border-border bg-surface hover:border-brand/40 hover:bg-surface-2/60',
              )
            "
            @click="selectedId = resume.id"
          >
            <span class="flex items-start justify-between gap-3">
              <span
                class="flex h-9 w-9 items-center justify-center rounded-md bg-brand/10 text-brand"
              >
                <FileText class="h-4 w-4" aria-hidden="true" />
              </span>
              <span
                v-if="selectedId === resume.id"
                class="flex h-5 w-5 items-center justify-center rounded-full bg-brand text-brand-foreground"
              >
                <CheckCircle2 class="h-4 w-4" aria-hidden="true" />
              </span>
            </span>
            <span class="mt-3 block text-sm font-semibold text-text">{{ resume.title }}</span>
            <span class="mt-1 line-clamp-2 block text-xs leading-5 text-text-muted">
              {{ resume.summary }}
            </span>
            <span
              class="mt-auto flex flex-wrap items-center gap-x-3 gap-y-1 pt-3 text-2xs text-text-muted"
            >
              <span class="inline-flex items-center gap-1">
                <Layers class="h-3 w-3" aria-hidden="true" />
                {{ resume.skillCount }} 项技能
              </span>
              <span
                class="inline-flex items-center gap-1"
                :title="formatDate(resume.updatedAt, true)"
              >
                <Clock class="h-3 w-3" aria-hidden="true" />
                {{ formatRelative(resume.updatedAt) }}更新
              </span>
            </span>
          </button>
        </div>
      </div>
    </section>
  </Card>
</template>
