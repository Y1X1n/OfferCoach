const api = require('../../utils/api');
const format = require('../../utils/format');

const STATUS_CLASS = {
  READY: 'badge-ok',
  PENDING: 'badge-warn',
  PARSING: 'badge-warn',
  FAILED: 'badge-warn'
};

Page({
  data: {
    loading: true,
    items: [],
    pasteOpen: false,
    pasteText: '',
    creating: false
  },

  onLoad: function () {
    if (my.setNavigationBar) {
      my.setNavigationBar({ title: '我的简历' });
    }
  },

  onShow: function () {
    this.load();
  },

  load: function () {
    const self = this;
    self.setData({ loading: !self.data.items.length });
    api
      .request('/resumes?page=1&size=50')
      .then(function (page) {
        const items = (page.items || []).map(function (item) {
          return {
            id: item.id,
            title: item.title || '未命名简历',
            summary: item.summary || (item.sourceType === 'MANUAL' ? '手动录入' : item.fileName || ''),
            isDefault: !!item.isDefault,
            statusText: item.parseStatus === 'READY' ? '' : format.parseStatusLabel(item.parseStatus),
            statusClass: STATUS_CLASS[item.parseStatus] || ''
          };
        });
        self.setData({ loading: false, items: items });
      })
      .catch(function (error) {
        self.setData({ loading: false });
        api.toast(error.message || '加载失败', 'fail');
      });
  },

  togglePaste: function () {
    this.setData({ pasteOpen: !this.data.pasteOpen });
  },

  onPasteInput: function (event) {
    this.setData({ pasteText: event.detail.value });
  },

  createManual: function () {
    const self = this;
    const text = (self.data.pasteText || '').trim();
    if (text.length < 20) {
      api.toast('简历内容至少 20 字');
      return;
    }
    self.setData({ creating: true });
    const firstLine = text.split('\n')[0] || '';
    api
      .request('/resumes/manual', {
        method: 'POST',
        data: { title: firstLine.slice(0, 30) || '我的简历', rawText: text, isDefault: true }
      })
      .then(function (resume) {
        self.setData({ creating: false, pasteOpen: false, pasteText: '' });
        api.toast('已创建，正在解析', 'success');
        my.redirectTo({ url: '/pages/resume/detail?id=' + resume.id });
      })
      .catch(function (error) {
        self.setData({ creating: false });
        api.toast(error.code === 'QUOTA_EXCEEDED' ? '解析额度不足' : error.message, 'fail');
      });
  },

  pickFile: function () {
    const self = this;
    my.chooseFile({
      count: 1,
      type: 'file',
      success: function (res) {
        const paths = res.apFilePaths || res.filePaths || [];
        if (paths.length) {
          self.uploadFile(paths[0]);
        }
      },
      fail: function () {
        api.toast('未选择文件');
      }
    });
  },

  pickImage: function () {
    const self = this;
    my.chooseImage({
      count: 1,
      success: function (res) {
        const paths = res.apFilePaths || res.filePaths || [];
        if (paths.length) {
          self.uploadFile(paths[0]);
        }
      },
      fail: function () {
        api.toast('未选择图片');
      }
    });
  },

  uploadFile: function (filePath) {
    const self = this;
    my.showLoading({ content: '上传中…' });
    api
      .upload('/resumes', filePath, { isDefault: 'true' })
      .then(function (resume) {
        my.hideLoading();
        api.toast('上传成功，正在解析', 'success');
        my.redirectTo({ url: '/pages/resume/detail?id=' + resume.id });
      })
      .catch(function (error) {
        my.hideLoading();
        let message = error.message || '上传失败';
        if (error.code === 'FILE_TOO_LARGE') message = '文件超过 10MB';
        if (error.code === 'UNSUPPORTED_FILE') message = '仅支持 PDF / DOCX / 图片 / PPT';
        if (error.code === 'QUOTA_EXCEEDED') message = '解析额度不足';
        api.toast(message, 'fail');
      });
  },

  open: function (event) {
    my.navigateTo({ url: '/pages/resume/detail?id=' + event.currentTarget.dataset.id });
  }
});
