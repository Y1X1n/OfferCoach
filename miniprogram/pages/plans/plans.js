const api = require('../../utils/api');
const format = require('../../utils/format');

Page({
  data: {
    plans: [],
    currentPlan: '',
    channelText: '',
    buying: false,
    buyingCode: '',
    phase: ''
  },

  onLoad: function () {
    if (my.setNavigationBar) {
      my.setNavigationBar({ title: '套餐与额度' });
    }
    this.load();
  },

  load: function () {
    const self = this;
    Promise.all([
      api.request('/plans'),
      api.request('/pay-channels').catch(function () {
        return [];
      }),
      api.request('/quota').catch(function () {
        return null;
      })
    ])
      .then(function (results) {
        const plans = (results[0] || [])
          .filter(function (item) {
            return item.active;
          })
          .map(function (item) {
            return {
              code: item.code,
              name: item.name,
              priceCents: item.priceCents,
              priceText: item.priceCents > 0 ? '¥' + format.money(item.priceCents) : '免费',
              periodDays: item.periodDays,
              interviewCredits: item.interviewCredits,
              resumeCredits: item.resumeCredits,
              featureList: Object.keys(item.features || {})
                .filter(function (key) {
                  return item.features[key];
                })
                .map(function (key) {
                  return format.planFeatureLabel(key);
                })
            };
          });
        const channels = (results[1] || []).filter(function (item) {
          return item.enabled;
        });
        self.channels = channels;
        self.setData({
          plans: plans,
          currentPlan: results[2] ? results[2].planCode : '',
          channelText: channels.length
            ? channels
                .map(function (item) {
                  return item.channel === 'alipay' ? '支付宝' : item.channel === 'wechat' ? '微信' : '本地模拟';
                })
                .join(' / ')
            : '未开通'
        });
      })
      .catch(function (error) {
        api.toast(error.message || '加载失败', 'fail');
      });
  },

  pickChannel: function () {
    const channels = this.channels || [];
    return (
      channels.filter(function (item) {
        return item.channel === 'alipay';
      })[0] ||
      channels.filter(function (item) {
        return item.channel === 'mock';
      })[0] ||
      null
    );
  },

  buy: function (event) {
    const self = this;
    if (self.data.buying) {
      return;
    }
    const planCode = event.currentTarget.dataset.code;
    const channel = self.pickChannel();
    if (!channel) {
      api.toast('支付渠道未开通，请联系管理员');
      return;
    }

    self.setData({ buying: true, buyingCode: planCode, phase: '正在下单…' });
    let orderNo = '';

    api
      .request('/orders', {
        method: 'POST',
        headers: { 'Idempotency-Key': 'mp-order-' + planCode + '-' + Date.now() },
        data: { planCode: planCode, channel: channel.channel, payMode: 'MINI' }
      })
      .then(function (order) {
        orderNo = order.orderNo;
        if (order.status === 'PAID') {
          return order;
        }
        self.setData({ phase: '正在唤起支付…' });
        const authCodePromise =
          channel.channel === 'alipay'
            ? api.getAuthCode()
            : Promise.resolve('mock-auth-code');
        return authCodePromise
          .then(function (authCode) {
            return api.request('/orders/' + orderNo + '/miniapp-pay', {
              method: 'POST',
              data: { authCode: authCode }
            });
          })
          .then(function (trade) {
            self.setData({ phase: '等待支付结果…' });
            return api.tradePay(trade.tradeNo);
          })
          .then(function () {
            return api.poll(
              function () {
                return api.request('/orders/' + orderNo);
              },
              function (view) {
                return view.status === 'PAID' || view.status === 'CLOSED' || view.status === 'FAILED';
              },
              { timeout: 120000, interval: 2000, timeoutMessage: '支付结果确认超时，请稍后在套餐页刷新' }
            );
          });
      })
      .then(function (order) {
        self.setData({ buying: false, buyingCode: '', phase: '' });
        if (order.status === 'PAID') {
          api.toast('支付成功，额度已到账', 'success');
          self.load();
          return;
        }
        api.toast('订单状态：' + format.orderStatusLabel(order.status), 'fail');
      })
      .catch(function (error) {
        self.setData({ buying: false, buyingCode: '', phase: '' });
        if (error.code === 'PAY_CANCELLED') {
          api.toast('已取消支付');
          return;
        }
        api.toast(error.message || '支付失败', 'fail');
      });
  }
});
