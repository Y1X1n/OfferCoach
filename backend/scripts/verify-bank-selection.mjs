#!/usr/bin/env node
// 验证岗位族修复后的出题相关性：创建会话→查看题目→放弃，不打印令牌/原文。
const BASE_URL = (process.env.BASE_URL || 'http://127.0.0.1:8081').replace(/\/+$/, '')
const API = `${BASE_URL}/api/v1`
const ACCOUNT = process.env.SMOKE_ACCOUNT
const PASSWORD = process.env.SMOKE_PASSWORD
if (!ACCOUNT || !PASSWORD) {
  console.error('需要 SMOKE_ACCOUNT / SMOKE_PASSWORD')
  process.exit(1)
}
async function call(method, path, { token, body, headers: extraHeaders = {} } = {}) {
  const headers = { ...extraHeaders }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${API}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(30000),
  })
  const text = await response.text()
  return { status: response.status, data: text ? JSON.parse(text) : null }
}
const login = await call('POST', '/auth/login', { body: { account: ACCOUNT, password: PASSWORD } })
if (login.status !== 200) throw new Error(`login ${login.status}`)
const token = login.data.accessToken
const created = await call('POST', '/interviews', {
  token,
  body: {
    resumeId: Number(process.env.RESUME_ID || 18),
    jdId: Number(process.env.JD_ID || 16),
    config: { questionCount: 5, difficulty: 'MIXED', interviewerStyle: 'STANDARD', answerMode: 'TEXT', timeLimitSec: 180, language: 'zh-CN' },
  },
  headers: { 'Idempotency-Key': crypto.randomUUID() },
})
if (created.status !== 201) throw new Error(`create ${created.status} ${JSON.stringify(created.data).slice(0, 200)}`)
const sessionId = created.data.id
await call('POST', `/interviews/${sessionId}/start`, { token })
let questions = []
for (let attempt = 0; attempt < 40; attempt += 1) {
  const session = await call('GET', `/interviews/${sessionId}`, { token })
  if (session.data?.status === 'READY' || session.data?.status === 'FAILED') break
  await new Promise((resolve) => setTimeout(resolve, 1000))
}
const result = await call('GET', `/interviews/${sessionId}/questions`, { token })
questions = result.data?.items || []
const aiKeywords = ['Agent', '大模型', 'RAG', 'MCP', 'Prompt', '向量', '模型']
const backendKeywords = ['Java', 'Spring', 'Redis', 'Kafka', 'MySQL', '微服务', '缓存', '消息', '并发', '订单', 'JVM', '线程']
for (const question of questions) {
  const text = `${question.text} ${(question.referencePoints || []).join(' ')}`
  const ai = aiKeywords.some((keyword) => text.includes(keyword))
  const backend = backendKeywords.some((keyword) => text.includes(keyword))
  console.log(`${question.dimension.padEnd(9)} | ${ai ? 'AI' : backend ? 'BE' : 'GEN'} | ${question.text}`)
}
await call('POST', `/interviews/${sessionId}/abandon`, { token })
console.log(`session ${sessionId} abandoned; questions ${questions.length}`)
