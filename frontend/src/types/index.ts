/** 与后端契约（docs/design/api.md）对齐的领域类型；ID 一律字符串，时间一律 ISO-8601。 */

export type Role = 'USER' | 'SUPPORT' | 'ADMIN'

export interface UserSummary {
  id: string
  nickname: string
  role: Role
  planCode: string
}

/** GET /users/me；avatarUrl 显式返回 null。 */
export interface Me {
  id: string
  email: string
  nickname: string
  avatarUrl: string | null
  role: Role
  timezone: string
  planCode: string
  createdAt: string
  /** 隐私开关；M0 契约已声明，后端未下发时视为 true。 */
  allowRawResumeToLlm?: boolean | undefined
}

export interface AuthResponse {
  userId: string
  accessToken: string
  expiresIn: number
  user: UserSummary
}

export interface Quota {
  planCode: string
  planName: string
  interviewCredits: number
  resumeCredits: number
  periodEnd: string
  daily: {
    date: string
    sessionsUsed: number
    sessionsLimit: number
  }
  /** 后端为自由 Map；布尔能力按 key 读取。 */
  features: Record<string, unknown>
}

export interface Plan {
  code: string
  name: string
  priceCents: number
  periodDays?: number | undefined
  period?: string | undefined
  interviewCredits?: number | undefined
  resumeCredits?: number | undefined
  description?: string | undefined
  recommended?: boolean | undefined
  active?: boolean | undefined
  features?: Record<string, unknown> | undefined
}

/** 统一分页响应。 */
export interface Page<T> {
  items: T[]
  total: number
  page: number
  size: number
}

export interface PageQuery {
  page?: number | undefined
  size?: number | undefined
}

// ---------------------------------------------------------------------------
// 简历
// ---------------------------------------------------------------------------

export type ResumeParseStatus = 'PARSING' | 'READY' | 'FAILED'

export interface ResumeBasic {
  name: string
  city: string
  yearsOfExperience: number
  phoneMasked: string
  emailMasked: string
}

export interface EducationItem {
  school: string
  degree: string
  major: string
  start: string
  end: string
}

export interface ExperienceItem {
  company: string
  title: string
  start: string
  end: string
  bullets: string[]
}

export interface ProjectItem {
  name: string
  role: string
  stack: string[]
  highlights: string[]
  metrics: string[]
}

export type SkillLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED'

export interface SkillItem {
  name: string
  level: SkillLevel
  years: number
}

export interface ResumeStructured {
  basic: ResumeBasic
  education: EducationItem[]
  experiences: ExperienceItem[]
  projects: ProjectItem[]
  skills: SkillItem[]
  certificates: string[]
}

/** 解析器原始结构：字段可能缺省或为 null，映射层负责补齐。 */
export interface ResumeStructuredDto {
  basic?: Partial<ResumeBasic> | null | undefined
  education?: EducationItem[] | null | undefined
  experiences?: ExperienceItem[] | null | undefined
  projects?: ProjectItem[] | null | undefined
  skills?: SkillItem[] | null | undefined
  certificates?: string[] | null | undefined
  confidence?: Partial<Record<ResumeSectionKey, number>> | null | undefined
}

export type ResumeLayout = 'classic' | 'modern' | 'compact' | 'minimal'

/** 简历中带识别置信度的区块，与 structured 的可信区块一一对应。 */
export type ResumeSectionKey = 'basic' | 'education' | 'experiences' | 'projects' | 'skills'

export interface ResumeSummary {
  id: string
  title: string
  status: ResumeParseStatus
  summary: string
  skillCount: number
  isDefault: boolean
  fileName: string
  updatedAt: string
  /** 列表接口不返回结构化字段，仅详情接口返回。 */
  structured?: ResumeStructured | undefined
}

export interface Resume {
  id: string
  title: string
  fileName: string
  status: ResumeParseStatus
  /** 解析中用于进度展示的估算值；服务端未提供真实进度。 */
  progress: number
  updatedAt: string
  summary: string
  structured: ResumeStructured
  confidence: Partial<Record<ResumeSectionKey, number>>
  isDefault: boolean
  lowConfidenceFields: string[]
}

export interface ResumeDetailDto {
  id: string
  title: string
  parseStatus: ResumeParseStatus
  pageCount?: number | null | undefined
  structured?: ResumeStructuredDto | null | undefined
  summary?: string | null | undefined
  lowConfidenceFields?: string[] | null | undefined
  isDefault?: boolean | undefined
  fileName?: string | null | undefined
  updatedAt?: string | null | undefined
  createdAt?: string | null | undefined
}

