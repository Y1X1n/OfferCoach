import type { Page, PageQuery } from '@/types'
import { request, type QueryValue } from './client'
import { toPage } from './normalize'

/** M5 管理端 API；响应字段做容错映射，后端缺省字段不阻塞页面。 */

// ---------------------------------------------------------------------------
// 通用解析
// ---------------------------------------------------------------------------

function asRecord(value: unknown): Record<string, unknown> {
  if (value !== null && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>
  }
  return {}
}

function asString(value: unknown, fallback = ''): string {
  return typeof value === 'string' && value.trim() ? value : fallback
}

function idOf(value: unknown): string {
  if (typeof value === 'string') return value
  if (typeof value === 'number' && Number.isFinite(value)) return String(value)
  return ''
}

function asNumber(value: unknown, fallback = 0): number {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    if (Number.isFinite(parsed)) return parsed
  }
  return fallback
}

function asNullableNumber(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    if (Number.isFinite(parsed)) return parsed
  }
  return null
}

function asStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return []
  return value.filter((item): item is string => typeof item === 'string')
}

function asBoolean(value: unknown, fallback = false): boolean {
  if (typeof value === 'boolean') return value
  if (value === 1 || value === '1' || value === 'true') return true
  if (value === 0 || value === '0' || value === 'false') return false
  return fallback
}

function asNullableString(value: unknown): string | null {
  return typeof value === 'string' && value ? value : null
}

function extractList(raw: unknown, fallbackKeys: string[] = []): unknown[] {
  if (Array.isArray(raw)) return raw
  const dto = asRecord(raw)
  if (Array.isArray(dto.items)) return dto.items
  for (const key of fallbackKeys) {
    const value = dto[key]
    if (Array.isArray(value)) return value
  }
  return []
}

function mapPage<T>(raw: unknown, map: (item: unknown) => T): Page<T> {
  const page = toPage<unknown>(raw)
  return { ...page, items: page.items.map(map) }
}

function pageQuery(query: PageQuery): Record<string, QueryValue> {
  return { page: query.page ?? 1, size: query.size ?? 20 }
}

// ---------------------------------------------------------------------------
// 指标概览
// ---------------------------------------------------------------------------

export type AdminRange = '1d' | '7d' | '30d'

export interface AdminOverview {
  users: number
  newUsers: number
  sessions: number
  completedSessions: number
  reports: number
  avgScore: number | null
  aiCalls: number
  aiErrors: number
  activeToday: number
}

export async function getOverview(range: AdminRange): Promise<AdminOverview> {
  const raw = await request<unknown>('/admin/metrics/overview', { query: { range } })
  const dto = asRecord(raw)
  return {
    users: asNumber(dto.users, asNumber(dto.totalUsers)),
    newUsers: asNumber(dto.newUsers),
    sessions: asNumber(dto.sessions, asNumber(dto.activeSessions)),
    completedSessions: asNumber(dto.completedSessions),
    reports: asNumber(dto.reports),
    avgScore: asNullableNumber(dto.avgScore ?? dto.averageScore),
    aiCalls: asNumber(dto.aiCalls),
    aiErrors: asNumber(dto.aiErrors, asNumber(dto.aiFailures)),
    activeToday: asNumber(
      dto.activeToday,
      asNumber(dto.activeUsersToday, asNumber(dto.activeUsers)),
    ),
  }
}

// ---------------------------------------------------------------------------
// 题库
// ---------------------------------------------------------------------------

export type AdminQuestionStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type AdminImportBatchStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'

export interface AdminQuestion {
  id: string
  title: string
  stem: string
  jobFamily: string
  dimension: string
  difficulty: string
  status: AdminQuestionStatus
  source: string
  sourceRepo: string
  company: string
  tags: string[]
  createdAt: string
  referenceAnswer: string
}

export interface AdminQuestionQuery extends PageQuery {
  status?: AdminQuestionStatus | undefined
  jobFamily?: string | undefined
  dimension?: string | undefined
  difficulty?: string | undefined
  keyword?: string | undefined
}

