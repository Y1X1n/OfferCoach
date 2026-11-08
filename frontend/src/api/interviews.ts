import type {
  AnswerResult,
  Difficulty,
  DifficultyMode,
  Feedback,
  FeedbackStreamInfo,
  FinishResult,
  InterviewConfig,
  InterviewCreatePayload,
  InterviewerStyle,
  InterviewSession,
  InterviewStartResult,
  InterviewStatus,
  Page,
  PageQuery,
  Question,
  QuestionDimension,
  QuestionStatus,
} from '@/types'
import { request } from './client'
import { toPage } from './normalize'

interface InterviewDto {
  id: string
  status?: InterviewStatus | undefined
  config?: Partial<InterviewConfig> | null | undefined
  progress?: { answered?: number; skipped?: number; total?: number } | null | undefined
  currentSeq?: number | null | undefined
  totalScore?: number | null | undefined
  startedAt?: string | null | undefined
  lastActiveAt?: string | null | undefined
  resumeId?: string | null | undefined
  jdId?: string | null | undefined
  resumeTitle?: string | null | undefined
  jdTitle?: string | null | undefined
  jdCompany?: string | null | undefined
  questionCount?: number | null | undefined
  reportId?: string | null | undefined
}

interface QuestionDto {
  id: string
  seq: number
  text: string
  dimension?: string | undefined
  difficulty?: string | undefined
  referencePoints?: string[] | null | undefined
  whyThisQuestion?: string | null | undefined
  status?: string | undefined
  source?: string | undefined
  answerText?: string | null | undefined
  durationMs?: number | null | undefined
  answerId?: string | null | undefined
}

const DIMENSIONS: QuestionDimension[] = ['DEPTH', 'PROJECT', 'BEHAVIOR', 'FIT']
const DIFFICULTIES: Difficulty[] = ['EASY', 'MEDIUM', 'HARD']
const STATUSES: QuestionStatus[] = ['PENDING', 'ASKED', 'ANSWERED', 'SKIPPED']
const DIFFICULTY_MODES: DifficultyMode[] = ['EASY', 'MIXED', 'HARD']
const STYLES: InterviewerStyle[] = ['GENTLE', 'STANDARD', 'PRESSURE']

export const DEFAULT_INTERVIEW_CONFIG: InterviewConfig = {
  questionCount: 8,
  difficulty: 'MIXED',
  interviewerStyle: 'STANDARD',
  answerMode: 'TEXT',
  timeLimitSec: 180,
  language: 'zh-CN',
}

function mapConfig(raw: Partial<InterviewConfig> | null | undefined): InterviewConfig {
  const config = raw ?? {}
  return {
    questionCount:
      typeof config.questionCount === 'number'
        ? config.questionCount
        : DEFAULT_INTERVIEW_CONFIG.questionCount,
    difficulty:
      config.difficulty && DIFFICULTY_MODES.includes(config.difficulty)
        ? config.difficulty
        : DEFAULT_INTERVIEW_CONFIG.difficulty,
    interviewerStyle:
      config.interviewerStyle && STYLES.includes(config.interviewerStyle)
        ? config.interviewerStyle
        : DEFAULT_INTERVIEW_CONFIG.interviewerStyle,
    answerMode: 'TEXT',
    timeLimitSec:
      typeof config.timeLimitSec === 'number'
        ? config.timeLimitSec
        : DEFAULT_INTERVIEW_CONFIG.timeLimitSec,
    language: config.language ?? 'zh-CN',
  }
}

export function mapInterview(dto: InterviewDto): InterviewSession {
  const progress = dto.progress
  return {
    id: dto.id,
    status: dto.status ?? 'CREATED',
    config: mapConfig(dto.config),
    ...(progress
      ? {
          progress: {
            answered: progress.answered ?? 0,
            skipped: progress.skipped ?? 0,
            total: progress.total ?? 0,
          },
        }
      : {}),
    currentSeq: dto.currentSeq ?? undefined,
    totalScore: dto.totalScore ?? null,
    startedAt: dto.startedAt ?? null,
    lastActiveAt: dto.lastActiveAt ?? null,
    resumeId: dto.resumeId ?? null,
    jdId: dto.jdId ?? null,
    resumeTitle: dto.resumeTitle ?? null,
    jdTitle: dto.jdTitle ?? null,
    jdCompany: dto.jdCompany ?? null,
    questionCount: dto.questionCount ?? undefined,
    reportId: dto.reportId ?? null,
  }
}

export function mapQuestion(dto: QuestionDto): Question {
  return {
    id: dto.id,
    seq: dto.seq,
    text: dto.text,
    dimension: DIMENSIONS.includes(dto.dimension as QuestionDimension)
      ? (dto.dimension as QuestionDimension)
      : 'PROJECT',
    difficulty: DIFFICULTIES.includes(dto.difficulty as Difficulty)
      ? (dto.difficulty as Difficulty)
      : 'MEDIUM',
    referencePoints: Array.isArray(dto.referencePoints) ? dto.referencePoints : [],
    whyThisQuestion: dto.whyThisQuestion ?? '',
    status: STATUSES.includes(dto.status as QuestionStatus)
      ? (dto.status as QuestionStatus)
      : 'PENDING',
    source: dto.source,
    answerText: dto.answerText ?? null,
    durationMs: typeof dto.durationMs === 'number' ? dto.durationMs : null,
    answerId: dto.answerId ?? null,
  }
}