export interface ResumeUploadResult {
  id: string
  title: string
  parseStatus: ResumeParseStatus
  estimatedReadyInMs?: number | null | undefined
  quota?: { charged?: number | undefined; resumeCredits?: number | undefined } | undefined
}

export interface ResumeUpdatePayload {
  title?: string | undefined
  summary?: string | undefined
  structured?: ResumeStructured | undefined
  isDefault?: boolean | undefined
}

// ---------------------------------------------------------------------------
// JD 与匹配
// ---------------------------------------------------------------------------

export type Seniority = 'JUNIOR' | 'MID' | 'SENIOR'

export interface JdStructured {
  seniority: Seniority
  responsibilities: string[]
  mustHave: string[]
  niceToHave: string[]
  techStack: string[]
  keywords: string[]
}

export interface MatchedSkill {
  skill: string
  evidence: string
}

export interface MissingSkill {
  skill: string
  suggestion: string
}

export interface MatchEvidence {
  jdQuote: string
  resumeQuote: string
}

export interface JdMatch {
  score: number
  matchedSkills: MatchedSkill[]
  missingSkills: MissingSkill[]
  risks: string[]
  evidence: MatchEvidence[]
}

export interface Jd {
  id: string
  title: string
  company: string
  rawText: string
  createdAt: string
  structured: JdStructured
  match?: JdMatch | undefined
}

export interface JdStructuredDto {
  seniority?: Seniority | null | undefined
  responsibilities?: string[] | null | undefined
  mustHave?: string[] | null | undefined
  niceToHave?: string[] | null | undefined
  techStack?: string[] | null | undefined
  keywords?: string[] | null | undefined
}

export interface JdDetailDto {
  id: string
  title: string
  company?: string | null | undefined
  rawText?: string | null | undefined
  structured?: JdStructuredDto | null | undefined
  createdAt?: string | null | undefined
}

export interface JdMatchDto {
  matchScore: number
  matchedSkills?: MatchedSkill[] | null | undefined
  missingSkills?: MissingSkill[] | null | undefined
  risks?: string[] | null | undefined
  evidence?: MatchEvidence[] | null | undefined
}

// ---------------------------------------------------------------------------
// 面试
// ---------------------------------------------------------------------------

export type QuestionDimension = 'DEPTH' | 'PROJECT' | 'BEHAVIOR' | 'FIT'
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'
export type QuestionStatus = 'PENDING' | 'ASKED' | 'ANSWERED' | 'SKIPPED'

export interface Question {
  id: string
  seq: number
  text: string
  dimension: QuestionDimension
  difficulty: Difficulty
  referencePoints: string[]
  whyThisQuestion: string
  status: QuestionStatus
  source?: string | undefined
  answerText?: string | null | undefined
  durationMs?: number | null | undefined
  answerId?: string | null | undefined
}

export type InterviewerStyle = 'GENTLE' | 'STANDARD' | 'PRESSURE'
export type AnswerMode = 'TEXT' | 'VOICE'
export type DifficultyMode = 'EASY' | 'MIXED' | 'HARD'

export interface InterviewConfig {
  questionCount: number
  difficulty: DifficultyMode
  interviewerStyle: InterviewerStyle
  answerMode: AnswerMode
  timeLimitSec: number
  language?: string | undefined
}

export type InterviewStatus =
  | 'CREATED'
  | 'PREPARING'
  | 'READY'
  | 'IN_PROGRESS'
  | 'PAUSED'
  | 'FINISHED'
  | 'REPORT_GENERATING'
  | 'COMPLETED'
  | 'ABANDONED'
  | 'FAILED'

export interface InterviewSession {
  id: string
  status: InterviewStatus
  config: InterviewConfig
  progress?: { answered: number; skipped: number; total: number } | undefined
  currentSeq?: number | undefined
  totalScore?: number | null | undefined
  startedAt?: string | null | undefined
  lastActiveAt?: string | null | undefined
  resumeId?: string | null | undefined
  jdId?: string | null | undefined
  resumeTitle?: string | null | undefined
  jdTitle?: string | null | undefined
  jdCompany?: string | null | undefined
  questionCount?: number | undefined
  reportId?: string | null | undefined
}

