# OfferCoach 开发文档

> AI 模拟面试与简历诊断平台。本文是 `docs/` 的**唯一索引**：先按“你要做的事”找（§1），找不到再看全量地图（§2）。
> 状态：生效 ｜ 版本：v1.11 ｜ 更新：2026-11-13

**文档分工**：

- **做什么 / 不做什么** → [requirements.md](requirements.md)（需求索引，一条一行）+ [design/product-requirements.md](design/product-requirements.md)（业务规则与验收）
- **怎么设计** → [design/](design/)（架构、数据、接口、AI、难点、路线）
- **怎么干活** → [dev/](dev/)（规范、流程、前端、后端、UI、测试）
- **怎么上线与维护** → [ops/](ops/)（部署、CI/CD、可观测性）
- **做到哪了 / 还有什么没定** → [CHANGELOG.md](CHANGELOG.md) + 本文 §4
- **为什么这么选 / 借鉴了什么** → [reference.md](reference.md)

> 单一来源原则：同一结论只在一处维护，其他文档只留指针。发现两处冲突，以“权威文档”标注为准，并在同批修改中删掉重复结论。

## 1. 按你要做的事找

| 你要做的事 | 读这个 |
|---|---|
| 第一次了解项目（10 分钟） | [overview.md](overview.md)（定位 / 形态 / 架构 / 技术栈 / 非功能目标） |
| 确认本期做什么、不做什么 | [requirements.md](requirements.md)（需求编号、优先级、版本、状态） |
| 查业务规则、额度、状态机、验收口径 | [design/product-requirements.md](design/product-requirements.md) |
| 做语音作答 / 题目朗读 / 简历 OCR | [design/ai-tools-prd.md](design/ai-tools-prd.md)（ASR / TTS / OCR 口径与验收） |
| 做目标岗位联网获取（Search / Fetch） | [design/ai-jd-web-prd.md](design/ai-jd-web-prd.md)（面试出题用真实 JD） |
| 看服务怎么拆、为什么这么拆、核心时序 | [design/architecture.md](design/architecture.md) |
| 建表 / 改表 / 写实体 / 查索引与类型 | [design/data-model.md](design/data-model.md)（含命名、类型、软删、建表自查） |
| 写 / 改 HTTP 或 SSE 接口 | [design/api.md](design/api.md) + [dev/workflow.md](dev/workflow.md)（契约冻结与变更） |
| 写后端（分层 / 事务 / 幂等 / MQ） | [dev/backend.md](dev/backend.md) + [dev/conventions.md](dev/conventions.md) |
| 写前端（目录 / 类型 / 状态 / SSE） | [dev/frontend.md](dev/frontend.md) |
| 改界面 / 组件 / 交互 / 设计 Token | [dev/ui-system.md](dev/ui-system.md) |
| 做 AI 解析 / RAG / 评分 / Prompt | [design/ai-and-prompts.md](design/ai-and-prompts.md) |
| 查首字 <1s、RAG 精度、Token 成本、防刷 | [design/key-challenges.md](design/key-challenges.md) |
| 写测试 / 跑 AI 评测 / 做验收 | [dev/testing.md](dev/testing.md) |
| 本地起服务 / 部署 / 回滚 | [ops/deployment.md](ops/deployment.md) |
| 改 CI/CD / 打 tag / 灰度发布 | [ops/ci-cd.md](ops/ci-cd.md) |
| 查监控指标 / 告警 / 故障处置 | [ops/observability.md](ops/observability.md) |
| 看排期、里程碑、任务依赖 | [dev/roadmap.md](dev/roadmap.md) |
| 看做到哪了、还剩什么 | [CHANGELOG.md](CHANGELOG.md) |
| 查借鉴来源与取舍依据 | [reference.md](reference.md) |
| 追溯某次复查 / 改造 | [reviews/](reviews/)（只作留档，结论已回写正文） |

## 2. 全量文档地图

状态标记：`生效`（按此执行）/ `评审中`（可讨论、可改）/ `规划`（方向已定、未实施）/ `留档`（历史记录，不作执行依据）。

### 根级——入口与全景

| 文档 | 一句话 | 状态 |
|---|---|---|
| [overview.md](overview.md) | 产品定位、系统形态、架构、技术选型、非功能目标 | 生效 |
| [requirements.md](requirements.md) | 平台需求索引：一条需求一行，含编号、优先级、版本、来源 | 评审中 |
| [reference.md](reference.md) | 外部参考、借鉴来源、采纳/否决裁决 | 生效 |
| [CHANGELOG.md](CHANGELOG.md) | 里程碑总览、当前进度、已知待办 | 生效 |

### `design/`——产品与系统设计

