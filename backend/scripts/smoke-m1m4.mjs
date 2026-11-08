#!/usr/bin/env node
// OfferCoach M1–M4 冒烟：注册 → 简历 → JD → 面试 → 轮询评分 → 报告。
// 运行：BASE_URL=http://127.0.0.1:8081 node scripts/smoke-m1m4.mjs
// 注意：只打印状态与分数，不打印令牌、邮箱或回答原文。

const BASE_URL = (process.env.BASE_URL || 'http://127.0.0.1:8081').replace(/\/+$/, '');
const API = `${BASE_URL}/api/v1`;
const PASSWORD = process.env.SMOKE_PASSWORD || `Smoke#${Math.random().toString(36).slice(2, 10)}a1`;
const VERIFY_CODE = process.env.DEV_VERIFY_CODE || '123456';
const TIMEOUT_MS = 30000;

function fail(message) {
  console.error(`\n[FAIL] ${message}`);
  process.exit(1);
}

function assert(condition, message) {
  if (!condition) fail(message);
}

function getSetCookies(response) {
  if (typeof response.headers.getSetCookie === 'function') return response.headers.getSetCookie();
  const raw = response.headers.get('set-cookie');
  return raw ? [raw] : [];
}

function pickCookie(setCookies, name) {
  const prefix = `${name.toLowerCase()}=`;
  const hit = setCookies.find((value) => value.trim().toLowerCase().startsWith(prefix));
  return hit ? hit.split(';')[0].trim() : null;
}

