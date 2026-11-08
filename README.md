# OfferCoach

AI 模拟面试与简历诊断平台：上传简历与目标岗位 JD，自动出题、逐题分析、生成可复盘的面试报告。

项目展示：<https://y1x1n.github.io/OfferCoach/>（静态展示页，不连接在线后端）

## 功能

- **账号与额度**：注册开关、算术验证码、可选邀请码、邀请奖励、单 IP / 单设备注册风控；FREE / PRO 额度、每日面试与解析上限、月度续发。
- **简历**：PDF / DOCX 上传解析、手动结构化录入、字段修正、默认简历、软删与重解析；支持 Markdown / JSON / 打印 PDF 导出。
- **岗位 JD**：文本结构化、技能与职责抽取、简历匹配度、匹配证据与缺口建议。
- **模拟面试**：题库优先、AI / 模板兜底；按维度配额出题；暂停、恢复、跳过、放弃；答题草稿与刷新恢复。
- **逐题反馈**：异步生成后一次性展示，避免等待流式输出；五维评分、证据引用、场景化优点 / 改进建议与 PRO 改写。
- **报告**：五维雷达、亮点与风险、改进计划、逐题拆解（回答原文 + 证据）、失败重试、打印导出。
- **管理端**：题库 CRUD / 导入 / 发布、用户角色 / 解锁 / 重置密码 / 额度、指标概览、AI 调用日志、Prompt 版本、平台策略、AI 运行时配置、审计日志。

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3.5、TypeScript 5.6（strict）、Vite 6、Pinia 2.3、Vue Router 4.5、Tailwind CSS 3.4、Reka UI、lucide-vue-next |
| 后端 | JDK 21、Spring Boot 3.3.5、Spring Security（OAuth2 Resource Server）、Maven 多模块、MyBatis-Plus 3.5.7 |
| 数据 | PostgreSQL 16（`pg_trgm`）、Redis 7.2、Flyway |
| 文档处理 | PDFBox 3.0.3、Apache POI 5.3.0 |
| AI | OpenAI 兼容 `/chat/completions`；运行时可切换 Base URL / Key / 模型；无 Key 时启发式 / 模板降级 |
| 质量 | JUnit 5、AssertJ、ArchUnit、Testcontainers、Vitest、ESLint、Prettier、Spotless、Checkstyle、JaCoCo |
| 部署 | Docker Compose、Nginx、Let's Encrypt |

## 架构

```text
Browser（Vue 3 SPA）
   │ HTTPS / JSON / 轮询
   ▼
Nginx ──▶ Spring Boot oc-app（模块化单体，端口 8081）
             ├─ oc-identity   认证、JWT、额度、邀请与注册风控
             ├─ oc-interview  简历、JD、题库、会话、评分、报告、看门狗
             ├─ oc-admin      管理端 API、平台策略、审计
             ├─ oc-ai-client  OpenAI 兼容客户端、密钥加密、运行时配置
             └─ oc-common     错误码、Trace、Redis Key
                          │
              PostgreSQL 16 · Redis 7 · Flyway
```

| 模块 | 文件 / 行数 | 职责 |
|---|---:|---|
| `oc-interview` | 128 / 11,945 | 简历解析与编辑、JD 匹配、题库检索、会话状态机、答题、反馈、报告、异步 Worker |
| `oc-identity` | 57 / 4,178 | 注册 / 登录 / Refresh 轮换、额度与流水、邀请奖励、验证码、注册风控、平台策略 |
| `oc-admin` | 51 / 4,424 | 题库、用户、指标、AI 日志、Prompt、实验、平台策略、审计 |
| `oc-ai-client` | 8 / 807 | OpenAI 兼容调用、AES-GCM 密钥、URL 安全校验、运行时配置 |
| `oc-common` | 5 / 218 | 错误码、异常、Trace、Redis Key |
| `oc-app` | 8 / 409 | Spring Boot 聚合启动、安全配置、管理员 / 演示账号引导 |
| `oc-gateway` | 2 / 104 | 预留网关模块，当前生产由 Nginx 直接反代 `oc-app` |
| `oc-report-worker` | 1 / 4 | 预留独立 Worker，当前报告由 `oc-interview` 内调度器生成 |

**关键链路**

1. 简历上传 → 魔数校验 → 异步解析 → 结构化字段与置信度。
2. JD 创建 → 启发式结构化 → 岗位族推断 → 技能匹配与缺口。
3. 创建面试 → 岗位族 + 关键词检索题库 → 维度配额选题 → AI / 模板补齐 → 会话 `READY`。
4. 提交回答 → 事务内落库 → 异步生成反馈 → 前端轮询 `GET feedback` → 一次性展示。
5. 结束面试 → 报告 Worker 抢占 `GENERATING` → 聚合并等待评分完成 → 写入雷达 / 计划 / 逐题拆解。

## 代码量

统计口径：不含 `target`、`node_modules`、`dist`、题库 JSONL 与构建产物。

| 范围 | 文件 | 行数 |
|---|---:|---:|
| 后端主代码（8 个模块） | 260 | 25,089 |
| 前端 Vue 组件（13 页面 + 68 组件） | 82 | 13,509 |
| 前端 TypeScript（API / 状态 / 工具，含 5 个单测） | 35 | 4,946 |
| 前端样式 | 2 | 141 |
| **业务代码合计** | **379** | **43,685** |
| 后端测试 | 15 | 1,066 |
| SQL 迁移 | 9 | 593 |
| 脚本（题库 / 冒烟 / 验证） | — | 2,906 |
| 文档 | 32 | 9,491 |

