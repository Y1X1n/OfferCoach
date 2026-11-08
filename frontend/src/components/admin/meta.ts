/** 管理端展示文案与状态映射；统一在此维护，避免各面板重复。 */

export type AdminBadgeVariant = 'brand' | 'success' | 'warning' | 'danger' | 'neutral' | 'accent'

export interface AdminStatusMeta {
  label: string
  variant: AdminBadgeVariant
}

const DIMENSION_LABELS: Record<string, string> = {
  DEPTH: '专业深度',
  PROJECT: '项目经历',
  BEHAVIOR: '行为面试',
  FIT: '岗位匹配',
}

const DIFFICULTY_LABELS: Record<string, string> = {
  EASY: '简单',
  MEDIUM: '中等',
  HARD: '困难',
}

const SOURCE_LABELS: Record<string, string> = {
  MANUAL: '手动创建',
  IMPORT: '仓库导入',
  GENERATED: 'AI 生成',
  PARTNER: '合作方',
}

const ROLE_LABELS: Record<string, string> = {
  USER: '用户',
  SUPPORT: '客服',
  ADMIN: '管理员',
}

const ROLE_META: Record<string, AdminStatusMeta> = {
  USER: { label: '用户', variant: 'neutral' },
  SUPPORT: { label: '客服', variant: 'accent' },
  ADMIN: { label: '管理员', variant: 'brand' },
}

const PROMPT_SCENE_LABELS: Record<string, string> = {
  RESUME_PARSE: '简历解析',
  JD_ANALYZE: 'JD 分析',
  QUESTION_GEN: '面试出题',
  FEEDBACK: '回答评分',
  REPORT: '报告生成',
}

const QUESTION_STATUS: Record<string, AdminStatusMeta> = {
  DRAFT: { label: '草稿', variant: 'warning' },
  PUBLISHED: { label: '已发布', variant: 'success' },
  ARCHIVED: { label: '已归档', variant: 'neutral' },
}

const USER_STATUS: Record<string, AdminStatusMeta> = {
  ACTIVE: { label: '正常', variant: 'success' },
  DISABLED: { label: '已停用', variant: 'danger' },
  LOCKED: { label: '已锁定', variant: 'warning' },
  DELETED: { label: '已注销', variant: 'neutral' },
}

const AI_STATUS: Record<string, AdminStatusMeta> = {
  SUCCESS: { label: '成功', variant: 'success' },
  FAILED: { label: '失败', variant: 'danger' },
  DEGRADED: { label: '降级', variant: 'warning' },
}

const PROMPT_STATUS: Record<string, AdminStatusMeta> = {
  DRAFT: { label: '草稿', variant: 'warning' },
  PUBLISHED: { label: '已发布', variant: 'success' },
  ARCHIVED: { label: '已归档', variant: 'neutral' },
}

const BATCH_STATUS: Record<string, AdminStatusMeta> = {
  PENDING: { label: '排队中', variant: 'neutral' },
  RUNNING: { label: '进行中', variant: 'brand' },
  DONE: { label: '已完成', variant: 'success' },
  FAILED: { label: '失败', variant: 'danger' },
}

function metaOf(table: Record<string, AdminStatusMeta>, value: string): AdminStatusMeta {
  return table[value] ?? { label: value || '未知', variant: 'neutral' }
}

export function dimensionLabel(value: string): string {
  return DIMENSION_LABELS[value] ?? value
}

export function difficultyLabel(value: string): string {
  return DIFFICULTY_LABELS[value] ?? value
}

export function sourceLabel(value: string): string {
  return SOURCE_LABELS[value] ?? value
}

export function roleLabel(value: string): string {
  return ROLE_LABELS[value] ?? value
}

export function roleMeta(value: string): AdminStatusMeta {
  return metaOf(ROLE_META, value)
}

export function promptSceneLabel(value: string): string {
  return PROMPT_SCENE_LABELS[value] ?? value
}

export function questionStatusMeta(value: string): AdminStatusMeta {
  return metaOf(QUESTION_STATUS, value)
}

export function userStatusMeta(value: string): AdminStatusMeta {
  return metaOf(USER_STATUS, value)
}

export function aiStatusMeta(value: string): AdminStatusMeta {
  return metaOf(AI_STATUS, value)
}

export function promptStatusMeta(value: string): AdminStatusMeta {
  return metaOf(PROMPT_STATUS, value)
}

export function batchStatusMeta(value: string): AdminStatusMeta {
  return metaOf(BATCH_STATUS, value)
}

/** 通用 JSON 预览：对象序列化为单行文本，供表格 title/内容展示。 */
export function jsonPreview(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'string') return value
  try {
    const text = JSON.stringify(value)
    return text ?? '—'
  } catch {
    return '[无法序列化]'
  }
}

export function optionalInt(value: number | null): string {
  return value === null ? '—' : String(value)
}