const api = require('../../utils/api');
const format = require('../../utils/format');

const DIFFICULTY_LABELS = { EASY: '简单', MEDIUM: '中等', HARD: '偏难' };

function friendlyModel(model) {
  if (!model) {
    return '内置评分规则';
  }
  if (/^rules/i.test(model)) {
    return '内置评分规则（AI 暂不可用时的降级）';
  }
  return model;
}
const MAX_ANSWER_LENGTH = 2000;

function decorate(question, total) {
  return {
    id: question.id,
    seq: question.seq,
    total: total,
    text: question.text,
    status: question.status,
    dimensionLabel: format.dimensionLabel(question.dimension),
    difficultyLabel: DIFFICULTY_LABELS[question.difficulty] || question.difficulty || '',
    whyThisQuestion: question.whyThisQuestion || ''
  };
}

/**
 * 后端题目逐题解锁：只返回「已答/已跳过 + 当前题」。
 * 当前题取 status 为 ASKED/PENDING 且 seq 不小于 currentSeq 的第一条。
 */
function pickCurrent(items, currentSeq) {
  let picked = null;
  for (let i = 0; i < items.length; i += 1) {
    const item = items[i];
    if (item.status === 'ANSWERED' || item.status === 'SKIPPED') {
      continue;
    }
    if (item.seq < currentSeq) {
      continue;
    }
    if (!picked || item.seq < picked.seq) {
      picked = item;
    }
  }
  return picked;
}

