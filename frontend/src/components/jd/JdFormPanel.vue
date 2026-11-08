<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ClipboardPaste, Sparkles } from 'lucide-vue-next'
import Alert from '@/components/ui/Alert.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import Input from '@/components/ui/Input.vue'
import Textarea from '@/components/ui/Textarea.vue'

withDefaults(defineProps<{ submitting?: boolean; errorMessage?: string }>(), {
  submitting: false,
  errorMessage: '',
})

const emit = defineEmits<{
  submit: [{ title: string; company: string; rawText: string }]
}>()

const MIN_LENGTH = 50
const MAX_LENGTH = 8000

const sampleJd =
  '岗位职责：负责核心交易系统的后端开发与性能优化；参与高并发场景下的稳定性治理与技术方案评审；' +
  '与产品、测试协作推进需求落地，持续改进工程质量。任职要求：3 年以上 Java 开发经验，熟悉 Spring Boot、MySQL、Redis；' +
  '了解 Kafka 等消息队列；有 Kubernetes 使用经验者优先；具备良好的工程习惯与跨团队沟通能力。'

const form = reactive({ title: '', company: '', rawText: '' })
const errors = reactive({ title: '', rawText: '' })
const formAlert = ref('')

const textCount = computed(() => form.rawText.trim().length)
const textHint = computed(() => {
  if (textCount.value === 0) return '至少 50 字，建议保留完整的岗位职责与任职要求'
  if (textCount.value < MIN_LENGTH) return `还需 ${MIN_LENGTH - textCount.value} 字达到最低要求`
  return '长度已满足要求，可继续补充业务背景'
})

watch([() => form.title, () => form.rawText], () => {
  if (errors.title && form.title.trim()) errors.title = ''
  if (errors.rawText && textCount.value >= MIN_LENGTH) errors.rawText = ''
  if (!errors.title && !errors.rawText) formAlert.value = ''
})

function validate(): boolean {
  errors.title = form.title.trim() ? '' : '请填写职位名称'
  if (textCount.value === 0) {
    errors.rawText = '请粘贴 JD 原文'
  } else if (textCount.value < MIN_LENGTH) {
    errors.rawText = `JD 原文至少 ${MIN_LENGTH} 字，当前 ${textCount.value} 字`
  } else {
    errors.rawText = ''
  }
  formAlert.value = errors.title || errors.rawText ? '请先补充必填信息，再开始分析' : ''
  return !errors.title && !errors.rawText
}

function handleSubmit() {
  if (!validate()) return
  emit('submit', {
    title: form.title.trim(),
    company: form.company.trim(),
    rawText: form.rawText.trim(),
  })
}

function fillSample() {
  form.title = '高级后端工程师'
  form.company = '云帆科技'
  form.rawText = sampleJd
  errors.title = ''
  errors.rawText = ''
  formAlert.value = ''
}
</script>

<template>
  <Card padding="lg">
    <div class="flex flex-wrap items-start justify-between gap-2">
      <div>
        <h2 class="text-base font-semibold text-text">粘贴岗位描述</h2>
        <p class="mt-1 text-xs leading-5 text-text-muted">
          我们会从 JD 中抽取技能与职责，再与已解析简历比对；最少 50 字，最多 8000 字。
        </p>
      </div>
      <Button variant="ghost" size="sm" @click="fillSample">
        <ClipboardPaste class="h-3.5 w-3.5" aria-hidden="true" />
        填入示例
      </Button>
    </div>

    <form class="mt-4 space-y-4" novalidate @submit.prevent="handleSubmit">
      <div class="grid gap-4 sm:grid-cols-2">
        <Input
          v-model="form.title"
          label="职位名称"
          placeholder="例如：高级后端工程师"
          required
          :error="errors.title"
        />
        <Input
          v-model="form.company"
          label="公司"
          placeholder="例如：云帆科技"
          hint="选填，不填时显示为“目标公司”"
        />
      </div>

      <Textarea
        v-model="form.rawText"
        label="JD 原文"
        required
        :rows="8"
        :maxlength="MAX_LENGTH"
        show-count
        placeholder="将招聘网站上的岗位职责与任职要求整段粘贴到这里…"
        :error="errors.rawText"
        :hint="textHint"
      />

      <Alert v-if="formAlert" variant="warning" :title="formAlert" />
      <Alert v-if="errorMessage" variant="danger" title="分析没有完成" :description="errorMessage">
        <p class="mt-1">已填写的内容不会丢失，可以直接重试。</p>
      </Alert>

      <div
        class="flex flex-col-reverse items-stretch gap-2 sm:flex-row sm:items-center sm:justify-between"
      >
        <p class="text-2xs leading-5 text-text-muted">匹配度仅用于安排准备重点，不代表录用结果。</p>
        <Button type="submit" :loading="submitting">
          <Sparkles class="h-4 w-4" aria-hidden="true" />
          分析岗位匹配
        </Button>
      </div>
    </form>
  </Card>
</template>
