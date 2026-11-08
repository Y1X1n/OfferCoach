# 可观测性与故障处置

> 状态：生效 ｜ 版本：v1.2 ｜ 更新：2026-11-07
> 本文覆盖指标、告警、日志、链路、看板、SLO 与 Runbook；部署拓扑见 [deployment.md](deployment.md)，发布门禁见 [ci-cd.md](ci-cd.md)。

## 1. 指标体系

### 1.1 技术指标

- HTTP：QPS、P50/P95/P99、5xx 率、活跃连接。
- JVM：堆、GC、线程、连接池（Hikari）。
- Python：进程内存、事件循环延迟、上游调用耗时。
- 中间件：Redis 命中率/延迟、RabbitMQ 队列深度/未 ACK、PG 连接数/慢查询、Milvus 检索延迟。

### 1.2 业务指标

| 指标 | 类型 | 用途 |
|---|---|---|
| `oc_sse_first_token_ms` | histogram | 首字性能 |
| `oc_sse_first_sentence_ms` | histogram | 首句可读 |
| `oc_sse_disconnect_total{reason}` | counter | 流稳定性 |
| `oc_ai_tokens_total{scene,model,direction}` | counter | Token 消耗 |
| `oc_ai_cost_micros_total{scene,model}` | counter | 成本 |
| `oc_quota_rejected_total{plan,reason}` | counter | 额度与防刷 |
| `oc_report_generation_seconds` | histogram | 报告时延 |
| `oc_rag_recall_at_5` | gauge | 检索质量（夜间评测） |
| `oc_prompt_version_errors_total{version}` | counter | 版本质量 |
| `oc_session_completion_rate` | gauge | 产品漏斗 |
| `oc_sse_active_connections{instance}` | gauge | 长连接负载 |

### 1.3 标签规范

- 通用：`service`、`env`、`instance`、`version`。
- AI：`scene`（parse/match/question/score/report）、`model`、`tier`、`prompt_version`。
- 禁止把 `userId`、`sessionId` 作为 Prometheus 标签（高基数）；只进日志/Trace。

## 2. 告警

| 告警 | 条件 | 级别 | 处置 |
|---|---|---|---|
| SSE 首字变慢 | P95 > 1200ms，5min | P1 | 查上游与网关缓冲 |
| 评分失败率高 | > 2%，5min | P1 | 切供应商/降级 |
| AI 供应商不可用 | 熔断打开 > 2min | P1 | 启用备用/模板反馈 |
| 报告积压 | 队列 > 100 或最老消息 > 5min | P2 | 扩 worker/查 DLQ |
| 额度对账不一致 | 任一用户余额 ≠ 流水 | P1 | 冻结变更，人工核对 |
| PG 连接耗尽 | 使用率 > 85% | P1 | 查慢查询/扩连接池 |
| 5xx 升高 | > 1%，5min | P1 | 回滚/限流 |
| 成本超预算 | 小时成本 > 120% | P2 | 降级/排查异常用户 |
| Outbox 积压 | > 1000 条 | P2 | 查 MQ/Relay |
| 证书到期 | < 14 天 | P3 | 续签 |

**告警原则**：

- 每条告警必须有 Runbook 链接、负责人、升级路径；没有处置方案的指标不配告警。
- 告警去重与抑制：同一根因只发一条；维护窗口静默。
- 分级：P0 全站不可用/数据泄露；P1 主链路受阻；P2 局部或成本异常；P3 提醒。

## 3. 日志

- 统一 JSON：`ts/level/service/env/traceId/requestId/userId/sessionId/scene/latencyMs/code/msg`。
- 不记录：简历/JD/回答正文、Token、密码、完整 Prompt。
- 采样：INFO 全量（低流量期），高峰期业务日志采样 20%；ERROR 全量。
- 保留：应用日志 30 天；审计日志 3 年；AI 调用日志 90 天。
- 日志中 `userId` 用内部 ID，禁止手机号/邮箱；异常堆栈只进日志，不回传客户端。
- 日志字段变更视为契约变更：看板与告警依赖的字段需同步更新。
- 统一 SLF4J + 占位符，禁止字符串拼接与生产 DEBUG；异常日志必须同时记录场景参数与堆栈。
- `ERROR` 只用于系统异常/重要错误，用户参数错误用 `WARN`；禁止重复 appender。
- DAO/Repository 不打业务日志；业务日志在 Application/Service 层记录并带 `requestId/traceId`。

## 4. 链路追踪

