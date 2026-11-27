const api = require('../../utils/api');

Page({
  data: {
    me: {},
    quota: null,
    invite: null
  },

  onLoad: function () {
    if (my.setNavigationBar) {
      my.setNavigationBar({ title: '我的' });
    }
  },

  onShow: function () {
    this.load();
  },

  load: function () {
    const self = this;
    Promise.all([
      api.request('/users/me').catch(function () {
        return {};
      }),
      api.request('/quota').catch(function () {
        return null;
      }),
      api.request('/users/me/invite').catch(function () {
        return null;
      })
    ]).then(function (results) {
      self.setData({ me: results[0] || {}, quota: results[1], invite: results[2] });
    });
  },

  editNickname: function () {
    const self = this;
    my.prompt({
      title: '修改昵称',
      placeholder: '2-40 个字符',
      value: self.data.me.nickname || '',
      success: function (res) {
        if (!res.ok || !res.inputValue) {
          return;
        }
        api
          .request('/users/me', { method: 'PATCH', data: { nickname: res.inputValue.trim() } })
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

  copyInvite: function () {
    const invite = this.data.invite;
    if (!invite || !invite.inviteCode) {
      return;
    }
    api.copy(invite.inviteCode).then(function () {
      api.toast('邀请码已复制', 'success');
    });
  },

  exportData: function () {
    const self = this;
    my.showLoading({ content: '正在导出…' });
    api
      .request('/users/me/export')
      .then(function (data) {
        my.hideLoading();
        const text = JSON.stringify(data, null, 2);
        const counts = [
          '简历 ' + ((data.resumes || []).length || 0),
          '面试 ' + ((data.sessions || data.interviews || []).length || 0),
          '报告 ' + ((data.reports || []).length || 0)
        ].join(' · ');
        my.confirm({
          title: '数据已就绪',
          content: counts + '\n复制完整 JSON 到剪贴板？',
          confirmButtonText: '复制',
          cancelButtonText: '取消',
          success: function (res) {
            if (res.confirm) {
              api.copy(text).then(function () {
                api.toast('已复制到剪贴板', 'success');
              });
            }
          }
        });
      })
      .catch(function (error) {
        my.hideLoading();
        api.toast(error.message || '导出失败', 'fail');
      });
  },

  logout: function () {
    const self = this;
    my.confirm({
      title: '退出登录',
      content: '退出后本设备会创建新的体验账号，历史数据仍在原账号下。',
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      success: function (res) {
        if (!res.confirm) {
          return;
        }
        api
          .request('/auth/logout', { method: 'POST', data: {} })
          .catch(function () {
            return null;
          })
          .then(function () {
            my.removeStorageSync({ key: 'oc_session' });
            my.reLaunch({ url: '/pages/index/index' });
          });
      }
    });
  },

  deleteAccount: function () {
    const self = this;
    const session = api.storage('oc_session') || {};
    my.prompt({
      title: '注销账号',
      placeholder: '输入登录密码确认（体验账号已自动填入）',
      value: session.password || '',
      success: function (res) {
        if (!res.ok || !res.inputValue) {
          return;
        }
        api
          .request('/users/me/delete', { method: 'POST', data: { password: res.inputValue } })
          .then(function () {
            my.removeStorageSync({ key: 'oc_session' });
            api.toast('账号已注销', 'success');
            setTimeout(function () {
              my.reLaunch({ url: '/pages/index/index' });
            }, 800);
          })
          .catch(function (error) {
            api.toast(error.message || '注销失败', 'fail');
          });
      }
    });
  },

  go: function (event) {
    my.navigateTo({ url: event.currentTarget.dataset.url });
  }
});
