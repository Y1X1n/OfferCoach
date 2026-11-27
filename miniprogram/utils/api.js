/**
 * 后端请求封装：统一错误、Token 注入、游客态自动登录。
 *
 * 会话模型（对齐 oc-identity 现有契约，后端零改动）：
 * 1. 首次进入用「设备指纹 + 一次性验证码/邀约码」注册一个体验账号，凭证保存在本地；
 * 2. Access Token 有效期 15 分钟，过期后用本地凭证静默登录；
 * 3. Refresh Cookie 无法在 my.request 中携带，因此不做刷新，直接重新登录。
 */
const config = require('./config');

const SESSION_KEY = 'oc_session';
const DEVICE_KEY = 'oc_device_id';

function storage(key, value) {
  if (value === undefined) {
    const result = my.getStorageSync({ key: key });
    return result && result.data ? result.data : null;
  }
  my.setStorageSync({ key: key, data: value });
  return value;
}

function randomHex(length) {
  const chars = '0123456789abcdef';
  let out = '';
  for (let i = 0; i < length; i += 1) {
    out += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return out;
}

function deviceId() {
  let id = storage(DEVICE_KEY);
  if (!id) {
    id = 'mp_' + randomHex(24);
    storage(DEVICE_KEY, id);
  }
  return id;
}

function randomPassword() {
  // 满足 PasswordPolicy：8-64 位且同时包含字母和数字。
  return 'oc' + randomHex(10) + String(Math.floor(Math.random() * 9000) + 1000);
}

function solveCaptcha(question) {
  const match = String(question || '').match(/(\d+)\s*([+\-*/×÷])\s*(\d+)/);
  if (!match) {
    return null;
  }
  const left = Number(match[1]);
  const right = Number(match[3]);
  const operator = match[2];
  if (operator === '+') return left + right;
  if (operator === '-') return left - right;
  if (operator === '*' || operator === '×') return left * right;
  if (operator === '/' || operator === '÷') return right === 0 ? null : Math.floor(left / right);
  return null;
}

function bizError(status, body) {
  const error = new Error((body && body.message) || '请求失败，请稍后重试');
  error.code = (body && body.code) || 'HTTP_' + status;
  error.status = status;
  error.details = (body && body.details) || {};
  return error;
}

function rawRequest(path, options) {
  const opts = options || {};
  const headers = Object.assign({ 'Content-Type': 'application/json' }, opts.headers || {});
  const session = storage(SESSION_KEY);
  if (session && session.accessToken) {
    headers.Authorization = 'Bearer ' + session.accessToken;
  }
  headers['X-Device-Id'] = deviceId();

  return new Promise(function (resolve, reject) {
    my.request({
      url: config.API_BASE + path,
      method: opts.method || 'GET',
      headers: headers,
      data: opts.data,
      dataType: 'json',
      timeout: config.REQUEST_TIMEOUT,
      success: function (res) {
        const status = res.status || 0;
        if (status >= 200 && status < 300) {
          resolve(res.data || {});
          return;
        }
        reject(bizError(status, res.data));
      },
      fail: function () {
        const error = new Error('网络异常，请检查网络后重试');
        error.code = 'NETWORK_ERROR';
        error.status = 0;
        reject(error);
      }
    });
  });
}

let loginPromise = null;

function registerGuest() {
  const email = deviceId() + '@miniapp.offercoach.local';
  const password = randomPassword();
  const payload = {
    email: email,
    password: password,
    nickname: '体验用户',
    verifyCode: config.ACCESS_CODE || null
  };

  function withCaptcha() {
    return rawRequest('/auth/captcha', { method: 'POST', data: {} }).then(function (challenge) {
      payload.captchaId = challenge.captchaId;
      payload.captchaAnswer = solveCaptcha(challenge.question);
      if (payload.captchaAnswer === null) {
        throw bizError(400, { code: 'CAPTCHA_UNSOLVABLE', message: '验证码解析失败，请重试' });
      }
      return doRegister();
    });
  }

  function doRegister() {
    return rawRequest('/auth/register', { method: 'POST', data: payload }).then(function (auth) {
      return saveSession(auth, email, password);
    });
  }

  return rawRequest('/auth/registration-policy')
    .then(function (policy) {
      if (!policy.registrationOpen) {
        throw bizError(403, { code: 'REGISTRATION_CLOSED', message: '注册暂未开放，请使用体验码进入' });
      }
      if (policy.requireVerifyCode && !config.ACCESS_CODE) {
        throw bizError(400, {
          code: 'ACCESS_CODE_REQUIRED',
          message: '当前环境需要体验码，请在 utils/config.js 中配置 ACCESS_CODE'
        });
      }
      return policy.requireCaptcha ? withCaptcha() : doRegister();
    })
    .catch(function (error) {
      // 同一设备重复注册：直接用自己的凭证登录。
      if (error && error.code === 'VALIDATION_FAILED' && error.details && error.details.fields) {
        return rawRequest('/auth/login', { method: 'POST', data: { account: email, password: password } }).then(
          function (auth) {
            return saveSession(auth, email, password);
          }
        );
      }
      throw error;
    });
}

function saveSession(auth, email, password) {
  const ttl = typeof auth.expiresIn === 'number' ? auth.expiresIn : 900;
  const session = {
    accessToken: auth.accessToken,
    userId: auth.userId,
    nickname: (auth.user && auth.user.nickname) || '体验用户',
    planCode: (auth.user && auth.user.planCode) || 'FREE',
    expiresAt: Date.now() + Math.max(ttl - 60, 60) * 1000,
    email: email,
    password: password
  };
  storage(SESSION_KEY, session);
  return session;
}

function relogin() {
  if (loginPromise) {
    return loginPromise;
  }
  const session = storage(SESSION_KEY);
  const task = session && session.email && session.password
    ? rawRequest('/auth/login', {
        method: 'POST',
        data: { account: session.email, password: session.password }
      }).then(function (auth) {
        return saveSession(auth, session.email, session.password);
      })
    : registerGuest();

  loginPromise = task.then(
    function (value) {
      loginPromise = null;
      return value;
    },
    function (error) {
      loginPromise = null;
      throw error;
    }
  );
  return loginPromise;
}

function ensureSession() {
  const session = storage(SESSION_KEY);
  if (session && session.accessToken && session.expiresAt > Date.now()) {
    return Promise.resolve(session);
  }
  return relogin();
}

function request(path, options) {
  const opts = options || {};
  return ensureSession().then(function () {
    return rawRequest(path, opts).catch(function (error) {
      if (error && error.status === 401 && !opts.retried) {
        return relogin().then(function () {
          return rawRequest(path, Object.assign({}, opts, { retried: true }));
        });
      }
      throw error;
    });
  });
}

function sleep(ms) {
  return new Promise(function (resolve) {
    setTimeout(resolve, ms);
  });
}

/** 轮询直到 predicate 命中或超时；用于出题、评分、报告三个异步阶段。 */
function poll(fetcher, predicate, options) {
  const opts = options || {};
  const interval = opts.interval || 1500;
  const deadline = Date.now() + (opts.timeout || 60000);
  function step() {
    return fetcher().then(function (value) {
      if (predicate(value)) {
        return value;
      }
      if (Date.now() > deadline) {
        throw new Error(opts.timeoutMessage || '处理超时，请稍后重试');
      }
      return sleep(interval).then(step);
    });
  }
  return step();
}

module.exports = {
  request: request,
  ensureSession: ensureSession,
  poll: poll,
  sleep: sleep,
  storage: storage,
  deviceId: deviceId
};
