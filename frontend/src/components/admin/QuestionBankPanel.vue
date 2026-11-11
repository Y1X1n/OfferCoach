<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  Archive,
  Layers,
  Pencil,
  Plus,
  RefreshCw,
  RotateCcw,
  Search,
  Send,
  Upload,
  X,
} from 'lucide-vue-next'
import AsyncState from '@/components/common/AsyncState.vue'
import AdminPagination from '@/components/admin/AdminPagination.vue'
import Alert from '@/components/ui/Alert.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import Dialog from '@/components/ui/Dialog.vue'
import Input from '@/components/ui/Input.vue'
import Textarea from '@/components/ui/Textarea.vue'
import { errorMessage } from '@/api/client'
import * as adminApi from '@/api/admin'
import type {
  AdminImportBatch,
  AdminQuestion,
  AdminQuestionPayload,
  AdminQuestionStatus,
} from '@/api/admin'
import { useToast } from '@/composables/useToast'
import { formatDate, formatRelative } from '@/utils/format'
import {
  batchStatusMeta,
  difficultyLabel,
  dimensionLabel,
  questionStatusMeta,
  sourceLabel,
} from './meta'

const PAGE_SIZE = 20

const { toast } = useToast()

const selectClass =
  'h-11 w-full rounded-md border border-border bg-surface px-3 text-sm text-text transition-colors focus:border-accent/60 sm:h-9'

