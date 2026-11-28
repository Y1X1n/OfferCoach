/**
 * 面试会话编排：配置页与「一键体验」共用同一段创建逻辑。
 *
 * 会话创建会扣 1 次面试额度，因此这里只负责编排，不做额度判断；
 * 调用方负责在失败时给出可读提示。
 */
const api = require('./api');

const QUESTION_COUNT = 3;
const TIME_LIMIT_SEC = 180;

function startSession(options) {
  const opts = options || {};
  const onPhase = opts.onPhase || function () {};
  let sessionId = '';

  onPhase('正在创建面试…');
  return api
    .request('/interviews', {
      method: 'POST',
      headers: { 'Idempotency-Key': opts.idempotencyKey || 'mp-' + Date.now() },
      data: {
        resumeId: opts.resumeId,
        jdId: opts.jdId,
        config: {
          questionCount: opts.questionCount || QUESTION_COUNT,
          difficulty: opts.difficulty || 'MIXED',
          interviewerStyle: opts.interviewerStyle || 'STANDARD',
          answerMode: 'TEXT',
          timeLimitSec: TIME_LIMIT_SEC,
          language: 'zh-CN'
        }
      }
    })
    .then(function (session) {
      sessionId = session.id;
      onPhase('正在按你的经历出题…');
      return api
        .request('/interviews/' + sessionId + '/start', { method: 'POST', data: {} })
        .catch(function () {
          // 已启动过会返回状态冲突，继续轮询即可。
          return null;
        });
    })
    .then(function () {
      return api.poll(
        function () {
          return api.request('/interviews/' + sessionId);
        },
        function (detail) {
          return detail.status === 'READY' || detail.status === 'IN_PROGRESS' || detail.status === 'FAILED';
        },
        { timeout: 90000, interval: 2000, timeoutMessage: '出题超时，请稍后在「进行中的面试」继续' }
      );
    })
    .then(function (detail) {
      if (detail.status === 'FAILED') {
        throw new Error('出题失败，请稍后重试');
      }
      return detail;
    })
    .then(function (detail) {
      return { sessionId: sessionId, detail: detail };
    });
}

/**
 * 一键体验：用示例简历 + 示例岗位跑通一场，用于首次进入或评审体验。
 * 复用最近一份可用简历/岗位，避免重复创建浪费额度。
 */
function ensureDemoMaterial() {
  const format = require('./format');
  return Promise.all([
    api.request('/resumes?page=1&size=50').catch(function () {
      return { items: [] };
    }),
    api.request('/jds?page=1&size=50').catch(function () {
      return { items: [] };
    })
  ]).then(function (results) {
    const resumes = (results[0] && results[0].items) || [];
    const jds = (results[1] && results[1].items) || [];
    const readyResume = resumes.filter(function (item) {
      return item.parseStatus === 'READY';
    })[0];

    const resumeTask = readyResume
      ? Promise.resolve(readyResume)
      : api
          .request('/resumes/manual', {
            method: 'POST',
            data: { title: '示例简历（林晓）', rawText: format.SAMPLE_RESUME, isDefault: true }
          })
          .then(function (created) {
            return api.poll(
              function () {
                return api.request('/resumes/' + created.id);
              },
              function (detail) {
                return detail.parseStatus === 'READY' || detail.parseStatus === 'FAILED';
              },
              { timeout: 40000, interval: 1500, timeoutMessage: '示例简历解析超时' }
            );
          });

    const jdTask = jds.length
      ? Promise.resolve(jds[0])
      : api
          .request('/jds', {
            method: 'POST',
            data: {
              title: format.SAMPLE_JD.split('\n')[0].slice(0, 60),
              company: '示例公司',
              rawText: format.SAMPLE_JD
            }
          })
          .catch(function (error) {
            // 同内容 JD 已存在：返回已有 ID
            if (error.code === 'STATE_CONFLICT' && error.details && error.details.existingJdId) {
              return { id: error.details.existingJdId };
            }
            throw error;
          });

    return Promise.all([resumeTask, jdTask]).then(function (pairs) {
      const resume = pairs[0];
      const jd = pairs[1];
      if (!resume || !resume.id) {
        throw new Error('示例简历不可用');
      }
      if (!jd || !jd.id) {
        throw new Error('示例岗位不可用');
      }
      return { resumeId: resume.id, jdId: jd.id };
    });
  });
}

module.exports = {
  startSession: startSession,
  ensureDemoMaterial: ensureDemoMaterial
};
