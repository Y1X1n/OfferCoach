/** 展示层映射与示例素材（示例内容均为虚构，用于演示与检测入口）。 */

const DIMENSION_LABELS = {
  content: '内容相关性',
  structure: '结构化表达',
  depth: '专业深度',
  fit: '岗位匹配',
  language: '语言表达',
  DEPTH: '专业深度',
  PROJECT: '项目经历',
  BEHAVIOR: '行为面试',
  FIT: '岗位匹配'
};

const STATUS_LABELS = {
  CREATED: '待出题',
  PREPARING: '出题中',
  READY: '可作答',
  IN_PROGRESS: '作答中',
  PAUSED: '已暂停',
  FINISHED: '已结束',
  REPORT_GENERATING: '报告生成中',
  ABANDONED: '已放弃',
  FAILED: '生成失败',
  GENERATING: '生成中',
  PENDING: '待处理',
  PARSING: '解析中',
  SCORING: '评分中',
  SCORED: '已评分'
};

const SAMPLE_RESUME = [
  '林晓，男，1999 年，求职方向：后端开发工程师（3 年经验）',
  '',
  '教育背景',
  '2017.09-2021.06  江南理工大学  计算机科学与技术  本科',
  '',
  '工作经历',
  '2021.07-2024.06  云帆科技  后端开发工程师',
  '- 负责电商订单中心，日均订单 80 万，主导订单查询接口重构：引入 Redis 多级缓存与分页游标，',
  '  P99 从 420ms 降到 85ms，数据库 QPS 下降 62%。',
  '- 设计并落地订单状态机的幂等方案，重复支付类线上问题从每月 6 起降到 0。',
  '- 参与库存扣减改造，用本地消息表 + 定时补偿替代强事务，超卖率从 0.3% 降到 0.02%。',
  '',
  '项目经历',
  '1. 订单中心重构（2023.03-2023.09）',
  '   - 背景：订单表 3.2 亿行，慢查询拖垮大促。',
  '   - 方案：冷热分离 + 覆盖索引 + 异步归档，迁移期双写校验。',
  '   - 结果：大促峰值 TPS 从 1.2 万提升到 3.5 万，零故障。',
  '2. 营销活动发券（2022.05-2022.08）',
  '   - 用 Redis Lua 保证发券原子性，单机 2 万 QPS，活动期间发出 1200 万张券。',
  '',
  '技能',
  'Java / Spring Boot / MySQL / Redis / Kafka / Docker；熟悉 JVM 调优与索引设计。',
  '',
  '自我评价',
  '习惯用数据说话，能独立完成从需求拆解到上线复盘的完整闭环。'
].join('\n');

const SAMPLE_JD = [
  '高级后端工程师（订单/交易方向）',
  '',
  '岗位职责',
  '1. 负责交易链路核心服务的设计与研发，保障大促期间高并发下的稳定性；',
  '2. 主导订单、库存等核心域的服务治理与性能优化；',
  '3. 参与技术方案评审，推动缓存、消息、分库分表等中间件的合理使用。',
  '',
  '任职要求',
  '1. 本科及以上学历，3 年以上 Java 后端经验；',
  '2. 精通 Spring Boot、MySQL、Redis，熟悉 Kafka 等消息中间件；',
  '3. 有高并发、大流量系统优化经验，能用量化数据说明优化效果；',
  '4. 熟悉分布式事务与幂等设计，有交易类系统经验优先；',
  '5. 具备良好的沟通能力与owner意识，能推动跨团队协作。'
].join('\n');

const HOT_JOBS = [
  { id: 'backend', name: '后端开发', jd: SAMPLE_JD },
  {
    id: 'frontend',
    name: '前端开发',
    jd: [
      '前端开发工程师（中高级）',
      '',
      '岗位职责',
      '1. 负责核心业务的前端架构与研发，保障页面性能与稳定性；',
      '2. 参与组件库与工程化建设，提升团队研发效率；',
      '3. 与设计、后端协作完成需求交付。',
      '',
      '任职要求',
      '1. 3 年以上前端经验，精通 JavaScript/TypeScript 与 Vue 或 React；',
      '2. 熟悉构建工具与性能优化，能定位并解决首屏与交互性能问题；',
      '3. 有组件库、微前端或 Node 服务经验优先；',
      '4. 具备良好的沟通能力与产品意识。'
    ].join('\n')
  },
  {
    id: 'product',
    name: '产品经理',
    jd: [
      '产品经理（B 端方向）',
      '',
      '岗位职责',
      '1. 负责业务流程梳理与需求分析，输出 PRD 与原型；',
      '2. 推动跨团队协作，跟进需求按期上线；',
      '3. 基于数据复盘功能效果并持续迭代。',
      '',
      '任职要求',
      '1. 3 年以上 B 端产品经验，熟悉企业服务场景；',
      '2. 具备结构化思维，能把模糊需求拆解为可执行方案；',
      '3. 熟悉数据分析工具，能独立完成效果归因；',
      '4. 沟通与推动能力强，有复杂项目落地经验优先。'
    ].join('\n')
  }
];

function dimensionLabel(key) {
  return DIMENSION_LABELS[key] || key;
}

function statusLabel(status) {
  return STATUS_LABELS[status] || status || '';
}

function scorePercent(score, max) {
  const limit = max || 10;
  const value = Number(score);
  if (isNaN(value)) return 0;
  return Math.max(0, Math.min(100, Math.round((value / limit) * 100)));
}

function formatDate(iso) {
  if (!iso) return '';
  const text = String(iso);
  const date = new Date(text);
  if (isNaN(date.getTime())) return text.slice(0, 10);
  const pad = function (n) {
    return n < 10 ? '0' + n : String(n);
  };
  return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
}

function dimensionsToRows(dimensions) {
  if (!dimensions) return [];
  const order = ['content', 'structure', 'depth', 'fit', 'language'];
  return order
    .filter(function (key) {
      return dimensions[key] !== undefined && dimensions[key] !== null;
    })
    .map(function (key) {
      return {
        key: key,
        label: dimensionLabel(key),
        score: Number(dimensions[key]),
        percent: scorePercent(dimensions[key], 10)
      };
    });
}

module.exports = {
  SAMPLE_RESUME: SAMPLE_RESUME,
  SAMPLE_JD: SAMPLE_JD,
  HOT_JOBS: HOT_JOBS,
  dimensionLabel: dimensionLabel,
  statusLabel: statusLabel,
  scorePercent: scorePercent,
  formatDate: formatDate,
  dimensionsToRows: dimensionsToRows
};
