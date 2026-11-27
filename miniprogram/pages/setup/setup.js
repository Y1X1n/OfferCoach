const api = require('../../utils/api');
const format = require('../../utils/format');

Page({
  data: {
    resumeText: '',
    jdText: '',
    jobs: format.HOT_JOBS,
    activeJob: '',
    questionCount: 5,
    difficulty: 'MIXED',
    creating: false,
    phase: '生成面试题'
  },

  onResumeInput: function (event) {
    this.setData({ resumeText: event.detail.value });
  },

  onJdInput: function (event) {
    this.setData({ jdText: event.detail.value, activeJob: '' });
  },

  fillSample: function (event) {
    const field = event.currentTarget.dataset.field;
    if (field === 'resume') {
      this.setData({ resumeText: format.SAMPLE_RESUME });
      return;
    }
    this.setData({ jdText: format.SAMPLE_JD, activeJob: 'backend' });
  },

  pickJob: function (event) {
    const id = event.currentTarget.dataset.id;
    const job = this.data.jobs.filter(function (item) {
      return item.id === id;
    })[0];
    if (job) {
      this.setData({ jdText: job.jd, activeJob: job.id });
    }
  },

  pickCount: function (event) {
    this.setData({ questionCount: Number(event.currentTarget.dataset.count) });
  },

  pickDifficulty: function (event) {
    this.setData({ difficulty: event.currentTarget.dataset.value });
  },

  resumeTitle: function (text) {
    const firstLine = String(text || '').split('\n')[0] || '';
    const name = firstLine.split(/[，,\s]/)[0].slice(0, 12);
    return name ? name + ' 的简历' : '我的简历';
  },

  setPhase: function (text) {
    this.setData({ phase: text });
  },

  createInterview: function () {
    const self = this;
    if (self.data.creating) {
      return;
    }
    const resumeText = (self.data.resumeText || '').trim();
    const jdText = (self.data.jdText || '').trim();
    if (resumeText.length < 20) {
      my.showToast({ type: 'none', content: '简历内容至少 20 字' });
      return;
    }
    if (jdText.length < 50) {
      my.showToast({ type: 'none', content: '岗位 JD 至少 50 字' });
      return;
    }

    self.setData({ creating: true });
    let resumeId = null;
    let jdId = null;
    let sessionId = null;

    self.setPhase('正在解析简历…');
    api
      .request('/resumes/manual', {
        method: 'POST',
        data: { title: self.resumeTitle(resumeText), rawText: resumeText, isDefault: true }
      })
      .then(function (resume) {
        resumeId = resume.id;
        return api.poll(
          function () {
            return api.request('/resumes/' + resumeId);
          },
          function (detail) {
            return detail.parseStatus === 'READY' || detail.parseStatus === 'FAILED';
          },
          { timeout: 30000, timeoutMessage: '简历解析超时，请稍后重试' }
        );
      })
      .then(function (detail) {
        if (detail.parseStatus === 'FAILED') {
          throw new Error('简历解析失败，请检查内容后重试');
        }
        self.setPhase('正在匹配目标岗位…');
        return api.request('/jds', {
          method: 'POST',
          data: {
            title: jdText.split('\n')[0].slice(0, 60) || '目标岗位',
            company: null,
            rawText: jdText
          }
        });
      })
      .then(function (jd) {
        jdId = jd.id;
        self.setPhase('正在按你的经历出题…');
        return api.request('/interviews', {
          method: 'POST',
          headers: { 'Idempotency-Key': 'mp-' + Date.now() },
          data: {
            resumeId: resumeId,
            jdId: jdId,
            config: {
              questionCount: self.data.questionCount,
              difficulty: self.data.difficulty,
              interviewerStyle: 'STANDARD',
              answerMode: 'TEXT',
              timeLimitSec: 180,
              language: 'zh-CN'
            }
          }
        });
      })
      .then(function (session) {
        sessionId = session.id;
        return api.request('/interviews/' + sessionId + '/start', { method: 'POST', data: {} }).catch(function () {
          // 已经启动过会返回状态冲突，直接进入轮询。
          return null;
        });
      })
      .then(function () {
        return api.poll(
          function () {
            return api.request('/interviews/' + sessionId);
          },
          function (detail) {
            return (
              detail.status === 'READY' ||
              detail.status === 'IN_PROGRESS' ||
              detail.status === 'FAILED'
            );
          },
          { timeout: 60000, interval: 2000, timeoutMessage: '出题超时，请稍后在「最近的面试」里继续' }
        );
      })
      .then(function (detail) {
        if (detail.status === 'FAILED') {
          throw new Error('出题失败，请稍后重试');
        }
        my.redirectTo({ url: '/pages/interview/interview?id=' + sessionId });
      })
      .catch(function (error) {
        self.setData({ creating: false });
        let message = (error && error.message) || '创建失败，请稍后重试';
        if (error && error.code === 'QUOTA_EXCEEDED') {
          message = '今日面试额度已用完，明天再来或稍后在 Web 端查看套餐';
        }
        my.showToast({ type: 'fail', content: message, duration: 3000 });
      });
  }
});
