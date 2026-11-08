#!/usr/bin/env node
// OfferCoach M0 冒烟：注册 → 当前用户 → 额度 → 刷新 → 登出 → 失效校验。
// 运行：node scripts/smoke.mjs
// BASE_URL 默认 http://127.0.0.1:8080（gateway）。
// 注意：Node 的 fetch 不会自动管理 Cookie，这里手工拼接 Cookie 头。

const BASE_URL = (process.env.BASE_URL || 'http://127.0.0.1:8080').replace(/\/+$/, '');
const COOKIE_NAME = 'oc_rt';
const PASSWORD = process.env.SMOKE_PASSWORD || `Smoke#${Math.random().toString(36).slice(2, 10)}a1`;
const VERIFY_CODE = process.env.DEV_VERIFY_CODE || '123456';
const TIMEOUT_MS = 15000;

function fail(message) {
  console.error(`\n[FAIL] ${message}`);
  process.exit(1);
}

function assert(condition, message) {
  if (!condition) fail(message);
}

const nodeMajor = Number(process.versions.node.split('.')[0]);
if (nodeMajor < 18) {
  fail(`需要 Node 18+，当前 ${process.versions.node}`);
}

// Node 18 部分版本没有 getSetCookie；退化为拼接后的 set-cookie 头
function getSetCookies(response) {
  if (typeof response.headers.getSetCookie === 'function') {
    return response.headers.getSetCookie();
  }
  const raw = response.headers.get('set-cookie');
  return raw ? [raw] : [];
}

function pickCookie(setCookies, name) {
  const prefix = `${name.toLowerCase()}=`;
  const hit = setCookies.find((value) => value.trim().toLowerCase().startsWith(prefix));
  return hit ? hit.split(';')[0].trim() : null;
}

async function call(method, path, { token, cookie, body } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;
  if (cookie) headers.Cookie = cookie;

  let response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
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

function expectStatus(label, result, expected) {
  const ok = result.status === expected;
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${label} -> ${result.status}（期望 ${expected}）`);
  if (!ok) {
    const body = typeof result.data === 'string' ? result.data : JSON.stringify(result.data);
    console.error(`响应体：${body}`);
    process.exit(1);
  }
  return result;
}

console.log(`OfferCoach 冒烟测试 BASE_URL=${BASE_URL}`);
const email = `smoke+${Date.now()}@offercoach.dev`;

// 1. 注册：201，拿到 accessToken 与 Refresh Cookie
const registered = expectStatus(
  '1/7 POST /api/v1/auth/register',
  await call('POST', '/api/v1/auth/register', {
    body: { email, password: PASSWORD, nickname: '冒烟测试', verifyCode: VERIFY_CODE },
  }),
  201,
);
const access1 = registered.data?.accessToken;
const refresh1 = pickCookie(registered.setCookies, COOKIE_NAME);
assert(typeof access1 === 'string' && access1.length > 0, '注册响应缺少 accessToken');
assert(refresh1, `注册响应缺少 ${COOKIE_NAME} Cookie`);

// 2. 当前用户
const me = expectStatus(
  '2/7 GET /api/v1/users/me',
  await call('GET', '/api/v1/users/me', { token: access1 }),
  200,
);
assert(me.data && typeof me.data === 'object' && !Array.isArray(me.data), 'users/me 返回体不是对象');

// 3. 额度：FREE 套餐包含 3 次面试
const quota = expectStatus(
  '3/7 GET /api/v1/quota',
  await call('GET', '/api/v1/quota', { token: access1 }),
  200,
);
assert(
  quota.data?.interviewCredits === 3,
  `interviewCredits 期望 3，实际 ${JSON.stringify(quota.data?.interviewCredits)}`,
);

// 4. 刷新：手工带 Cookie，拿到新 access 与轮换 Cookie
const refreshed = expectStatus(
  '4/7 POST /api/v1/auth/refresh',
  await call('POST', '/api/v1/auth/refresh', { cookie: refresh1 }),
  200,
);
const access2 = refreshed.data?.accessToken;
const refresh2 = pickCookie(refreshed.setCookies, COOKIE_NAME);
assert(typeof access2 === 'string' && access2.length > 0, '刷新响应缺少 accessToken');
assert(access2 !== access1, '刷新后 accessToken 未变化');
assert(refresh2 && refresh2 !== refresh1, '刷新后 Refresh Cookie 未轮换');

// 5. 登出：204
expectStatus(
  '5/7 POST /api/v1/auth/logout',
  await call('POST', '/api/v1/auth/logout', { token: access2, cookie: refresh2 }),
  204,
);

// 6. 登出后旧 access 不可再用
expectStatus(
  '6/7 GET /api/v1/users/me（旧 access）',
  await call('GET', '/api/v1/users/me', { token: access2 }),
  401,
);

// 7. 已轮换的旧 refresh 不可再用
expectStatus(
  '7/7 POST /api/v1/auth/refresh（旧 refresh）',
  await call('POST', '/api/v1/auth/refresh', { cookie: refresh1 }),
  401,
);

console.log('\n全部通过：注册、当前用户、额度、刷新、登出、令牌失效均符合预期。');