export interface AdminQuestionPayload {
  title: string
  stem: string
  jobFamily: string
  dimension: string
  difficulty: string
  tags: string[]
  source?: string | undefined
  sourceRepo?: string | undefined
  company?: string | undefined
  referenceAnswer?: string | undefined
}

function questionStatusOf(value: unknown): AdminQuestionStatus {
  if (value === 'PUBLISHED' || value === 'ARCHIVED') return value
  return 'DRAFT'
}

function mapQuestion(raw: unknown): AdminQuestion {
  const dto = asRecord(raw)
  return {
    id: idOf(dto.id),
    title: asString(dto.title),
    stem: asString(dto.stem),
    jobFamily: asString(dto.jobFamily, asString(dto.jobFamilyCode)),
    dimension: asString(dto.dimension, 'DEPTH'),
    difficulty: asString(dto.difficulty, 'MEDIUM'),
    status: questionStatusOf(dto.status),
    source: asString(dto.source, 'MANUAL'),
    sourceRepo: asString(dto.sourceRepo),
    company: asString(dto.company),
    tags: asStringArray(dto.tags),
    createdAt: asString(dto.createdAt, asString(dto.updatedAt)),
    referenceAnswer: asString(dto.referenceAnswer),
  }
}

export function listQuestions(query: AdminQuestionQuery = {}): Promise<Page<AdminQuestion>> {
  const path = '/admin/questions'
  const params: Record<string, QueryValue> = {
    ...pageQuery(query),
    status: query.status,
    jobFamily: query.jobFamily,
    dimension: query.dimension,
    difficulty: query.difficulty,
    keyword: query.keyword,
  }
  return request<unknown>(path, { query: params }).then((raw) => mapPage(raw, mapQuestion))
}

export async function createQuestion(payload: AdminQuestionPayload): Promise<void> {
  await request<unknown>('/admin/questions', { method: 'POST', body: payload })
}

