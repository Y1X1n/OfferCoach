<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { BriefcaseBusiness, ClipboardPaste, Target, Trash2, Wand2 } from 'lucide-vue-next'
import type { Jd, JdMatch, ResumeSummary } from '@/types'
import PageHeader from '@/components/common/PageHeader.vue'
import AsyncState from '@/components/common/AsyncState.vue'
import JdFormPanel from '@/components/jd/JdFormPanel.vue'
import JdListPanel from '@/components/jd/JdListPanel.vue'
import MatchPanel from '@/components/jd/MatchPanel.vue'
import Alert from '@/components/ui/Alert.vue'
import Badge from '@/components/ui/Badge.vue'
import Button from '@/components/ui/Button.vue'
import Card from '@/components/ui/Card.vue'
import Dialog from '@/components/ui/Dialog.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import Spinner from '@/components/ui/Spinner.vue'
import { ApiError, errorMessage } from '@/api/client'
import * as jdsApi from '@/api/jds'
import * as resumesApi from '@/api/resumes'
import { useToast } from '@/composables/useToast'
import { formatScore } from '@/utils/format'

const router = useRouter()
const { toast } = useToast()

const formSection = ref<HTMLElement | null>(null)
const list = ref<Jd[]>([])
const listLoading = ref(true)
const listError = ref<string | null>(null)
const selectedId = ref('')
const detail = ref<Jd | null>(null)
const detailLoading = ref(false)
const detailError = ref<string | null>(null)

const resumes = ref<ResumeSummary[]>([])
const selectedResumeId = ref('')
const matches = reactive<Record<string, JdMatch>>({})

const submitting = ref(false)
const submitError = ref('')
const matching = ref(false)
const matchError = ref('')

const deleteOpen = ref(false)
const deleteTarget = ref<Jd | null>(null)
const deleteError = ref('')
const deleting = ref(false)

const selectedJd = computed<Jd | null>(() => detail.value)
const selectedMatch = computed<JdMatch | null>(() =>
  selectedId.value ? (matches[selectedId.value] ?? null) : null,
)
const readyResumes = computed(() => resumes.value.filter((resume) => resume.status === 'READY'))
const analyzedCount = computed(() => Object.keys(matches).length)

async function loadList(): Promise<void> {
  listLoading.value = true
  listError.value = null
  try {
    const page = await jdsApi.listJds({ page: 1, size: 50 })
    list.value = page.items
    if (!selectedId.value || !page.items.some((item) => item.id === selectedId.value)) {
      selectedId.value = page.items[0]?.id ?? ''
      if (selectedId.value) void loadDetail(selectedId.value)
    }
  } catch (cause) {
    listError.value = errorMessage(cause, '岗位列表加载失败')
  } finally {
    listLoading.value = false
  }
}

async function loadDetail(id: string): Promise<void> {
  detailLoading.value = true
  detailError.value = null
  try {
    const jd = await jdsApi.getJd(id)
    if (selectedId.value !== id) return
    detail.value = jd
  } catch (cause) {
    if (selectedId.value === id) detailError.value = errorMessage(cause, '岗位详情加载失败')
  } finally {
    if (selectedId.value === id) detailLoading.value = false
  }
}

async function loadResumes(): Promise<void> {
  try {
    const page = await resumesApi.listResumes({ page: 1, size: 50 })
    resumes.value = page.items
    const preferred =
      page.items.find((resume) => resume.isDefault && resume.status === 'READY') ??
      page.items.find((resume) => resume.status === 'READY')
    if (preferred && !selectedResumeId.value) selectedResumeId.value = preferred.id
  } catch {
    resumes.value = []
  }
}

watch(selectedId, (id) => {
  if (id) void loadDetail(id)
  else detail.value = null
})

