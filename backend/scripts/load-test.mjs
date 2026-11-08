#!/usr/bin/env node
// 本地高并发压测：注册竞争、登录锁定、额度竞争、答题幂等、反馈并发、Refresh 轮换、读接口与报告生成。
// 运行：node scripts/load-test.mjs；只打印状态分布与延迟分位，不打印令牌/邮箱。
const BASE = (process.env.BASE_URL || 'http://127.0.0.1:8081').replace(/\/+$/, '')
const API = `${BASE}/api/v1`
const VERIFY_CODE = process.env.DEV_VERIFY_CODE || '123456'
const PASSWORD = `Load#${Math.random().toString(36).slice(2, 10)}a1`
const stamp = Date.now().toString(36)
let ipSeed = 10

function nextIp() {
  ipSeed += 1
  const third = Math.floor(ipSeed / 200) % 200
  const fourth = (ipSeed % 200) + 20
  return `198.51.${third}.${fourth}`
}

function device(name) {
  return `load-${stamp}-${name}`
}

async function call(method, path, { token, cookie, body, headers = {}, timeout = 30000 } = {}) {
  const requestHeaders = { ...headers }
  if (body !== undefined) requestHeaders['Content-Type'] = 'application/json'
  if (token) requestHeaders.Authorization = `Bearer ${token}`
  if (cookie) requestHeaders.Cookie = cookie
  const started = performance.now()
  try {
    const response = await fetch(`${API}${path}`, {
      method,
      headers: requestHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(timeout),
    })
    const text = await response.text()
    let data = null
    if (text) {
      try {
        data = JSON.parse(text)
      } catch {
        data = text
      }
    }
    return {
      status: response.status,
      data,
      ms: performance.now() - started,
      cookies: response.headers.getSetCookie?.() ?? [],
    }
  } catch (error) {
    return { status: 0, error: error.name, ms: performance.now() - started, cookies: [] }
  }
}

function pickCookie(cookies, name) {
  const prefix = `${name.toLowerCase()}=`
  const hit = cookies.find((value) => value.trim().toLowerCase().startsWith(prefix))
  return hit ? hit.split(';')[0].trim() : null
}

function summarize(results) {
  const counts = {}
  const times = []
  for (const result of results) {
    counts[result.status] = (counts[result.status] ?? 0) + 1
    times.push(result.ms)
  }
  times.sort((a, b) => a - b)
  const percentile = (p) => (times.length ? Math.round(times[Math.min(times.length - 1, Math.floor(times.length * p))]) : 0)
  return {
    count: results.length,
    status: counts,
    p50: percentile(0.5),
    p95: percentile(0.95),
    max: times.length ? Math.round(times[times.length - 1]) : 0,
  }
}

async function register(email, label) {
  const result = await call('POST', '/auth/register', {
    body: {
      email,
      password: PASSWORD,
      nickname: '压测用户',
      verifyCode: VERIFY_CODE,
    },
    headers: { 'X-Real-IP': nextIp(), 'X-Device-Id': device(label) },
  })
  const token = result.data?.accessToken
  return { ...result, token, cookie: pickCookie(result.cookies, 'oc_rt') }
}

async function login(email) {
  const result = await call('POST', '/auth/login', {
    body: { account: email, password: PASSWORD },
    headers: { 'X-Real-IP': nextIp() },
  })
  return { ...result, token: result.data?.accessToken, cookie: pickCookie(result.cookies, 'oc_rt') }
}

async function waitFor(label, fn, predicate, timeoutMs = 20000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const value = await fn()
    if (predicate(value)) return value
    await new Promise((resolve) => setTimeout(resolve, 250))
  }
  throw new Error(`${label} 超时`)
}

async function prepareResumeJd(token, suffix) {
  const resume = await call('POST', '/resumes/manual', {
    token,
    body: {
      title: `压测简历-${suffix}`,
      rawText: `张三（${suffix}），3 年后端开发经验。项目：订单系统重构，使用 Java、Spring Boot、MySQL、Redis、Kafka，将 P99 从 800ms 降到 180ms，QPS 从 800 提升到 2400。`,
      isDefault: true,
    },
  })
  if (resume.status !== 201) throw new Error(`简历创建 ${resume.status}`)
  const resumeId = resume.data.id
  await waitFor('简历解析', async () => (await call('GET', `/resumes/${resumeId}`, { token })).data, (data) => data?.parseStatus === 'READY' || data?.parseStatus === 'FAILED')
  const jd = await call('POST', '/jds', {
    token,
    body: {
      title: '高级后端工程师',
      company: '压测公司',
      rawText: '岗位职责：负责高并发订单系统架构设计与核心链路开发，保障稳定性；参与容量评估、缓存一致性、消息可靠性方案。任职要求：3 年以上 Java 经验，熟悉 Spring Boot、MySQL、Redis、Kafka，具备高并发与分布式事务经验。',
    },
  })
  if (jd.status !== 201) throw new Error(`JD 创建 ${jd.status}`)
  return { resumeId, jdId: jd.data.id }
}

