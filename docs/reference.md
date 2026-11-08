# 外部参考与取舍

> 状态：生效 ｜ 版本：v1.2 ｜ 更新：2026-11-07
> 本文记录 OfferCoach 借鉴了哪些外部资料、采纳了什么、否决了什么。技术选型的正式裁决在 [design/architecture.md](design/architecture.md) 的 ADR，本文不重复。

## 1. 主要借鉴来源

### 1.1 Lamburger ERP 项目文档仓

- **地址**：`https://git.lamburger.cc/Lamburger-ERP/lamburger-erp-doc`（私有仓库，仅内部参考）
- **规模**：根级入口 + `dev/`（约定、流程、环境、数据库、后端、UI）+ `design/`（蓝图、数据模型）+ `features/`（专项规格）+ `ops/`（部署、CI/CD）+ `reviews/`（复查留档）。
- **借鉴内容**：文档治理结构，不借鉴其 ERP 业务内容与具体技术栈。

### 1.2 采纳的文档实践

| 实践 | 在 OfferCoach 的落地 |
|---|---|
| README 是唯一索引 | [README.md](README.md) 按“你要做的事”导航 + 全量地图；所有文档必须在此登记 |
| 状态标记 | 每份文档标题下写 `生效 / 评审中 / 规划 / 留档`，索引同步展示 |
| 单一来源 | 业务规则只在 PRD、契约只在 API、表结构只在 data-model、Prompt 只在 AI 文档；其他位置只留指针 |
| 结论归位 | 决策确定后写入正文，从待决清单删除；不建长期决策台账，历史走 git |
| 待决显式 | 未定项用 `> 待决：` 标注，并登记到 README §4；共 12 项 |
| 目录分层 | `design/` 设计与蓝图、`dev/` 规范与流程、`ops/` 运维、`reviews/` 一次性记录 |
| 复查归位 | `reviews/` 只留档；结论必须回写 `design/` 或 `dev/` |
| 契约冻结 | 骨架层先定、字段层随 UI 演进、后端动工时冻结；变更走“契约 → 类型 → 实现”三步 |
| 数据库规范 | 命名、类型、索引、软删、注释、建表自查；见 [design/data-model.md](design/data-model.md) §1 |
| 交付流程 | 启动诊断 → 蓝图 → 实现 → 测试/UAT → 上线 → 验收移交；见 [dev/workflow.md](dev/workflow.md) |
| 里程碑俯瞰 | [CHANGELOG.md](CHANGELOG.md) 只保留跨阶段里程碑与待办，不堆逐次明细 |

### 1.3 明确不采纳的内容

| 内容 | 原因 |
|---|---|
| 单人开发直推 `main`、不做 PR 评审 | OfferCoach 按多人协作设计，保留 `branch → PR → 独立 review → merge` |
| “前端服务可关、浏览器不可关”等单机操作偏好 | 属于参考仓库作者的工作习惯，不进入项目规范 |
| ERP 专用 UI 规则（操作列六档颜色、固定 158px 等） | 业务形态不同；OfferCoach 用自身 Design Token 与交互铁律 |
| PostgreSQL 18 / RustFS / Sa-Token / Dubbo / Nacos | 参考仓库的选型上下文不同；OfferCoach 用 PG16 / MinIO / JWT / Spring Cloud Gateway |
| 微服务 20+ 仓、一模块一仓 | OfferCoach 先多模块单体，按瓶颈拆分；避免过早分布式 |
| 中文业务文件名 | 新文档统一小写英文 slug；存量编号文档已迁移，标题保留中文 |

### 1.4 后端规范来源（评审中）