function scrollToForm(): void {
  formSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function handleSubmit(payload: {
  title: string
  company: string
  rawText: string
}): Promise<void> {
  submitting.value = true
  submitError.value = ''
  try {
    const jd = await jdsApi.createJd(payload)
    selectedId.value = jd.id
    detail.value = jd
    await loadList()
    selectedId.value = jd.id
    detail.value = jd
    toast('岗位已保存', 'success', jd.title)
    if (selectedResumeId.value) {
      await runMatch(jd)
    }
  } catch (cause) {
    if (cause instanceof ApiError && cause.code === 'STATE_CONFLICT') {
      const existing = typeof cause.details.existingJdId === 'string' ? cause.details.existingJdId : ''
      if (existing) {
        submitError.value = ''
        await loadList()
        selectedId.value = existing
        toast('该 JD 已存在', 'info', '已为你打开已有岗位，不会重复保存')
        return
      }
    }
    submitError.value = errorMessage(cause, '创建岗位失败，请稍后重试')
    toast('创建岗位失败', 'error', submitError.value)
  } finally {
    submitting.value = false
  }
}

async function runMatch(jd: Jd | null = selectedJd.value): Promise<void> {
  if (!jd || matching.value) return
  if (!selectedResumeId.value) {
    toast('请先选择用于匹配的简历', 'warning')
    return
  }
  matching.value = true
  matchError.value = ''
  try {
    const match = await jdsApi.matchJd(jd.id, selectedResumeId.value)
    matches[jd.id] = match
    toast('匹配分析完成', 'success', `${jd.title} · 匹配度 ${formatScore(match.score)}`)
  } catch (cause) {
    matchError.value = errorMessage(cause, '匹配分析失败，请稍后重试')
    toast('匹配分析失败', 'error', matchError.value)
  } finally {
    matching.value = false
  }
}

function startInterview(jdId: string): void {
  const query: Record<string, string> = { jdId }
  if (selectedResumeId.value) query.resumeId = selectedResumeId.value
  void router.push({ name: 'interview-setup', query })
}

function askDelete(jd: Jd): void {
  deleteTarget.value = jd
  deleteError.value = ''
  deleteOpen.value = true
}

async function confirmDelete(): Promise<void> {
  const target = deleteTarget.value
  if (!target || deleting.value) return
  deleting.value = true
  try {
    await jdsApi.deleteJd(target.id)
    deleteOpen.value = false
    deleteTarget.value = null
    deleteError.value = ''
    if (selectedId.value === target.id) {
      selectedId.value = ''
      detail.value = null
    }
    await loadList()
    toast('已删除岗位', 'success', target.title)
  } catch (cause) {
    deleteError.value = errorMessage(cause, '删除失败，请稍后重试')
    toast('删除失败', 'error', deleteError.value)
  } finally {
    deleting.value = false
  }
}

function retryDetail(): void {
  if (selectedId.value) void loadDetail(selectedId.value)
}

onMounted(() => {
  void loadList()
  void loadResumes()
})
</script>

<template>
  <div class="space-y-6">
    <PageHeader title="目标岗位" description="粘贴 JD，生成岗位针对性题目与匹配分析。">
      <template #actions>
        <Badge variant="neutral" class="hidden sm:inline-flex">
          已分析 {{ analyzedCount }} / {{ list.length }}
        </Badge>
        <Button variant="outline" @click="scrollToForm">
          <ClipboardPaste class="h-4 w-4" aria-hidden="true" />
          粘贴新 JD
        </Button>
      </template>
    </PageHeader>

    <section ref="formSection" class="scroll-mt-20">
      <JdFormPanel :submitting="submitting" :error-message="submitError" @submit="handleSubmit" />
    </section>

    <Card padding="md" class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div class="min-w-0">
        <h2 class="text-sm font-semibold text-text">用于匹配的简历</h2>
        <p class="mt-0.5 text-xs text-text-muted">
          选择一份已解析简历，用于计算匹配度、技能缺口与证据。
        </p>
      </div>
      <div class="flex flex-col gap-2 sm:flex-row sm:items-center">
        <label for="jd-match-resume" class="sr-only">选择用于匹配的简历</label>
        <select
          id="jd-match-resume"
          v-model="selectedResumeId"
          class="h-10 w-full rounded-md border border-border bg-surface px-3 text-sm text-text transition-colors focus:border-accent/60 sm:w-64"
          :disabled="readyResumes.length === 0 || matching"
        >
          <option value="">{{ readyResumes.length === 0 ? '暂无已解析简历' : '选择简历' }}</option>
          <option v-for="resume in readyResumes" :key="resume.id" :value="resume.id">
            {{ resume.title }}
          </option>
        </select>
        <Button
          variant="secondary"
          :disabled="!selectedJd || !selectedResumeId"
          :loading="matching"
          @click="runMatch()"
        >
          <Wand2 class="h-4 w-4" aria-hidden="true" />
          运行匹配
        </Button>
      </div>
    </Card>

    <AsyncState
      :loading="listLoading"
      :error="listError"
      error-title="岗位列表加载失败"
      :empty="!listLoading && !listError && list.length === 0"
      empty-title="还没有目标岗位"
      empty-description="把招聘网站上的岗位职责与任职要求粘贴到上方，我们会提取技能要求并与简历比对。"
      :empty-icon="Target"
      :loading-lines="3"
      @retry="loadList"
    >
      <template #action>
        <Button variant="outline" @click="scrollToForm">去粘贴 JD</Button>
      </template>

      <div class="grid items-start gap-6 lg:grid-cols-[20rem_minmax(0,1fr)]">
        <aside class="scrollbar-thin lg:sticky lg:top-20 lg:max-h-[calc(100vh-6rem)] lg:overflow-y-auto">
          <JdListPanel
            :jds="list"
            :selected-id="selectedId"
            @select="selectedId = $event"
            @delete="askDelete"
          />
        </aside>

        <section class="min-w-0">
          <AsyncState
            :loading="detailLoading"
            :error="detailError"
            error-title="岗位详情加载失败"
            :loading-lines="6"
            @retry="retryDetail"
          >
            <Card v-if="matching" padding="lg">
              <div class="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p class="text-sm font-semibold text-text">正在分析岗位匹配</p>
                  <p class="mt-1 text-xs leading-5 text-text-muted">
                    正在抽取技能关键词并与已解析简历比对，通常需要几秒钟。
                  </p>
                </div>
                <Spinner size="sm" label="分析中" />
              </div>
            </Card>

            <template v-else-if="selectedJd && selectedMatch">
              <Alert v-if="matchError" variant="danger" title="匹配分析失败" :description="matchError" class="mb-4" />
              <MatchPanel :jd="selectedJd" :match="selectedMatch" @start="startInterview(selectedId)" />
            </template>

            <Card v-else-if="selectedJd" padding="lg">
              <Alert v-if="matchError" variant="danger" title="匹配分析失败" :description="matchError" class="mb-4" />
              <div class="flex items-start gap-3">
                <span
                  class="flex h-10 w-10 shrink-0 items-center justify-center rounded-md bg-surface-2 text-text-muted"
                >
                  <BriefcaseBusiness class="h-5 w-5" aria-hidden="true" />
                </span>
                <div class="min-w-0">
                  <h2 class="text-base font-semibold text-text">{{ selectedJd.title }}</h2>
                  <p class="mt-0.5 text-xs text-text-muted">{{ selectedJd.company }}</p>
                </div>
              </div>

              <div class="mt-4 rounded-md border border-border bg-surface-2/40 p-4">
                <p class="text-sm font-medium text-text">这个岗位还没有匹配分析</p>
                <p class="mt-1 text-xs leading-5 text-text-muted">
                  选择一份已解析简历后运行匹配，查看匹配度、已匹配技能、缺口与证据。
                </p>
                <div class="mt-3 flex flex-wrap items-center gap-2">
                  <Button
                    v-if="readyResumes.length > 0"
                    :loading="matching"
                    :disabled="!selectedResumeId"
                    @click="runMatch(selectedJd)"
                  >
                    <Wand2 class="h-4 w-4" aria-hidden="true" />
                    运行匹配
                  </Button>
                  <RouterLink
                    v-else
                    to="/resumes"
                    class="inline-flex h-10 items-center rounded-md border border-border bg-surface px-4 text-sm font-medium text-text hover:bg-surface-2"
                  >
                    先上传一份简历
                  </RouterLink>
                  <Button variant="outline" @click="startInterview(selectedJd.id)">
                    跳过匹配，直接开始面试
                  </Button>
                </div>
              </div>

              <div v-if="selectedJd.structured.mustHave.length" class="mt-4">
                <p class="text-xs font-medium text-text-muted">必备技能</p>
                <div class="mt-2 flex flex-wrap gap-1.5">
                  <Badge v-for="skill in selectedJd.structured.mustHave" :key="skill">
                    {{ skill }}
                  </Badge>
                </div>
              </div>
            </Card>

            <EmptyState
              v-else
              :icon="Target"
              title="选择一个岗位查看详情"
              description="从左侧列表选择岗位，查看匹配度、技能缺口与证据。"
            >
              <template #action>
                <Button variant="outline" @click="scrollToForm">粘贴并分析 JD</Button>
              </template>
            </EmptyState>
          </AsyncState>
        </section>
      </div>
    </AsyncState>

    <Dialog
      v-model:open="deleteOpen"
      title="删除这个岗位？"
      :description="
        deleteTarget
          ? `「${deleteTarget.title}」的 JD 原文与匹配分析会被移除，历史面试报告不受影响。`
          : undefined
      "
      size="sm"
    >
      <Alert v-if="deleteError" variant="danger" title="暂时无法删除" :description="deleteError" />
      <p v-else class="text-sm leading-6 text-text-muted">
        删除后需要重新粘贴 JD 才能再次分析，此操作不可撤销。
      </p>

      <template #footer>
        <Button variant="outline" @click="deleteOpen = false">取消</Button>
        <Button variant="danger" :loading="deleting" @click="confirmDelete">
          <Trash2 class="h-4 w-4" aria-hidden="true" />
          确认删除
        </Button>
      </template>
    </Dialog>
  </div>
</template>