async function call(method, path, { token, cookie, body, headers = {} } = {}) {
  const requestHeaders = { ...headers };
  if (body !== undefined && !(body instanceof FormData)) requestHeaders['Content-Type'] = 'application/json';
  if (token) requestHeaders.Authorization = `Bearer ${token}`;
  if (cookie) requestHeaders.Cookie = cookie;
  let response;
  try {
    response = await fetch(`${API}${path}`, {
      method,
      headers: requestHeaders,
      body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch (error) {
    fail(`${method} ${path} 请求失败：${error.message}`);
  }
  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }
  return { status: response.status, data, setCookies: getSetCookies(response) };
}

function expect(label, result, expected) {
  const ok = result.status === expected;
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${label} -> ${result.status}（期望 ${expected}）`);
  if (!ok) {
    console.error(JSON.stringify(result.data).slice(0, 500));
    process.exit(1);
  }
  return result;
}

async function waitFor(label, fn, predicate, timeoutMs = 40000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const value = await fn();
    if (predicate(value)) {
      console.log(`ok   ${label} -> ${value?.status || 'ready'}`);
      return value;
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }
  fail(`${label} 超时`);
}

async function readSse(url, token) {
  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
    signal: AbortSignal.timeout(TIMEOUT_MS),
  });
  assert(response.status === 200, `SSE 状态 ${response.status}`);
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  const events = [];
  let tokens = 0;
  const startedAt = Date.now();
  let firstTokenMs = null;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let boundary = buffer.indexOf('\n\n');
    while (boundary >= 0) {
      const raw = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      const lines = raw.split('\n');
      let eventName = 'message';
      let payload = '';
      for (const line of lines) {
        if (line.startsWith('event:')) eventName = line.slice(6).trim();
        if (line.startsWith('data:')) payload += line.slice(5).trim();
      }
      if (payload) {
        events.push({ event: eventName, data: JSON.parse(payload) });
        if (eventName === 'token') {
          tokens++;
          if (firstTokenMs === null) firstTokenMs = Date.now() - startedAt;
        }
        if (eventName === 'result') {
          try {
            await reader.cancel();
          } catch {
            // 流已结束，忽略。
          }
          return { events, tokens, firstTokenMs };
        }
      }
      boundary = buffer.indexOf('\n\n');
    }
  }
  return { events, tokens, firstTokenMs };
}

console.log(`OfferCoach M1–M4 冒烟 BASE_URL=${BASE_URL}`);
const ACCOUNT = process.env.SMOKE_ACCOUNT || '';
const email = ACCOUNT || `smoke-m1m4+${Date.now()}@offercoach.dev`;

const sessionResponse = ACCOUNT
  ? expect(
      '1/12 POST /auth/login',
      await call('POST', '/auth/login', { body: { account: ACCOUNT, password: PASSWORD } }),
      200,
    )
  : expect(
      '1/12 POST /auth/register',
      await call('POST', '/auth/register', {
        body: { email, password: PASSWORD, nickname: 'M1M4冒烟', verifyCode: VERIFY_CODE },
      }),
      201,
    );
const access = sessionResponse.data?.accessToken;
const refreshCookie = pickCookie(sessionResponse.setCookies, 'oc_rt');
assert(access && refreshCookie, '登录响应缺少令牌或 Cookie');

const runTag = Date.now().toString(36);
const resumeText = [
  `张三（冒烟 ${runTag}），3 年后端开发经验，城市上海。`,
  '教育：某大学计算机科学与技术本科，2018-09 至 2022-06。',
  '工作经历：某互联网公司 Java 后端工程师，负责订单履约系统。',
  '项目：订单系统重构，使用 Java、Spring Boot、MySQL、Redis、Kafka，将 QPS 从 800 提升到 2400。',
  '项目：稳定性治理，设计缓存一致性方案与消息队列重试，故障率下降 40%。',
  '技能：Java、Spring Boot、Spring Cloud、MySQL、Redis、Kafka、Docker、Kubernetes。',
].join('\n');

const createdResume = expect(
  '2/12 POST /resumes/manual',
  await call('POST', '/resumes/manual', {
    token: access,
    body: { title: '冒烟简历', rawText: resumeText, isDefault: true },
  }),
  201,
);
const resumeId = createdResume.data?.id;
assert(resumeId, '简历响应缺少 id');
const readyResume = await waitFor(
  '3/12 简历解析完成',
  async () => {
    const result = await call('GET', `/resumes/${resumeId}`, { token: access });
    return result.data;
  },
  (data) => data?.parseStatus === 'READY' || data?.parseStatus === 'FAILED',
);
assert(readyResume.parseStatus === 'READY', `简历解析失败：${readyResume.parseError}`);

const jdText = [
  '岗位职责：负责高并发订单系统的架构设计与核心链路开发，保障系统稳定性。',
  '任职要求：3 年以上 Java 后端经验，熟悉 Spring Boot、Spring Cloud、MySQL、Redis、Kafka。',
  '加分项：熟悉 Kubernetes、容器化、消息队列与缓存一致性方案。',
  '工作地点：上海。',
].join('\n');
const createdJd = expect(
  '4/12 POST /jds',
  await call('POST', '/jds', {
    token: access,
    body: { title: '高级后端工程师', company: '某互联网公司', rawText: jdText },
  }),
  201,
);
const jdId = createdJd.data?.id;
assert(jdId, 'JD 响应缺少 id');

const matched = expect(
  '5/12 POST /jds/{id}/match',
  await call('POST', `/jds/${jdId}/match`, { token: access, body: { resumeId } }),
  200,
);
assert(typeof matched.data?.matchScore === 'number', '匹配结果缺少 matchScore');

const createdSession = expect(
  '6/12 POST /interviews',
  await call('POST', '/interviews', {
    token: access,
    headers: { 'Idempotency-Key': crypto.randomUUID() },
    body: {
      resumeId,
      jdId,
      config: { questionCount: 4, difficulty: 'MIXED', interviewerStyle: 'STANDARD', answerMode: 'TEXT', timeLimitSec: 180, language: 'zh-CN' },
    },
  }),
  201,
);
const sessionId = createdSession.data?.id;
assert(sessionId, '会话响应缺少 id');

expect(
  '7/12 POST /interviews/{id}/start',
  await call('POST', `/interviews/${sessionId}/start`, { token: access }),
  202,
);
const readySession = await waitFor(
  '8/12 出题完成',
  async () => (await call('GET', `/interviews/${sessionId}`, { token: access })).data,
  (data) => data?.status === 'READY' || data?.status === 'FAILED',
);
assert(readySession.status === 'READY', '出题失败');

const questions = expect(
  '9/12 GET /interviews/{id}/questions',
  await call('GET', `/interviews/${sessionId}/questions`, { token: access }),
  200,
);
const items = questions.data?.items || [];
assert(items.length >= 1, `当前题目为空`);
const bankCount = items.filter((item) => item.source === 'BANK').length;
console.log(`     可见题目 ${items.length}，题库来源 ${bankCount}`);
const first = items[0];
assert(first?.id, '题目响应缺少 id');

let answerId = null;
let firstFeedback = null;
const answersByDimension = {
  PROJECT:
    '我在订单履约系统里负责核心链路重构。背景是大促期间 QPS 从 800 涨到 2400，单体架构撑不住。我主导了容量评估，拆分订单查询与履约写入，引入 Redis 缓存热点数据，并用 Kafka 解耦库存扣减。对比同步与异步方案后选择了异步补偿，把 P99 延迟从 800ms 降到 180ms，故障率下降 40%。复盘发现缓存一致性还需要补降级预案。',
  DEPTH:
    '结论是先更新数据库再删缓存，并配合延迟双删。因为主从复制存在延迟，如果先删缓存，读请求可能把旧值回填。对比先删缓存再更新数据库的方案，我们选择延迟双删，并在并发、超时和数据不一致场景加了重试、幂等与降级兜底。实测缓存命中率 96%，数据库压力下降约 35%。',
  BEHAVIOR:
    '当时和产品在排期上有冲突，产品希望一次性上线全部功能，我判断风险不可控。我先列出线上风险和取舍，组织了对齐会，说服大家先上线核心流程，把次要功能放到下一迭代。最终按时交付，复盘后我们把需求评审和灰度流程提前了一周。',
  FIT:
    '我选择这个岗位是因为看重团队在高并发订单方向的技术积累。我做过三年 Java 后端，主导过支付系统重构，QPS 提升 2 倍，熟悉 Spring Boot、Redis、Kafka。入职后计划 30 天熟悉链路，60 天独立承接需求，90 天主导一次稳定性专项。',
};
function answerFor(dimension) {
  return answersByDimension[dimension] || answersByDimension.DEPTH;
}
for (let index = 0; index < 4; index++) {
  const current = (await call('GET', `/interviews/${sessionId}/questions`, { token: access })).data?.items?.find(
    (item) => item.status === 'ASKED' || item.status === 'PENDING',
  );
  assert(current?.id, `第 ${index + 1} 题不可作答`);
  const answered = expect(
    `10/12 POST /interviews/{id}/answers #${index + 1}`,
    await call('POST', `/interviews/${sessionId}/answers`, {
      token: access,
      headers: { 'Idempotency-Key': crypto.randomUUID() },
      body: {
        questionId: current.id,
        answerText: answerFor(current.dimension),
        durationMs: 96000,
        inputMode: 'TEXT',
      },
    }),
    201,
  );
  answerId = answered.data?.answerId;
  assert(answerId, '回答响应缺少 answerId');
  let currentFeedback = null;
  for (let attempt = 0; attempt < 30; attempt++) {
    const result = await call('GET', `/interviews/${sessionId}/answers/${answerId}/feedback`, {
      token: access,
    });
    if (result.status === 200) {
      currentFeedback = result.data;
      break;
    }
    if (result.status !== 404) {
      fail(`反馈轮询失败：${result.status} ${JSON.stringify(result.data).slice(0, 120)}`);
    }
    await new Promise((resolve) => setTimeout(resolve, 300));
  }
  assert(currentFeedback, `第 ${index + 1} 题反馈轮询超时`);
  if (index === 0) firstFeedback = currentFeedback;
}
console.log('ok   11/12 轮询反馈 4/4，未使用 SSE');

const feedback = firstFeedback ?? {};
assert(feedback?.dimensions?.content !== undefined, '反馈缺少五维评分');

const finished = await call('POST', `/interviews/${sessionId}/finish`, { token: access });
assert(finished.status === 202, `结束会话状态 ${finished.status}`);
const reportId = finished.data?.reportId;
assert(reportId, '结束会话缺少 reportId');
const report = await waitFor(
  '报告生成完成',
  async () => (await call('GET', `/reports/${reportId}`, { token: access })).data,
  (data) => data?.status === 'READY' || data?.status === 'FAILED',
);
assert(report.status === 'READY', `报告状态 ${report.status}`);
assert(report.radar?.length === 5, '报告雷达维度不足');
assert(report.questionBreakdown?.length >= 1, '报告缺少逐题拆解');

const scenarioFeedback = (report.questionBreakdown || []).map((item) => ({
  dimension: item.dimension,
  strength: item.feedback?.strengths?.[0] || '',
  improvement: item.feedback?.improvements?.[0] || '',
  evidence: item.feedback?.evidence?.[0]?.reason || '',
}));
for (const item of scenarioFeedback) {
  console.log(`     ${item.dimension}｜优点：${item.strength}｜改进：${item.improvement}`);
}
assert(
  new Set(scenarioFeedback.map((item) => item.improvement)).size >= 2,
  '逐题改进意见没有按题目场景区分',
);

const quota = expect('GET /quota', await call('GET', '/quota', { token: access }), 200);
assert(
  ACCOUNT ? quota.data?.interviewCredits >= 0 : quota.data?.interviewCredits === 2,
  `面试额度异常：${quota.data?.interviewCredits}`,
);

console.log(
  `\n全部通过：简历 ${resumeId}、JD ${jdId}、会话 ${sessionId}、报告 ${reportId}、总分 ${report.overallScore}、题库占比 ${bankCount}/${items.length}。`,
);