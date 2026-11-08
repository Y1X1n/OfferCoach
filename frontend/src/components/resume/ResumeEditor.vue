<script setup lang="ts">
import { computed, ref, toRaw, watch } from 'vue'
import type { Resume, ResumeLayout, ResumeStructured } from '@/types'
import Alert from '@/components/ui/Alert.vue'
import Button from '@/components/ui/Button.vue'
import Dialog from '@/components/ui/Dialog.vue'
import Input from '@/components/ui/Input.vue'
import Textarea from '@/components/ui/Textarea.vue'
import ExportMenu from '@/components/common/ExportMenu.vue'
import { useToast } from '@/composables/useToast'
import { exportResumeJson, exportResumeMarkdown, exportResumePdf } from '@/utils/resumeExport'
import BasicSection from '@/components/resume/editor/BasicSection.vue'
import CertificateSection from '@/components/resume/editor/CertificateSection.vue'
import EducationSection from '@/components/resume/editor/EducationSection.vue'
import ExperienceSection from '@/components/resume/editor/ExperienceSection.vue'
import ProjectSection from '@/components/resume/editor/ProjectSection.vue'
import SkillSection from '@/components/resume/editor/SkillSection.vue'

const props = withDefaults(defineProps<{ open: boolean; resume: Resume | null; layout?: ResumeLayout }>(), {
  layout: 'classic',
})
const { toast } = useToast()
const emit = defineEmits<{
  'update:open': [boolean]
  save: [{ title: string; summary: string; structured: ResumeStructured }]
}>()

interface Draft {
  title: string
  summary: string
  structured: ResumeStructured
}

interface EditorErrors {
  title?: string
  summary?: string
  years?: string
}

function emptyStructured(): ResumeStructured {
  return {
    basic: { name: '', city: '', yearsOfExperience: 0, phoneMasked: '', emailMasked: '' },
    education: [],
    experiences: [],
    projects: [],
    skills: [],
    certificates: [],
  }
}

const draft = ref<Draft>({ title: '', summary: '', structured: emptyStructured() })
const yearsRaw = ref('0')
const certificatesText = ref('')
const errors = ref<EditorErrors>({})
const isOpen = computed({
  get: () => props.open,
  set: (value: boolean) => emit('update:open', value),
})

const errorList = computed(() =>
  [errors.value.title, errors.value.summary, errors.value.years].filter(
    (message): message is string => Boolean(message),
  ),
)

function cloneForDraft(resume: Resume): Draft {
  const source = {
    title: resume.title,
    summary: resume.summary,
    // resume 来自 Pinia 的响应式代理，structuredClone 不能直接克隆 Proxy，先取原始对象
    structured: toRaw(resume.structured),
  }
  try {
    return structuredClone(source)
  } catch {
    return JSON.parse(JSON.stringify(source)) as Draft
  }
}

function hydrate(resume: Resume) {
  draft.value = cloneForDraft(resume)
  yearsRaw.value = String(resume.structured.basic.yearsOfExperience)
  certificatesText.value = resume.structured.certificates.join('\n')
  errors.value = {}
}

watch(
  () => props.open,
  (open) => {
    if (open && props.resume) hydrate(props.resume)
  },
  { immediate: true },
)

function cleanLines(lines: string[]): string[] {
  return lines.map((line) => line.trim()).filter((line) => line.length > 0)
}

function sanitize(structured: ResumeStructured, years: number): ResumeStructured {
  return {
    basic: {
      name: structured.basic.name.trim(),
      city: structured.basic.city.trim(),
      yearsOfExperience: years,
      phoneMasked: structured.basic.phoneMasked.trim(),
      emailMasked: structured.basic.emailMasked.trim(),
    },
    education: structured.education
      .map((item) => ({
        school: item.school.trim(),
        degree: item.degree.trim(),
        major: item.major.trim(),
        start: item.start.trim(),
        end: item.end.trim(),
      }))
      .filter((item) => item.school || item.degree || item.major),
    experiences: structured.experiences
      .map((item) => ({
        company: item.company.trim(),
        title: item.title.trim(),
        start: item.start.trim(),
        end: item.end.trim(),
        bullets: cleanLines(item.bullets),
      }))
      .filter((item) => item.company || item.title),
    projects: structured.projects
      .map((item) => ({
        name: item.name.trim(),
        role: item.role.trim(),
        stack: cleanLines(item.stack),
        highlights: cleanLines(item.highlights),
        metrics: cleanLines(item.metrics),
      }))
      .filter((item) => item.name),
    skills: structured.skills
      .map((item) => ({
        name: item.name.trim(),
        level: item.level,
        years: Number.isFinite(item.years) ? Math.max(0, Math.floor(item.years)) : 0,
      }))
      .filter((item) => item.name),
    certificates: cleanLines(certificatesText.value.split('\n')),
  }
}