Page({
  data: {
    loading: true,
    sessionId: '',
    questions: [],
    current: null,
    total: 0,
    answeredCount: 0,
    skippedCount: 0,
    progressPercent: 0,
    answerText: '',
    maxLength: MAX_ANSWER_LENGTH,
    submitting: false,
    phase: '',
    feedback: null,
    isLast: false,
    finished: false,
    modelText: '—'
  },

  onLoad: function (query) {
    this.sessionId = (query && query.id) || '';
    this.setData({ sessionId: this.sessionId });
    this.load();
  },

  load: function () {
    const self = this;
    self.setData({ loading: !self.data.total });
    Promise.all([
      api.request('/interviews/' + self.sessionId),
      api.request('/interviews/' + self.sessionId + '/questions')
    ])
      .then(function (results) {
        self.apply(results[0], (results[1] && results[1].items) || []);
      })
      .catch(function (error) {
        self.setData({ loading: false });
        my.showToast({ type: 'fail', content: error.message || '题目加载失败', duration: 2500 });
      });
  },

  apply: function (detail, items) {
    const progress = detail.progress || { answered: 0, skipped: 0, total: 0 };
    const total = progress.total || items.length;
    const done = (progress.answered || 0) + (progress.skipped || 0);
    const currentItem = pickCurrent(items, detail.currentSeq || 1);
    const current = currentItem ? decorate(currentItem, total) : null;

    this.setData({
      loading: false,
      questions: items.map(function (item) {
        return decorate(item, total);
      }),
      current: current,
      total: total,
      answeredCount: progress.answered || 0,
      skippedCount: progress.skipped || 0,
      progressPercent: total ? Math.round((done / total) * 100) : 0,
      answerText: '',
      feedback: null,
      submitting: false,
      phase: '',
      isLast: !!current && current.seq >= total,
      finished: !current && done >= total && total > 0,
      modelText: this.data.modelText
    });
    this.startedAt = Date.now();
  },

  onAnswerInput: function (event) {
    this.setData({ answerText: event.detail.value });
  },

  submitAnswer: function () {
    const self = this;
    if (self.data.submitting || !self.data.current) {
      return;
    }
    const text = (self.data.answerText || '').trim();
    if (text.length < 10) {
      my.showToast({ type: 'none', content: '至少写 10 个字，再提交评分' });
      return;
    }

    const questionId = self.data.current.id;
    const isLast = self.data.isLast;
    let answerId = null;
    self.setData({ submitting: true, phase: '正在评分…' });

    api
      .request('/interviews/' + self.sessionId + '/answers', {
        method: 'POST',
        headers: { 'Idempotency-Key': 'mp-' + questionId + '-' + Date.now() },
        data: {
          questionId: questionId,
          answerText: text,
          durationMs: Date.now() - (self.startedAt || Date.now()),
          inputMode: 'TEXT'
        }
      })
      .then(function (answer) {
        answerId = answer.answerId;
        return api
          .request('/interviews/' + self.sessionId + '/answers/' + answerId + '/feedback', {
            method: 'POST',
            data: {}
          })
          .catch(function () {
            return null;
          });
      })
      .then(function (feedback) {
        if (feedback && feedback.overallScore !== null && feedback.overallScore !== undefined) {
          return feedback;
        }
        return api.poll(
          function () {
            return api.request('/interviews/' + self.sessionId + '/answers/' + answerId + '/feedback');
          },
          function (value) {
            return value && value.overallScore !== null && value.overallScore !== undefined;
          },
          { timeout: 45000, timeoutMessage: '评分超时，请稍后重试' }
        );
      })
      .then(function (feedback) {
        const answered = self.data.answeredCount + 1;
        self.setData({
          submitting: false,
          phase: '',
          isLast: isLast,
          finished: false,
          answeredCount: answered,
          progressPercent: self.data.total ? Math.round(((answered + self.data.skippedCount) / self.data.total) * 100) : 0,
          feedback: {
            overallScore: feedback.overallScore,
            dimensions: format.dimensionsToRows(feedback.dimensions),
            strengths: feedback.strengths || [],
            improvements: feedback.improvements || [],
            evidence: (feedback.evidence || []).slice(0, 2),
            rewrittenAnswer: feedback.rewrittenAnswer || '',
            degraded: !!feedback.degraded
          },
          modelText: friendlyModel(feedback.model)
        });
        my.pageScrollTo({ scrollTop: 0, duration: 200 });
      })
      .catch(function (error) {
        self.setData({ submitting: false, phase: '' });
        my.showToast({ type: 'fail', content: error.message || '提交失败', duration: 2500 });
      });
  },

  skipQuestion: function () {
    const self = this;
    if (self.data.submitting || !self.data.current) {
      return;
    }
    const questionId = self.data.current.id;
    self.setData({ submitting: true, phase: '跳过中…' });
    api
      .request('/interviews/' + self.sessionId + '/questions/' + questionId + '/skip', {
        method: 'POST',
        data: {}
      })
      .then(function () {
        self.setData({ submitting: false, phase: '' });
        self.load();
      })
      .catch(function (error) {
        self.setData({ submitting: false, phase: '' });
        my.showToast({ type: 'fail', content: error.message || '跳过失败', duration: 2000 });
      });
  },

  nextQuestion: function () {
    const self = this;
    if (self.data.isLast) {
      self.finishInterview();
      return;
    }
    self.load();
    my.pageScrollTo({ scrollTop: 0, duration: 200 });
  },

  finishInterview: function () {
    const self = this;
    if (self.data.submitting) {
      return;
    }
    self.setData({ submitting: true, phase: '正在生成复盘报告…' });
    api
      .request('/interviews/' + self.sessionId + '/finish', { method: 'POST', data: {} })
      .then(function (result) {
        if (result && result.reportId) {
          return result.reportId;
        }
        return api
          .poll(
            function () {
              return api.request('/interviews/' + self.sessionId);
            },
            function (detail) {
              return detail && detail.reportId;
            },
            { timeout: 30000 }
          )
          .then(function (detail) {
            return detail.reportId;
          });
      })
      .then(function (reportId) {
        my.redirectTo({ url: '/pages/report/report?id=' + reportId });
      })
      .catch(function (error) {
        self.setData({ submitting: false, phase: '' });
        my.showToast({ type: 'fail', content: error.message || '报告生成失败', duration: 2500 });
      });
  }
});