const questions = ref<AdminQuestion[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(true)
const loadError = ref<string | null>(null)

const keyword = ref('')
const statusFilter = ref('')
const dimensionFilter = ref('')
const difficultyFilter = ref('')
const jobFamilyFilter = ref('')

let loadSeq = 0

const hasFilters = computed(
  () =>
    keyword.value.trim() !== '' ||
    statusFilter.value !== '' ||
    dimensionFilter.value !== '' ||
    difficultyFilter.value !== '' ||
    jobFamilyFilter.value.trim() !== '',
)
const isEmpty = computed(
  () => !loading.value && !loadError.value && questions.value.length === 0,
)

async function load(): Promise<void> {
  const seq = ++loadSeq
  loading.value = true
  loadError.value = null
  try {
    const result = await adminApi.listQuestions({
      page: page.value,
      size: PAGE_SIZE,
      ...(keyword.value.trim() ? { keyword: keyword.value.trim() } : {}),
      ...(statusFilter.value ? { status: statusFilter.value as AdminQuestionStatus } : {}),
      ...(dimensionFilter.value ? { dimension: dimensionFilter.value } : {}),
      ...(difficultyFilter.value ? { difficulty: difficultyFilter.value } : {}),
      ...(jobFamilyFilter.value.trim() ? { jobFamily: jobFamilyFilter.value.trim() } : {}),
    })
    if (seq !== loadSeq) return
    questions.value = result.items
    total.value = result.total
  } catch (cause) {
    if (seq !== loadSeq) return
    loadError.value = errorMessage(cause, '题库加载失败')
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function applyFilters(): void {
  page.value = 1
  void load()
}

function resetFilters(): void {
  keyword.value = ''
  statusFilter.value = ''
  dimensionFilter.value = ''
  difficultyFilter.value = ''
  jobFamilyFilter.value = ''
  applyFilters()
}

function goPage(next: number): void {
  page.value = next
  void load()
}

// ---------------------------------------------------------------------------
// 新建 / 编辑
// ---------------------------------------------------------------------------

interface QuestionFormState {
  title: string
  stem: string
  jobFamily: string
  dimension: string
  difficulty: string
  tagsText: string
  source: string
  sourceRepo: string
  company: string
  referenceAnswer: string
}

interface FormFieldErrors {
  title?: string | undefined
  stem?: string | undefined
  jobFamily?: string | undefined
}

function emptyForm(): QuestionFormState {
  return {
    title: '',
    stem: '',
    jobFamily: '',
    dimension: 'DEPTH',
    difficulty: 'MEDIUM',
    tagsText: '',
    source: 'MANUAL',
    sourceRepo: '',
    company: '',
    referenceAnswer: '',
  }
}

const editorOpen = ref(false)
const editingId = ref('')
const form = ref<QuestionFormState>(emptyForm())
const fieldErrors = ref<FormFieldErrors>({})
const formError = ref('')
const saving = ref(false)

const editorTitle = computed(() => (editingId.value ? '编辑题目' : '新建题目'))

function openCreate(): void {
  editingId.value = ''
  form.value = emptyForm()
  fieldErrors.value = {}
  formError.value = ''
  editorOpen.value = true
}

function openEdit(question: AdminQuestion): void {
  editingId.value = question.id
  form.value = {
    title: question.title,
    stem: question.stem,
    jobFamily: question.jobFamily,
    dimension: question.dimension,
    difficulty: question.difficulty,
    tagsText: question.tags.join(', '),
    source: question.source,
    sourceRepo: question.sourceRepo,
    company: question.company,
    referenceAnswer: question.referenceAnswer,
  }
  fieldErrors.value = {}
  formError.value = ''
  editorOpen.value = true
}

function validateForm(): boolean {
  const errors: FormFieldErrors = {}
  if (!form.value.title.trim()) errors.title = '请输入题目标题'
  if (!form.value.stem.trim()) errors.stem = '请输入题干'
  if (!form.value.jobFamily.trim()) errors.jobFamily = '请输入岗位族'
  fieldErrors.value = errors
  return Object.keys(errors).length === 0
}

function buildPayload(): AdminQuestionPayload {
  const state = form.value
  return {
    title: state.title.trim(),
    stem: state.stem.trim(),
    jobFamily: state.jobFamily.trim(),
    dimension: state.dimension,
    difficulty: state.difficulty,
    tags: state.tagsText
      .split(/[,，]/)
      .map((tag) => tag.trim())
      .filter((tag) => tag.length > 0),
    ...(state.source ? { source: state.source } : {}),
    ...(state.sourceRepo.trim() ? { sourceRepo: state.sourceRepo.trim() } : {}),
    ...(state.company.trim() ? { company: state.company.trim() } : {}),
    ...(state.referenceAnswer.trim() ? { referenceAnswer: state.referenceAnswer.trim() } : {}),
  }
}

async function saveQuestion(): Promise<void> {
  if (!validateForm() || saving.value) return
  saving.value = true
  formError.value = ''
  try {
    const payload = buildPayload()
    if (editingId.value) {
      await adminApi.updateQuestion(editingId.value, payload)
      toast('题目已更新', 'success', '修改已保存；如已发布，服务端会自动升级版本。')
    } else {
      await adminApi.createQuestion(payload)
      toast('题目已创建', 'success', '可在列表中发布后进入出题题库。')
      page.value = 1
    }
    editorOpen.value = false
    await load()
  } catch (cause) {
    formError.value = errorMessage(cause, '保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

// ---------------------------------------------------------------------------
// 发布 / 归档
// ---------------------------------------------------------------------------

const actingId = ref('')
const archiveOpen = ref(false)
const archiveTarget = ref<AdminQuestion | null>(null)
const archiving = ref(false)

async function publishQuestion(question: AdminQuestion): Promise<void> {
  if (actingId.value) return
  actingId.value = question.id
  try {
    await adminApi.publishQuestion(question.id)
    toast('题目已发布', 'success', '已触发增量向量化，生效可能有短暂延迟。')
    await load()
  } catch (cause) {
    toast('发布失败', 'error', errorMessage(cause))
  } finally {
    actingId.value = ''
  }
}

function openArchive(question: AdminQuestion): void {
  archiveTarget.value = question
  archiveOpen.value = true
}

async function confirmArchive(): Promise<void> {
  const target = archiveTarget.value
  if (!target || archiving.value) return
  archiving.value = true
  try {
    await adminApi.archiveQuestion(target.id)
    toast('题目已归档', 'success', `《${target.title}》已从出题索引中移除。`)
    archiveOpen.value = false
    archiveTarget.value = null
    await load()
  } catch (cause) {
    toast('归档失败', 'error', errorMessage(cause))
  } finally {
    archiving.value = false
  }
}

// ---------------------------------------------------------------------------
// JSON 导入与批次
// ---------------------------------------------------------------------------

const importOpen = ref(false)
const importSourceRepo = ref('')
const importJson = ref('')
const importError = ref('')
const importing = ref(false)
const importCount = computed(() => {
  try {
    const parsed: unknown = JSON.parse(importJson.value)
    return Array.isArray(parsed) ? parsed.length : 0
  } catch {
    return 0
  }
})

function openImport(): void {
  importSourceRepo.value = ''
  importJson.value = ''
  importError.value = ''
  importOpen.value = true
}

async function submitImport(): Promise<void> {
  if (importing.value) return
  importError.value = ''
  const repo = importSourceRepo.value.trim()
  if (!repo) {
    importError.value = '请填写来源仓库，例如 owner/repo'
    return
  }

  let parsed: unknown
  try {
    parsed = JSON.parse(importJson.value)
  } catch {
    importError.value = 'JSON 解析失败，请检查引号、逗号与括号是否完整。'
    return
  }
  if (!Array.isArray(parsed) || parsed.length === 0) {
    importError.value = '请输入非空的 JSON 数组，每个元素为一题目对象。'
    return
  }
  const items: Record<string, unknown>[] = []
  for (const item of parsed) {
    if (item === null || typeof item !== 'object' || Array.isArray(item)) {
      importError.value = '数组每一项都必须是 JSON 对象。'
      return
    }
    items.push(item as Record<string, unknown>)
  }

  importing.value = true
  try {
    const result = await adminApi.importQuestions({ sourceRepo: repo, items })
    toast(
      '导入已提交',
      'success',
      `批次 ${result.batchId || '—'}：成功 ${result.success} / 失败 ${result.failed} / 跳过 ${result.skipped}`,
    )
    importOpen.value = false
    page.value = 1
    await load()
    if (batchesOpen.value) await loadBatches()
  } catch (cause) {
    importError.value = errorMessage(cause, '导入失败，请稍后重试')
  } finally {
    importing.value = false
  }
}

const batchesOpen = ref(false)
const batches = ref<AdminImportBatch[]>([])
const batchesLoading = ref(false)
const batchesError = ref<string | null>(null)

async function loadBatches(): Promise<void> {
  batchesLoading.value = true
  batchesError.value = null
  try {
    batches.value = await adminApi.listImportBatches()
  } catch (cause) {
    batchesError.value = errorMessage(cause, '导入批次加载失败')
  } finally {
    batchesLoading.value = false
  }
}

function openBatches(): void {
  batchesOpen.value = true
  void loadBatches()
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="min-w-0">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <div>
        <h2 class="text-base font-semibold text-text">题库管理</h2>
        <p class="mt-1 text-xs text-text-muted">
          发布后进入出题索引；归档仅从索引移除，不删除历史引用。
        </p>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <Button variant="outline" size="sm" @click="openBatches">
          <Layers class="h-3.5 w-3.5" aria-hidden="true" />
          导入批次
        </Button>
        <Button variant="outline" size="sm" @click="openImport">
          <Upload class="h-3.5 w-3.5" aria-hidden="true" />
          JSON 导入
        </Button>
        <Button size="sm" @click="openCreate">
          <Plus class="h-3.5 w-3.5" aria-hidden="true" />
          新建题目
        </Button>
      </div>
    </div>

    <Card padding="sm" class="mt-3">
      <form
        class="grid gap-3 sm:grid-cols-2 lg:grid-cols-6"
        role="search"
        aria-label="筛选题库"
        @submit.prevent="applyFilters"
      >
        <div class="relative sm:col-span-2">
          <label for="question-keyword" class="sr-only">搜索题目或题干</label>
          <Search
            class="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted"
            aria-hidden="true"
          />
          <input
            id="question-keyword"
            v-model="keyword"
            type="search"
            placeholder="搜索标题、题干或公司"
            class="h-11 w-full rounded-md border border-border bg-surface pl-9 pr-3 text-sm text-text transition-colors placeholder:text-muted focus:border-accent/60 sm:h-9"
          />
        </div>

        <div>
          <label for="question-job-family" class="sr-only">岗位族</label>
          <input
            id="question-job-family"
            v-model="jobFamilyFilter"
            type="text"
            placeholder="岗位族，如 后端"
            class="h-11 w-full rounded-md border border-border bg-surface px-3 text-sm text-text transition-colors placeholder:text-muted focus:border-accent/60 sm:h-9"
          />
        </div>

        <div>
          <label for="question-dimension" class="sr-only">维度</label>
          <select id="question-dimension" v-model="dimensionFilter" :class="selectClass">
            <option value="">全部维度</option>
            <option value="DEPTH">专业深度</option>
            <option value="PROJECT">项目经历</option>
            <option value="BEHAVIOR">行为面试</option>
            <option value="FIT">岗位匹配</option>
          </select>
        </div>

        <div>
          <label for="question-difficulty" class="sr-only">难度</label>
          <select id="question-difficulty" v-model="difficultyFilter" :class="selectClass">
            <option value="">全部难度</option>
            <option value="EASY">简单</option>
            <option value="MEDIUM">中等</option>
            <option value="HARD">困难</option>
          </select>
        </div>

        <div>
          <label for="question-status" class="sr-only">状态</label>
          <select id="question-status" v-model="statusFilter" :class="selectClass">
            <option value="">全部状态</option>
            <option value="DRAFT">草稿</option>
            <option value="PUBLISHED">已发布</option>
            <option value="ARCHIVED">已归档</option>
          </select>
        </div>

        <div class="flex flex-wrap items-center gap-2 sm:col-span-2 lg:col-span-6">
          <Button type="submit" size="sm">
            <Search class="h-3.5 w-3.5" aria-hidden="true" />
            查询
          </Button>
          <Button type="button" variant="outline" size="sm" @click="resetFilters">
            <RotateCcw class="h-3.5 w-3.5" aria-hidden="true" />
            重置
          </Button>
          <Button
            v-if="hasFilters"
            type="button"
            variant="ghost"
            size="sm"
            @click="resetFilters"
          >
            <X class="h-3.5 w-3.5" aria-hidden="true" />
            清除筛选
          </Button>
          <Button variant="ghost" size="sm" :loading="loading" @click="load">
            <RefreshCw v-if="!loading" class="h-3.5 w-3.5" aria-hidden="true" />
            刷新
          </Button>
        </div>
      </form>
    </Card>

    <AsyncState
      class="mt-3"
      :loading="loading"
      :error="loadError"
      error-title="题库加载失败"
      :empty="isEmpty"
      empty-title="没有匹配的题目"
      :empty-description="
        hasFilters ? '调整筛选条件或清除筛选后重试。' : '创建第一道题目，或从题库仓库导入 JSON。'
      "
      :empty-icon="Search"
      :loading-lines="5"
      @retry="load"
    >
      <template #action>
        <div class="flex flex-wrap justify-center gap-2">
          <Button v-if="hasFilters" variant="outline" size="sm" @click="resetFilters">
            清除筛选
          </Button>
          <Button size="sm" @click="openCreate">
            <Plus class="h-3.5 w-3.5" aria-hidden="true" />
            新建题目
          </Button>
        </div>
      </template>

      <div class="overflow-x-auto rounded-lg border border-border">
        <table class="w-full min-w-[960px] border-collapse text-sm">
          <thead class="bg-surface-2 text-left text-xs text-text-muted">
            <tr>
              <th scope="col" class="px-3 py-2.5 font-medium">题目</th>
              <th scope="col" class="px-3 py-2.5 font-medium">岗位族</th>
              <th scope="col" class="px-3 py-2.5 font-medium">维度 / 难度</th>
              <th scope="col" class="px-3 py-2.5 font-medium">状态</th>
              <th scope="col" class="px-3 py-2.5 font-medium">来源</th>
              <th scope="col" class="px-3 py-2.5 font-medium">创建时间</th>
              <th scope="col" class="px-3 py-2.5 text-right font-medium">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-border bg-surface">
            <tr v-for="question in questions" :key="question.id" class="align-top">
              <td class="max-w-72 px-3 py-3">
                <p class="truncate font-medium text-text" :title="question.title">
                  {{ question.title || '（未命名）' }}
                </p>
                <p class="mt-0.5 line-clamp-2 text-xs leading-5 text-text-muted">
                  {{ question.stem || '暂无题干' }}
                </p>
                <div v-if="question.tags.length" class="mt-1.5 flex flex-wrap gap-1">
                  <Badge v-for="tag in question.tags.slice(0, 3)" :key="tag" variant="neutral">
                    {{ tag }}
                  </Badge>
                  <span v-if="question.tags.length > 3" class="text-2xs text-text-muted">
                    +{{ question.tags.length - 3 }}
                  </span>
                </div>
              </td>
              <td class="px-3 py-3 text-text-muted">{{ question.jobFamily || '—' }}</td>
              <td class="px-3 py-3">
                <p class="text-text">{{ dimensionLabel(question.dimension) }}</p>
                <p class="mt-0.5 text-xs text-text-muted">{{ difficultyLabel(question.difficulty) }}</p>
              </td>
              <td class="px-3 py-3">
                <Badge :variant="questionStatusMeta(question.status).variant" dot>
                  {{ questionStatusMeta(question.status).label }}
                </Badge>
              </td>
              <td class="max-w-48 px-3 py-3 text-xs text-text-muted">
                <p class="truncate" :title="question.source">{{ sourceLabel(question.source) }}</p>
                <p v-if="question.sourceRepo" class="truncate" :title="question.sourceRepo">
                  {{ question.sourceRepo }}
                </p>
                <p v-if="question.company" class="truncate" :title="question.company">
                  {{ question.company }}
                </p>
              </td>
              <td class="whitespace-nowrap px-3 py-3 text-xs text-text-muted">
                <span :title="formatDate(question.createdAt, true)">
                  {{ question.createdAt ? formatRelative(question.createdAt) : '—' }}
                </span>
              </td>
              <td class="px-3 py-3">
                <div class="flex flex-wrap justify-end gap-1.5">
                  <Button
                    variant="outline"
                    size="sm"
                    :disabled="actingId !== '' && actingId !== question.id"
                    @click="openEdit(question)"
                  >
                    <Pencil class="h-3.5 w-3.5" aria-hidden="true" />
                    编辑
                  </Button>
                  <Button
                    v-if="question.status !== 'PUBLISHED'"
                    variant="secondary"
                    size="sm"
                    :loading="actingId === question.id"
                    @click="publishQuestion(question)"
                  >
                    <Send v-if="actingId !== question.id" class="h-3.5 w-3.5" aria-hidden="true" />
                    发布
                  </Button>
                  <Button
                    v-if="question.status !== 'ARCHIVED'"
                    variant="ghost"
                    size="sm"
                    class="text-danger-strong"
                    :disabled="actingId !== ''"
                    @click="openArchive(question)"
                  >
                    <Archive class="h-3.5 w-3.5" aria-hidden="true" />
                    归档
                  </Button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <AdminPagination
        v-if="total > 0"
        class="mt-3"
        :page="page"
        :total="total"
        :size="PAGE_SIZE"
        :disabled="loading"
        @change="goPage"
      />
    </AsyncState>

    <Dialog v-model:open="editorOpen" :title="editorTitle" size="lg">
      <form id="question-editor-form" class="space-y-4" novalidate @submit.prevent="saveQuestion">
        <Alert v-if="formError" variant="danger" title="保存失败" :description="formError" />
        <Input
          v-model="form.title"
          label="标题"
          required
          placeholder="如：缓存与数据库一致性"
          :error="fieldErrors.title"
        />
        <Textarea
          v-model="form.stem"
          label="题干"
          required
          :rows="5"
          placeholder="描述完整题目与考察点"
          :error="fieldErrors.stem"
        />
        <div class="grid gap-4 sm:grid-cols-2">
          <Input
            v-model="form.jobFamily"
            label="岗位族"
            required
            placeholder="如：后端 / BACKEND"
            :error="fieldErrors.jobFamily"
          />
          <Input v-model="form.company" label="公司" placeholder="可选" />
        </div>
        <div class="grid gap-4 sm:grid-cols-2">
          <div class="flex flex-col gap-1.5">
            <label for="editor-dimension" class="text-sm font-medium text-text">维度</label>
            <select id="editor-dimension" v-model="form.dimension" :class="selectClass">
              <option value="DEPTH">专业深度</option>
              <option value="PROJECT">项目经历</option>
              <option value="BEHAVIOR">行为面试</option>
              <option value="FIT">岗位匹配</option>
            </select>
          </div>
          <div class="flex flex-col gap-1.5">
            <label for="editor-difficulty" class="text-sm font-medium text-text">难度</label>
            <select id="editor-difficulty" v-model="form.difficulty" :class="selectClass">
              <option value="EASY">简单</option>
              <option value="MEDIUM">中等</option>
              <option value="HARD">困难</option>
            </select>
          </div>
        </div>
        <div class="grid gap-4 sm:grid-cols-2">
          <div class="flex flex-col gap-1.5">
            <label for="editor-source" class="text-sm font-medium text-text">来源</label>
            <select id="editor-source" v-model="form.source" :class="selectClass">
              <option value="MANUAL">手动创建</option>
              <option value="IMPORT">仓库导入</option>
              <option value="GENERATED">AI 生成</option>
              <option value="PARTNER">合作方</option>
            </select>
          </div>
          <Input v-model="form.sourceRepo" label="来源仓库" placeholder="owner/repo（可选）" />
        </div>
        <Input
          v-model="form.tagsText"
          label="标签"
          placeholder="用逗号分隔，如：Redis, 缓存, 一致性"
          hint="会用于检索与筛选；建议 1–5 个。"
        />
        <Textarea
          v-model="form.referenceAnswer"
          label="参考答案"
          :rows="4"
          placeholder="可选；仅管理端可见，不展示给候选人。"
        />
      </form>

      <template #footer>
        <Button variant="outline" @click="editorOpen = false">取消</Button>
        <Button type="submit" form="question-editor-form" :loading="saving">
          {{ editingId ? '保存修改' : '创建题目' }}
        </Button>
      </template>
    </Dialog>

    <Dialog
      :open="archiveOpen"
      title="归档题目"
      description="归档后题目会从出题索引移除，历史面试与报告不受影响。"
      size="sm"
      @update:open="archiveOpen = $event"
    >
      <Alert
        v-if="archiveTarget"
        variant="warning"
        title="确认归档"
        :description="`《${archiveTarget.title}》归档后可再次编辑并重新发布。`"
      />
      <template #footer>
        <Button variant="outline" @click="archiveOpen = false">取消</Button>
        <Button variant="danger" :loading="archiving" @click="confirmArchive">确认归档</Button>
      </template>
    </Dialog>

    <Dialog v-model:open="importOpen" title="JSON 导入题库" size="lg">
      <form id="question-import-form" class="space-y-4" novalidate @submit.prevent="submitImport">
        <Alert v-if="importError" variant="danger" title="导入未提交" :description="importError" />
        <Input
          v-model="importSourceRepo"
          label="来源仓库"
          required
          placeholder="如：didilili/mianstack"
          hint="同一仓库 + 路径按幂等处理，重复导入会跳过。"
        />
        <Textarea
          v-model="importJson"
          label="题目 JSON 数组"
          required
          :rows="10"
          placeholder='[{"title":"...","stem":"...","jobFamily":"BACKEND","dimension":"DEPTH","difficulty":"MEDIUM","tags":["Redis"]}]'
          :hint="`当前可解析 ${importCount} 条；提交后由服务端逐条校验并返回批次结果。`"
        />
      </form>

      <template #footer>
        <Button variant="outline" @click="importOpen = false">取消</Button>
        <Button type="submit" form="question-import-form" :loading="importing">
          提交导入
        </Button>
      </template>
    </Dialog>

    <Dialog v-model:open="batchesOpen" title="导入批次" size="lg">
      <AsyncState
        :loading="batchesLoading"
        :error="batchesError"
        error-title="导入批次加载失败"
        :empty="!batchesLoading && !batchesError && batches.length === 0"
        empty-title="暂无导入批次"
        empty-description="通过 JSON 导入题目后，这里会显示批次进度与结果。"
        :empty-icon="Layers"
        :loading-lines="4"
        @retry="loadBatches"
      >
        <div class="overflow-x-auto rounded-lg border border-border">
          <table class="w-full min-w-[640px] border-collapse text-sm">
            <thead class="bg-surface-2 text-left text-xs text-text-muted">
              <tr>
                <th scope="col" class="px-3 py-2.5 font-medium">批次</th>
                <th scope="col" class="px-3 py-2.5 font-medium">来源仓库</th>
                <th scope="col" class="px-3 py-2.5 font-medium">状态</th>
                <th scope="col" class="px-3 py-2.5 text-right font-medium">成功 / 失败 / 跳过</th>
                <th scope="col" class="px-3 py-2.5 font-medium">创建时间</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-border bg-surface">
              <tr v-for="batch in batches" :key="batch.id">
                <td class="whitespace-nowrap px-3 py-2.5 text-text-muted">#{{ batch.id || '—' }}</td>
                <td class="max-w-56 truncate px-3 py-2.5 text-text" :title="batch.sourceRepo">
                  {{ batch.sourceRepo || '—' }}
                </td>
                <td class="px-3 py-2.5">
                  <Badge :variant="batchStatusMeta(batch.status).variant" dot>
                    {{ batchStatusMeta(batch.status).label }}
                  </Badge>
                </td>
                <td class="tabular whitespace-nowrap px-3 py-2.5 text-right text-text-muted">
                  {{ batch.success }} / {{ batch.failed }} / {{ batch.skipped }}
                  <span class="ml-1 text-2xs">（共 {{ batch.total }}）</span>
                </td>
                <td class="whitespace-nowrap px-3 py-2.5 text-xs text-text-muted">
                  <span :title="formatDate(batch.createdAt, true)">
                    {{ batch.createdAt ? formatRelative(batch.createdAt) : '—' }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </AsyncState>

      <template #footer>
        <Button variant="outline" @click="batchesOpen = false">关闭</Button>
        <Button :loading="batchesLoading" @click="loadBatches">
          <RefreshCw v-if="!batchesLoading" class="h-4 w-4" aria-hidden="true" />
          刷新
        </Button>
      </template>
    </Dialog>
  </div>
</template>