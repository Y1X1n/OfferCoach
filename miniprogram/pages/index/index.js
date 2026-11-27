const api = require('../../utils/api');
const format = require('../../utils/format');

Page({
  data: {
    loading: true,
    quota: null,
    sessions: []
  },

  onShow: function () {
    this.load();
  },

  load: function () {
    const self = this;
    self.setData({ loading: true });
    Promise.all([
      api.request('/quota').catch(function () {
        return null;
      }),
      api.request('/interviews?page=1&size=3').catch(function () {
        return { items: [] };
      })
    ])
      .then(function (results) {
        const quota = results[0];
        const page = results[1] || { items: [] };
        const sessions = (page.items || []).map(function (item) {
          return {
            id: item.id,
            reportId: item.reportId || null,
            title: item.jdTitle || '模拟面试',
            totalScore: item.totalScore,
            statusText: format.statusLabel(item.status),
            dateText: format.formatDate(item.createdAt)
          };
        });
        self.setData({ loading: false, quota: quota, sessions: sessions });
      })
      .catch(function (error) {
        self.setData({ loading: false, sessions: [] });
        my.showToast({ type: 'fail', content: error.message || '加载失败', duration: 2500 });
      });
  },

  startInterview: function () {
    my.navigateTo({ url: '/pages/setup/setup' });
  },

  openSession: function (event) {
    const dataset = event.currentTarget.dataset;
    const reportId = dataset.report;
    if (reportId) {
      my.navigateTo({ url: '/pages/report/report?id=' + reportId });
      return;
    }
    my.navigateTo({ url: '/pages/interview/interview?id=' + dataset.id });
  },

  onShareAppMessage: function () {
    return {
      title: 'OfferCoach 面试教练：按简历和目标岗位练一场真面试',
      path: '/pages/index/index'
    };
  }
});
