const api = require('../../utils/api');
const format = require('../../utils/format');

const SOURCE_LABELS = { MANUAL: '手工粘贴', SEARCH: '搜索获取', URL: '链接抓取' };

Page({
  data: {
    loading: true,
    jd: null,
    sourceText: '',
    dateText: '',
    mustHave: [],
    niceToHave: [],
    rawPreview: '',
    rawLong: false,
    rawExpanded: false,
    resumes: [],
    currentResumeTitle: '',
    matching: false,
    match: null
  },

  onLoad: function (query) {
    this.jdId = (query && query.id) || '';
    if (my.setNavigationBar) {
      my.setNavigationBar({ title: '岗位详情' });
    }
    this.load();
  },

  load: function () {
    const self = this;
    Promise.all([
      api.request('/jds/' + self.jdId),
      api.request('/resumes?page=1&size=50').catch(function () {
        return { items: [] };
      })
    ])
      .then(function (results) {
        const jd = results[0];
        const resumes = (results[1] && results[1].items) || [];
        const structured = jd.structured || {};
        const mustHave = structured.mustHave || structured.must_have || [];
        const niceToHave = structured.niceToHave || structured.nice_to_have || [];
        const raw = jd.rawText || '';
        const preferred = resumes.filter(function (item) {
          return item.isDefault;
        })[0] || resumes[0];
        self.setData({
          loading: false,
          jd: jd,
          sourceText: SOURCE_LABELS[jd.sourceType] || '手工粘贴',
          dateText: format.dateTime(jd.createdAt),
          mustHave: mustHave,
          niceToHave: niceToHave,
          rawPreview: raw.length > 500 ? raw.slice(0, 500) + '…' : raw,
          rawLong: raw.length > 500,
          resumes: resumes,
          currentResumeTitle: preferred ? preferred.title : ''
        });
        self.resumeId = preferred ? preferred.id : '';
      })
      .catch(function (error) {
        self.setData({ loading: false });
        api.toast(error.message || '加载失败', 'fail');
      });
  },

  expandRaw: function () {
    this.setData({ rawPreview: this.data.jd.rawText, rawExpanded: true });
  },

  pickResume: function () {
    const self = this;
    my.showActionSheet({
      items: self.data.resumes.map(function (item) {
        return item.title;
      }),
      success: function (res) {
        const picked = self.data.resumes[res.tapIndex];
        if (picked) {
          self.resumeId = picked.id;
          self.setData({ currentResumeTitle: picked.title, match: null });
        }
      }
    });
  },

  runMatch: function () {
    const self = this;
    if (!self.resumeId) {
      api.toast('请先选择简历');
      return;
    }
    self.setData({ matching: true });
    api
      .request('/jds/' + self.jdId + '/match', {
        method: 'POST',
        data: { resumeId: self.resumeId }
      })
      .then(function (match) {
        self.setData({ matching: false, match: match });
      })
      .catch(function (error) {
        self.setData({ matching: false });
        api.toast(error.message || '匹配失败', 'fail');
      });
  },

  startInterview: function () {
    my.navigateTo({ url: '/pages/setup/setup?jdId=' + this.jdId });
  },

  remove: function () {
    const self = this;
    my.confirm({
      title: '删除岗位',
      content: '删除后不可恢复，历史报告仍会保留。',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      success: function (res) {
        if (!res.confirm) {
          return;
        }
        api
          .request('/jds/' + self.jdId, { method: 'DELETE' })
          .then(function () {
            api.toast('已删除', 'success');
            my.navigateBack();
          })
          .catch(function (error) {
            api.toast(error.message || '删除失败', 'fail');
          });
      }
    });
  }
});
