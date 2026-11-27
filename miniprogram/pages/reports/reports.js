const api = require('../../utils/api');
const format = require('../../utils/format');

Page({
  data: {
    loading: true,
    items: [],
    trend: [],
    average: 0
  },

  onLoad: function () {
    if (my.setNavigationBar) {
      my.setNavigationBar({ title: '复盘报告' });
    }
  },

  onShow: function () {
    this.load();
  },

  load: function () {
    const self = this;
    api
      .request('/reports?page=1&size=50')
      .then(function (page) {
        const items = (page.items || []).map(function (item) {
          return {
            id: item.id,
            title: item.role || item.title || '面试报告',
            company: item.company || '—',
            questionCount: item.questionCount || 0,
            overallScore: item.overallScore === null || item.overallScore === undefined ? null : item.overallScore,
            statusText: item.status === 'READY' ? '' : format.statusLabel(item.status),
            statusClass: item.status === 'FAILED' ? 'badge-warn' : 'badge-warn',
            dateText: format.dateTime(item.createdAt)
          };
        });
        const scored = items
          .filter(function (item) {
            return item.overallScore !== null;
          })
          .slice(0, 10)
          .reverse();
        const trend = scored.map(function (item) {
          return {
            id: item.id,
            score: Math.round(item.overallScore),
            percent: Math.max(6, Math.min(100, Math.round(item.overallScore)))
          };
        });
        const average = scored.length
          ? Math.round(
              scored.reduce(function (sum, item) {
                return sum + item.overallScore;
              }, 0) / scored.length
            )
          : 0;
        self.setData({ loading: false, items: items, trend: trend, average: average });
      })
      .catch(function (error) {
        self.setData({ loading: false });
        api.toast(error.message || '加载失败', 'fail');
      });
  },

  open: function (event) {
    my.navigateTo({ url: '/pages/report/report?id=' + event.currentTarget.dataset.id });
  }
});