async function startSession(token, resumeId, jdId, questionCount = 3) {
  const created = await call('POST', '/interviews', {
    token,
    headers: { 'Idempotency-Key': crypto.randomUUID() },
    body: {
      resumeId,
      jdId,
      config: {
        questionCount,
        difficulty: 'MIXED',
        interviewerStyle: 'STANDARD',
        answerMode: 'TEXT',
        timeLimitSec: 180,
        language: 'zh-CN',
      },
    },
  })
  if (created.status !== 201) throw new Error(`会话创建 ${created.status}`)
  const sessionId = created.data.id
  await call('POST', `/interviews/${sessionId}/start`, { token })
  const session = await waitFor('出题', async () => (await call('GET', `/interviews/${sessionId}`, { token })).data, (data) => data?.status === 'READY' || data?.status === 'FAILED')
  if (session.status !== 'READY') throw new Error(`出题失败 ${session.status}`)
  return sessionId
}

async function answerAndFinish(token, sessionId) {
  const questions = await call('GET', `/interviews/${sessionId}/questions`, { token })
  const question = questions.data?.items?.[0]
  if (!question) throw new Error('没有可作答题目')
  const answer = await call('POST', `/interviews/${sessionId}/answers`, {
    token,
    headers: { 'Idempotency-Key': crypto.randomUUID() },
    body: {
      questionId: question.id,
      answerText: '我负责订单履约链路重构。背景是大促 QPS 从 800 涨到 2400；我主导容量评估与读写分离，引入 Redis 缓存和 Kafka 解耦，对比同步与异步方案后选择异步补偿。结果 P99 从 800ms 降到 180ms，故障率下降 40%，复盘补了缓存一致性降级预案。',
      durationMs: 60000,
      inputMode: 'TEXT',
    },
  })
  if (answer.status !== 201) throw new Error(`回答 ${answer.status}`)
  const answerId = answer.data.answerId
  await waitFor('反馈', async () => (await call('GET', `/interviews/${sessionId}/answers/${answerId}/feedback`, { token })).data, (data) => data?.overallScore !== undefined, 15000)
  const finish = await call('POST', `/interviews/${sessionId}/finish`, { token })
  if (finish.status !== 202) throw new Error(`结束 ${finish.status}`)
  return finish.data.reportId
}

