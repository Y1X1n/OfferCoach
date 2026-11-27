# OfferCoach

AI 模拟面试与简历诊断平台：上传简历与目标岗位 JD，自动出题、逐题点评，最终产出一份可复盘的面试报告。

> 仓库已转为**私有**，此前的 GitHub Pages 静态展示页随之下线；本地按下方「快速开始」即可跑通全栈。

---

## 目录

- [功能](#功能)
- [技术栈](#技术栈)
- [架构](#架构)
- [目录结构](#目录结构)
- [快速开始](#快速开始)
- [支付宝小程序（参赛版）](#支付宝小程序参赛版)
- [测试与质量门禁](#测试与质量门禁)
- [规模](#规模)
- [题库](#题库)
- [配置与安全](#配置与安全)
- [已知限制](#已知限制)
- [文档](#文档)
- [上游依赖与许可](#上游依赖与许可)

## 功能

- **账号与额度**：注册开关、算术验证码、可选邀请码与邀请奖励、单 IP / 单设备注册风控；FREE / PRO 额度、每日面试与解析上限、额度月度续发与到期降级；支持个人数据 JSON 导出与密码确认注销。
- **简历**：PDF / DOCX / 图片 / PPT 上传解析（扫描件自动 OCR 兜底）、手动结构化录入、字段修正、默认简历、软删与重解析；支持 Markdown / JSON / 打印 PDF 导出，并在版式预设下实时预览。
- **岗位 JD**：文本结构化、技能与职责抽取、简历匹配度与缺口建议；同用户相同 JD 自动去重。
- **目标岗位获取**：创建面试时**粘贴招聘网站链接**（优先抓取，抓不到自动改用搜索兜底）或**直接搜索岗位**；预览 JD 原文与必备技能后用于定制题目。
- **模拟面试**：**联网搜索优先**（按岗位标题与技能检索公开面经线索，再由模型出题，覆盖各行业）、题库与模板兜底、按维度配额出题，生成题沉淀为行业题库草稿；暂停 / 恢复 / 跳过 / 放弃、答题草稿与刷新恢复；支持**语音作答**（录音 → 转写 → 可编辑提交）、**题目朗读**（TTS，音色可选、服务端缓存）与**基于回答的追问**（每场最多 2 次，计入题量、不额外扣额度）。
- **逐题反馈**：异步生成后一次性展示（不做流式 token 渲染）；五维评分、证据引用、场景化优点 / 改进建议与 PRO 改写。
- **报告**：五维雷达、亮点与风险、改进计划、逐题拆解（回答原文 + 证据）、失败重试、打印导出；新增分数趋势与弱项专项练习入口。
- **管理端**：题库 CRUD / 导入 / 发布归档、用户角色 / 解锁 / 重置密码 / 额度补偿、指标概览（含 AI Token / 估算成本 / 首字 P95）、AI 调用日志（按会话 / 模型 / 请求编号筛选）、运行诊断（报告积压与单场会话时间线）、Prompt 模板与版本、实验、平台策略、AI 运行时配置、审计日志。
- **可观测性**：请求 / 用户 / 会话 Trace 贯通到 AI 工具与支付服务；AI 调用与报告生成写入业务指标；前端错误可按请求编号反馈。
- **套餐与支付**：`GET /plans`、`POST /orders` 下单并生成支付二维码（微信支付 v3 / 支付宝扫码，独立服务 `oc-pay`）；渠道回调验签后幂等发放套餐与额度，支持退款与对账兜底。

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3.5、TypeScript 5.6（strict）、Vite 6、Pinia 2.3、Vue Router 4.5、Tailwind CSS 3.4、Reka UI、lucide-vue-next |
| 后端 | JDK 21、Spring Boot 4.2.0、Spring Security（OAuth2 Resource Server）、Maven 多模块、MyBatis-Plus 3.5.18、Jackson 3（`tools.jackson.*`） |
| 数据 | PostgreSQL 16（`pg_trgm`）、Redis 7.2、Flyway |
| 文档处理 | PDFBox 3.0.3、Apache POI 5.3.0 |
| AI | 对话走 OpenAI 兼容 `/chat/completions`；语音 / 识别 / 联网检索走 DeepSeek 工具接口 `/v1/tools/{asr,tts,ocr,search,fetch}`；运行时可切换 Base URL / Key / 模型；无 Key 时启发式 / 模板降级 |
| 质量 | JUnit 5、AssertJ、Mockito、ArchUnit、Testcontainers、Vitest、ESLint、Prettier、Spotless、Checkstyle、JaCoCo、maven-enforcer |
| 部署 | Docker Compose、Nginx、Let's Encrypt |

## 架构

模块化单体（`oc-app` 为唯一公网入口）+ 一组仅内网可达的 AI 工具微服务。

```text
Browser（Vue 3 SPA）
   │ HTTPS / JSON / 轮询
   ▼
Nginx ──▶ Spring Boot oc-app（模块化单体，端口 8081）
             ├─ oc-identity   认证、JWT、额度、邀请、注册风控、平台策略
             ├─ oc-interview  简历、JD、题库、会话状态机、答题、反馈、报告、看门狗
             ├─ oc-admin      管理端 API、指标、Prompt、审计
             ├─ oc-ai-client  OpenAI 兼容客户端、AES-GCM 密钥、运行时配置
             ├─ oc-common     错误码、Trace、Redis Key、摘要工具
             ├─ oc-ai         AI 工具域：oc-ai-core 共享库
                              + oc-ai-asr(8094) / oc-ai-tts(8095) / oc-ai-ocr(8096)
                              / oc-ai-search(8097) / oc-ai-fetch(8098)（仅回环监听，无公网路由）
             └─ oc-pay        支付域：oc-pay-core 共享库 + oc-pay-service(8099)
                              （微信支付 v3 / 支付宝扫码；仅回环监听，渠道回调 /pay/notify/* 经 Nginx 反代）
                          │
              PostgreSQL 16 · Redis 7 · Flyway
```

`oc-app` 是所有业务判断的唯一归属（鉴权、会话校验、额度、去重、降级）；AI 工具服务只做「文件 ↔ 文本 / 音频」转换，`oc-pay-service` 只做「订单 ↔ 渠道」，通过共享密钥 `X-OC-Internal-Token` 鉴权。支付成功后由 `oc-pay` 回调 `oc-app` 发放套餐与额度（金额与套餐以服务端为准，见 [design/payment.md](docs/design/payment.md)）。

**模块职责与规模**（`src/main/java`）

| 模块 | 文件 / 行数 | 职责 |
|---|---:|---|
| `oc-interview` | 144 / 14,771 | 简历解析与编辑、JD 匹配、题库检索、会话状态机、答题、反馈、报告、异步 Worker |
| `oc-identity` | 61 / 4,624 | 注册 / 登录 / Refresh 轮换、额度与流水、邀请奖励、验证码、注册风控、平台策略 |
| `oc-admin` | 54 / 4,689 | 题库、用户、指标、AI 日志、Prompt、实验、平台策略、审计 |
| `oc-ai` | 23 / 2,046 | `oc-ai-core`（1,197 行：工具客户端、内部鉴权、并发限流、`ai_call_log` 写入、DTO）+ asr / tts / ocr / search / fetch 五个独立服务 |
| `oc-pay` | 46 / 3,529 | `oc-pay-core`（契约 DTO、配置、内部令牌工具、`PayGateway`）+ `oc-pay-service`（订单生命周期、微信 v3 / 支付宝渠道适配与验签、退款、对账扫描、额度发放通知） |
| `oc-ai-client` | 8 / 1,013 | OpenAI 兼容调用、AES-GCM 密钥、URL 安全校验、运行时配置 |
| `oc-app` | 8 / 413 | Spring Boot 聚合启动、安全配置、管理员 / 演示账号引导 |
| `oc-common` | 6 / 313 | 错误码、异常、Trace、Redis Key、SHA-256 摘要 |
| `oc-gateway` | 2 / 104 | 预留网关模块；当前生产由 Nginx 直接反代 `oc-app` |
| `oc-report-worker` | 1 / 4 | 预留独立 Worker；当前报告由 `oc-interview` 内调度器生成 |

**关键链路**

1. 简历上传 → 扩展名 / MIME / 魔数校验 → 异步解析 → 结构化字段与置信度。
2. JD 创建 → 启发式结构化 → 岗位族推断 → 技能匹配与缺口。
3. 创建面试 → 岗位族 + 关键词检索题库 → 维度配额选题 → AI / 模板补齐 → 会话 `READY`。
4. 提交回答 → 事务内落库 → 异步生成反馈 → 前端轮询 `GET feedback` → 一次性展示。
5. 结束面试 → 报告 Worker 抢占 `GENERATING` → 聚合并等待评分完成 → 写入雷达 / 计划 / 逐题拆解。
6. 语音作答 → 浏览器采 PCM 封 16 kHz WAV → `oc-app` 校验（会话 / 题目 / 魔数）→ `oc-ai-asr` 转写 → 走文本作答同一链路。
7. 题目朗读 → `oc-app` 校验归属与每题次数 → `oc-ai-tts` 合成（内容哈希缓存）→ 浏览器播放。
8. 简历扫描件 → 本地抽取文本过短或为图片 / PPT → `oc-ai-ocr` 识别 → 清理坐标标记 → 结构化。
9. 目标岗位获取 → 粘贴链接优先 `oc-ai-fetch` 抓取 → 抓不到自动 `oc-ai-search` 兜底 → 抽取 JD 正文 → 预览确认 → `previewToken` → 创建面试事务内落库并用于出题。
10. 套餐下单 → `oc-app` 校验套餐（金额以服务端为准）→ `oc-pay` 渠道下单返回二维码 → 用户扫码支付 → 渠道回调验签置 `PAID` → `oc-pay` 回调 `oc-app` 幂等发放套餐与额度（`payment_entitlement.order_no` 唯一）。

## 目录结构

```text
backend/
  oc-app/            Spring Boot 启动、安全与配置
  oc-identity/       账号、额度、邀请、注册风控、平台策略
  oc-interview/      简历、JD、题库、会话、评分、报告、语音与朗读
  oc-admin/          管理端 API
  oc-ai-client/      AI 客户端与运行时配置
  oc-ai/             AI 工具域（oc-ai-core + 五个工具服务）
  oc-pay/            支付域（oc-pay-core + oc-pay-service）
  oc-common/         通用错误、Trace、缓存 Key
  oc-gateway/        预留网关
  oc-report-worker/  预留报告 Worker
  compose.yaml       本地 PostgreSQL / Redis / RabbitMQ / MinIO
  scripts/           冒烟与验证脚本
frontend/
  src/api/           API 客户端（401 刷新重放、轮询、幂等）
  src/pages/         13 个路由页面
  src/components/    业务组件与 UI 原子件（含 admin / interview / settings 等域）
  src/composables/   会话、语音朗读、设置等组合式逻辑
  src/stores/        Pinia 会话与额度
miniprogram/         支付宝小程序工程（首页 / 准备 / 答题 / 报告）
agent/               千问平台独立功能检测入口（agent.py + 样例）
data/
  test-resumes/      虚构测试简历与 JD
  question-bank/     题库审计报告与许可证说明
docs/                产品、架构、API、数据、部署与审查文档
scripts/qbank/       题库构建、过滤、清洗与相关性评估
site/                静态展示页源码（仓库转私有后 GitHub Pages 已下线）
SECURITY.md          安全策略与漏洞上报方式
```

## 快速开始

**方式一：Docker 一键启动**（只需 Docker，镜像内完成前后端构建）

```bash
cp .env.example .env      # 可选：修改端口/密码；不创建也能用内置默认值启动
docker compose up -d --build
```

启动后访问 `http://localhost:8082`，`/api` 由前端容器反代到 `oc-app`。首次构建需拉取 Maven / Node / Nginx 基础镜像。

- 停止 `docker compose down`；连数据卷一起清空 `docker compose down -v`。
- 本地默认开启固定验证码 `123456`，可直接注册；管理端需在 `.env` 中设置 `ADMIN_*`。
- AI 工具服务与支付服务暂未纳入该 compose，按下方步骤在宿主机启动即可（容器默认经 `host.docker.internal` 访问）。

**方式二：本地开发**（前置：JDK 21、Maven 3.9+、Node.js 20+、pnpm、Docker）

**后端**

```bash
cd backend
cp .env.example .env          # 修改本地密码；DEMO_PASSWORD 留空则不创建演示账号
docker compose up -d postgres redis
set -a; . ./.env; set +a
mvn -q -DskipTests install
mvn -pl oc-app spring-boot:run   # http://127.0.0.1:8081
```

健康检查 `GET /actuator/health`；接口文档 `/swagger-ui.html`。

AI 工具服务（可选）按需单独启动，各自读取同一份运行时配置：

```bash
mvn -pl oc-ai/oc-ai-asr  spring-boot:run   # 8094
mvn -pl oc-ai/oc-ai-tts  spring-boot:run   # 8095
mvn -pl oc-ai/oc-ai-ocr  spring-boot:run   # 8096
mvn -pl oc-ai/oc-ai-search spring-boot:run # 8097
mvn -pl oc-ai/oc-ai-fetch  spring-boot:run # 8098
```

支付服务（可选）同样按需单独启动，需与 oc-app 配置同一 `OC_PAY_INTERNAL_TOKEN`：

```bash
mvn -pl oc-pay/oc-pay-service spring-boot:run   # 8099；本地联调可开 OC_PAY_MOCK_ENABLED=true
```

**前端**

```bash
cd frontend
pnpm install
pnpm dev                         # http://localhost:5174
```

Vite 开发服务器默认把 `/api` 代理到 `http://localhost:8080`（预留 gateway）；直接连 `oc-app` 时设置 `VITE_PROXY_TARGET=http://localhost:8081`。也可用 `VITE_API_BASE_URL` 指向其他环境。

## 支付宝小程序（参赛版）

面向「支付宝·智能体涌现奖」的交付形态：同一套后端，外加一个原生支付宝小程序与一个可独立运行的检测入口。

| 产物 | 位置 | 说明 |
|---|---|---|
| 小程序工程 | `miniprogram/` | 12 个页面：工作台、简历、岗位（文本/链接/搜索）、面试配置、答题（语音/朗读/追问）、报告与趋势、我的、套餐支付；设备指纹游客登录，不持有模型密钥 |
| 检测入口 | `agent/` | `agent.py`（Python 3.12 仅标准库），复现「简历 → 岗位 → 出题 → 评分 → 报告」链路 |
| 本地预览 | `scripts/competition/preview/` | 直接跑真实 axml/acss/js，`my.*` 代理到本地后端 |
| 小程序支付 | `backend/oc-pay`、`oc-identity` | `payMode=MINI` + `POST /orders/{orderNo}/miniapp-pay`：授权码换买家标识后 `alipay.trade.create`，返回 `tradeNo` 给 `my.tradePay` |
| 打包与自检 | `scripts/competition/` | `build-product.py` 产出 `product.zip`，`check-package.py` 校验结构/大小/`--version` |

```bash
# 本地预览（需先启动 oc-app，默认 127.0.0.1:8081）
node scripts/competition/preview/server.js
# → http://127.0.0.1:5180/preview/?page=pages/index/index&code=123456

# 组装并校验参赛包
python scripts/competition/build-product.py
python scripts/competition/check-package.py
```

部署配置（后端域名、体验码、服务器域名白名单）见 [docs/competition/miniprogram.md](docs/competition/miniprogram.md)；
检测入口的环境变量与输出约定见 [docs/competition/agent-entry.md](docs/competition/agent-entry.md)。

## 测试与质量门禁

```bash
cd backend  && mvn verify        # 单测 + JaCoCo + Spotless + Checkstyle + ArchUnit + enforcer
cd frontend && pnpm check        # typecheck + ESLint + Vitest + build
```

CI 对每个 PR 运行 `Backend verify` 与 `Frontend check`。其中：

- **ArchUnit** 约束分层与模块无环，并强制「AI 域不得反向依赖业务模块」。
- **maven-enforcer** 禁止 AI 工具服务之间互相依赖（它们的类不在同一 classpath，ArchUnit 无法覆盖跨模块依赖）。
- **Spotless/Checkstyle** 统一 4 空格、120 列、UTF-8、LF。

## 规模

统计口径：不含 `target`、`node_modules`、`dist`、题库 JSONL 与构建产物。

| 范围 | 文件 | 行数 |
|---|---:|---:|
| 后端主代码（10 个模块） | 353 | 31,506 |
| 后端测试 | 37 | 3,339 |
| 前端 Vue | 96 | 14,697 |
| 前端 TypeScript（含 6 个单测） | 42 | 6,129 |
| SQL 迁移 | 17 | 793 |
| 文档 | 38 | 11,283 |
| 脚本（题库 / 冒烟 / 验证） | 5 | 1,878 |

其他规模：24 个 Controller、84 个 HTTP 端点、23 张业务表、22,409 条题库（MIT 来源，不在仓库内）。

## 题库

仓库不包含克隆的第三方仓库和全量题库 JSONL，只保留审计报告与可复现脚本：

```bash
node scripts/qbank/build-question-bank.mjs      # 从本地克隆仓库生成
node scripts/qbank/filter-permissive.mjs        # 仅保留 MIT 来源
node scripts/qbank/clean-and-evaluate.mjs       # 清洗、质量分、相关性评估
```

生产导入路径由 `OC_QBANK_IMPORT_PATH` 指定；导入器按 `(source_repo, source_path, stem_hash)` 幂等，并在启动时排除非宽松许可来源。

审计结论：22,409 条题库无重复题干、无链接题；主要问题是岗位族缺失与维度分布不均。修复后后端 JD 的 top 20 核心命中率 65% → 100%，非本域题目 50% → 0%。详见 [audit-report.md](data/question-bank/audit-report.md)。

## 配置与安全

- Access Token 为 RS256 JWT（15 分钟，只存前端内存）；Refresh Token 为 HttpOnly Cookie（7 天、家族轮换、重放即吊销）。
- 生产必须显式配置 `CORS_ORIGINS`、数据库 / Redis / JWT 密钥；仓库不保存生产凭据，文档一律使用占位符（`<PUBLIC_DOMAIN>`、`<ECS_PUBLIC_IP>`、`<ECS_INSTANCE_ID>`）。
- AI Key 使用 AES-GCM 加密存库，根密钥来自 `OC_AI_SECRET`；接口只返回掩码。
- AI Base URL 默认拒绝私网与回环地址，避免 SSRF；抓取链接再做一层协议 / 主机校验，避免被当作开放代理。
- AI 工具次数上限（朗读每题、OCR / 搜索 / 抓取每日）属**防滥用限流**，走 Redis 原子计数；计费额度仍以 `quota_ledger` 流水为事实源（见 ADR-012）。
- 上传校验扩展名、MIME、文件魔数、DOCX 解压比与 PDF 页数；语音音频额外校验 WAV / Ogg Opus 魔数。
- 管理端写操作落 `admin_audit_log`，平台策略与 AI 配置变更可审计追踪。

## 已知限制

- 生产部署版本落后于 `main`：AI 工具能力与部分并发加固已完成本地 / 真实 API 验证，但尚未发布到线上。
- 备份在单机，未接入异地 / PITR；Redis 为单实例。
- 管理端尚未启用 MFA / IP 白名单；Prompt 注入防护仍需加强。
- 会话创建暂不接受 `answerMode=VOICE`（语音是逐题选择，非会话级默认）。
- 题库少量维度标注存在语义噪声，AI-Agent 长题较多，建议继续人工抽检。
- 历史报告中的逐题反馈是生成时快照，不随评分规则升级自动重算。

## 文档

| 想了解 | 入口 |
|---|---|
| 10 分钟了解项目 | [docs/overview.md](docs/overview.md) |
| 需求与验收 | [docs/requirements.md](docs/requirements.md) |
| 架构与服务拆分 | [docs/design/architecture.md](docs/design/architecture.md) |
| AI 工具能力 | [docs/design/ai-tools-prd.md](docs/design/ai-tools-prd.md)、[docs/design/ai-jd-web-prd.md](docs/design/ai-jd-web-prd.md) |
| 套餐与支付 | [docs/design/payment.md](docs/design/payment.md) |
| 数据模型 | [docs/design/data-model.md](docs/design/data-model.md) |
| API 契约 | [docs/design/api.md](docs/design/api.md) |
| 开发规范 | [docs/dev/conventions.md](docs/dev/conventions.md) |
| 测试与部署 | [docs/dev/testing.md](docs/dev/testing.md)、[docs/ops/deployment.md](docs/ops/deployment.md) |
| 进度与审查 | [docs/CHANGELOG.md](docs/CHANGELOG.md)、[docs/reviews/](docs/reviews/) |

## 上游依赖与许可

- 代码仅用于学习与面试练习。
- 对话与工具能力调用第三方 AI 服务（DeepSeek 工具接口 + OpenAI 兼容对话接口），运行时可配置 Base URL / Key / 模型；仓库不保存任何真实凭据。
- 题库来源与许可证见 [data/question-bank/LICENSES.md](data/question-bank/LICENSES.md)；仓库不包含非宽松许可来源的数据。