export interface InterviewCreatePayload {
  resumeId: string
  jdId: string
  config: InterviewConfig
}

export interface InterviewStartResult {
  id: string
  status: InterviewStatus
  estimatedReadyInMs?: number | null | undefined
}

export interface FeedbackDimensions {
  content: number
  structure: number
  depth: number
  fit: number
  language: number
}

export interface FeedbackEvidence {
  quote: string
  reason: string
}

export interface Feedback {
  feedbackId?: string | undefined
  version?: number | undefined
  answerId?: string | undefined
  overallScore: number
  dimensions: FeedbackDimensions
  strengths: string[]
  improvements: string[]
  evidence: FeedbackEvidence[]
  rewrittenAnswer: string | null
  model?: string | undefined
  degraded?: boolean | undefined
}

export interface FeedbackStreamInfo {
  url: string
  answerId: string
  token: string
  expiresIn?: number | undefined
}

export interface AnswerResult {
  answerId: string
  status: string
  stream?: FeedbackStreamInfo | undefined
}

export interface FinishResult {
  reportId: string
  status: ReportStatus
  estimatedReadyInMs?: number | null | undefined
}

// ---------------------------------------------------------------------------
// 报告
// ---------------------------------------------------------------------------

export type ReportStatus = 'GENERATING' | 'READY' | 'FAILED'

export interface RadarItem {
  dimension: string
  label: string
  score: number
}

export interface ImprovementItem {
  priority: number
  action: string
  reason: string
  resource: string
}

export interface ReportBreakdownItem {
  questionId: string
  answerId?: string | undefined
  feedbackId?: string | undefined
  seq: number
  dimension: QuestionDimension
  score: number | null
  summary?: string | null | undefined
  question?: string | null | undefined
  answer?: string | null | undefined
  feedback?: Feedback | null | undefined
}

export interface ReportMatch {
  before: number
  after: number
  delta: number
}

export interface ReportSummary {
  id: string
  sessionId: string
  status: ReportStatus
  overallScore: number | null
  summary: string
  title: string
  company: string
  role: string
  questionCount: number
  createdAt: string
}

export interface DashboardActiveSession {
  id: string
  status: InterviewStatus
  resumeId?: string | null | undefined
  jdId?: string | null | undefined
  resumeTitle?: string | null | undefined
  jdTitle?: string | null | undefined
  jdCompany?: string | null | undefined
  questionCount: number
  answeredCount: number
  startedAt?: string | null | undefined
  lastActiveAt?: string | null | undefined
}

/** 工作台聚合数据：一次请求返回额度、就绪状态、进行中会话与最近报告。 */
export interface DashboardData {
  quota: Quota
  resumeReady: boolean
  jdCount: number
  activeSession?: DashboardActiveSession | null | undefined
  recentReports: ReportSummary[]
  reportTotal: number
  averageScore?: number | null | undefined
  scoredReportCount: number
}

export interface Report {
  id: string
  sessionId: string
  title: string
  company: string
  role: string
  questionCount: number
  status: ReportStatus
  overallScore: number | null
  radar: RadarItem[]
  summary: string
  highlights: string[]
  risks: string[]
  improvementPlan: ImprovementItem[]
  match?: ReportMatch | undefined
  breakdown: ReportBreakdownItem[]
  pdf?: { available: boolean; expiresAt?: string | null | undefined } | undefined
  generatedAt: string
}

export interface ReportDetailDto {
  id: string
  sessionId: string
  title?: string | null | undefined
  company?: string | null | undefined
  role?: string | null | undefined
  questionCount?: number | null | undefined
  status: ReportStatus
  overallScore?: number | null | undefined
  radar?: RadarItem[] | null | undefined
  summary?: string | null | undefined
  highlights?: string[] | null | undefined
  risks?: string[] | null | undefined
  improvementPlan?: ImprovementItem[] | null | undefined
  match?: ReportMatch | null | undefined
  questionBreakdown?: ReportBreakdownItem[] | null | undefined
  pdf?: { available?: boolean | undefined; expiresAt?: string | null | undefined } | null | undefined
  generatedAt?: string | null | undefined
  createdAt?: string | null | undefined
}

// ---------------------------------------------------------------------------
// UI
// ---------------------------------------------------------------------------

export type ToastVariant = 'success' | 'error' | 'info' | 'warning'

export interface ToastItem {
  id: number
  title: string
  description?: string | undefined
  variant: ToastVariant
}