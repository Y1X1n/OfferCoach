#!/usr/bin/env node
// 验证「联网搜索优先 + 行业通用」出题链路：注册 → 简历 → 岗位 → 建会话 → 看题目。
// 运行：
//   node backend/scripts/verify-search-first.mjs                      # 技术岗样例
//   INDUSTRY=nursing node backend/scripts/verify-search-first.mjs      # 护理岗样例
//   VERIFY_EMAIL=... VERIFY_PASSWORD=... node ...                      # 复用已有账号
// 说明：只打印题目与来源，不打印令牌与简历原文。

import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE_URL = (process.env.BASE_URL || 'http://127.0.0.1:8081').replace(/\/+$/, '');
const API = `${BASE_URL}/api/v1`;
const VERIFY_CODE = process.env.DEV_VERIFY_CODE || '123456';
const INDUSTRY = (process.env.INDUSTRY || 'tech').toLowerCase();
const EMAIL = process.env.VERIFY_EMAIL;
const PASSWORD = process.env.VERIFY_PASSWORD || 'oc12345678ab';
const QUESTION_COUNT = Number(process.env.QUESTION_COUNT || 3);
const TIMEOUT_MS = 30000;

const HERE = dirname(fileURLToPath(import.meta.url));
const SAMPLES = join(HERE, '..', '..', 'agent', 'samples', INDUSTRY === 'nursing' ? 'nursing' : '');

const INDUSTRY_KEYWORDS =
  INDUSTRY === 'nursing'
    ? ['护理', '护士', '患者', '交接班', '院感', '输液', '医嘱', '病房']
    : ['项目', '系统', '性能', '架构', '缓存', '接口', '数据'];

function fail(message) {
  console.error(`\n[FAIL] ${message}`);
  process.exit(1);
}

function assert(condition, message) {
  if (!condition) fail(message);
}

async function call(method, path, { token, body, headers } = {}) {
  const finalHeaders = { ...(headers || {}) };
  if (body !== undefined) finalHeaders['Content-Type'] = 'application/json';
  if (token) finalHeaders.Authorization = `Bearer ${token}`;
  let response;
  try {
    response = await fetch(`${API}${path}`, {
      method,
      headers: finalHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    });
  } catch (error) {
    fail(`${method} ${path} 请求失败：${error.message}`);
  }
  const text = await response.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = { raw: text };
  }
  return { status: response.status, data };
}

function solveCaptcha(question) {
  const match = String(question || '').match(/(\d+)\s*([+\-*/×÷])\s*(\d+)/);
  if (!match) return null;
  const left = Number(match[1]);
  const right = Number(match[3]);
  const operator = match[2];
  if (operator === '+') return left + right;
  if (operator === '-') return left - right;
  if (operator === '*' || operator === '×') return left * right;
  if (operator === '/' || operator === '÷') return right === 0 ? null : Math.floor(left / right);
  return null;
}

async function authenticate() {
  if (EMAIL) {
    const login = await call('POST', '/auth/login', { body: { account: EMAIL, password: PASSWORD } });
    assert(login.status === 200, `登录失败：${login.status} ${JSON.stringify(login.data).slice(0, 160)}`);
    return { token: login.data.accessToken, email: EMAIL };
  }
  const email = `verify_${Date.now()}@miniapp.offercoach.local`;
  const device = `verify_${Date.now()}`;
  const policy = await call('GET', '/auth/registration-policy');
  const payload = { email, password: PASSWORD, nickname: '验证用户', verifyCode: VERIFY_CODE };

  // 生产环境不配固定验证码时必须过算术验证码，这里按真实客户端的方式解出来。
  if (policy.status === 200 && policy.data && policy.data.requireCaptcha) {
    const challenge = await call('POST', '/auth/captcha', { body: {} });
    if (challenge.status !== 200) {
      fail(`获取验证码失败：${challenge.status}`);
    }
    payload.captchaId = challenge.data.captchaId;
    payload.captchaAnswer = solveCaptcha(challenge.data.question);
    if (payload.captchaAnswer === null) {
      fail(`无法解析验证码题目：${challenge.data.question}`);
    }
  }

  const register = await call('POST', '/auth/register', {
    body: payload,
    headers: { 'X-Device-Id': device },
  });
  if (register.status === 201) {
    return { token: register.data.accessToken, email };
  }
  // 注册被限流/关闭时，尝试用同邮箱登录（上次跑过的话账号已存在）
  const login = await call('POST', '/auth/login', { body: { account: email, password: PASSWORD } });
  if (login.status === 200) {
    return { token: login.data.accessToken, email };
  }
  fail(`注册失败：${register.status} ${JSON.stringify(register.data).slice(0, 200)}`);
}

async function waitFor(check, { attempts = 40, intervalMs = 2000, description }) {
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    const value = await check();
    if (value) return value;
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
  fail(`${description} 超时`);
}