export async function listInterviews(query: PageQuery = {}): Promise<Page<InterviewSession>> {
  const raw = await request<unknown>('/interviews', {
    query: { page: query.page ?? 1, size: query.size ?? 20 },
  })
  const page = toPage<InterviewDto>(raw)
  return { ...page, items: page.items.map(mapInterview) }
}

export async function getInterview(id: string): Promise<InterviewSession> {
  return mapInterview(await request<InterviewDto>(`/interviews/${encodeURIComponent(id)}`))
}

export async function createInterview(
  payload: InterviewCreatePayload,
  idempotencyKey: string,
): Promise<InterviewSession> {
  return mapInterview(
    await request<InterviewDto>('/interviews', {
      method: 'POST',
      body: payload,
      idempotencyKey,
    }),
  )
}

export async function startInterview(id: string): Promise<InterviewStartResult> {
  return request<InterviewStartResult>(`/interviews/${encodeURIComponent(id)}/start`, {
    method: 'POST',
  })
}

export async function getQuestions(interviewId: string): Promise<Question[]> {
  const raw = await request<unknown>(`/interviews/${encodeURIComponent(interviewId)}/questions`)
  return toPage<QuestionDto>(raw).items.map(mapQuestion)
}

export async function skipQuestion(interviewId: string, questionId: string): Promise<void> {
  await request<void>(
    `/interviews/${encodeURIComponent(interviewId)}/questions/${encodeURIComponent(questionId)}/skip`,
    { method: 'POST' },
  )
}

export async function pauseInterview(id: string): Promise<void> {
  await request<void>(`/interviews/${encodeURIComponent(id)}/pause`, { method: 'POST' })
}

export async function resumeInterview(id: string): Promise<void> {
  await request<void>(`/interviews/${encodeURIComponent(id)}/resume`, { method: 'POST' })
}

export async function abandonInterview(id: string): Promise<void> {
  await request<void>(`/interviews/${encodeURIComponent(id)}/abandon`, { method: 'POST' })
}

export interface SubmitAnswerPayload {
  questionId: string
  answerText: string
  durationMs: number
  inputMode: 'TEXT' | 'VOICE'
}

export async function submitAnswer(
  interviewId: string,
  payload: SubmitAnswerPayload,
  idempotencyKey: string,
): Promise<AnswerResult> {
  const res = await request<{
    answerId: string
    status?: string | undefined
    stream?: FeedbackStreamInfo | null | undefined
  }>(`/interviews/${encodeURIComponent(interviewId)}/answers`, {
    method: 'POST',
    body: payload,
    idempotencyKey,
  })
  return {
    answerId: res.answerId,
    status: res.status ?? 'SCORING',
    ...(res.stream ? { stream: res.stream } : {}),
  }
}

type FeedbackDto = Partial<Feedback> & { rewrittenAnswer?: string | null | undefined }

function mapFeedback(res: FeedbackDto, answerId: string): Feedback {
  return {
    feedbackId: res.feedbackId,
    version: res.version,
    answerId: res.answerId ?? answerId,
    overallScore: typeof res.overallScore === 'number' ? res.overallScore : 0,
    dimensions: {
      content: res.dimensions?.content ?? 0,
      structure: res.dimensions?.structure ?? 0,
      depth: res.dimensions?.depth ?? 0,
      fit: res.dimensions?.fit ?? 0,
      language: res.dimensions?.language ?? 0,
    },
    strengths: Array.isArray(res.strengths) ? res.strengths : [],
    improvements: Array.isArray(res.improvements) ? res.improvements : [],
    evidence: Array.isArray(res.evidence) ? res.evidence : [],
    rewrittenAnswer: res.rewrittenAnswer ?? null,
    model: res.model,
    degraded: res.degraded,
  }
}

export async function getAnswerFeedback(
  interviewId: string,
  answerId: string,
): Promise<Feedback> {
  const res = await request<FeedbackDto>(
    `/interviews/${encodeURIComponent(interviewId)}/answers/${encodeURIComponent(answerId)}/feedback`,
  )
  return mapFeedback(res, answerId)
}

/**
 * 主动触发评分并直接返回结果；用于轮询超时后的重试。
 */
export async function generateAnswerFeedback(
  interviewId: string,
  answerId: string,
): Promise<Feedback> {
  const res = await request<FeedbackDto>(
    `/interviews/${encodeURIComponent(interviewId)}/answers/${encodeURIComponent(answerId)}/feedback`,
    { method: 'POST' },
  )
  return mapFeedback(res, answerId)
}

export async function finishInterview(id: string): Promise<FinishResult> {
  return request<FinishResult>(`/interviews/${encodeURIComponent(id)}/finish`, { method: 'POST' })
}