- OpenTelemetry 自动埋点 + 手动关键 Span：`assemble_context`、`llm_call`、`parse_feedback`、`rag_retrieve`、`rerank`。
- Java → Python 透传 `traceparent`；MQ 消息带 `traceId`，消费者创建 Link。
- 采样：正常 10%，错误 100%，慢请求（> 2s）100%。
- Trace 中记录模型名、Prompt 版本、Token 数，不记录内容。
- SSE 以 `streamId` 关联请求、上游调用与反馈落库；断流时保留已生成片段长度。

## 5. 看板

1. **系统总览**：QPS、错误率、P95、Pod 状态、依赖健康。
2. **面试漏斗**：创建 → 开始 → 完成 → 报告 → 查看。
3. **AI 质量**：TTFT、失败率、Token、成本、评分分布、降级率。
4. **RAG**：Recall/nDCG 趋势、低置信率、重复题率。
5. **商业化**：额度消耗、付费转化、退款/补偿。
6. **管理端**：Prompt 版本对比、实验分组指标。

看板与指标命名同源：每个面板必须标注数据来源、刷新周期、负责人。

## 6. SLO 与错误预算

| SLI | SLO | 计算 |
|---|---|---|
| 评分流首字 | 99% 请求 < 1s | `oc_sse_first_token_ms` |
| 评分成功率 | 99.5% | 成功反馈 / 提交回答 |
| 报告成功率 | 99% | READY / 触发数 |
| API 可用性 | 99.5%/月 | 非 5xx 比例 |
| 报告时延 | 95% < 30s | `oc_report_generation_seconds` |

- 错误预算耗尽 → 冻结非紧急发布，优先修复稳定性。
- SLO 评审每月一次；连续两月不达标需调整架构或降级策略。

## 7. Runbook

### 7.1 SSE 首字突然变慢

1. 看 `oc_sse_first_token_ms` 是服务端还是端到端变慢。
2. 服务端慢：查上游供应商状态、模型排队、Prompt 是否变长。
3. 端到端慢但服务端正常：查 Ingress/LB 缓冲、CDN、客户端网络。
4. 临时措施：切备用供应商、降低 `max_tokens`、关闭非关键上下文。
5. 复盘：记录变更（Prompt/模型/网关配置）。

### 7.2 AI 供应商故障

1. 确认熔断与错误码分布。
2. 手动切换到备用供应商（配置中心）。
3. 仍不可用：开启模板反馈降级，页面提示“精简模式”。
4. 停止非核心任务（评测、批量向量化）。
5. 恢复后逐步放量，观察错误率与 TTFT。

### 7.3 额度对账不一致

1. 冻结该用户额度变更（标记 `quota_locked`）。
2. 导出 `user_quota` 与 `quota_ledger`，定位分叉点。
3. 以流水为准修正余额，写补偿流水与审计日志。
4. 检查是否有重复消费/漏写流水，修复代码后灰度。
5. 全量对账，确认无其他用户受影响。

### 7.4 报告积压

1. 看队列深度、消费者数量、错误日志。
2. 若是 AI 超时：扩容 worker、提高超时、切小模型。
3. 若是 DB 锁：查长事务，必要时终止。
4. DLQ 消息人工重放；确认幂等。
5. 前端展示“生成较慢”，避免用户重复点击。

### 7.5 PG 连接耗尽

1. 查 `pg_stat_activity` 长事务与空闲连接。
2. 应用侧检查连接池泄漏（未关闭的流/事务）。
3. 临时提高 `max_connections` 与 PgBouncer 上限（若启用）。
4. 限流非核心接口，保护写入。
5. 修复后回归压测。

### 7.6 Milvus 不可用

1. 出题降级为题库随机 + 生成题，标记 `degraded`。
2. 检查 etcd/MinIO/磁盘。
3. 恢复后先小流量验证检索，再全量。
4. 如索引损坏，按 [../design/data-model.md](../design/data-model.md) §9.4 重建并切别名。

## 8. 故障流程

1. **发现**：告警/用户反馈/巡检；记录时间、影响面、版本。
2. **止血**：回滚、限流、降级、切供应商；先恢复，再定位。
3. **沟通**：P0/P1 每 15 分钟同步一次；对外文案避免技术细节与承诺。
4. **定位**：Trace + 日志 + 指标 + 最近变更；确认根因与影响数据。
5. **修复**：最小变更；补测试；灰度验证。
6. **复盘**：24h 内初稿，72h 内定稿；行动项有负责人与截止时间。
7. **归档**：Runbook 更新；若是一次性复查，记录到 `reviews/`，结论回写正文。