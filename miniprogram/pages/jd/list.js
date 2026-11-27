const api = require('../../utils/api');

Page({
  data: {
    loading: true,
    items: []
  },

  onLoad: function () {
    if (my.setNavigationBar) {
      my.setNavigationBar({ title: '目标岗位' });
    }
  },

  onShow: function () {
    this.load();
  },

  load: function () {
    const self = this;
    api
      .request('/jds?page=1&size=50')
      .then(function (page) {
        const items = (page.items || []).map(function (item) {
          const structured = item.structured || {};
          const mustHave = structured.mustHave || structured.must_have || [];
          return {
            id: item.id,
            title: item.title || '未命名岗位',
            company: item.company || '',
            skillsText: mustHave.slice(0, 3).join(' / '),
            matchText:
              item.matchScore === null || item.matchScore === undefined ? '' : '匹配 ' + Math.round(item.matchScore)
          };
        });
        self.setData({ loading: false, items: items });
      })
      .catch(function (error) {
        self.setData({ loading: false });
        api.toast(error.message || '加载失败', 'fail');
      });
  },

  create: function () {
    my.navigateTo({ url: '/pages/jd/create' });
  },

  open: function (event) {
    my.navigateTo({ url: '/pages/jd/detail?id=' + event.currentTarget.dataset.id });
  }
});
