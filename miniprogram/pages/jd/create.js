const api = require('../../utils/api');

Page({
  data: {
    mode: 'text',
    rawText: '',
    url: '',
    keywords: '',
    query: '',
    location: '',
    busy: false,
    results: [],
    searchToken: '',
    preview: null,
    previewPreview: ''
  },

  onLoad: function () {
    if (my.setNavigationBar) {
      my.setNavigationBar({ title: '新增岗位' });
    }
  },

  switchMode: function (event) {
    this.setData({ mode: event.currentTarget.dataset.mode, results: [] });
  },

  onTextInput: function (event) {
    this.setData({ rawText: event.detail.value });
  },

  onUrlInput: function (event) {
    this.setData({ url: event.detail.value });
  },

  onKeywordsInput: function (event) {
    this.setData({ keywords: event.detail.value });
  },

  onQueryInput: function (event) {
    this.setData({ query: event.detail.value });
  },

  onLocationInput: function (event) {
    this.setData({ location: event.detail.value });
  },

  saveText: function () {
    const self = this;
    const text = (self.data.rawText || '').trim();
    if (text.length < 50) {
      api.toast('JD 原文至少 50 字');
      return;
    }
    self.setData({ busy: true });
    api
      .request('/jds', {
        method: 'POST',
        data: { title: text.split('\n')[0].slice(0, 80) || '目标岗位', company: null, rawText: text }
      })
      .then(function (jd) {
        self.setData({ busy: false });
        api.toast('已保存', 'success');
        my.redirectTo({ url: '/pages/jd/detail?id=' + jd.id });
      })
      .catch(function (error) {
        self.setData({ busy: false });
        if (error.code === 'STATE_CONFLICT' && error.details && error.details.existingJdId) {
          api.toast('这份 JD 已存在');
          my.redirectTo({ url: '/pages/jd/detail?id=' + error.details.existingJdId });
          return;
        }
        api.toast(error.message || '保存失败', 'fail');
      });
  },

  fetchUrl: function () {
    const self = this;
    const url = (self.data.url || '').trim();
    if (!/^https?:\/\//i.test(url)) {
      api.toast('请填写 http(s) 链接');
      return;
    }
    self.setData({ busy: true, results: [], preview: null });
    api
      .request('/jds/preview', {
        method: 'POST',
        data: { url: url, keywords: (self.data.keywords || '').trim() || null }
      })
      .then(function (result) {
        self.setData({ busy: false });
        self.applyPreview(result);
      })
      .catch(function (error) {
        self.setData({ busy: false });
        self.handlePreviewError(error);
      });
  },

  searchJobs: function () {
    const self = this;
    const query = (self.data.query || '').trim();
    if (query.length < 2) {
      api.toast('请填写岗位关键词');
      return;
    }
    self.setData({ busy: true, results: [], preview: null });
    api
      .request('/jds/search', {
        method: 'POST',
        data: { query: query, location: (self.data.location || '').trim() || null, count: 8 }
      })
      .then(function (result) {
        self.setData({
          busy: false,
          results: result.results || [],
          searchToken: result.searchToken || ''
        });
        if (!(result.results || []).length) {
          api.toast('没有找到岗位，换个关键词试试');
        }
      })
      .catch(function (error) {
        self.setData({ busy: false });
        self.handlePreviewError(error);
      });
  },

  pickResult: function (event) {
    const self = this;
    if (!self.data.searchToken) {
      api.toast('搜索已过期，请重新搜索');
      return;
    }
    self.setData({ busy: true });
    api
      .request('/jds/preview', {
        method: 'POST',
        data: { searchToken: self.data.searchToken, resultId: event.currentTarget.dataset.id }
      })
      .then(function (result) {
        self.setData({ busy: false });
        self.applyPreview(result);
      })
      .catch(function (error) {
        self.setData({ busy: false });
        self.handlePreviewError(error);
      });
  },

  applyPreview: function (result) {
    if (result && result.needsChoice) {
      this.setData({
        results: result.candidates || [],
        searchToken: result.searchToken || this.data.searchToken
      });
      api.toast('链接抓取不完整，请从候选里选一个');
      return;
    }
    const rawText = (result && result.rawText) || '';
    this.setData({
      results: [],
      preview: result,
      previewPreview: rawText.length > 400 ? rawText.slice(0, 400) + '…' : rawText
    });
  },

  handlePreviewError: function (error) {
    if (error.code === 'PARSE_NEED_MANUAL') {
      api.toast('抓取失败，请改为粘贴 JD 原文');
      this.setData({ mode: 'text' });
      return;
    }
    if (error.code === 'AI_UNAVAILABLE' || error.status === 503) {
      api.toast('联网获取暂不可用，请粘贴 JD 原文');
      return;
    }
    api.toast(error.message || '获取失败', 'fail');
  },

  savePreview: function () {
    const self = this;
    const preview = self.data.preview;
    if (!preview) {
      return;
    }
    self.setData({ busy: true });
    api
      .request('/jds', {
        method: 'POST',
        data: {
          title: preview.title || '目标岗位',
          company: preview.siteName || null,
          rawText: preview.rawText
        }
      })
      .then(function (jd) {
        self.setData({ busy: false });
        my.redirectTo({ url: '/pages/setup/setup?jdId=' + jd.id });
      })
      .catch(function (error) {
        self.setData({ busy: false });
        if (error.code === 'STATE_CONFLICT' && error.details && error.details.existingJdId) {
          my.redirectTo({ url: '/pages/setup/setup?jdId=' + error.details.existingJdId });
          return;
        }
        api.toast(error.message || '保存失败', 'fail');
      });
  }
});
