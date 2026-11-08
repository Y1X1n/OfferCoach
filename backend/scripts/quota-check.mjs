#!/usr/bin/env node
const BASE = (process.env.BASE_URL || 'http://127.0.0.1:8081').replace(/\/+$/, '')
const API = `${BASE}/api/v1`
const PASSWORD = `Quota#${Math.random().toString(36).slice(2, 10)}a1`
const email = `quota-${Date.now().toString(36)}@offercoach.dev`
async function call(method, path, { token, body, headers = {} } = {}) {
  const h = { ...headers }
  if (body !== undefined) h['Content-Type'] = 'application/json'
  if (token) h.Authorization = `Bearer ${token}`
  const r = await fetch(`${API}${path}`, { method, headers: h, body: body === undefined ? undefined : JSON.stringify(body) })
  const text = await r.text()
  return { status: r.status, data: text ? JSON.parse(text) : null }
}
const reg = await call('POST', '/auth/register', { body: { email, password: PASSWORD, nickname: '额度压测', verifyCode: '123456' }, headers: { 'X-Real-IP': '198.51.100.201', 'X-Device-Id': `quota-${Date.now()}` } })
if (reg.status !== 201) throw new Error(`register ${reg.status}`)
const token = reg.data.accessToken
const resume = await call('POST', '/resumes/manual', { token, body: { title: '额度压测简历', rawText: '三年 Java 后端经验，负责订单系统重构，使用 Redis 与 Kafka，P99 从 800ms 降到 180ms。', isDefault: true } })
let ready = false
for (let i = 0; i < 30; i += 1) {
  const r = await call('GET', `/resumes/${resume.data.id}`, { token })
  if (r.data?.parseStatus === 'READY') { ready = true; break }
  await new Promise((resolve) => setTimeout(resolve, 300))
}
if (!ready) throw new Error('resume not ready')
const jd = await call('POST', '/jds', { token, body: { title: '后端工程师', company: '压测公司', rawText: '岗位职责：负责高并发订单系统开发与稳定性治理。任职要求：Java、Spring Boot、MySQL、Redis、Kafka，高并发经验。' } })
const statuses = []
for (let i = 0; i < 5; i += 1) {
  const created = await call('POST', '/interviews', { token, headers: { 'Idempotency-Key': crypto.randomUUID() }, body: { resumeId: resume.data.id, jdId: jd.data.id, config: { questionCount: 3, difficulty: 'MIXED', interviewerStyle: 'STANDARD', answerMode: 'TEXT', timeLimitSec: 180, language: 'zh-CN' } } })
  statuses.push(created.status)
  if (created.status === 201) {
    await call('POST', `/interviews/${created.data.id}/abandon`, { token })
  }
}
const quota = await call('GET', '/quota', { token })
console.log({ sessionCreateStatuses: statuses, interviewCredits: quota.data?.interviewCredits, dailyUsed: quota.data?.daily?.sessionsUsed, dailyLimit: quota.data?.daily?.sessionsLimit })
