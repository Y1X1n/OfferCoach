import type {
  Jd,
  JdDetailDto,
  JdMatch,
  JdMatchDto,
  JdStructured,
  Page,
  PageQuery,
  Seniority,
} from '@/types'
import { request } from './client'
import { toPage } from './normalize'

interface JdSummaryDto extends JdDetailDto {
  matchScore?: number | null | undefined
}

const SENIORITIES: Seniority[] = ['JUNIOR', 'MID', 'SENIOR']

function mapStructured(dto: JdDetailDto['structured']): JdStructured {
  const seniority = dto?.seniority
  return {
    seniority: seniority && SENIORITIES.includes(seniority) ? seniority : 'MID',
    responsibilities: Array.isArray(dto?.responsibilities) ? dto.responsibilities : [],
    mustHave: Array.isArray(dto?.mustHave) ? dto.mustHave : [],
    niceToHave: Array.isArray(dto?.niceToHave) ? dto.niceToHave : [],
    techStack: Array.isArray(dto?.techStack) ? dto.techStack : [],
    keywords: Array.isArray(dto?.keywords) ? dto.keywords : [],
  }
}

export function mapJd(dto: JdSummaryDto | JdDetailDto): Jd {
  return {
    id: dto.id,
    title: dto.title,
    company: dto.company ?? '目标公司',
    rawText: dto.rawText ?? '',
    createdAt: dto.createdAt ?? '',
    structured: mapStructured(dto.structured),
  }
}

export function mapMatch(dto: JdMatchDto): JdMatch {
  return {
    score: dto.matchScore,
    matchedSkills: Array.isArray(dto.matchedSkills) ? dto.matchedSkills : [],
    missingSkills: Array.isArray(dto.missingSkills) ? dto.missingSkills : [],
    risks: Array.isArray(dto.risks) ? dto.risks : [],
    evidence: Array.isArray(dto.evidence) ? dto.evidence : [],
  }
}

export async function listJds(query: PageQuery = {}): Promise<Page<Jd>> {
  const raw = await request<unknown>('/jds', {
    query: { page: query.page ?? 1, size: query.size ?? 20 },
  })
  const page = toPage<JdSummaryDto>(raw)
  return { ...page, items: page.items.map((item) => mapJd(item)) }
}

export async function getJd(id: string): Promise<Jd> {
  return mapJd(await request<JdDetailDto>(`/jds/${encodeURIComponent(id)}`))
}

export interface CreateJdPayload {
  title: string
  company: string
  rawText: string
}

export async function createJd(payload: CreateJdPayload): Promise<Jd> {
  const dto = await request<JdDetailDto>('/jds', { method: 'POST', body: payload })
  return mapJd({ ...payload, ...dto })
}

export async function deleteJd(id: string): Promise<void> {
  await request<void>(`/jds/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

export async function matchJd(jdId: string, resumeId: string): Promise<JdMatch> {
  return mapMatch(
    await request<JdMatchDto>(`/jds/${encodeURIComponent(jdId)}/match`, {
      method: 'POST',
      body: { resumeId },
    }),
  )
}