| 来源 | 定位 | 采纳概况 |
|---|---|---|
| [阿里巴巴 Java 开发手册](https://developer.aliyun.com/article/1589859)（2024-08-19） | 命名、格式、异常、日志、并发、集合、单测、工程结构 | 主来源；采纳 4 空格/120 列、AIR、SLF4J 与异常/日志红线 |
| [SpringCloud 微服务开发最佳实践](https://developer.aliyun.com/article/1254545)（2023-06-18） | Maven、API/实现拆分、REST 访问分级、网关灰度 | 采纳访问分级策略与未来 `-api/-service` 拆分；Feign/Nacos 暂缓 |
| [Spring Boot 项目开发规范（CSDN）](https://blog.csdn.net/2301_80035882/article/details/158535778)（2026-03-06） | 前后端分离、REST、接口文档 | 仅佐证复数资源与契约先行；不作为强制依据 |

- 冲突裁决、采纳/适配/不采纳明细见 [reviews/backend-spec-fusion-2026-11-07.md](reviews/backend-spec-fusion-2026-11-07.md)。
- 关键裁决：保留复数资源与 `/api/v1`；访问分级只做网关策略；Boot 3.3.5 + Cloud 2023.0.3；生产禁 SNAPSHOT。

## 2. 技术参考

| 主题 | 参考 | 用途 |
|---|---|---|
| SSE | WHATWG HTML Standard: Server-sent events | 事件格式、重连、`Last-Event-ID` |
| 向量检索 | Milvus 官方文档：Hybrid Search / RRF / HNSW / Sparse | Collection、索引、混合检索参数 |
| 前端组件 | shadcn-vue、Reka UI、Tailwind CSS | 组件源码与可访问性基座 |
| 后端 | Spring Boot、Spring Cloud Gateway、LangChain4j、MyBatis-Plus | 分层、网关、AI 轻量调用、持久化 |
| AI 编排 | FastAPI、LangChain、pydantic、RAGAS | 流式、结构化输出、RAG 评测 |
| 数据库 | PostgreSQL 16 官方文档 | JSONB、部分索引、分区、行锁 |
| 消息 | RabbitMQ Quorum Queues / Publisher Confirms | 报告异步、可靠投递、重试 |
| 安全 | OWASP Top 10、OWASP ASVS | 认证、越权、注入、上传、SSRF |
| 观测 | OpenTelemetry、Prometheus、Grafana、Loki | Trace / Metrics / Logs |
| 规范 | Conventional Commits、SemVer | 提交与版本号 |

> 以上仅作工程参考；具体版本与用法以各项目锁定版本为准。文档不复制大段官方内容，只记录本项目采用的参数与约束。

## 3. 竞品观察与产品假设

> 以下为公开产品形态的概括性观察，**未做正式竞品测评**；结论是产品假设，需在 MVP 后通过用户访谈与数据验证。

| 类别 | 代表产品 | 公开形态 | OfferCoach 的差异假设 |
|---|---|---|---|
| 通用面试练习 | Google Interview Warmup | 提供问题练习与回答洞察，偏通用岗位 | 以“简历 × JD”缺口出题，题目可追溯到岗位要求 |
| AI 面试助手 | Final Round AI 等 | 实时辅助、模拟面试、反馈 | 不做出面试实时作弊；聚焦练习与复盘，强调证据与改进计划 |
| 国内题库/模拟面试 | 牛客、面试鸭等 | 题库、面经、模拟面试 | 不做大而全题库；做岗位针对性 + 结构化评分 + 成本可控 |
| 简历诊断 | 各类简历评分工具 | 简历打分、修改建议 | 诊断结果直接驱动面试题，形成“简历 → 面试 → 报告”闭环 |

**待验证假设**：

1. 用户更愿意为“针对性题目 + 具体改写”付费，而不是更多题库。
2. 报告中的“原文证据 + 一周改进计划”比总分更能提升留存。
3. 单场 10 分钟内完成，是完成率与付费转化的关键平衡点。
4. 语音作答的完成率高于文字，但 ASR 错误会伤害评分公平性。

## 4. 参考仓库使用声明

- 参考仓库为私有仓库，仅用于阅读其**文档组织结构与治理规范**。
- 未复制其业务代码、数据库结构、客户数据或大段正文；本文仅总结可复用的文档实践。
- 未在 OfferCoach 仓库或文档中保存该仓库的访问凭据、归档副本或克隆目录。
- 参考仓库中的结论若与 OfferCoach 业务冲突，以 OfferCoach 自身文档为准。

## 5. 变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.1 | 2026-11-07 | 新增本文：记录 Lamburger ERP 文档规范借鉴、技术参考、竞品假设与不采纳项 |