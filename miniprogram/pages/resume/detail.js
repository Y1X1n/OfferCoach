const api = require('../../utils/api');
const format = require('../../utils/format');
const config = require('../../utils/config');

function absolute(path) {
  if (/^https?:\/\//.test(path)) {
    return path;
  }
  return config.API_BASE.replace(/\/api\/v1\/?$/, '') + path;
}

Page({
  data: {
    loading: true,
    refreshing: false,
    resume: null,
    sections: [],
    statusText: '',
    dateText: '',
    lowConfidence: [],
    lowConfidenceText: ''
  },

  onLoad: function (query) {
    this.resumeId = (query && query.id) || '';
    if (my.setNavigationBar) {
      my.setNavigationBar({ title: '简历详情' });
    }
    this.load();
  },

  load: function () {
    const self = this;
    api
      .request('/resumes/' + self.resumeId)
      .then(function (resume) {
        const lowConfidence = resume.lowConfidenceFields || [];
        self.setData({
          loading: false,
          refreshing: false,
          resume: resume,
          sections: format.resumeSections(resume.structured),
          statusText: format.parseStatusLabel(resume.parseStatus),
          dateText: format.dateTime(resume.createdAt),
          lowConfidence: lowConfidence,
          lowConfidenceText: lowConfidence.join('、')
        });
      })
      .catch(function (error) {
        self.setData({ loading: false, refreshing: false });
        api.toast(error.message || '加载失败', 'fail');
      });
  },

  refresh: function () {
    this.setData({ refreshing: true });
    this.load();
  },

  rename: function () {
    const self = this;
    my.prompt({
      title: '重命名简历',
      placeholder: '最多 120 字',
      value: self.data.resume.title,
      success: function (res) {
        if (!res.ok || !res.inputValue) {
          return;
        }
        api
          .request('/resumes/' + self.resumeId, {
            method: 'PATCH',
            data: { title: res.inputValue.trim().slice(0, 120) }
          })
          .then(function () {
            api.toast('已更新', 'success');
            self.load();
          })
          .catch(function (error) {
            api.toast(error.message || '更新失败', 'fail');
          });
      }
    });
  },

  editBasics: function () {
    const self = this;
    const structured = self.data.resume.structured || {};
    const basic = structured.basic || {};
    my.showActionSheet({
      items: [
        '姓名：' + (basic.name || '未填写'),
        '城市：' + (basic.city || '未填写'),
        '经验年限：' + (basic.yearsOfExperience || '未填写')
      ],
      success: function (res) {
        const fields = ['name', 'city', 'yearsOfExperience'];
        const field = fields[res.tapIndex];
        if (!field) {
          return;
        }
        my.prompt({
          title: '修改' + ['姓名', '城市', '经验年限'][res.tapIndex],
          value: basic[field] === undefined || basic[field] === null ? '' : String(basic[field]),
          success: function (input) {
            if (!input.ok) {
              return;
            }
            const value = (input.inputValue || '').trim();
            const nextBasic = Object.assign({}, basic);
            nextBasic[field] = field === 'yearsOfExperience' ? Number(value) || 0 : value;
            api
              .request('/resumes/' + self.resumeId, {
                method: 'PATCH',
                data: { structured: Object.assign({}, structured, { basic: nextBasic }) }
              })
              .then(function () {
                api.toast('已更新，正在重建摘要', 'success');
                setTimeout(function () {
                  self.load();
                }, 1500);
              })
              .catch(function (error) {
                api.toast(error.message || '更新失败', 'fail');
              });
          }
        });
      }
    });
  },

  setDefault: function () {
    const self = this;
    api
      .request('/resumes/' + self.resumeId, { method: 'PATCH', data: { isDefault: true } })
      .then(function () {
        api.toast('已设为默认', 'success');
        self.load();
      })
      .catch(function (error) {
        api.toast(error.message || '操作失败', 'fail');
      });
  },

  reparse: function () {
    const self = this;
    my.showLoading({ content: '已提交…' });
    api
      .request('/resumes/' + self.resumeId + '/reparse', { method: 'POST', data: {} })
      .then(function () {
        my.hideLoading();
        api.toast('重新解析中', 'success');
        setTimeout(function () {
          self.load();
        }, 2500);
      })
      .catch(function (error) {
        my.hideLoading();
        api.toast(error.message || '操作过于频繁', 'fail');
      });
  },

  exportMarkdown: function () {
    const resume = this.data.resume;
    const lines = ['# ' + resume.title, '', resume.summary || ''];
    this.data.sections.forEach(function (section) {
      lines.push('', '## ' + section.title, '');
      section.items.forEach(function (item) {
        lines.push(item.replace(/\n/g, '\n  '));
      });
    });
    api.copy(lines.join('\n')).then(function () {
      api.toast('Markdown 已复制', 'success');
    });
  },

  exportJson: function () {
    api.copy(JSON.stringify(this.data.resume.structured || {}, null, 2)).then(function () {
      api.toast('JSON 已复制', 'success');
    });
  },

  download: function () {
    const self = this;
    api
      .request('/resumes/' + self.resumeId + '/download-url')
      .then(function (result) {
        const url = absolute(result.url);
        my.downloadFile({
          url: url,
          header: { Authorization: 'Bearer ' + (api.storage('oc_session') || {}).accessToken },
          success: function (res) {
            my.openDocument({
              filePath: res.apFilePath,
              fileType: 'pdf',
              fail: function () {
                api.toast('无法打开文件，请稍后重试', 'fail');
              }
            });
          },
          fail: function () {
            api.toast('下载失败', 'fail');
          }
        });
      })
      .catch(function (error) {
        api.toast(error.message || '下载失败', 'fail');
      });
  },

  remove: function () {
    const self = this;
    my.confirm({
      title: '删除简历',
      content: '删除后无法恢复，进行中的面试引用该简历时会被拒绝。',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      success: function (res) {
        if (!res.confirm) {
          return;
        }
        api
          .request('/resumes/' + self.resumeId, { method: 'DELETE' })
          .then(function () {
            api.toast('已删除', 'success');
            my.navigateBack();
          })
          .catch(function (error) {
            api.toast(error.code === 'STATE_CONFLICT' ? '该简历被进行中的面试占用' : error.message, 'fail');
          });
      }
    });
  }
});
