import type {
  Page,
  PageQuery,
  QuestionDimension,
  Report,
  ReportBreakdownItem,
  ReportDetailDto,
  ReportStatus,
  ReportSummary,
} from '@/types'
import { request } from './client'
import { toPage } from './normalize'

const DIMENSIONS: QuestionDimension[] = ['DEPTH', 'PROJECT', 'BEHAVIOR', 'FIT']

interface ReportSummaryDto {
  id: string
  sessionId?: string | null | undefined
  status?: ReportStatus | undefined
  overallScore?: number | null | undefined
  summary?: string | null | undefined
  title?: string | null | undefined
  company?: string | null | undefined
  role?: string | null | undefined
  questionCount?: number | null | undefined
  createdAt?: string | null | undefined
  generatedAt?: string | null | undefined
}

function mapSummary(dto: ReportSummaryDto): ReportSummary {
  return {
    id: dto.id,
    sessionId: dto.sessionId ?? '',
    status: dto.status ?? 'GENERATING',
    overallScore: typeof dto.overallScore === 'number' ? dto.overallScore : null,
    summary: dto.summary ?? '',
    title: dto.title ?? '模拟面试报告',
    company: dto.company ?? '目标公司',
    role: dto.role ?? '模拟面试',
    questionCount: typeof dto.questionCount === 'number' ? dto.questionCount : 0,
    createdAt: dto.createdAt ?? dto.generatedAt ?? '',
  }
}

export async function listReports(query: PageQuery = {}): Promise<Page<ReportSummary>> {
  const raw = await request<unknown>('/reports', {
    query: { page: query.page ?? 1, size: query.size ?? 20 },
  })
  const page = toPage<ReportSummaryDto>(raw)
  return { ...page, items: page.items.map(mapSummary) }
}

function mapBreakdown(items: ReportBreakdownItem[]): ReportBreakdownItem[] {
  return items.map((item) => ({
    ...item,
    score: typeof item.score === 'number' ? item.score : null,
    dimension: DIMENSIONS.includes(item.dimension) ? item.dimension : 'DEPTH',
  }))
}

export function mapReport(dto: ReportDetailDto): Report {
  return {
    id: dto.id,
    sessionId: dto.sessionId,
    title: dto.title ?? '模拟面试报告',
    company: dto.company ?? '目标公司',
    role: dto.role ?? '模拟面试',
    questionCount: typeof dto.questionCount === 'number' ? dto.questionCount : 0,
    status: dto.status ?? 'READY',
    overallScore: typeof dto.overallScore === 'number' ? dto.overallScore : null,
    radar: Array.isArray(dto.radar) ? dto.radar : [],
    summary: dto.summary ?? '',
    highlights: Array.isArray(dto.highlights) ? dto.highlights : [],
    risks: Array.isArray(dto.risks) ? dto.risks : [],
    improvementPlan: Array.isArray(dto.improvementPlan) ? dto.improvementPlan : [],
    ...(dto.match ? { match: dto.match } : {}),
    breakdown: Array.isArray(dto.questionBreakdown) ? mapBreakdown(dto.questionBreakdown) : [],
    ...(dto.pdf
      ? {
          pdf: {
            available: dto.pdf.available === true,
            expiresAt: dto.pdf.expiresAt ?? null,
          },
        }
      : {}),
    generatedAt: dto.generatedAt ?? dto.createdAt ?? '',
  }
}

export async function getReport(id: string): Promise<Report> {
  return mapReport(await request<ReportDetailDto>(`/reports/${encodeURIComponent(id)}`))
}

export async function retryReport(id: string): Promise<void> {
  await request<void>(`/reports/${encodeURIComponent(id)}/retry`, { method: 'POST' })
}