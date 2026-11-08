# 文档一致性复查（2026-11-07）

> 状态：留档 ｜ 类型：一次性复查 ｜ 结论已回写正文
> 范围：首版 12 份文档 + 吸收 Lamburger ERP 文档规范后的重组
> 说明：本文件只记录本次发现与处置，**不作为长期执行依据**；执行口径以 `design/` 与 `dev/` 正文为准。

## 1. 复查方法

1. 全量读取 12 份首版文档，建立“接口—数据—AI—前端—部署”交叉引用表。
2. 用脚本检查代码块闭合、内部链接、括号配对、版本/阈值一致性。
3. 对每条可疑项做“三问”：是否同一概念两种定义？是否违反状态机/事务/计费规则？是否会在真实实现中报错？
4. 修复后再次跑链接与代码块检查；结论回写对应正文。

## 2. 发现与处置

| # | 问题 | 风险 | 处置 | 回写位置 |
|---|---|---|---|---|
| 1 | 会话状态机用 `Map.of` 放 11 组键值对 | Java 编译失败（`Map.of` 最多 10 对） | 改 `Map.ofEntries(Map.entry(...))` | [backend.md](../dev/backend.md) §4.1 |
| 2 | 额度扣减在事务内 `catch` 唯一键冲突后继续查询 | PostgreSQL 事务已 aborted，后续 SQL 全部失败 | 改为行锁内幂等预检；唯一键冲突让事务回滚、调用方重试 | [backend.md](../dev/backend.md) §5 |
| 3 | 重新评分会插入第二条 feedback，但 `answer_id` 唯一 | 重评分直接失败 | 增加 `version` 与 `request_id`；唯一键改 `(answer_id, version)`、`request_id` 唯一；回写按 answerId 加锁 | [data-model.md](../design/data-model.md) §5、[backend.md](../dev/backend.md) §7 |
| 4 | 报告失败后会话状态与重试路径冲突 | 报告失败后无法重试或状态卡死 | 增加 `REPORT_FAILED`，允许 `REPORT_FAILED → REPORT_GENERATING`；重试重置 attempts | [data-model.md](../design/data-model.md) §5、[backend.md](../dev/backend.md) §10、[api.md](../design/api.md) §8.2 |
| 5 | PRD 把 PDF 导出列为 MVP P0，版本规划与排期列为 V1.1 | 范围蔓延、验收争议 | 统一为 V1.1；MVP 隐藏入口 | [requirements.md](../requirements.md)、[product-requirements.md](../design/product-requirements.md) §6.6、[ui-system.md](../dev/ui-system.md) §6.3 |
| 6 | 单场 Token 预算 25k/8k 与成本表 41k/12k 不一致 | 预算熔断过早触发或成本失真 | 统一为输入 ≤45k、输出 ≤12k；成本示例按 41k/12k 计算 | [ai-and-prompts.md](../design/ai-and-prompts.md) §11/§14、[key-challenges.md](../design/key-challenges.md) §3.5、[architecture.md](../design/architecture.md) §8 |
| 7 | 会话 `total_score` 为 `NUMERIC(3,1)`，报告总分 0–100 可能溢出 | 100.0 无法存储 | 改为 `NUMERIC(4,1)` 并加 0–100 CHECK | [data-model.md](../design/data-model.md) §5 |
| 8 | 题目主题枚举混入评分维度（CONTENT/STRUCTURE/LANGUAGE） | 数据语义混乱、筛选错误 | 题目维度限定为 DEPTH/PROJECT/BEHAVIOR/FIT；评分五维单独定义 | [data-model.md](../design/data-model.md) §5、[product-requirements.md](../design/product-requirements.md) §6.5 |
| 9 | SSE 返回 URL 带 `/api/v1`，前端 baseURL 也带 `/api/v1` | 双前缀请求 404 | 契约统一为相对 `/ai/feedback/stream` | [api.md](../design/api.md) §6.4/§9.1、[frontend.md](../dev/frontend.md) §6 |
| 10 | 幂等记录主键只有 `key` | 不同用户同 Key 冲突/越权回放 | 主键改 `(user_id, key)` | [data-model.md](../design/data-model.md) §7 |
| 11 | 简历软删后仍占 `(user_id, sha256)` 唯一键 | 重新上传同一文件失败 | 改为部分唯一索引 `WHERE deleted_at IS NULL` | [data-model.md](../design/data-model.md) §4 |
| 12 | 账号物理删除与历史会话/流水外键冲突 | 注销流程失败或数据断裂 | 注销改为删除内容 + 匿名化用户行；简历/JD 到期只清敏感字段 | [product-requirements.md](../design/product-requirements.md) §6.1/§8、[data-model.md](../design/data-model.md) §11 |
| 13 | 前端 401 刷新后无限递归 | 刷新失效时死循环 | `request(path, init, retry)`，刷新后只重试一次 | [frontend.md](../dev/frontend.md) §4.1 |
| 14 | 通用请求固定设置 `Content-Type: application/json`，上传 FormData 会坏 | 文件上传失败 | 仅非 FormData 设置 JSON Content-Type | [frontend.md](../dev/frontend.md) §4.1 |
| 15 | `useSseStream` 使用未定义的 `answerId`，签名缺参数 | 编译失败/请求错误 | 签名改为 `start(url, answerId, token, handlers)` | [frontend.md](../dev/frontend.md) §6 |
| 16 | 原“部署运维与可观测性”一篇混合部署、CI/CD、监控、Runbook | 难检索、职责不清 | 拆为 deployment / ci-cd / observability 三篇 | [ops/](../ops/) |
| 17 | 报告超时 PRD 90s、后端 120s | 行为不一致 | 统一硬超时 90s（P95 目标 30s） | [api.md](../design/api.md)、[backend.md](../dev/backend.md) §8/§10 |
| 18 | 反馈回写示例缺 `requestId`，无法区分重试与新评分 | 幂等失效 | 内部回调增加 `requestId`；AI 每次评分生成新 ID | [api.md](../design/api.md) §12、[backend.md](../dev/backend.md) §7 |

## 3. 未在本次关闭的项

以下属于产品/外部决策，不在文档一致性范围，已登记到 [README.md](../README.md) §4：

- D-01 AI 供应商与单价基线
- D-02 ASR 供应商与音频保留期
- D-03 支付渠道与套餐定价
- D-04 PDF 渲染方案
- D-05 文件存储形态与跨区备份
- D-06 生产部署目标与域名/证书
- D-07 题库冷启动版权与审核责任人
- D-08 评分标注负责人与复核节奏
- D-09 数据保留期法务确认
- D-10 验证码供应商
- D-11 PgBouncer
- D-12 索引重建与评测窗口

## 4. 复查结论

1. **状态机、额度、反馈版本、报告重试**是首版最容易被实现细节击穿的部分，已补成可执行规则。
2. **契约以 API 文档 + OpenAPI 为准**；前端类型由 OpenAPI 生成，禁止手写 DTO。
3. **文档重组后**，`design/` 管设计与规则，`dev/` 管规范与流程，`ops/` 管部署与运维，`reviews/` 只留档。
4. 本文的修复项已逐条回写正文；后续若再发现同类问题，新建复查记录，不复用本文件。

## 5. 参考仓库使用声明

- 本次仅阅读 Lamburger ERP 文档仓的**组织结构与治理规范**，未复制业务内容。
- 未在 OfferCoach 仓库中保留该私有仓库的归档、克隆目录或访问凭据。
- 借鉴清单与不采纳项见 [reference.md](../reference.md)。