function validate(): boolean {
  const next: EditorErrors = {}
  const title = draft.value.title.trim()
  if (title.length === 0) next.title = '请填写简历标题'
  else if (title.length > 120) next.title = '标题最多 120 个字符'
  if (draft.value.summary.length > 500) next.summary = '摘要最多 500 个字符'
  const yearsText = String(yearsRaw.value ?? '').trim()
  const years = Number(yearsText)
  if (yearsText === '' || !Number.isFinite(years) || years < 0) {
    next.years = '工作年限需为不小于 0 的数字'
  }
  errors.value = next
  return Object.keys(next).length === 0
}

function handleSave() {
  if (!validate()) return
  emit('save', {
    title: draft.value.title.trim(),
    summary: draft.value.summary.trim(),
    structured: sanitize(draft.value.structured, Number(yearsRaw.value)),
  })
}

function exportCurrent(kind: 'markdown' | 'json' | 'pdf') {
  const years = Number(yearsRaw.value)
  const input = {
    title: draft.value.title.trim() || '我的简历',
    summary: draft.value.summary.trim(),
    structured: sanitize(draft.value.structured, Number.isFinite(years) ? years : 0),
    layout: props.layout,
  }
  try {
    if (kind === 'markdown') exportResumeMarkdown(input)
    else if (kind === 'json') exportResumeJson(input)
    else exportResumePdf(input)
    if (kind !== 'pdf') toast('已导出当前编辑内容', 'success')
  } catch (error) {
    toast(
      '导出失败',
      'error',
      error instanceof Error ? error.message : '请允许弹窗后重试',
    )
  }
}
</script>

<template>
  <Dialog
    v-model:open="isOpen"
    title="编辑简历内容"
    description="修改只作用于这份简历；保存后各字段的解析置信度会重置为 100%。"
    size="lg"
  >
    <div class="scrollbar-thin -mx-1 max-h-[85vh] overflow-y-auto px-1 pb-1">
      <Alert
        v-if="errorList.length"
        variant="warning"
        title="请先修正以下字段"
        :description="errorList.join('；')"
        class="mb-5"
      />

      <div class="space-y-5">
        <div class="space-y-4">
          <Input
            v-model="draft.title"
            label="简历标题"
            required
            :error="errors.title"
            placeholder="例如：张三 · 后端开发"
          />
          <Textarea
            v-model="draft.summary"
            label="简历摘要"
            :rows="3"
            :maxlength="500"
            show-count
            :error="errors.summary"
            placeholder="用 2-3 句话概括年限、主栈与代表成果"
          />
        </div>

        <BasicSection
          v-model:basic="draft.structured.basic"
          v-model:years-raw="yearsRaw"
          :years-error="errors.years"
        />

        <EducationSection v-model:education="draft.structured.education" />

        <ExperienceSection v-model:experiences="draft.structured.experiences" />

        <ProjectSection v-model:projects="draft.structured.projects" />

        <SkillSection v-model:skills="draft.structured.skills" />

        <CertificateSection v-model="certificatesText" />
      </div>
    </div>

    <template #footer>
      <div class="mr-auto w-full sm:w-auto">
        <ExportMenu label="导出当前内容" @markdown="exportCurrent('markdown')" @json="exportCurrent('json')" @pdf="exportCurrent('pdf')" />
      </div>
      <Button variant="outline" @click="emit('update:open', false)">取消</Button>
      <Button @click="handleSave">保存</Button>
    </template>
  </Dialog>
</template>
