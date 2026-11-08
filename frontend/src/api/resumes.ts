import type {
  Page,
  PageQuery,
  Resume,
  ResumeBasic,
  ResumeDetailDto,
  ResumeParseStatus,
  ResumeStructured,
  ResumeStructuredDto,
  ResumeSummary,
  ResumeUpdatePayload,
  ResumeUploadResult,
  SkillItem,
  SkillLevel,
} from '@/types'
import { request } from './client'
import { toPage } from './normalize'

interface ResumeSummaryDto {
  id: string
  title: string
  parseStatus?: ResumeParseStatus | undefined
  status?: ResumeParseStatus | undefined
  summary?: string | null | undefined
  isDefault?: boolean | undefined
  fileName?: string | null | undefined
  updatedAt?: string | null | undefined
  createdAt?: string | null | undefined
  skillCount?: number | null | undefined
  structured?: ResumeStructuredDto | null | undefined
}

const SKILL_LEVELS: SkillLevel[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED']

function parseStatusOf(raw: string | undefined): ResumeParseStatus {
  if (raw === 'READY' || raw === 'FAILED' || raw === 'PARSING') return raw
  return 'PARSING'
}

export function mapStructured(dto: ResumeStructuredDto | null | undefined): ResumeStructured {
  const basic: Partial<ResumeBasic> = dto?.basic ?? {}
  const skills = Array.isArray(dto?.skills) ? dto.skills : []
  return {
    basic: {
      name: basic.name ?? '',
      city: basic.city ?? '',
      yearsOfExperience:
        typeof basic.yearsOfExperience === 'number' ? basic.yearsOfExperience : 0,
      phoneMasked: basic.phoneMasked ?? '',
      emailMasked: basic.emailMasked ?? '',
    },
    education: Array.isArray(dto?.education) ? dto.education : [],
    experiences: Array.isArray(dto?.experiences) ? dto.experiences : [],
    projects: Array.isArray(dto?.projects) ? dto.projects : [],
    skills: skills.map((skill): SkillItem => ({
      name: skill.name ?? '',
      level: SKILL_LEVELS.includes(skill.level) ? skill.level : 'INTERMEDIATE',
      years: typeof skill.years === 'number' ? skill.years : 0,
    })),
    certificates: Array.isArray(dto?.certificates) ? dto.certificates : [],
  }
}

function mapSummary(dto: ResumeSummaryDto): ResumeSummary {
  const status = parseStatusOf(dto.parseStatus ?? dto.status)
  const structured = dto.structured ? mapStructured(dto.structured) : undefined
  return {
    id: dto.id,
    title: dto.title,
    status,
    summary: dto.summary ?? '',
    skillCount:
      typeof dto.skillCount === 'number' ? dto.skillCount : (structured?.skills.length ?? 0),
    isDefault: dto.isDefault === true,
    fileName: dto.fileName ?? dto.title,
    updatedAt: dto.updatedAt ?? dto.createdAt ?? '',
    ...(structured === undefined ? {} : { structured }),
  }
}

export function mapResume(dto: ResumeDetailDto): Resume {
  const status = parseStatusOf(dto.parseStatus)
  return {
    id: dto.id,
    title: dto.title,
    fileName: dto.fileName ?? dto.title,
    status,
    progress: status === 'READY' ? 100 : status === 'FAILED' ? 0 : 45,
    updatedAt: dto.updatedAt ?? dto.createdAt ?? '',
    summary: dto.summary ?? '',
    structured: mapStructured(dto.structured),
    confidence: dto.structured?.confidence ?? {},
    isDefault: dto.isDefault === true,
    lowConfidenceFields: Array.isArray(dto.lowConfidenceFields) ? dto.lowConfidenceFields : [],
  }
}

export async function listResumes(query: PageQuery = {}): Promise<Page<ResumeSummary>> {
  const raw = await request<unknown>('/resumes', {
    query: { page: query.page ?? 1, size: query.size ?? 20 },
  })
  const page = toPage<ResumeSummaryDto>(raw)
  return { ...page, items: page.items.map(mapSummary) }
}

export async function getResume(id: string): Promise<Resume> {
  return mapResume(await request<ResumeDetailDto>(`/resumes/${encodeURIComponent(id)}`))
}

export interface UploadResumeOptions {
  title?: string | undefined
  isDefault?: boolean | undefined
}

export async function uploadResume(
  file: File,
  options: UploadResumeOptions = {},
): Promise<ResumeUploadResult> {
  const form = new FormData()
  form.append('file', file)
  if (options.title) form.append('title', options.title)
  if (options.isDefault !== undefined) form.append('isDefault', String(options.isDefault))
  const res = await request<ResumeUploadResult>('/resumes', { method: 'POST', body: form })
  return {
    id: res.id,
    title: res.title,
    parseStatus: parseStatusOf(res.parseStatus),
    estimatedReadyInMs: res.estimatedReadyInMs,
    quota: res.quota,
  }
}

export interface ManualResumePayload {
  title: string
  summary?: string | undefined
  structured: ResumeStructured
  isDefault?: boolean | undefined
}

/**
 * 手动建档：结构化字段直接落库，不触发异步解析。
 */
export async function createResumeManually(payload: ManualResumePayload): Promise<Resume> {
  const created = await request<{ id: string }>('/resumes/manual', {
    method: 'POST',
    body: {
      title: payload.title,
      summary: payload.summary ?? '',
      structured: payload.structured,
      isDefault: payload.isDefault,
    },
  })
  return getResume(created.id)
}

export async function updateResume(id: string, payload: ResumeUpdatePayload): Promise<Resume> {
  const dto = await request<ResumeDetailDto | undefined>(`/resumes/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: payload,
  })
  return dto ? mapResume(dto) : getResume(id)
}

export async function reparseResume(id: string): Promise<void> {
  await request<void>(`/resumes/${encodeURIComponent(id)}/reparse`, { method: 'POST' })
}

export async function deleteResume(id: string): Promise<void> {
  await request<void>(`/resumes/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export async function setDefaultResume(id: string): Promise<Resume> {
  return updateResume(id, { isDefault: true })
}

export interface ResumeDownloadUrl {
  url: string
  expiresIn: number
}

export function getResumeDownloadUrl(id: string): Promise<ResumeDownloadUrl> {
  return request<ResumeDownloadUrl>(`/resumes/${encodeURIComponent(id)}/download-url`)
}