const resumeText = readFileSync(join(SAMPLES, 'resume.md'), 'utf8');
const jdText = readFileSync(join(SAMPLES, 'jd.md'), 'utf8');

const { token, email } = await authenticate();
console.log(`账号：${email}`);
console.log(`行业样例：${INDUSTRY}（${SAMPLES}）`);

// 1. 简历
const resumeCreated = await call('POST', '/resumes/manual', {
  token,
  body: { title: `验证简历-${INDUSTRY}`, rawText: resumeText, isDefault: true },
});
assert([200, 201].includes(resumeCreated.status), `创建简历失败：${resumeCreated.status}`);
const resumeId = resumeCreated.data.id;
const resumeReady = await waitFor(
  async () => {
    const detail = await call('GET', `/resumes/${resumeId}`, { token });
    if (detail.status !== 200) return null;
    return detail.data.parseStatus === 'READY' ? detail.data : null;
  },
  { description: '简历解析' },
);
console.log(`简历：${resumeId}（READY）`);

// 2. 岗位 JD
const jdCreated = await call('POST', '/jds', {
  token,
  body: { title: jdText.split('\n')[0].slice(0, 60), company: null, rawText: jdText },
});
if (jdCreated.status === 409 && jdCreated.data?.details?.existingJdId) {
  console.log(`岗位：复用已有 JD ${jdCreated.data.details.existingJdId}`);
}
const jdId = jdCreated.status === 201 ? jdCreated.data.id : jdCreated.data?.details?.existingJdId;
assert(jdId, `创建 JD 失败：${jdCreated.status} ${JSON.stringify(jdCreated.data).slice(0, 200)}`);
console.log(`岗位：${jdId}`);

// 3. 会话与出题
const created = await call('POST', '/interviews', {
  token,
  headers: { 'Idempotency-Key': crypto.randomUUID() },
  body: {
    resumeId: String(resumeId),
    jdId: String(jdId),
    config: {
      questionCount: QUESTION_COUNT,
      difficulty: 'MIXED',
      interviewerStyle: 'STANDARD',
      answerMode: 'TEXT',
      timeLimitSec: 180,
      language: 'zh-CN',
    },
  },
});
assert(created.status === 201, `创建会话失败：${created.status} ${JSON.stringify(created.data).slice(0, 200)}`);
const sessionId = created.data.id;
console.log(`会话：${sessionId}，开始出题…`);

await call('POST', `/interviews/${sessionId}/start`, { token });
const detail = await waitFor(
  async () => {
    const current = await call('GET', `/interviews/${sessionId}`, { token });
    if (current.status !== 200) return null;
    return ['READY', 'IN_PROGRESS', 'FAILED'].includes(current.data.status) ? current.data : null;
  },
  { attempts: 45, description: '出题' },
);
assert(detail.status !== 'FAILED', '出题失败（会话 FAILED）');

const questions = await call('GET', `/interviews/${sessionId}/questions`, { token });
assert(questions.status === 200, `读取题目失败：${questions.status}`);
const items = questions.data.items || [];
assert(items.length > 0, '没有拿到题目');

const summary = items.map((item) => ({
  seq: item.seq,
  dimension: item.dimension,
  source: item.source,
  text: item.text,
}));

console.log('\n题目：');
for (const item of summary) {
  console.log(`  #${item.seq} [${item.dimension}/${item.source}] ${item.text}`);
  const why = items.find((q) => q.seq === item.seq)?.whyThisQuestion;
  if (why) console.log(`      因为：${why}`);
}

const joined = summary.map((item) => item.text).join(' ');
const hits = INDUSTRY_KEYWORDS.filter((keyword) => joined.includes(keyword));
console.log(`\n行业关键词命中：${hits.length}/${INDUSTRY_KEYWORDS.length}${hits.length ? '（' + hits.join('、') + '）' : ''}`);
if (hits.length === 0) {
  console.log('[warn] 题目里没有行业关键词：确认搜索与模型是否启用（见 docs/competition/verify.md）');
}

const generated = summary.filter((item) => item.source === 'GENERATED').length;
const bank = summary.filter((item) => item.source === 'BANK').length;
console.log(`来源分布：GENERATED ${generated} · BANK ${bank}`);

console.log('\n想确认搜索证据与题库沉淀，执行：');
console.log(`  docker exec offercoach-postgres-1 psql -U oc -d offercoach -c "select seq, source, retrieval_json->>'source' as retrieval from interview_question where session_id=${sessionId} order by seq;"`);
console.log(`  docker exec offercoach-postgres-1 psql -U oc -d offercoach -c "select id, job_family, status, left(stem, 40) from question_bank where source_repo='ai-search' order by id desc limit 5;"`);

// 清理：放弃未作答的会话，避免占额度（已作答则不返还不影响）
await call('POST', `/interviews/${sessionId}/abandon`, { token });
console.log('\n[OK] 出题链路跑通，会话已放弃（未作答会返还额度）');
