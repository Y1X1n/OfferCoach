const api = require('./utils/api');

App({
  globalData: {
    sessionReady: false
  },

  onLaunch: function () {
    // 静默准备游客会话；失败不阻塞首屏，页面在需要时会再次触发并给出可读错误。
    api.ensureSession().then(
      () => {
        this.globalData.sessionReady = true;
      },
      () => {
        this.globalData.sessionReady = false;
      }
    );
  },

  onError: function (error) {
    console.error('[app]', error);
  }
});
