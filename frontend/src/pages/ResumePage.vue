<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { FileText, LayoutTemplate, Pencil, RefreshCw, ShieldCheck, Star, Trash2, Upload } from 'lucide-vue-next'
import type { Resume, ResumeLayout, ResumeSummary, ResumeStructured } from '@/types'
import PageHeader from '@/components/common/PageHeader.vue'
import AsyncState from '@/components/common/AsyncState.vue'
import ExportMenu from '@/components/common/ExportMenu.vue'
import UploadDropzone from '@/components/interview/UploadDropzone.vue'
import ResumeDetailPanel from '@/components/resume/ResumeDetailPanel.vue'
import ResumeEditor from '@/components/resume/ResumeEditor.vue'
import ResumeLayoutPicker from '@/components/resume/ResumeLayoutPicker.vue'
import ResumeListPanel from '@/components/resume/ResumeListPanel.vue'
import ResumeParseState from '@/components/resume/ResumeParseState.vue'
import Alert from '@/components/ui/Alert.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import Dialog from '@/components/ui/Dialog.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import { ApiError, errorMessage } from '@/api/client'
import * as resumesApi from '@/api/resumes'
import { usePolling } from '@/composables/usePolling'
import { useToast } from '@/composables/useToast'
import { useResumeLayout } from '@/utils/resumeLayout'
import { exportResumeJson, exportResumeMarkdown, exportResumePdf } from '@/utils/resumeExport'
import type { ResumeExportInput } from '@/utils/resumeExport'

const { toast } = useToast()

const uploadSection = ref<HTMLElement | null>(null)
const list = ref<ResumeSummary[]>([])
const listLoading = ref(true)
const listError = ref<string | null>(null)
const selectedId = ref('')
const detail = ref<Resume | null>(null)
const detailLoading = ref(false)
const detailError = ref<string | null>(null)

const uploading = ref(false)
const uploadError = ref('')
const uploadFileName = ref('')

const deleteOpen = ref(false)
const deleteTarget = ref<ResumeSummary | null>(null)
const deleteError = ref('')
const deleting = ref(false)

const layoutOpen = ref(false)
const editorOpen = ref(false)
const editorMode = ref<'edit' | 'create'>('edit')
const savingEditor = ref(false)

const { layout, sync: syncLayout, choose: chooseLayout } = useResumeLayout(() => selectedId.value)

const blankResume = computed<Resume>(() => ({
  id: '',
  title: '',
  fileName: '',
  status: 'READY',
  progress: 100,
  updatedAt: new Date().toISOString(),
  summary: '',
  structured: {
    basic: { name: '', city: '', yearsOfExperience: 0, phoneMasked: '', emailMasked: '' },
    education: [],
    experiences: [],
    projects: [],
    skills: [],
    certificates: [],
  },
  confidence: { basic: 1, education: 1, experiences: 1, projects: 1, skills: 1 },
  isDefault: false,
  lowConfidenceFields: [],
}))

const editorResume = computed<Resume | null>(() =>
  editorMode.value === 'create' ? blankResume.value : detail.value,
)
const selectedReady = computed(() => detail.value?.status === 'READY')
const parsingCount = computed(() => list.value.filter((item) => item.status === 'PARSING').length)

const poller = usePolling(async () => {
  const id = selectedId.value
  if (!id) return false
  try {
    const resume = await resumesApi.getResume(id)
    if (selectedId.value === id) {
      detail.value = resume
      detailError.value = null
    }
    patchSummary(resume)
    if (resume.status === 'PARSING') return true
    if (resume.status === 'READY') {
      toast('简历解析完成', 'success', resume.title)
      void loadList()
    } else {
      toast('简历解析失败', 'error', '可重新解析，或直接手动编辑结构化内容')
    }
    return false
  } catch {
    return false
  }
}, 2000)

function patchSummary(resume: Resume): void {
  const index = list.value.findIndex((item) => item.id === resume.id)
  const summary: ResumeSummary = {
    id: resume.id,
    title: resume.title,
    status: resume.status,
    summary: resume.summary,
    skillCount: resume.structured.skills.length,
    isDefault: resume.isDefault,
    fileName: resume.fileName,
    updatedAt: resume.updatedAt,
  }
  if (index >= 0) list.value[index] = summary
  else list.value.unshift(summary)
}

