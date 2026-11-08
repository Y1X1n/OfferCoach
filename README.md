# OfferCoach

AI 模拟面试与简历诊断平台：上传简历 + 目标岗位 JD，生成针对性面试题；文字/语音作答，AI 逐题流式评分；最终产出可复盘、可执行的面试报告。

## 文档

完整文档入口：[docs/README.md](docs/README.md)（唯一索引：按“你要做的事”导航 + 全量地图 + 待决清单）。

| 想了解 | 入口 |
|---|---|
| 10 分钟了解项目 | [docs/overview.md](docs/overview.md) |
| 本期做什么、不做什么 | [docs/requirements.md](docs/requirements.md) |
| 业务规则与验收 | [docs/design/product-requirements.md](docs/design/product-requirements.md) |
| 架构与服务拆分 | [docs/design/architecture.md](docs/design/architecture.md) |
| 数据模型与建表规范 | [docs/design/data-model.md](docs/design/data-model.md) |
| API 与 SSE 契约 | [docs/design/api.md](docs/design/api.md) |
| AI / RAG / Prompt | [docs/design/ai-and-prompts.md](docs/design/ai-and-prompts.md) |
| 关键难点方案 | [docs/design/key-challenges.md](docs/design/key-challenges.md) |
| 开发规范与流程 | [docs/dev/conventions.md](docs/dev/conventions.md)、[docs/dev/workflow.md](docs/dev/workflow.md) |
| 前端 / UI / 后端 | [docs/dev/frontend.md](docs/dev/frontend.md)、[docs/dev/ui-system.md](docs/dev/ui-system.md)、[docs/dev/backend.md](docs/dev/backend.md) |
| 测试与验收 | [docs/dev/testing.md](docs/dev/testing.md) |
| 部署 / CI-CD / 观测 | [docs/ops/deployment.md](docs/ops/deployment.md)、[docs/ops/ci-cd.md](docs/ops/ci-cd.md)、[docs/ops/observability.md](docs/ops/observability.md) |
| 排期与里程碑 | [docs/dev/roadmap.md](docs/dev/roadmap.md) |
| 进度与待办 | [docs/CHANGELOG.md](docs/CHANGELOG.md) |

## 系统形态

```text
Vue 3 SPA
   │ HTTPS
   ▼
Nginx → Spring Boot oc-app（模块化单体）
          ├─ oc-identity    账号、额度、邀请与注册风控
          ├─ oc-interview   简历 / JD / 题库 / 会话 / 评分 / 报告
          ├─ oc-admin       管理端 API 与审计
          └─ oc-ai-client   OpenAI 兼容客户端 / Prompt / 启发式降级
PostgreSQL 16 · Redis 7 · Flyway
```

## 三个硬指标

1. 评分流式首 Token P95 < 1000ms。
2. RAG 题库 Recall@5 ≥ 0.85，nDCG@10 ≥ 0.80。
3. 单场 AI 成本 ≤ ¥0.30，可按用户与会话核算、可熔断。

## 状态

当前线上版本 `0.8.0`（2026-11-08）：注册/登录、简历解析与手录、JD 匹配、题库出题、文字作答、异步五维反馈、逐题场景化建议、报告与导出、管理端（题库/用户/指标/Prompt/审计/平台策略/AI 配置）。
题库审计与岗位族修复后，后端 JD 的 5 道题全部来自后端岗位族；详见 [audit-report.md](data/question-bank/audit-report.md)。
前端启动见 [frontend/README.md](frontend/README.md)，文档入口见 [docs/README.md](docs/README.md)。