| 文档 | 一句话 | 状态 |
|---|---|---|
| [product-requirements.md](design/product-requirements.md) | PRD：角色、旅程、业务规则、非功能指标、风险 | 评审中 |
| [ai-tools-prd.md](design/ai-tools-prd.md) | AI 工具 PRD：语音作答 ASR / 题目朗读 TTS / 简历 OCR（DeepSeek Tools API） | 评审中 |
| [ai-jd-web-prd.md](design/ai-jd-web-prd.md) | 目标岗位联网获取 PRD：Search / Fetch，面试出题用真实 JD | 评审中 |
| [architecture.md](design/architecture.md) | ADR、服务边界、逻辑架构、核心时序、容量与降级 | 生效 |
| [data-model.md](design/data-model.md) | PostgreSQL DDL、Redis Key、Milvus Collection、对象存储、建表规范 | 生效 |
| [api.md](design/api.md) | REST/SSE 契约、错误码、幂等、限流、接口总览 | 生效 |
| [ai-and-prompts.md](design/ai-and-prompts.md) | 简历/JD 解析、RAG、评分、报告、Prompt 版本与评测 | 生效 |
| [key-challenges.md](design/key-challenges.md) | 首字 <1s、RAG 精度、Token 成本、防刷、ASR、追问预判 | 生效 |

### `dev/`——开发规范、流程与实现

| 文档 | 一句话 | 状态 |
|---|---|---|
| [conventions.md](dev/conventions.md) | 跨端开发规范：分支/提交/代码组织/接口/数据库摘要（细节走指针） | 生效 |
| [workflow.md](dev/workflow.md) | 开发流程与契约管理：骨架层/字段层、冻结与变更、自测、联调 | 生效 |
| [pr-workflow.md](dev/pr-workflow.md) | PR 提交流程与硬性限制：分支、尺寸、评审、CI、合并、例外 | 生效 |
| [frontend.md](dev/frontend.md) | Vue 3 目录、TypeScript 约束、状态、SSE 封装、性能与安全 | 生效 |
| [ui-system.md](dev/ui-system.md) | Design Token、组件清单、核心组件规格、页面模板、交互铁律 | 生效 |
| [backend.md](dev/backend.md) | Spring Boot 分层、事务、幂等、额度、MQ、LangChain4j | 生效 |
| [testing.md](dev/testing.md) | 测试金字塔、E2E、AI 评测、性能、安全、防刷、验收清单 | 生效 |
| [roadmap.md](dev/roadmap.md) | 8 周里程碑、WBS、关键路径、DoD、风险登记 | 规划 |

### `ops/`——部署与运维

| 文档 | 一句话 | 状态 |
|---|---|---|
| [deployment.md](ops/deployment.md) | 环境矩阵、Compose、镜像、K8s、备份、安全基线、成本 | 生效 |
| [ci-cd.md](ops/ci-cd.md) | 流水线、迁移门禁、灰度、回滚、发布检查清单 | 生效 |
| [observability.md](ops/observability.md) | 指标、告警、日志、链路、看板、Runbook | 生效 |

### `reviews/`——复查与一次性记录

| 文档 | 一句话 | 状态 |
|---|---|---|
| [doc-consistency-review-2026-11-07.md](reviews/doc-consistency-review-2026-11-07.md) | 首版文档一致性问题、修复与遗留项 | 留档 |
| [frontend-review-2026-11-07.md](reviews/frontend-review-2026-11-07.md) | 前端逻辑/UI 复查、P1 修复与剩余 P2 | 留档 |
| [frontend-spec-audit-2026-11-07.md](reviews/frontend-spec-audit-2026-11-07.md) | 阿里前端规范对照、采纳取舍、工具链落地与验证 | 留档 |
| [backend-spec-fusion-2026-11-07.md](reviews/backend-spec-fusion-2026-11-07.md) | 后端规范对照、验收、M0 重构与验证记录 | 留档 |
| [full-stack-audit-2026-11-07.md](reviews/full-stack-audit-2026-11-07.md) | 前端/后端/数据库/中间件/契约五路审查、第一批修复与剩余优先级 | 留档 |
| [architecture-backend-review-2026-11-08.md](reviews/architecture-backend-review-2026-11-08.md) | 后端架构、边界与幂等复查 | 留档 |
| [data-infra-review-2026-11-08.md](reviews/data-infra-review-2026-11-08.md) | 数据模型、索引、Redis、备份与基础设施复查 | 留档 |
| [frontend-ux-review-2026-11-08.md](reviews/frontend-ux-review-2026-11-08.md) | 前端体验、可访问性与交互复查 | 留档 |
| [red-team-review-2026-11-08.md](reviews/red-team-review-2026-11-08.md) | 红队攻击面、可利用路径与优先级 | 留档 |
| [remediation-2026-11-08.md](reviews/remediation-2026-11-08.md) | 额度续发、看门狗、线程池隔离与报告等待评分 | 留档 |
| [load-test-2026-11-08.md](reviews/load-test-2026-11-08.md) | 本地并发压测、两轮修复与复测数据 | 留档 |
| [code-audit-2026-11-08.md](reviews/code-audit-2026-11-08.md) | 四路只读审计、第二批修复与未修优先级 | 留档 |

## 3. 维护规则

