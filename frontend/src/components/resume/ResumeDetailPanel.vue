<script setup lang="ts">
import { computed } from 'vue'
import {
  BriefcaseBusiness,
  FileText,
  GraduationCap,
  Layers,
  LayoutTemplate,
  Mail,
  MapPin,
  Pencil,
  Phone,
  User,
  Wrench,
} from 'lucide-vue-next'
import type { Resume, ResumeLayout, ResumeSectionKey, SkillItem } from '@/types'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import ResumePreview from './ResumePreview.vue'
import ResumeSection from './ResumeSection.vue'
import { formatDate, formatRelative } from '@/utils/format'

const props = withDefaults(defineProps<{ resume: Resume; layout?: ResumeLayout }>(), {
  layout: 'classic',
})
const emit = defineEmits<{ edit: []; 'change-layout': [] }>()

const structured = computed(() => props.resume.structured)
const basic = computed(() => structured.value.basic)
const skillCount = computed(() => structured.value.skills.length)
const projectCount = computed(() => structured.value.projects.length)

const levelLabel: Record<SkillItem['level'], string> = {
  BEGINNER: '了解',
  INTERMEDIATE: '熟练',
  ADVANCED: '精通',
}
const levelVariant: Record<SkillItem['level'], 'neutral' | 'brand' | 'success'> = {
  BEGINNER: 'neutral',
  INTERMEDIATE: 'brand',
  ADVANCED: 'success',
}

function confidenceOf(key: ResumeSectionKey): number {
  return props.resume.confidence[key] ?? 0
}
</script>