async function main() {
  const email = `load-primary-${stamp}@offercoach.dev`
  const primary = await register(email, 'primary')
  if (primary.status !== 201) throw new Error(`主账号注册 ${primary.status}`)
  let token = primary.token
  const { resumeId, jdId } = await prepareResumeJd(token, stamp)
  const refreshEmail = `load-refresh-${stamp}@offercoach.dev`
  const refreshAccount = await register(refreshEmail, 'refresh')
  if (refreshAccount.status !== 201) throw new Error(`Refresh 账号注册 ${refreshAccount.status}`)
  console.log('setup: 主账号、简历、JD、Refresh 账号就绪')

  // 1. 同邮箱并发注册
  const raceEmail = `load-race-${stamp}@offercoach.dev`
  const registrations = await Promise.all(
    Array.from({ length: 30 }, (_, index) => register(raceEmail, `race-${index}`)),
  )
  console.log('1. 同邮箱并发注册:', summarize(registrations))
  const successCount = registrations.filter((item) => item.status === 201).length
  console.log(`   创建成功 ${successCount}（预期 1）`)

  // 2. 账号密码错误并发登录（同一账号）
  const loginStorm = await Promise.all(
    Array.from({ length: 100 }, () =>
      call('POST', '/auth/login', {
        body: { account: email, password: 'WrongPass9!' },
        headers: { 'X-Real-IP': nextIp() },
      }),
    ),
  )
  console.log('2. 100 并发错误密码登录:', summarize(loginStorm))

  // 3. 创建会话额度竞争（3 次额度 / 每日 3 次）
  const sessionRace = await Promise.all(
    Array.from({ length: 20 }, () =>
      call('POST', '/interviews', {
        token,
        headers: { 'Idempotency-Key': crypto.randomUUID() },
        body: {
          resumeId,
          jdId,
          config: { questionCount: 3, difficulty: 'MIXED', interviewerStyle: 'STANDARD', answerMode: 'TEXT', timeLimitSec: 180, language: 'zh-CN' },
        },
      }),
    ),
  )
  console.log('3. 20 并发创建会话:', summarize(sessionRace))
  const createdSessions = sessionRace.filter((item) => item.status === 201).map((item) => item.data.id)
  const quota = await call('GET', '/quota', { token })
  console.log('   会话成功数', createdSessions.length, '剩余面试额度', quota.data?.interviewCredits, '每日已用', quota.data?.daily?.sessionsUsed)

  // 4. 同一题并发提交
  const sessionId = createdSessions[0]
  await call('POST', `/interviews/${sessionId}/start`, { token })
  await waitFor('出题', async () => (await call('GET', `/interviews/${sessionId}`, { token })).data, (data) => data?.status === 'READY' || data?.status === 'FAILED')
  const question = (await call('GET', `/interviews/${sessionId}/questions`, { token })).data?.items?.[0]
  const answerRace = await Promise.all(
    Array.from({ length: 20 }, () =>
      call('POST', `/interviews/${sessionId}/answers`, {
        token,
        headers: { 'Idempotency-Key': crypto.randomUUID() },
        body: { questionId: question.id, answerText: '重复提交压测回答，内容长度足够触发评分。', durationMs: 1000, inputMode: 'TEXT' },
      }),
    ),
  )
  console.log('4. 同一题 20 并发提交:', summarize(answerRace))
  const answerId = answerRace.find((item) => item.status === 201)?.data?.answerId

  // 5. 反馈并发读取与生成
  const feedbackReads = await Promise.all(
    Array.from({ length: 30 }, () => call('GET', `/interviews/${sessionId}/answers/${answerId}/feedback`, { token })),
  )
  console.log('5. 30 并发读取反馈:', summarize(feedbackReads))
  const feedbackGenerate = await Promise.all(
    Array.from({ length: 10 }, () => call('POST', `/interviews/${sessionId}/answers/${answerId}/feedback`, { token })),
  )
  console.log('   10 并发生成反馈:', summarize(feedbackGenerate))

  // 6. 同一 Refresh Cookie 并发轮换
  const freshLogin = await login(refreshEmail)
  const cookie = freshLogin.cookie
  const refreshRace = await Promise.all(
    Array.from({ length: 20 }, () => call('POST', '/auth/refresh', { cookie })),
  )
  console.log('6. 同一 Refresh Cookie 20 并发轮换:', summarize(refreshRace))
  const winner = refreshRace.find((item) => item.status === 200)
  if (winner) {
    const winnerCookie = pickCookie(winner.cookies, 'oc_rt')
    const retry = await call('POST', '/auth/refresh', { cookie: winnerCookie })
    console.log('   轮换成功后的 Cookie 再刷新:', retry.status)
  }

  // 7. 读接口并发
  const readSession = await Promise.all(
    Array.from({ length: 120 }, () => call('GET', `/interviews/${sessionId}`, { token })),
  )
  const readQuestions = await Promise.all(
    Array.from({ length: 120 }, () => call('GET', `/interviews/${sessionId}/questions`, { token })),
  )
  console.log('7. 120 并发读取会话:', summarize(readSession))
  console.log('   120 并发读取题目:', summarize(readQuestions))

  // 8. 多账号报告并发生成
  const reportTasks = Array.from({ length: 3 }, async (_, index) => {
    const accountEmail = `load-report-${stamp}-${index}@offercoach.dev`
    const account = await register(accountEmail, `report-${index}`)
    if (account.status !== 201) throw new Error(`报告账号注册 ${account.status}`)
    const prepared = await prepareResumeJd(account.token, `${stamp}-r${index}`)
    const reportSession = await startSession(account.token, prepared.resumeId, prepared.jdId, 2)
    const started = performance.now()
    const reportId = await answerAndFinish(account.token, reportSession)
    const report = await waitFor('报告', async () => (await call('GET', `/reports/${reportId}`, { token: account.token })).data, (data) => data?.status === 'READY' || data?.status === 'FAILED', 60000)
    return { reportId, status: report.status, score: report.overallScore, ms: Math.round(performance.now() - started) }
  })
  const reports = await Promise.all(reportTasks)
  console.log('8. 3 账号并发报告:', reports)
}

main().catch((error) => {
  console.error('LOAD TEST FAILED:', error.message)
  process.exit(1)
})