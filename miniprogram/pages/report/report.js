const api = require('../../utils/api');
const format = require('../../utils/format');

function mapReport(detail) {
  return {
    title: detail.title || '模拟面试复盘',
    company: detail.company && detail.company !== '目标公司' ? detail.company : '',
    role: detail.role || '',
    questionCount: detail.questionCount || 0,
    overallScore: detail.overallScore,
    summary: detail.summary || '',
    dateText: format.formatDate(detail.generatedAt || detail.createdAt),
    radar: (detail.radar || []).map(function (item) {
      return {
        dimension: item.dimension,
        label: item.label || format.dimensionLabel(item.dimension),
        score: item.score,
        percent: format.scorePercent(item.score, 10)
      };
    }),
    highlights: detail.highlights || [],
    risks: detail.risks || [],
    improvementPlan: detail.improvementPlan || [],
    breakdown: (detail.questionBreakdown || []).map(function (item) {
      return {
        seq: item.seq,
        score: item.score,
        question: item.question,
        answer: item.answer,
        summary: item.summary,
        dimensionLabel: format.dimensionLabel(item.dimension),
        strengths: (item.feedback && item.feedback.strengths) || [],
        improvements: (item.feedback && item.feedback.improvements) || [],
        evidence: (item.feedback && item.feedback.evidence) || []
      };
    })
  };
}

Page({
  data: {
    phase: 'loading',
    retrying: false,
    reportId: '',
    report: null
  },

  onLoad: function (query) {
    this.reportId = (query && query.id) || '';
    this.setData({ reportId: this.reportId });
    this.load();
  },

  load: function () {
    const self = this;
    self.setData({ phase: 'loading' });
    api
      .poll(
        function () {
          return api.request('/reports/' + self.reportId);
        },
        function (detail) {
          return detail && (detail.status === 'READY' || detail.status === 'FAILED');
        },
        { timeout: 150000, interval: 2500, timeoutMessage: '报告生成时间较长，请稍后从首页进入查看' }
      )
      .then(function (detail) {
        if (detail.status === 'FAILED') {
          self.setData({ phase: 'failed' });
          return;
        }
        self.setData({
          phase: 'ready',
          report: mapReport(detail),
          raw: { breakdownRaw: detail.questionBreakdown || [] }
        });
      })
      .catch(function (error) {
        self.setData({ phase: 'failed' });
        my.showToast({ type: 'fail', content: error.message || '报告加载失败', duration: 2500 });
      });
  },

  retry: function () {
    const self = this;
    self.setData({ retrying: true });
    api
      .request('/reports/' + self.reportId + '/retry', { method: 'POST', data: {} })
      .then(function () {
        self.setData({ retrying: false });
        self.load();
      })
      .catch(function (error) {
        self.setData({ retrying: false });
        my.showToast({ type: 'fail', content: error.message || '重试失败', duration: 2000 });
      });
  },

  exportMarkdown: function () {
    const self = this;
    const report = self.data.report;
    if (!report) {
      return;
    }
    const raw = self.data.raw || {};
    api.copy(format.reportMarkdown({
      title: report.role || report.title,
      role: report.role,
      company: report.company,
      overallScore: report.overallScore,
      questionCount: report.questionCount,
      summary: report.summary,
      radar: report.radar,
      highlights: report.highlights,
      risks: report.risks,
      improvementPlan: report.improvementPlan,
      questionBreakdown: raw.breakdownRaw || []
    })).then(function () {
      api.toast('报告已复制为 Markdown', 'success');
    });
  },

  practiceAgain: function () {
    my.redirectTo({ url: '/pages/setup/setup' });
  },

  backHome: function () {
    my.reLaunch({ url: '/pages/index/index' });
  },

  onShareAppMessage: function () {
    return {
      title: '我刚用 OfferCoach 练了一场面试，看看复盘报告',
      path: '/pages/index/index'
    };
  }
});
