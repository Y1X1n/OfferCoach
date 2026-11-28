const api = require('../../utils/api');
const session = require('../../utils/session');

Page({
  data: {
    resumes: [],
    jds: [],
    currentResume: null,
    currentJd: null,
    questionCount: 5,
    difficulty: 'MIXED',
    style: 'STANDARD',
    creating: false,
    phase: '生成面试题'
  },

  onLoad: function (query) {
    this.query = query || {};
    if (query && (query.difficulty === 'HARD' || query.difficulty === 'MIXED')) {
      this.setData({ difficulty: query.difficulty });
    }
    if (my.setNavigationBar) {
      my.setNavigationBar({ title: '准备面试' });
    }
    this.load();
  },

  load: function () {
    const self = this;
    Promise.all([
      api.request('/resumes?page=1&size=50').catch(function () {
        return { items: [] };
      }),
      api.request('/jds?page=1&size=50').catch(function () {
        return { items: [] };
      })
    ])
      .then(function (results) {
        const resumes = (results[0] && results[0].items) || [];
        const jds = (results[1] && results[1].items) || [];
        const preferredResume =
          resumes.filter(function (item) {
            return item.id === self.query.resumeId;
          })[0] ||
          resumes.filter(function (item) {
            return item.isDefault;
          })[0] ||
          resumes[0] ||
          null;
        const preferredJd =
          jds.filter(function (item) {
            return item.id === self.query.jdId;
          })[0] ||
          jds[0] ||
          null;

        self.setData({
          resumes: resumes,
          jds: jds.map(function (item) {
            const structured = item.structured || {};
            const mustHave = structured.mustHave || structured.must_have || [];
            return Object.assign({}, item, { skillsText: mustHave.slice(0, 3).join(' / ') });
          }),
          currentResume: self.decorateResume(preferredResume),
          currentJd: self.decorateJd(preferredJd)
        });
        self.resumeId = preferredResume ? preferredResume.id : '';
        self.jdId = preferredJd ? preferredJd.id : '';
      })
      .catch(function (error) {
        api.toast(error.message || '加载失败', 'fail');
      });
  },

  decorateResume: function (item) {
    return item ? { id: item.id, title: item.title, summary: item.summary || '' } : null;
  },

  decorateJd: function (item) {
    return item
      ? { id: item.id, title: item.title, company: item.company, skillsText: item.skillsText || '' }
      : null;
  },

  pickResume: function () {
    const self = this;
    if (!self.data.resumes.length) {
      self.goResume();
      return;
    }
    my.showActionSheet({
      items: self.data.resumes.map(function (item) {
        return item.title;
      }),
      success: function (res) {
        const picked = self.data.resumes[res.tapIndex];
        if (picked) {
          self.resumeId = picked.id;
          self.setData({ currentResume: self.decorateResume(picked) });
        }
      }
    });
  },

  pickJd: function () {
    const self = this;
    if (!self.data.jds.length) {
      self.goJd();
      return;
    }
    my.showActionSheet({
      items: self.data.jds.map(function (item) {
        return item.title;
      }),
      success: function (res) {
        const picked = self.data.jds[res.tapIndex];
        if (picked) {
          self.jdId = picked.id;
          self.setData({ currentJd: self.decorateJd(picked) });
        }
      }
    });
  },

  goResume: function () {
    my.navigateTo({ url: '/pages/resume/list' });
  },

  goJd: function () {
    my.navigateTo({ url: '/pages/jd/create' });
  },

  pickCount: function (event) {
    this.setData({ questionCount: Number(event.currentTarget.dataset.count) });
  },

  pickDifficulty: function (event) {
    this.setData({ difficulty: event.currentTarget.dataset.value });
  },

  pickStyle: function (event) {
    this.setData({ style: event.currentTarget.dataset.value });
  },

  setPhase: function (text) {
    this.setData({ phase: text });
  },

  createInterview: function () {
    const self = this;
    if (self.data.creating) {
      return;
    }
    if (!self.resumeId) {
      api.toast('请先添加简历');
      return;
    }
    if (!self.jdId) {
      api.toast('请先添加目标岗位');
      return;
    }

    self.setData({ creating: true });
    session
      .startSession({
        resumeId: self.resumeId,
        jdId: self.jdId,
        questionCount: self.data.questionCount,
        difficulty: self.data.difficulty,
        interviewerStyle: self.data.style,
        onPhase: self.setPhase
      })
      .then(function (result) {
        my.redirectTo({ url: '/pages/interview/interview?id=' + result.sessionId });
      })
      .catch(function (error) {
        self.setData({ creating: false, phase: '生成面试题' });
        let message = (error && error.message) || '创建失败，请稍后重试';
        if (error && error.code === 'QUOTA_EXCEEDED') {
          message = '面试额度不足，可在「套餐与额度」里升级';
        }
        api.toast(message, 'fail');
      });
  }
});
