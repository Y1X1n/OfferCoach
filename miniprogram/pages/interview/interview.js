const api = require('../../utils/api');
const format = require('../../utils/format');
const config = require('../../utils/config');

const DIFFICULTY_LABELS = { EASY: '简单', MEDIUM: '中等', HARD: '偏难' };
const MAX_ANSWER_LENGTH = 2000;

function friendlyModel(model) {
  if (!model) {
    return '内置评分规则';
  }
  if (/^rules/i.test(model)) {
    return '内置评分规则（AI 暂不可用时的降级）';
  }
  return model;
}

function decorate(question, total) {
  return {
    id: question.id,
    seq: question.seq,
    total: total,
    text: question.text,
    status: question.status,
    dimensionLabel: format.dimensionLabel(question.dimension),
    difficultyLabel: DIFFICULTY_LABELS[question.difficulty] || question.difficulty || '',
    whyThisQuestion: question.whyThisQuestion || '',
    isFollowUp: false
  };
}

/** 后端题目逐题解锁：只返回「已答/已跳过 + 当前题」。 */
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

function draftKey(sessionId, questionId) {
  return 'draft:' + sessionId + ':' + questionId;
}

Page({
  data: {
    loading: true,
    sessionId: '',
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
    modelText: '—',
    paused: false,
    speaking: false,
    recording: false,
    recordSeconds: 0,
    voicePath: '',
    followUpUsed: false
  },

  onLoad: function (query) {
    this.sessionId = (query && query.id) || '';
    this.setData({ sessionId: this.sessionId });
    if (my.setNavigationBar) {
      my.setNavigationBar({ title: '模拟面试' });
    }
    this.load();
  },

  onUnload: function () {
    this.stopTimers();
    if (this.audio && this.audio.destroy) {
      this.audio.destroy();
    }
  },

  stopTimers: function () {
    if (this.recordTimer) {
      clearInterval(this.recordTimer);
      this.recordTimer = null;
    }
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
        api.toast(error.message || '题目加载失败', 'fail');
      });
  },

  apply: function (detail, items) {
    const progress = detail.progress || { answered: 0, skipped: 0, total: 0 };
    const total = progress.total || items.length;
    const done = (progress.answered || 0) + (progress.skipped || 0);
    const currentItem = pickCurrent(items, detail.currentSeq || 1);
    const current = currentItem ? decorate(currentItem, total) : null;
    const draft = current ? api.storage(draftKey(this.sessionId, current.id)) || '' : '';

    this.setData({
      loading: false,
      current: current,
      total: total,
      answeredCount: progress.answered || 0,
      skippedCount: progress.skipped || 0,
      progressPercent: total ? Math.round((done / total) * 100) : 0,
      answerText: draft,
      feedback: null,
      submitting: false,
      phase: '',
      paused: detail.status === 'PAUSED',
      isLast: !!current && current.seq >= total,
      finished: !current && done > 0 && done >= total,
      voicePath: '',
      recordSeconds: 0,
      followUpUsed: (api.storage('fu:' + this.sessionId) || 0) >= 2
    });
    this.startedAt = Date.now();
  },

  onAnswerInput: function (event) {
    const value = event.detail.value;
    this.setData({ answerText: value });
    if (this.data.current) {
      api.storage(draftKey(this.sessionId, this.data.current.id), value);
    }
  },

  submitAnswer: function () {
    const self = this;
    if (self.data.submitting || !self.data.current || self.data.paused) {
      return;
    }
    const text = (self.data.answerText || '').trim();
    if (text.length < 10) {
      api.toast('至少写 10 个字，再提交评分');
      return;
    }

    const questionId = self.data.current.id;
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
        api.storage(draftKey(self.sessionId, questionId), '');
        return self.loadFeedback(answer.answerId);
      })
      .then(function (feedback) {
        self.showFeedback(feedback);
      })
      .catch(function (error) {
        self.finishSubmit(error);
      });
  },

  submitVoice: function () {
    const self = this;
    if (self.data.submitting || !self.data.current || !self.data.voicePath) {
      return;
    }
    const questionId = self.data.current.id;
    self.setData({ submitting: true, phase: '正在转写…' });

    api
      .upload('/interviews/' + self.sessionId + '/answers/audio', self.data.voicePath, {
        questionId: questionId,
        durationMs: String(self.data.recordSeconds * 1000)
      })
      .then(function (answer) {
        return self.loadFeedback(answer.answerId);
      })
      .then(function (feedback) {
        self.setData({ voicePath: '' });
        self.showFeedback(feedback);
      })
      .catch(function (error) {
        self.setData({ submitting: false, phase: '' });
        if (error.code === 'ASR_FAILED') {
          api.toast('语音转写失败，请改用文字输入', 'fail');
          return;
        }
        api.toast(error.message || '语音提交失败', 'fail');
      });
  },

  loadFeedback: function (answerId) {
    const self = this;
    return api
      .request('/interviews/' + self.sessionId + '/answers/' + answerId + '/feedback', { method: 'POST', data: {} })
      .catch(function () {
        return null;
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
      });
  },

  showFeedback: function (feedback) {
    const self = this;
    const answered = self.data.answeredCount + 1;
    self.setData({
      submitting: false,
      phase: '',
      isLast: self.data.isLast,
      finished: false,
      answeredCount: answered,
      progressPercent: self.data.total
        ? Math.round(((answered + self.data.skippedCount) / self.data.total) * 100)
        : 0,
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
  },

  finishSubmit: function (error) {
    this.setData({ submitting: false, phase: '' });
    if (error && error.code === 'DUPLICATE_ANSWER') {
      this.load();
      return;
    }
    api.toast((error && error.message) || '提交失败', 'fail');
  },

  skipQuestion: function () {
    const self = this;
    if (self.data.submitting || !self.data.current) {
      return;
    }
    self.setData({ submitting: true, phase: '跳过中…' });
    api
      .request('/interviews/' + self.sessionId + '/questions/' + self.data.current.id + '/skip', {
        method: 'POST',
        data: {}
      })
      .then(function () {
        self.setData({ submitting: false, phase: '' });
        self.load();
      })
      .catch(function (error) {
        self.setData({ submitting: false, phase: '' });
        api.toast(error.message || '跳过失败', 'fail');
      });
  },

  nextQuestion: function () {
    if (this.data.isLast) {
      this.finishInterview();
      return;
    }
    this.load();
    my.pageScrollTo({ scrollTop: 0, duration: 200 });
  },

  followUp: function () {
    const self = this;
    if (!self.data.current || self.data.submitting) {
      return;
    }
    api
      .request('/interviews/' + self.sessionId + '/questions/' + self.data.current.id + '/follow-up', {
        method: 'POST',
        data: {}
      })
      .then(function () {
        const used = (api.storage('fu:' + self.sessionId) || 0) + 1;
        api.storage('fu:' + self.sessionId, used);
        self.setData({ followUpUsed: used >= 2 });
        api.toast('已加入追问，稍后出现', 'success');
      })
      .catch(function (error) {
        const reason = (error.details && error.details.reason) || error.message;
        api.toast(reason || '追问失败', 'fail');
      });
  },

  togglePause: function () {
    const self = this;
    const target = self.data.paused ? 'resume' : 'pause';
    api
      .request('/interviews/' + self.sessionId + '/' + target, { method: 'POST', data: {} })
      .then(function () {
        self.setData({ paused: !self.data.paused });
        api.toast(target === 'pause' ? '已暂停' : '已恢复', 'success');
      })
      .catch(function (error) {
        api.toast(error.message || '操作失败', 'fail');
      });
  },

  abandon: function () {
    const self = this;
    my.confirm({
      title: '放弃这场面试',
      content: '未提交任何回答时会返还额度；已作答则不再返还。',
      confirmButtonText: '放弃',
      cancelButtonText: '继续练习',
      success: function (res) {
        if (!res.confirm) {
          return;
        }
        api
          .request('/interviews/' + self.sessionId + '/abandon', { method: 'POST', data: {} })
          .then(function () {
            api.toast('已放弃', 'success');
            setTimeout(function () {
              my.navigateBack();
            }, 600);
          })
          .catch(function (error) {
            api.toast(error.message || '操作失败', 'fail');
          });
      }
    });
  },

  speak: function () {
    const self = this;
    if (self.data.speaking) {
      if (self.audio) {
        self.audio.stop();
      }
      self.setData({ speaking: false });
      return;
    }
    if (!self.data.current) {
      return;
    }
    const url =
      config.API_BASE.replace(/\/api\/v1\/?$/, '') +
      '/api/v1/interviews/' +
      self.sessionId +
      '/questions/' +
      self.data.current.id +
      '/speech';
    const session = api.storage('oc_session') || {};
    my.showLoading({ content: '加载语音…' });
    my.downloadFile({
      url: url,
      header: { Authorization: 'Bearer ' + (session.accessToken || '') },
      success: function (res) {
        my.hideLoading();
        self.playAudio(res.apFilePath);
      },
      fail: function () {
        my.hideLoading();
        api.toast('朗读暂不可用', 'fail');
      }
    });
  },

  playAudio: function (filePath) {
    const self = this;
    if (!my.createInnerAudioContext) {
      api.toast('当前环境不支持音频播放', 'fail');
      return;
    }
    if (!self.audio) {
      self.audio = my.createInnerAudioContext();
      self.audio.onEnded(function () {
        self.setData({ speaking: false });
      });
      self.audio.onError(function () {
        self.setData({ speaking: false });
        api.toast('播放失败', 'fail');
      });
    }
    self.audio.src = filePath;
    self.audio.play();
    self.setData({ speaking: true });
  },

  toggleRecord: function () {
    const self = this;
    if (self.data.recording) {
      self.stopRecord();
      return;
    }
    const manager = my.getRecorderManager ? my.getRecorderManager() : null;
    if (!manager) {
      api.toast('当前环境不支持录音，请用文字作答', 'fail');
      return;
    }
    this.recorder = manager;
    manager.onStop(function (res) {
      self.stopTimers();
      const path = res.tempFilePath || res.apFilePath || '';
      const seconds = Math.max(1, Math.round((Date.now() - (self.recordStartedAt || Date.now())) / 1000));
      self.setData({ recording: false, recordSeconds: seconds, voicePath: path });
      if (!path) {
        api.toast('没有录到音频，请重试', 'fail');
      }
    });
    manager.onError(function () {
      self.stopTimers();
      self.setData({ recording: false });
      api.toast('录音失败，请检查麦克风权限', 'fail');
    });
    manager.start({ duration: 120000, format: 'wav', sampleRate: 16000, numberOfChannels: 1 });

    this.recordStartedAt = Date.now();
    this.setData({ recording: true, recordSeconds: 0, voicePath: '' });
    this.recordTimer = setInterval(function () {
      self.setData({ recordSeconds: Math.round((Date.now() - self.recordStartedAt) / 1000) });
    }, 1000);
  },

  stopRecord: function () {
    if (this.recorder) {
      this.recorder.stop();
    }
    this.stopTimers();
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
        api.toast(error.message || '报告生成失败', 'fail');
      });
  }
});
