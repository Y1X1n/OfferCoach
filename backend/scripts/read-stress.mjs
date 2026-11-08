#!/usr/bin/env node
const BASE = (process.env.BASE_URL || 'http://127.0.0.1:8081').replace(/\/+$/, '')
const API = `${BASE}/api/v1`
const PASSWORD = `Read#${Math.random().toString(36).slice(2, 10)}a1`
const email = `read-${Date.now().toString(36)}@offercoach.dev`
async function call(method, path, { token, body, headers = {}, timeout = 30000 } = {}) {
  const h = { ...headers }
  if (body !== undefined) h['Content-Type'] = 'application/json'
  if (token) h.Authorization = `Bearer ${token}`
  const started = performance.now()
  try {
    const r = await fetch(`${API}${path}`, { method, headers: h, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(timeout) })
    const t = await r.text()
    return { status: r.status, ms: performance.now() - started, data: t ? JSON.parse(t) : null }
  } catch (e) { return { status: 0, error: e.name, ms: performance.now() - started } }
}
const reg = await call('POST', '/auth/register', { body: { email, password: PASSWORD, nickname: '读压测', verifyCode: '123456' }, headers: { 'X-Real-IP': '198.51.100.210', 'X-Device-Id': `read-${Date.now()}` } })
if (reg.status !== 201) throw new Error(`register ${reg.status}`)
const token = reg.data.accessToken
const resume = await call('POST', '/resumes/manual', { token, body: { title: '读压测简历', rawText: '三年 Java 后端经验，负责订单系统重构，使用 Redis 与 Kafka，P99 从 800ms 降到 180ms。', isDefault: true } })
for (let i = 0; i < 30; i += 1) { const r = await call('GET', `/resumes/${resume.data.id}`, { token }); if (r.data?.parseStatus === 'READY') break; await new Promise((x) => setTimeout(x, 300)) }
const jd = await call('POST', '/jds', { token, body: { title: '后端工程师', company: '压测公司', rawText: '岗位职责：负责高并发订单系统开发与稳定性治理。任职要求：Java、Spring Boot、MySQL、Redis、Kafka，高并发经验。' } })
const created = await call('POST', '/interviews', { token, headers: { 'Idempotency-Key': crypto.randomUUID() }, body: { resumeId: resume.data.id, jdId: jd.data.id, config: { questionCount: 3, difficulty: 'MIXED', interviewerStyle: 'STANDARD', answerMode: 'TEXT', timeLimitSec: 180, language: 'zh-CN' } } })
const sessionId = created.data.id
await call('POST', `/interviews/${sessionId}/start`, { token })
for (let i = 0; i < 40; i += 1) { const r = await call('GET', `/interviews/${sessionId}`, { token }); if (r.data?.status === 'READY' || r.data?.status === 'FAILED') break; await new Promise((x) => setTimeout(x, 300)) }
function summarize(results) {
  const counts = {}; const times = results.map((r) => r.ms).sort((a, b) => a - b)
  for (const r of results) counts[r.status] = (counts[r.status] ?? 0) + 1
  const p = (q) => Math.round(times[Math.min(times.length - 1, Math.floor(times.length * q))] ?? 0)
  return { count: results.length, status: counts, p50: p(0.5), p95: p(0.95), p99: p(0.99), max: Math.round(times.at(-1) ?? 0) }
}
const reads = await Promise.all(Array.from({ length: 500 }, () => call('GET', `/interviews/${sessionId}`, { token })))
const questions = await Promise.all(Array.from({ length: 500 }, () => call('GET', `/interviews/${sessionId}/questions`, { token })))
console.log('500 并发读取会话:', summarize(reads))
console.log('500 并发读取题目:', summarize(questions))