<template>
  <div class="space-y-4">
    <Card padding="lg">
      <div class="flex flex-wrap items-start justify-between gap-4">
        <div class="min-w-0">
          <div class="flex flex-wrap items-center gap-2">
            <h2 class="text-lg font-semibold text-text">{{ resume.title }}</h2>
            <Badge variant="success" dot>已解析</Badge>
          </div>
          <p class="mt-1.5 flex items-center gap-1.5 text-xs text-text-muted">
            <FileText class="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
            <span class="truncate">{{ resume.fileName }}</span>
          </p>
          <p class="mt-3 max-w-2xl text-sm leading-6 text-text-muted">{{ resume.summary }}</p>
          <div class="mt-3 flex flex-wrap gap-2">
            <Button size="sm" variant="outline" @click="emit('edit')">
              <Pencil class="h-3.5 w-3.5" aria-hidden="true" />
              编辑内容
            </Button>
            <Button size="sm" variant="outline" @click="emit('change-layout')">
              <LayoutTemplate class="h-3.5 w-3.5" aria-hidden="true" />
              版式
            </Button>
          </div>
        </div>
        <dl class="grid shrink-0 grid-cols-3 gap-4 text-center">
          <div>
            <dt class="text-2xs text-text-muted">技能</dt>
            <dd class="tabular mt-0.5 text-lg font-semibold text-text">{{ skillCount }}</dd>
          </div>
          <div>
            <dt class="text-2xs text-text-muted">项目</dt>
            <dd class="tabular mt-0.5 text-lg font-semibold text-text">{{ projectCount }}</dd>
          </div>
          <div>
            <dt class="text-2xs text-text-muted">年限</dt>
            <dd class="tabular mt-0.5 text-lg font-semibold text-text">
              {{ basic.yearsOfExperience }}
            </dd>
          </div>
        </dl>
      </div>
      <p class="mt-4 border-t border-border pt-3 text-2xs text-text-muted">
        更新于 {{ formatRelative(resume.updatedAt) }}（{{ formatDate(resume.updatedAt, true) }}）
      </p>
    </Card>

    <ResumePreview :resume="resume" :layout="layout ?? 'classic'" />

    <div class="pt-2">
      <h3 class="text-sm font-semibold text-text">解析明细与置信度</h3>
      <p class="mt-1 text-xs leading-5 text-text-muted">
        以下为解析器输出的字段明细，预览仅改变排版，不修改内容。
      </p>
    </div>

    <ResumeSection title="基本信息" :icon="User" :confidence="confidenceOf('basic')">
      <dl class="grid gap-x-6 gap-y-4 sm:grid-cols-2">
        <div>
          <dt class="text-xs text-text-muted">姓名</dt>
          <dd class="mt-1 text-sm font-medium text-text">{{ basic.name }}</dd>
        </div>
        <div>
          <dt class="flex items-center gap-1.5 text-xs text-text-muted">
            <MapPin class="h-3.5 w-3.5" aria-hidden="true" /> 所在城市
          </dt>
          <dd class="mt-1 text-sm font-medium text-text">{{ basic.city }}</dd>
        </div>
        <div>
          <dt class="flex items-center gap-1.5 text-xs text-text-muted">
            <BriefcaseBusiness class="h-3.5 w-3.5" aria-hidden="true" /> 工作年限
          </dt>
          <dd class="tabular mt-1 text-sm font-medium text-text">
            {{ basic.yearsOfExperience }} 年
          </dd>
        </div>
        <div>
          <dt class="flex items-center gap-1.5 text-xs text-text-muted">
            <Phone class="h-3.5 w-3.5" aria-hidden="true" /> 手机
          </dt>
          <dd class="tabular mt-1 text-sm font-medium text-text">{{ basic.phoneMasked }}</dd>
        </div>
        <div class="sm:col-span-2">
          <dt class="flex items-center gap-1.5 text-xs text-text-muted">
            <Mail class="h-3.5 w-3.5" aria-hidden="true" /> 邮箱
          </dt>
          <dd class="mt-1 text-sm font-medium text-text">{{ basic.emailMasked }}</dd>
        </div>
      </dl>
      <p class="mt-4 text-2xs text-text-muted">联系方式已按隐私规则脱敏展示，面试前请自行核对。</p>
    </ResumeSection>

    <ResumeSection title="教育经历" :icon="GraduationCap" :confidence="confidenceOf('education')">
      <ul v-if="structured.education.length" class="space-y-4">
        <li v-for="(item, index) in structured.education" :key="`edu-${index}`" class="flex gap-3">
          <span
            class="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-surface-2 text-text-muted"
          >
            <GraduationCap class="h-4 w-4" aria-hidden="true" />
          </span>
          <div class="min-w-0">
            <p class="text-sm font-medium text-text">
              {{ item.school }}
              <span class="ml-1.5 text-xs font-normal text-text-muted"
                >{{ item.degree }} · {{ item.major }}</span
              >
            </p>
            <p class="tabular mt-0.5 text-xs text-text-muted">{{ item.start }} – {{ item.end }}</p>
          </div>
        </li>
      </ul>
      <p v-else class="text-sm text-text-muted">未识别到教育经历，建议核对原始简历。</p>
    </ResumeSection>

    <ResumeSection
      title="工作经历"
      :icon="BriefcaseBusiness"
      :confidence="confidenceOf('experiences')"
    >
      <ol v-if="structured.experiences.length" class="space-y-5">
        <li
          v-for="(item, index) in structured.experiences"
          :key="`exp-${index}`"
          class="relative border-l border-border pl-4"
        >
          <span
            class="absolute -left-1 top-1.5 h-2 w-2 rounded-full bg-brand/70"
            aria-hidden="true"
          />
          <div class="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
            <p class="text-sm font-medium text-text">{{ item.company }} · {{ item.title }}</p>
            <p class="tabular text-xs text-text-muted">{{ item.start }} – {{ item.end }}</p>
          </div>
          <ul v-if="item.bullets.length" class="mt-2 space-y-1.5">
            <li
              v-for="(bullet, bulletIndex) in item.bullets"
              :key="`bullet-${bulletIndex}`"
              class="flex gap-2 text-sm leading-6 text-text-muted"
            >
              <span class="mt-2.5 h-1 w-1 shrink-0 rounded-full bg-text-muted" aria-hidden="true" />
              <span>{{ bullet }}</span>
            </li>
          </ul>
          <p v-else class="mt-2 text-xs text-text-muted">该段经历没有识别到职责描述，建议补充。</p>
        </li>
      </ol>
      <p v-else class="text-sm text-text-muted">未识别到工作经历，建议核对原始简历。</p>
    </ResumeSection>

    <ResumeSection title="项目经历" :icon="Layers" :confidence="confidenceOf('projects')">
      <div v-if="structured.projects.length" class="space-y-4">
        <article
          v-for="(project, index) in structured.projects"
          :key="`project-${index}`"
          class="rounded-lg border border-border bg-surface-2/40 p-4"
        >
          <div class="flex flex-wrap items-start justify-between gap-2">
            <h4 class="text-sm font-semibold text-text">{{ project.name }}</h4>
            <p class="text-xs text-text-muted">{{ project.role }}</p>
          </div>

          <div v-if="project.stack.length" class="mt-2.5 flex flex-wrap gap-1.5">
            <Badge v-for="tech in project.stack" :key="tech" variant="neutral">{{ tech }}</Badge>
          </div>

          <template v-if="project.highlights.length">
            <p class="mt-3 text-2xs font-medium uppercase tracking-wider text-text-muted">
              关键动作
            </p>
            <ul class="mt-1.5 space-y-1.5">
              <li
                v-for="(highlight, highlightIndex) in project.highlights"
                :key="`highlight-${highlightIndex}`"
                class="flex gap-2 text-sm leading-6 text-text-muted"
              >
                <span
                  class="mt-2.5 h-1 w-1 shrink-0 rounded-full bg-text-muted"
                  aria-hidden="true"
                />
                <span>{{ highlight }}</span>
              </li>
            </ul>
          </template>

          <template v-if="project.metrics.length">
            <p class="mt-3 text-2xs font-medium uppercase tracking-wider text-text-muted">
              量化结果
            </p>
            <div class="mt-1.5 flex flex-wrap gap-1.5">
              <Badge v-for="metric in project.metrics" :key="metric" variant="accent" dot>{{
                metric
              }}</Badge>
            </div>
          </template>
        </article>
      </div>
      <p v-else class="text-sm text-text-muted">未识别到项目经历，建议补充项目名称、职责与结果。</p>
    </ResumeSection>

    <ResumeSection title="技能与证书" :icon="Wrench" :confidence="confidenceOf('skills')">
      <div v-if="structured.skills.length" class="flex flex-wrap gap-2">
        <div
          v-for="skill in structured.skills"
          :key="skill.name"
          class="flex items-center gap-2 rounded-md border border-border bg-surface px-2.5 py-1.5"
        >
          <span class="text-sm font-medium text-text">{{ skill.name }}</span>
          <Badge :variant="levelVariant[skill.level]">{{ levelLabel[skill.level] }}</Badge>
          <span class="tabular text-xs text-text-muted">{{ skill.years }} 年</span>
        </div>
      </div>
      <p v-else class="text-sm text-text-muted">未识别到技能标签，建议补充技术栈与熟练度。</p>

      <div v-if="structured.certificates.length" class="mt-4 border-t border-border pt-3">
        <p class="text-xs text-text-muted">证书</p>
        <div class="mt-1.5 flex flex-wrap gap-1.5">
          <Badge
            v-for="certificate in structured.certificates"
            :key="certificate"
            variant="neutral"
          >
            {{ certificate }}
          </Badge>
        </div>
      </div>
    </ResumeSection>
  </div>
</template>