async function loadList(): Promise<void> {
  listLoading.value = true
  listError.value = null
  try {
    const page = await resumesApi.listResumes({ page: 1, size: 50 })
    list.value = page.items
    if (!selectedId.value || !page.items.some((item) => item.id === selectedId.value)) {
      const preferred = page.items.find((item) => item.status === 'READY') ?? page.items[0]
      selectedId.value = preferred?.id ?? ''
      if (selectedId.value) void loadDetail(selectedId.value)
    }
  } catch (cause) {
    listError.value = errorMessage(cause, '简历列表加载失败')
  } finally {
    listLoading.value = false
  }
}

async function loadDetail(id: string): Promise<void> {
  detailLoading.value = true
  detailError.value = null
  try {
    const resume = await resumesApi.getResume(id)
    if (selectedId.value !== id) return
    detail.value = resume
    if (resume.status === 'PARSING') poller.start()
  } catch (cause) {
    if (selectedId.value === id) detailError.value = errorMessage(cause, '简历详情加载失败')
  } finally {
    if (selectedId.value === id) detailLoading.value = false
  }
}

watch(selectedId, (id) => {
  syncLayout()
  if (!id) {
    detail.value = null
    return
  }
  void loadDetail(id)
})

function scrollToUpload(): void {
  uploadSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function handleFile(file: File): Promise<void> {
  if (uploading.value) {
    toast('正在处理上一份简历', 'info', '请等待当前上传完成后再试')
    return
  }
  uploadError.value = ''
  uploadFileName.value = file.name
  uploading.value = true
  try {
    const result = await resumesApi.uploadResume(file)
    await loadList()
    selectedId.value = result.id
    void loadDetail(result.id)
    if (result.parseStatus === 'PARSING') {
      poller.start()
      toast('已上传，正在解析', 'info', '解析完成后会自动展示结构化结果')
    } else {
      toast('简历已就绪', 'success', result.title)
    }
  } catch (cause) {
    uploadError.value = errorMessage(cause, '上传失败，请稍后重试')
    if (cause instanceof ApiError && cause.code === 'PARSE_NEED_MANUAL') {
      uploadError.value = `${cause.message}，可按手动新建录入结构化内容。`
    }
    toast('上传失败', 'error', uploadError.value)
  } finally {
    uploading.value = false
    uploadFileName.value = ''
  }
}

function onValidationError(message: string): void {
  uploadError.value = ''
  toast('文件未通过校验', 'error', message)
}

function openManualCreate(): void {
  editorMode.value = 'create'
  editorOpen.value = true
}

function openEditor(): void {
  editorMode.value = 'edit'
  editorOpen.value = true
}

function retryDetail(): void {
  if (selectedId.value) void loadDetail(selectedId.value)
}

async function handleSave(payload: {
  title: string
  summary: string
  structured: ResumeStructured
}): Promise<void> {
  if (savingEditor.value) return
  savingEditor.value = true
  try {
    if (editorMode.value === 'create') {
      const created = await resumesApi.createResumeManually({
        title: payload.title,
        summary: payload.summary,
        structured: payload.structured,
        isDefault: list.value.length === 0,
      })
      editorOpen.value = false
      await loadList()
      selectedId.value = created.id
      detail.value = created
      toast('已手动创建简历', 'success', created.title)
    } else {
      const target = detail.value
      if (!target) return
      const updated = await resumesApi.updateResume(target.id, {
        title: payload.title,
        summary: payload.summary,
        structured: payload.structured,
      })
      detail.value = updated
      patchSummary(updated)
      editorOpen.value = false
      toast('简历已更新', 'success', `${updated.title} · 摘要会在后台重建`)
    }
  } catch (cause) {
    const message = errorMessage(cause, '保存失败，请稍后重试')
    const unsupported =
      cause instanceof ApiError && (cause.status === 415 || cause.code === 'UNSUPPORTED_FILE')
    toast(
      '保存失败',
      'error',
      unsupported ? '后端暂未开放 JSON 建档，请改用上传 PDF/DOCX 后编辑。' : message,
    )
  } finally {
    savingEditor.value = false
  }
}

async function reparse(): Promise<void> {
  const target = detail.value
  if (!target) return
  try {
    await resumesApi.reparseResume(target.id)
    target.status = 'PARSING'
    target.progress = 45
    patchSummary(target)
    poller.start()
    toast('已提交重新解析', 'info', '每份简历每小时最多 2 次，不重复扣额度')
  } catch (cause) {
    toast('重新解析失败', 'error', errorMessage(cause))
  }
}

async function setDefault(): Promise<void> {
  const target = detail.value
  if (!target) return
  try {
    const updated = await resumesApi.setDefaultResume(target.id)
    detail.value = updated
    await loadList()
    toast('已设为默认简历', 'success', updated.title)
  } catch (cause) {
    toast('设置默认失败', 'error', errorMessage(cause))
  }
}

function askDelete(resume: ResumeSummary): void {
  deleteTarget.value = resume
  deleteError.value = ''
  deleteOpen.value = true
}

function askDeleteSelected(): void {
  const target = detail.value
  if (!target) return
  askDelete({
    id: target.id,
    title: target.title,
    status: target.status,
    summary: target.summary,
    skillCount: target.structured.skills.length,
    isDefault: target.isDefault,
    fileName: target.fileName,
    updatedAt: target.updatedAt,
  })
}

async function confirmDelete(): Promise<void> {
  const target = deleteTarget.value
  if (!target || deleting.value) return
  deleting.value = true
  try {
    await resumesApi.deleteResume(target.id)
    deleteOpen.value = false
    deleteTarget.value = null
    deleteError.value = ''
    poller.stop()
    if (selectedId.value === target.id) {
      selectedId.value = ''
      detail.value = null
    }
    await loadList()
    toast('已删除简历', 'success', target.title)
  } catch (cause) {
    deleteError.value = errorMessage(cause, '删除失败，请稍后重试')
    toast('删除失败', 'error', deleteError.value)
  } finally {
    deleting.value = false
  }
}

function handleLayoutSelect(next: ResumeLayout): void {
  chooseLayout(next)
  layoutOpen.value = false
  toast('版式已更新', 'success', '只改变排版，内容不变；导出 PDF 时会沿用该版式')
}

function exportInput(): ResumeExportInput | null {
  const target = detail.value
  if (!target) return null
  return {
    title: target.title,
    summary: target.summary,
    structured: target.structured,
    layout: layout.value,
  }
}

function handleExport(kind: 'markdown' | 'json' | 'pdf'): void {
  const input = exportInput()
  if (!input) return
  try {
    if (kind === 'markdown') exportResumeMarkdown(input)
    else if (kind === 'json') exportResumeJson(input)
    else exportResumePdf(input)
    if (kind !== 'pdf') toast('已导出简历', 'success', input.title)
  } catch (cause) {
    toast('导出失败', 'error', errorMessage(cause, '请允许弹窗后重试'))
  }
}

onMounted(() => {
  void loadList()
})

onBeforeUnmount(() => {
  poller.stop()
})
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="简历管理" description="上传、解析与修正简历；结构化结果会用于出题与岗位匹配。">
      <template #actions>
        <Badge variant="neutral" class="hidden sm:inline-flex">
          已保存 {{ list.length }} 份<template v-if="parsingCount"> · {{ parsingCount }} 份解析中</template>
        </Badge>
        <ExportMenu :disabled="!selectedReady" @markdown="handleExport('markdown')" @json="handleExport('json')" @pdf="handleExport('pdf')" />
        <Button variant="outline" :disabled="!selectedReady" @click="layoutOpen = true">
          <LayoutTemplate class="h-4 w-4" aria-hidden="true" />
          选择版式
        </Button>
        <Button variant="outline" @click="openManualCreate">
          <Pencil class="h-4 w-4" aria-hidden="true" />
          手动新建
        </Button>
        <Button @click="scrollToUpload">
          <Upload class="h-4 w-4" aria-hidden="true" />
          上传简历
        </Button>
      </template>
    </PageHeader>

    <section ref="uploadSection" class="scroll-mt-20">
      <Card padding="lg">
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 class="text-base font-semibold text-text">上传并解析简历</h2>
            <p class="mt-1 text-xs text-text-muted">
              支持 PDF / DOCX / 图片（PNG、JPG 等）/ PPT，单文件不超过 10MB；图片与扫描件自动 OCR 识别。
            </p>
          </div>
          <Badge v-if="uploading" variant="brand" dot>处理中</Badge>
        </div>

        <div class="mt-4">
          <UploadDropzone
            :uploading="uploading"
            :progress="uploading ? 55 : 0"
            :file-name="uploadFileName"
            accept=".pdf,.docx,.png,.jpg,.jpeg,.webp,.bmp,.tif,.tiff,.ppt,.pptx"
            @file="handleFile"
            @error="onValidationError"
          />
        </div>

        <Alert
          v-if="uploadError"
          variant="danger"
          title="上传或解析未完成"
          :description="uploadError"
          class="mt-4"
        />

        <p class="mt-3 flex items-start gap-1.5 text-2xs leading-5 text-text-muted">
          <ShieldCheck class="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
          联系方式在展示层统一脱敏；删除简历会移除其解析结果，不影响历史面试报告。
        </p>
      </Card>
    </section>

    <AsyncState
      :loading="listLoading"
      :error="listError"
      error-title="简历列表加载失败"
      :empty="!listLoading && !listError && list.length === 0"
      empty-title="还没有简历"
      empty-description="上传一份 PDF 或 DOCX 简历，或手动新建结构化简历，即可开始准备面试。"
      :empty-icon="FileText"
      :loading-lines="3"
      @retry="loadList"
    >
      <template #action>
        <div class="flex flex-wrap items-center justify-center gap-2">
          <Button @click="scrollToUpload">
            <Upload class="h-4 w-4" aria-hidden="true" />
            上传简历
          </Button>
          <Button variant="outline" @click="openManualCreate">
            <Pencil class="h-4 w-4" aria-hidden="true" />
            手动新建
          </Button>
        </div>
      </template>

      <div class="grid items-start gap-6 lg:grid-cols-[20rem_minmax(0,1fr)]">
        <aside class="scrollbar-thin lg:sticky lg:top-20 lg:max-h-[calc(100vh-6rem)] lg:overflow-y-auto">
          <ResumeListPanel
            :resumes="list"
            :selected-id="selectedId"
            @select="selectedId = $event"
            @delete="askDelete"
          />
        </aside>

        <section class="min-w-0">
          <AsyncState :loading="detailLoading" :error="detailError" error-title="简历详情加载失败" :loading-lines="6" @retry="retryDetail">
            <ResumeParseState v-if="detail && detail.status === 'PARSING'" :resume="detail" />
            <Card v-else-if="detail && detail.status === 'FAILED'" padding="lg">
              <Alert
                variant="danger"
                title="这份简历解析失败"
                description="可能是文件损坏或版式无法识别。可以重新解析，或手动编辑结构化内容。"
              />
              <div class="mt-4 flex flex-wrap justify-end gap-2">
                <Button variant="outline" @click="openEditor">
                  <Pencil class="h-4 w-4" aria-hidden="true" />
                  手动编辑
                </Button>
                <Button variant="outline" @click="reparse">
                  <RefreshCw class="h-4 w-4" aria-hidden="true" />
                  重新解析
                </Button>
                <Button variant="danger" @click="askDeleteSelected">
                  <Trash2 class="h-4 w-4" aria-hidden="true" />
                  删除
                </Button>
              </div>
            </Card>
            <template v-else-if="detail">
              <div class="mb-3 flex flex-wrap items-center justify-end gap-2">
                <Badge v-if="detail.isDefault" variant="brand" dot>
                  默认简历
                </Badge>
                <Button v-if="!detail.isDefault" variant="ghost" size="sm" @click="setDefault">
                  <Star class="h-3.5 w-3.5" aria-hidden="true" />
                  设为默认
                </Button>
                <Button variant="outline" size="sm" @click="reparse">
                  <RefreshCw class="h-3.5 w-3.5" aria-hidden="true" />
                  重新解析
                </Button>
              </div>
              <ResumeDetailPanel
                :resume="detail"
                :layout="layout"
                @edit="openEditor"
                @change-layout="layoutOpen = true"
              />
            </template>
            <EmptyState
              v-else
              :icon="FileText"
              title="选择一份简历查看解析结果"
              description="左侧列表展示全部简历，点击任意一份即可查看结构化信息与字段置信度。"
            />
          </AsyncState>
        </section>
      </div>
    </AsyncState>

    <Dialog
      v-model:open="deleteOpen"
      title="删除这份简历？"
      :description="
        deleteTarget
          ? `「${deleteTarget.title}」及其解析结果会被移除，历史面试报告不受影响。`
          : undefined
      "
      size="sm"
    >
      <Alert v-if="deleteError" variant="danger" title="暂时无法删除" :description="deleteError" />
      <p v-else class="text-sm leading-6 text-text-muted">
        删除后需要重新上传才能用于模拟面试，此操作不可撤销。
      </p>

      <template #footer>
        <Button variant="outline" @click="deleteOpen = false">取消</Button>
        <Button variant="danger" :loading="deleting" @click="confirmDelete">
          <Trash2 class="h-4 w-4" aria-hidden="true" />
          确认删除
        </Button>
      </template>
    </Dialog>

    <ResumeLayoutPicker
      v-if="detail && detail.status === 'READY'"
      :open="layoutOpen"
      :current="layout"
      @update:open="layoutOpen = $event"
      @select="handleLayoutSelect"
    />

    <ResumeEditor
      :open="editorOpen"
      :resume="editorResume"
      :layout="layout"
      @update:open="editorOpen = $event"
      @save="handleSave"
    />
  </div>
</template>