1. **单一来源**：同一结论只写一处。PRD 写业务规则，API 写契约，data-model 写表结构，ai-and-prompts 写 Prompt；其他文档只留指针，不复制正文。
2. **改动即更新**：涉及架构、契约、表结构、部署方式、额度规则的变更，必须在同一批提交中更新对应文档；接口变更同时更新 OpenAPI 与前端类型。
3. **结论归位**：决策确定后把结论写进对应主题正文，并从 §4 待决清单删除；不另建长期“决策台账”。历史原因走 git 记录，不在正文堆叠。
4. **待决显式**：未定事项用 `> 待决：` 引用块标注，并登记到 §4；确定后删除条目。
5. **新增文档**：放入对应目录（`design/` 设计、`dev/` 规范流程、`ops/` 运维、`reviews/` 留档），并在本索引登记；文件名使用小写英文单词 + 中划线（如 `data-access.md`）。根级只放入口与全景（README / overview / requirements / reference / CHANGELOG）。
6. **复查归位**：`reviews/` 只记录某次复查或一次性改造；结论必须回写到 `design/` 或 `dev/` 正文，不能让复查报告成为长期执行依据。
7. **状态先行**：每份文档标题下写状态与更新日期；状态变化时先改状态，再改内容。
8. **不写敏感信息**：文档不出现密钥、真实用户简历、供应商账号、未脱敏手机号/邮箱；成本单价用“配置项 + 示例”表达。

## 4. 待决事项清单

只登记仍会改变实现方向的未决项；已关闭项结论在对应正文，不在此列表。

| 编号 | 事项 | 当前口径 / 关闭时机 | 关联 |
|---|---|---|---|
| D-01 | LLM / Embedding / Rerank 供应商与单价基线 | 代码用 `ModelTier` 抽象，配置化；上线前按可用性、价格、数据合规拍板 | [ai-and-prompts.md](design/ai-and-prompts.md) §3/§14 |
| D-02 | ASR 供应商与音频保留期 | V1.1 前拍板；默认音频 30 天、转写后可提前删除 | [key-challenges.md](design/key-challenges.md) 难点六 |
| D-03 | 支付渠道与套餐定价 | V1.1；MVP 仅免费额度，支付用模拟回调 | [product-requirements.md](design/product-requirements.md) §6.7 |
| D-04 | PDF 渲染方案与模板来源 | V1.1；候选 Playwright 截图 / WeasyPrint；MVP 不做导出 | [api.md](design/api.md) §8.2 |
| D-05 | 文件存储最终形态与跨区备份 | 默认 MinIO；生产可选云 OSS，接口层保持 S3 协议 | [deployment.md](ops/deployment.md) §5 |
| D-06 | 生产部署目标与域名/证书 | 默认自建 K8s + Helm；托管 K8s 需评估 Ingress SSE 缓冲 | [deployment.md](ops/deployment.md) §4 |
| D-07 | 题库冷启动来源、版权与审核责任人 | 300 题人工 + 200 题 AI 生成后人工审核；上线前完成版权确认 | [key-challenges.md](design/key-challenges.md) 难点二 |
| D-08 | 评分人工标注集负责人、标注指南与复核节奏 | 黄金集 200 条，双人标注、第三人仲裁；每季度复核 | [ai-and-prompts.md](design/ai-and-prompts.md) §12 |
| D-09 | 数据保留期法务确认 | 简历 180 天 / 会话 24 个月 / 日志 90 天；上线前法务签字 | [data-model.md](design/data-model.md) §11 |
| D-10 | 邮件 / 短信验证码供应商 | MVP 用邮件，开发环境打日志；生产前选型 | [product-requirements.md](design/product-requirements.md) §6.1 |
| D-11 | 是否引入 PgBouncer | 连接池压测后决定；默认应用侧 Hikari 上限 20/实例 | [observability.md](ops/observability.md) §7.5 |
| D-12 | RAG 索引重建频率与全量评测窗口 | 默认题库发布增量、每日 02:00 全量评测；按题库增速调整 | [ai-and-prompts.md](design/ai-and-prompts.md) §12 |

## 5. 文档命名与版本

- 新增文档：`lowercase-english-with-hyphens.md`；目录名用完整英文单词，不缩写（`design` / `dev` / `ops` / `reviews`）。
- 存量编号文档已迁移为英文 slug，标题仍保留中文；交叉引用统一使用相对路径。
- 版本写在每份文档顶部：`状态：生效/评审中/规划 ｜ 版本：vX.Y ｜ 更新：YYYY-MM-DD`。
- 破坏性文档变更（删除章节、改变结论）需在 [CHANGELOG.md](CHANGELOG.md) 留一行说明。

## 6. 建议阅读顺序

- **产品 / 面试官**：overview → requirements → product-requirements → key-challenges → roadmap
- **前端**：overview → api → ui-system → frontend → conventions
- **后端**：architecture → data-model → api → backend → conventions → workflow
- **AI 工程**：ai-and-prompts → key-challenges → testing
- **运维**：architecture → deployment → ci-cd → observability