export async function updateQuestion(id: string, payload: AdminQuestionPayload): Promise<void> {
  await request<unknown>(`/admin/questions/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: payload,
  })
}

export async function publishQuestion(id: string): Promise<void> {
  await request<unknown>(`/admin/questions/${encodeURIComponent(id)}/publish`, { method: 'POST' })
}

export async function archiveQuestion(id: string): Promise<void> {
  await request<unknown>(`/admin/questions/${encodeURIComponent(id)}/archive`, { method: 'POST' })
}

export interface AdminQuestionImportPayload {
  sourceRepo: string
  items: Record<string, unknown>[]
}

export interface AdminImportResult {
  batchId: string
  total: number
  success: number
  failed: number
  skipped: number
}

export async function importQuestions(
  payload: AdminQuestionImportPayload,
): Promise<AdminImportResult> {
  const raw = await request<unknown>('/admin/questions/import', {
    method: 'POST',
    body: payload,
  })
  const dto = asRecord(raw)
  return {
    batchId: idOf(dto.batchId ?? dto.id),
    total: asNumber(dto.total, asNumber(dto.totalCount)),
    success: asNumber(dto.success, asNumber(dto.successCount)),
    failed: asNumber(dto.failed, asNumber(dto.failedCount)),
    skipped: asNumber(dto.skipped, asNumber(dto.skippedCount)),
  }
}

export interface AdminImportBatch {
  id: string
  sourceRepo: string
  total: number
  success: number
  failed: number
  skipped: number
  status: AdminImportBatchStatus
  createdAt: string
  finishedAt: string
}

function batchStatusOf(value: unknown): AdminImportBatchStatus {
  if (value === 'RUNNING' || value === 'DONE' || value === 'FAILED') return value
  return 'PENDING'
}

function mapBatch(raw: unknown): AdminImportBatch {
  const dto = asRecord(raw)
  return {
    id: idOf(dto.id),
    sourceRepo: asString(dto.sourceRepo),
    total: asNumber(dto.total, asNumber(dto.totalCount)),
    success: asNumber(dto.success, asNumber(dto.successCount)),
    failed: asNumber(dto.failed, asNumber(dto.failedCount)),
    skipped: asNumber(dto.skipped, asNumber(dto.skippedCount)),
    status: batchStatusOf(dto.status),
    createdAt: asString(dto.createdAt),
    finishedAt: asString(dto.finishedAt),
  }
}

export async function listImportBatches(): Promise<AdminImportBatch[]> {
  const raw = await request<unknown>('/admin/questions/batches')
  return extractList(raw, ['batches']).map(mapBatch)
}

// ---------------------------------------------------------------------------
// 用户与额度
// ---------------------------------------------------------------------------

export type AdminUserStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED'
export type AdminUserState = AdminUserStatus | 'DELETED'
export type AdminRole = 'USER' | 'SUPPORT' | 'ADMIN'

export interface AdminUser {
  id: string
  email: string
  nickname: string
  role: AdminRole
  status: AdminUserState
  planCode: string
  createdAt: string
  lastLoginAt: string
  interviewCredits: number | null
  resumeCredits: number | null
}

export interface AdminUserQuery extends PageQuery {
  keyword?: string | undefined
  status?: AdminUserStatus | undefined
}

function roleOf(value: unknown): AdminRole {
  if (value === 'SUPPORT' || value === 'ADMIN') return value
  return 'USER'
}

function mapUser(raw: unknown): AdminUser {
  const dto = asRecord(raw)
  return {
    id: idOf(dto.id),
    email: asString(dto.email),
    nickname: asString(dto.nickname),
    role: roleOf(dto.role),
    status: asString(dto.status, 'ACTIVE') as AdminUserState,
    planCode: asString(dto.planCode, 'FREE'),
    createdAt: asString(dto.createdAt),
    lastLoginAt: asString(dto.lastLoginAt),
    interviewCredits: asNullableNumber(dto.interviewCredits),
    resumeCredits: asNullableNumber(dto.resumeCredits),
  }
}

export function listUsers(query: AdminUserQuery = {}): Promise<Page<AdminUser>> {
  return request<unknown>('/admin/users', {
    query: { ...pageQuery(query), keyword: query.keyword, status: query.status },
  }).then((raw) => mapPage(raw, mapUser))
}

export async function updateUserStatus(id: string, status: AdminUserStatus): Promise<void> {
  await request<unknown>(`/admin/users/${encodeURIComponent(id)}/status`, {
    method: 'PATCH',
    body: { status },
  })
}

export type AdminQuotaType = 'INTERVIEW' | 'RESUME'

export interface AdminQuotaPayload {
  type: AdminQuotaType
  delta: number
  reason: string
  idempotencyKey: string
}

export async function adjustUserQuota(id: string, payload: AdminQuotaPayload): Promise<void> {
  await request<unknown>(`/admin/users/${encodeURIComponent(id)}/quota`, {
    method: 'POST',
    body: payload,
  })
}

/** 调整角色；管理员不能调整自己的角色，由后端拒绝。 */
export async function updateUserRole(id: string, role: AdminRole): Promise<void> {
  await request<unknown>(`/admin/users/${encodeURIComponent(id)}/role`, {
    method: 'PATCH',
    body: { role },
  })
}

/** 解除登录锁定：清空失败计数与锁定状态。 */
export async function unlockUser(id: string): Promise<void> {
  await request<unknown>(`/admin/users/${encodeURIComponent(id)}/unlock`, { method: 'POST' })
}

/** 管理员重置密码；后端可能同时吊销该用户已有会话。 */
export async function resetUserPassword(id: string, password: string): Promise<void> {
  await request<unknown>(`/admin/users/${encodeURIComponent(id)}/reset-password`, {
    method: 'POST',
    body: { password },
  })
}

// ---------------------------------------------------------------------------
// AI 配置
// ---------------------------------------------------------------------------

export interface AdminAiConfig {
  enabled: boolean
  baseUrl: string
  model: string
  smallModel: string
  /** 是否已配置 Key；明文永不返回。 */
  hasApiKey: boolean
  apiKeyMasked: string
  asrEnabled: boolean
  ttsEnabled: boolean
  ocrEnabled: boolean
  searchEnabled: boolean
  fetchEnabled: boolean
  ttsDefaultVoice: 'stella' | 'tide'
  updatedAt: string
  updatedBy: string
}

export interface AdminAiConfigPayload {
  enabled: boolean
  baseUrl: string
  model: string
  smallModel: string
  /** 留空表示保留旧 Key。 */
  apiKey?: string | undefined
  /** 显式清空已保存的 Key。 */
  clearApiKey?: boolean | undefined
  asrEnabled?: boolean | undefined
  ttsEnabled?: boolean | undefined
  ocrEnabled?: boolean | undefined
  searchEnabled?: boolean | undefined
  fetchEnabled?: boolean | undefined
  ttsDefaultVoice?: 'stella' | 'tide' | undefined
}

export interface AdminAiTestResult {
  ok: boolean
  model: string
  latencyMs: number | null
  message: string
}

function unwrapConfig(raw: unknown): unknown {
  const dto = asRecord(raw)
  const nested = dto.config
  return nested !== null && typeof nested === 'object' && !Array.isArray(nested) ? nested : raw
}

function mapAiConfig(raw: unknown): AdminAiConfig {
  const dto = asRecord(unwrapConfig(raw))
  const apiKeyMasked = asString(dto.apiKeyMasked)
  // 工具开关在响应中嵌套为 tools 对象；兼容旧版扁平字段。
  const tools = asRecord(dto.tools ?? dto)
  const voice = asString(tools.ttsDefaultVoice)
  return {
    enabled: asBoolean(dto.enabled),
    baseUrl: asString(dto.baseUrl, asString(dto.baseURL)),
    model: asString(dto.model),
    smallModel: asString(dto.smallModel, asString(dto.smallModelName)),
    hasApiKey: asBoolean(dto.hasApiKey ?? dto.apiKeyPresent, apiKeyMasked !== ''),
    apiKeyMasked,
    asrEnabled: asBoolean(tools.asrEnabled, true),
    ttsEnabled: asBoolean(tools.ttsEnabled, true),
    ocrEnabled: asBoolean(tools.ocrEnabled, true),
    searchEnabled: asBoolean(tools.searchEnabled, true),
    fetchEnabled: asBoolean(tools.fetchEnabled, true),
    ttsDefaultVoice: voice === 'tide' ? 'tide' : 'stella',
    updatedAt: asString(dto.updatedAt),
    updatedBy: idOf(dto.updatedBy ?? dto.updatedByName),
  }
}

export async function getAiConfig(): Promise<AdminAiConfig> {
  const raw = await request<unknown>('/admin/ai-config')
  return mapAiConfig(raw)
}

/** 保存配置；返回体可能为空，前端保存后重新 GET 以获取最新掩码。 */
export async function updateAiConfig(payload: AdminAiConfigPayload): Promise<void> {
  await request<unknown>('/admin/ai-config', { method: 'PUT', body: payload })
}

export async function testAiConfig(): Promise<AdminAiTestResult> {
  const raw = await request<unknown>('/admin/ai-config/test', { method: 'POST' })
  const dto = asRecord(raw)
  return {
    ok: asBoolean(dto.ok ?? dto.success, false),
    model: asString(dto.model),
    latencyMs: asNullableNumber(dto.latencyMs),
    message: asString(dto.message, asString(dto.errorMessage)),
  }
}

// ---------------------------------------------------------------------------
// AI 调用日志
// ---------------------------------------------------------------------------

export type AdminAiCallStatus = 'SUCCESS' | 'FAILED' | 'DEGRADED'

export interface AdminAiCall {
  id: string
  userId: string
  scene: string
  provider: string
  model: string
  status: AdminAiCallStatus
  tokensIn: number | null
  tokensOut: number | null
  firstTokenMs: number | null
  latencyMs: number | null
  errorCode: string
  createdAt: string
}

export interface AdminAiCallQuery extends PageQuery {
  scene?: string | undefined
  status?: AdminAiCallStatus | undefined
}

function aiStatusOf(value: unknown): AdminAiCallStatus {
  if (value === 'FAILED' || value === 'DEGRADED') return value
  return 'SUCCESS'
}

function mapAiCall(raw: unknown): AdminAiCall {
  const dto = asRecord(raw)
  return {
    id: idOf(dto.id),
    userId: idOf(dto.userId),
    scene: asString(dto.scene),
    provider: asString(dto.provider),
    model: asString(dto.model),
    status: aiStatusOf(dto.status),
    tokensIn: asNullableNumber(dto.tokensIn),
    tokensOut: asNullableNumber(dto.tokensOut),
    firstTokenMs: asNullableNumber(dto.firstTokenMs),
    latencyMs: asNullableNumber(dto.latencyMs),
    errorCode: asString(dto.errorCode),
    createdAt: asString(dto.createdAt),
  }
}

export function listAiCalls(query: AdminAiCallQuery = {}): Promise<Page<AdminAiCall>> {
  return request<unknown>('/admin/ai-calls', {
    query: { ...pageQuery(query), scene: query.scene, status: query.status },
  }).then((raw) => mapPage(raw, mapAiCall))
}

// ---------------------------------------------------------------------------
// Prompt 模板与版本
// ---------------------------------------------------------------------------

export type AdminPromptVersionStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export interface AdminPromptTemplate {
  code: string
  name: string
  scene: string
  description: string
  activeVersion: string | null
  publishedAt: string | null
  versionCount: number
}

export interface AdminPromptVersion {
  version: string
  status: AdminPromptVersionStatus
  content: string
  contentHash: string
  variablesSchema: Record<string, unknown>
  modelConfig: Record<string, unknown>
  evalReport: unknown
  createdBy: string
  publishedAt: string | null
  createdAt: string
}

export interface AdminPromptVersionPayload {
  version: string
  content: string
  variablesSchema: Record<string, unknown>
  modelConfig: Record<string, unknown>
}

function promptStatusOf(value: unknown): AdminPromptVersionStatus {
  if (value === 'PUBLISHED' || value === 'ARCHIVED') return value
  return 'DRAFT'
}

function mapPromptTemplate(raw: unknown): AdminPromptTemplate {
  const dto = asRecord(raw)
  return {
    code: asString(dto.code),
    name: asString(dto.name, asString(dto.code)),
    scene: asString(dto.scene),
    description: asString(dto.description),
    activeVersion: asNullableString(dto.activeVersion),
    publishedAt: asNullableString(dto.publishedAt),
    versionCount: asNumber(dto.versionCount),
  }
}

function mapPromptVersion(raw: unknown): AdminPromptVersion {
  const dto = asRecord(raw)
  return {
    version: asString(dto.version),
    status: promptStatusOf(dto.status),
    content: asString(dto.content),
    contentHash: asString(dto.contentHash),
    variablesSchema: asRecord(dto.variablesSchema),
    modelConfig: asRecord(dto.modelConfig),
    evalReport: dto.evalReport ?? null,
    createdBy: idOf(dto.createdBy),
    publishedAt: asNullableString(dto.publishedAt),
    createdAt: asString(dto.createdAt),
  }
}

export async function listPrompts(): Promise<AdminPromptTemplate[]> {
  const raw = await request<unknown>('/admin/prompts')
  return extractList(raw, ['templates', 'prompts']).map(mapPromptTemplate)
}

export async function listPromptVersions(code: string): Promise<AdminPromptVersion[]> {
  const raw = await request<unknown>(
    `/admin/prompts/${encodeURIComponent(code)}/versions`,
  )
  return extractList(raw, ['versions']).map(mapPromptVersion)
}

export async function createPromptVersion(
  code: string,
  payload: AdminPromptVersionPayload,
): Promise<void> {
  await request<unknown>(`/admin/prompts/${encodeURIComponent(code)}/versions`, {
    method: 'POST',
    body: payload,
  })
}

export async function publishPromptVersion(code: string, version: string): Promise<void> {
  await request<unknown>(
    `/admin/prompts/${encodeURIComponent(code)}/versions/${encodeURIComponent(version)}/publish`,
    { method: 'POST' },
  )
}

// ---------------------------------------------------------------------------
// 审计日志
// ---------------------------------------------------------------------------

export interface AdminAuditLog {
  id: string
  adminUserId: string
  action: string
  targetType: string
  targetId: string
  before: unknown
  after: unknown
  requestId: string
  createdAt: string
}

function mapAuditLog(raw: unknown): AdminAuditLog {
  const dto = asRecord(raw)
  return {
    id: idOf(dto.id),
    adminUserId: idOf(dto.adminUserId ?? dto.actorId),
    action: asString(dto.action),
    targetType: asString(dto.targetType),
    targetId: asString(dto.targetId),
    before: dto.before ?? dto.beforeJson ?? null,
    after: dto.after ?? dto.afterJson ?? null,
    requestId: asString(dto.requestId),
    createdAt: asString(dto.createdAt),
  }
}

export function listAuditLogs(query: PageQuery = {}): Promise<Page<AdminAuditLog>> {
  return request<unknown>('/admin/audit-logs', { query: pageQuery(query) }).then((raw) =>
    mapPage(raw, mapAuditLog),
  )
}
// ---------------------------------------------------------------------------
// 平台策略（注册开关、每日额度、邀请奖励与注册风控）
// ---------------------------------------------------------------------------

export interface AdminPlatformSettings {
  registrationOpen: boolean
  dailyInterviewLimit: number
  dailyResumeLimit: number
  freeMonthlyInterviews: number
  freeMonthlyResumes: number
  inviteRewardInviterInterview: number
  inviteRewardInviteeResume: number
  inviteDailyCap: number
  inviteTotalCap: number
  registrationIpDailyCap: number
  registrationDeviceDailyCap: number
  updatedAt: string
  updatedBy: string
}

export type AdminPlatformSettingsPayload = Omit<AdminPlatformSettings, 'updatedAt' | 'updatedBy'>

function mapPlatformSettings(raw: unknown): AdminPlatformSettings {
  const dto = asRecord(raw)
  return {
    registrationOpen: asBoolean(dto.registrationOpen, true),
    dailyInterviewLimit: asNumber(dto.dailyInterviewLimit, 3),
    dailyResumeLimit: asNumber(dto.dailyResumeLimit, 3),
    freeMonthlyInterviews: asNumber(dto.freeMonthlyInterviews, 3),
    freeMonthlyResumes: asNumber(dto.freeMonthlyResumes, 3),
    inviteRewardInviterInterview: asNumber(dto.inviteRewardInviterInterview, 1),
    inviteRewardInviteeResume: asNumber(dto.inviteRewardInviteeResume, 1),
    inviteDailyCap: asNumber(dto.inviteDailyCap, 3),
    inviteTotalCap: asNumber(dto.inviteTotalCap, 20),
    registrationIpDailyCap: asNumber(dto.registrationIpDailyCap, 20),
    registrationDeviceDailyCap: asNumber(dto.registrationDeviceDailyCap, 3),
    updatedAt: asString(dto.updatedAt),
    updatedBy: idOf(dto.updatedBy),
  }
}

export function getPlatformSettings(): Promise<AdminPlatformSettings> {
  return request<unknown>('/admin/settings/quota').then(mapPlatformSettings)
}

export function updatePlatformSettings(
  payload: AdminPlatformSettingsPayload,
): Promise<AdminPlatformSettings> {
  return request<unknown>('/admin/settings/quota', { method: 'PUT', body: payload }).then(
    mapPlatformSettings,
  )
}
