const api = require('../../utils/api');
const format = require('../../utils/format');

Page({
  data: {
    loading: true,
    planLabel: '免费版',
    quota: null,
    active: null,
    reports: [],
    resumeReady: false,
    jdCount: 0,
    reportTotal: 0,
    avgText: ''
  },

  onShow: function () {
    this.load();
  },

  load: function () {
    const self = this;
    self.setData({ loading: true });
    api
      .request('/dashboard')
      .then(function (board) {
        const quota = board.quota || null;
        const active = board.activeSession
          ? {
              id: board.activeSession.id,
              statusLabel: format.statusLabel(board.activeSession.status),
              jdTitle: board.activeSession.jdTitle,
              resumeTitle: board.activeSession.resumeTitle,
              answeredCount: board.activeSession.answeredCount || 0,
              questionCount: board.activeSession.questionCount || 0,
              timeText: format.timeAgo(board.activeSession.lastActiveAt)
            }
          : null;
        const reports = (board.recentReports || []).map(function (item) {
          return {
            id: item.id,
            title: item.role || item.title || '面试报告',
            role: item.company || item.role || '—',
            overallScore: item.overallScore === null || item.overallScore === undefined ? '—' : item.overallScore,
            dateText: format.dateTime(item.createdAt)
          };
        });
        self.setData({
          loading: false,
          quota: quota,
          planLabel: (quota && quota.planName) || '免费版',
          active: active,
          reports: reports,
          resumeReady: !!board.resumeReady,
          jdCount: board.jdCount || 0,
          reportTotal: board.reportTotal || 0,
          avgText: board.scoredReportCount ? ' · 均分 ' + board.averageScore : ''
        });
      })
      .catch(function (error) {
        self.setData({ loading: false });
        api.toast(error.message || '加载失败', 'fail');
      });
  },

  startInterview: function () {
    my.navigateTo({ url: '/pages/setup/setup' });
  },

  continueSession: function () {
    if (!this.data.active) {
      return;
    }
    my.navigateTo({ url: '/pages/interview/interview?id=' + this.data.active.id });
  },

  openReport: function (event) {
    my.navigateTo({ url: '/pages/report/report?id=' + event.currentTarget.dataset.id });
  },

  go: function (event) {
    my.navigateTo({ url: event.currentTarget.dataset.url });
  },

  onShareAppMessage: function () {
    return {
      title: 'OfferCoach 面试教练：按简历和目标岗位练一场真面试',
      path: '/pages/index/index'
    };
  }
});