其他规模：18 个 Controller、74 个 HTTP 端点、21 张业务表、22,409 条题库（MIT 来源，不在仓库内）。

## 目录结构

```text
backend/
  oc-app/            Spring Boot 启动、安全与配置
  oc-identity/       账号、额度、邀请、注册风控
  oc-interview/      简历、JD、题库、会话、评分、报告
  oc-admin/          管理端 API
  oc-ai-client/      AI 客户端与运行时配置
  oc-common/         通用错误与缓存 Key
  compose.yaml       本地 PostgreSQL / Redis / RabbitMQ / MinIO
  scripts/           冒烟与验证脚本
frontend/
  src/api/           API 客户端（401 刷新、轮询、幂等）
  src/pages/         13 个页面
  src/components/    68 个业务与 UI 组件
  src/stores/        Pinia 会话与额度
data/
  test-resumes/      虚构测试简历与 JD
  question-bank/     题库审计报告与许可证说明
docs/                产品、架构、API、数据、部署与审查文档
scripts/qbank/       题库构建、过滤、清洗与相关性评估
```

## 快速开始

前置：JDK 21、Maven 3.9+、Node.js 20+、pnpm、Docker。

**后端**

```bash
cd backend
cp .env.example .env          # 修改本地密码；DEMO_PASSWORD 留空则不创建演示账号
docker compose up -d postgres redis
set -a; . ./.env; set +a
mvn -q -DskipTests install
mvn -pl oc-app spring-boot:run   # http://127.0.0.1:8081
```

健康检查：`GET /actuator/health`；接口文档：`/swagger-ui.html`。

**前端**

```bash
cd frontend
pnpm install
pnpm dev                         # http://localhost:5174
```

Vite 开发服务器默认把 `/api` 代理到 `http://localhost:8080`（预留 gateway）；直接连 `oc-app` 时设置 `VITE_PROXY_TARGET=http://localhost:8081`。也可用 `VITE_API_BASE_URL` 指向其他环境。

**测试与构建**

```bash
cd backend && mvn verify         # 单测 + ArchUnit + Spotless + Checkstyle + JaCoCo
cd frontend && pnpm check        # typecheck + ESLint + Vitest + build
```

## 题库

仓库不包含克隆的第三方仓库和全量题库 JSONL，只保留审计报告与可复现脚本：

```bash
node scripts/qbank/build-question-bank.mjs      # 从本地克隆仓库生成
node scripts/qbank/filter-permissive.mjs        # 仅保留 MIT 来源
node scripts/qbank/clean-and-evaluate.mjs       # 清洗、质量分、相关性评估
```

生产导入路径由 `OC_QBANK_IMPORT_PATH` 指定；导入器按 `(source_repo, source_path, stem_hash)` 幂等，并在启动时排除非宽松许可来源。

审计结论：22,409 条题库无重复题干、无链接题；主要问题是岗位族缺失与维度分布不均。修复后后端 JD 的 top 20 核心命中率 65% → 100%，非本域题目 50% → 0%，真实验证 5/5 题为后端岗位族。详见 [audit-report.md](data/question-bank/audit-report.md)。

## 配置与安全

- Access Token 为 RS256 JWT（15 分钟，只存前端内存）；Refresh Token 为 HttpOnly Cookie（7 天、家族轮换、重放即吊销）。
- 生产必须显式配置 `CORS_ORIGINS`、数据库 / Redis / JWT 密钥；仓库不保存生产凭据。
- AI Key 使用 AES-GCM 加密存库，根密钥来自 `OC_AI_SECRET`；接口只返回末 4 位掩码。
- AI Base URL 默认拒绝私网与回环地址，避免 SSRF。
- 上传校验扩展名、MIME、文件魔数、DOCX 解压比与 PDF 页数。
- 注册支持算术验证码、单 IP / 单设备日限、邀请码可选；邀请奖励有每日与累计上限。
- 管理端写操作落 `admin_audit_log`，平台策略与 AI 配置变更可通过审计追踪。

## 已知限制

- 尚无 CI 流水线与镜像 digest 固定；发布仍为手工构建。
- 备份在单机，未接入异地 / PITR；Redis 为单实例。
- 管理端尚未启用 MFA / IP 白名单；Prompt 注入防护仍需加强。
- 题库少量维度标注存在语义噪声，AI-Agent 长题较多，建议继续人工抽检。
- 历史报告中的逐题反馈是生成时快照，不会随评分规则升级自动重算。

## 文档

| 想了解 | 入口 |
|---|---|
| 10 分钟了解项目 | [docs/overview.md](docs/overview.md) |
| 产品范围与验收 | [docs/design/product-requirements.md](docs/design/product-requirements.md) |
| 架构与服务拆分 | [docs/design/architecture.md](docs/design/architecture.md) |
| 数据模型 | [docs/design/data-model.md](docs/design/data-model.md) |
| API 契约 | [docs/design/api.md](docs/design/api.md) |
| AI / Prompt | [docs/design/ai-and-prompts.md](docs/design/ai-and-prompts.md) |
| 开发规范 | [docs/dev/conventions.md](docs/dev/conventions.md) |
| 测试与部署 | [docs/dev/testing.md](docs/dev/testing.md)、[docs/ops/deployment.md](docs/ops/deployment.md) |
| 进度与审查 | [docs/CHANGELOG.md](docs/CHANGELOG.md)、[docs/reviews/](docs/reviews/) |

## License

代码仅用于学习与面试练习。题库来源与许可证见 [data/question-bank/LICENSES.md](data/question-bank/LICENSES.md)；仓库不包含非宽松许可来源的数据。