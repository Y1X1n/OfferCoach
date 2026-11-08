# 开发规范（跨端）

> 状态：生效 ｜ 版本：v1.3 ｜ 更新：2026-11-07
> 本文是跨端、跨模块的**开发必读**：分支、提交、代码组织、接口与数据库的通用口径。
> 细节不在这里重复：前端 → [frontend.md](frontend.md)；UI → [ui-system.md](ui-system.md)；后端 → [backend.md](backend.md)；表 → [../design/data-model.md](../design/data-model.md)；接口 → [../design/api.md](../design/api.md)；流程 → [workflow.md](workflow.md)。

## 1. 仓库与分支

| 分支 | 用途 | 规则 |
|---|---|---|
| `main` | 唯一可发布基线 | 受保护；禁止直接 push；合并前 CI 全绿 + 至少 1 次独立 review |
| `feat/<简述>` | 新功能 | 从 `main` 拉出；完成后 PR 合回并删除 |
| `fix/<简述>` | 缺陷修复 | 同上；紧急线上问题用 `hotfix/<简述>`，合回后打 tag |
| `chore/<简述>` | 构建、依赖、文档 | 同上 |

- 分支名用小写英文 + 中划线：`feat/sse-reconnect`。
- 一个 PR 只做一件事；跨端改动拆成后端契约 / 前端实现 / 文档三个 PR，按依赖顺序合并。
- 禁止 force push 到 `main`；禁止用 `--no-verify` 绕过钩子。
- 发布以 tag 为准：`v<major>.<minor>.<patch>[-stage]`；tag 触发 CI 构建镜像，`main` 的普通提交不发布。
- 开发/联调允许 `SNAPSHOT`；**生产镜像与发布制品禁止 `SNAPSHOT`**，CI 直接阻断。

## 2. 提交规范（Conventional Commits）

```
<type>(<scope>): <subject>

[body]
[footer]
```

| type | 用途 |
|---|---|
| `feat` | 新功能 |
| `fix` | 缺陷修复 |
| `refactor` | 重构（不改行为） |
| `perf` | 性能优化 |
| `test` | 测试 |
| `docs` | 文档 |
| `chore` | 构建/依赖/工具 |
| `style` | 格式（不影响逻辑） |

- `subject` 用中文祈使句，≤ 50 字；一个提交只做一件事。
- 破坏性变更必须在 footer 写 `BREAKING CHANGE:`，并同步升级 API 版本或迁移说明。
- 不写协作者署名尾注；提交信息只保留变更本身。
- 示例：`feat(interview): 支持评分流断线重连`。

## 3. 代码组织

### 3.1 前端

- 演示版目录按“域优先”：`pages/`（扁平路由页）、`components/<域>/`、`components/ui/`（shadcn 生成件）、`composables/`、`stores/app.ts`、`mocks/`；接入后端后迁移为 `pages/` + `features/<域>/` + `api/`（见 [frontend.md](frontend.md) §2）。
- `components/ui` 不得依赖 `pages`/`stores`/`features`；`components/A` 不得深层依赖 `components/B`，跨域能力上提到 `composables` 或 `components/common`。
- 命名：组件 `PascalCase.vue`；composable `useXxx.ts`；工具 `camelCase`；常量 `UPPER_SNAKE_CASE`；目录用完整英文单词，不缩写（`interview` 不写 `intv`）。
- 单单词 UI 原子组件（`Button`/`Input`/`Card`/`Dialog`）保留 shadcn 命名；新业务组件用域前缀（`ResumeEditor`、`QuestionNav`）。
- SFC 块顺序统一 `script → template → style`，由 ESLint `vue/block-order` 强制；不采用阿里规范的 `template` 优先顺序。
- `src` 禁止 `console`/`debugger`；校验脚本（`scripts/`）除外。
- 一个组件一个文件；业务组件 ≤ 250 行；超过则拆分展示与逻辑；页面编排可放宽，但 > 500 行必须登记拆分计划。
- 详细约束见 [frontend.md](frontend.md)。

### 3.2 后端

- Maven 多模块：`oc-common`、`oc-ai-client`、`oc-identity`、`oc-interview`、`oc-admin`、`oc-report-worker`、`oc-gateway`、`oc-app`。
- 分层：`api`（Controller/DTO）→ `application`（用例/事务）→ `domain`（状态机/规则）→ `infrastructure`（Mapper/Client/MQ）。
- Controller 不写规则；Service 不返回 Entity；Repository 不调用外部服务；跨模块不共享表。
- 模块依赖用 ArchUnit 强制，禁止循环。
- 命名：类 `PascalCase`，方法/字段 `camelCase`，常量 `UPPER_SNAKE_CASE`，表/列 `snake_case`；Service/Repository 接口 + `Impl`；DTO/DO 布尔字段不加 `is` 前缀。
- 格式：4 空格、120 列、UTF-8、LF；公共接口/枚举必须有 Javadoc；`TODO/FIXME` 写清人与日期。
- 详细约束见 [backend.md](backend.md) §17。

## 4. 接口口径（摘要）

| 项 | 口径 |
|---|---|
| 前缀 | `/api/v1`；内部接口 `/internal/v1`，不暴露公网 |
| 风格 | REST；资源用复数（`/interviews`，保留本地契约，不采用单数写法）；嵌套 ≤2 层；动作端点用 `POST /{id}/<动作>` |
| 访问分级 | 公开 `pb` / 登录 `pt` / 内部 `pv`；作为网关策略，不写进 URL；`df` 暂不启用 |
| 成功响应 | 直接返回 DTO；`204` 无 Body；不套 `{code,data}` 壳 |
| 失败响应 | `{code, message, requestId, traceId, details, timestamp}` |
| 分页 | `page`（1 起）/`size`（≤100），响应 `items/total/page/size` |
| ID | 后端 `int64`，JSON 字符串；前端类型 `string` |
| 时间 | UTC 存储，ISO-8601 传输；前端按用户时区展示 |
| 金额 | 整数分；字段名 `...Cents` |
| 鉴权 | `Authorization: Bearer <accessToken>`；Refresh 走 HttpOnly Cookie |
| 幂等 | 计费/创建类 POST 带 `Idempotency-Key`；服务端 24h 幂等记录 |
| 错误码 | 业务码 + HTTP 状态映射；见 [../design/api.md](../design/api.md) §1.6 |
| 版本 | 按服务整体升级；破坏性变更升 `/api/v2` 或双版本过渡，旧端点标 `deprecated`；新增可选字段视为兼容 |
| SSE | `text/event-stream`；事件 `meta/token/section/result/error/ping`；见 [../design/api.md](../design/api.md) §7 |

## 5. 数据库口径（摘要）

- 表名：小写 `snake_case`、**单数**、语义完整；如 `interview_session`、`answer_feedback`。
- 主键：`id BIGINT`（应用层生成或 identity），接口出参字符串化。
- 公共列：`created_at TIMESTAMPTZ NOT NULL DEFAULT now()`；可变表加 `updated_at`。
- 软删：`deleted_at TIMESTAMPTZ`（NULL = 未删除）；唯一索引用部分索引 `WHERE deleted_at IS NULL`。
- 金额/分数：`NUMERIC`，禁止 `FLOAT/DOUBLE`；金额单位分用 `BIGINT` 或 `INT`。
- 枚举：`VARCHAR + CHECK`（当前 DDL 口径），禁止 PostgreSQL 原生 ENUM；码值写进列注释或文档。
- 时间：一律 `TIMESTAMPTZ`；纯日期用 `DATE`。
- JSONB：只用于“整体读取、不按内部字段高频过滤”的结构；需要过滤/排序的字段必须独立成列。
- 索引命名：`idx_<table>_<cols>`、`uq_<table>_<cols>`；只建真实查询路径。
- 禁止：跨服务 JOIN、触发器、视图、无注释的表/列、`SELECT *` 进生产代码。
- 建表前逐条自查见 [../design/data-model.md](../design/data-model.md) §1.3。

## 6. 质量门禁

| 检查 | 门槛 |
|---|---|
| 前端 `vue-tsc` / ESLint / Prettier | 0 error / 0 warning；禁止 `any` 与 `src` 内 `console` |
| 后端 Spotless（4 空格/120 列）/ Checkstyle / ArchUnit | 必须通过；无循环依赖 |
| 单元测试 | 核心域 ≥ 70%，整体 ≥ 55% |
| 契约 | OpenAPI diff 无未声明破坏性变更；前端类型可重新生成 |
| 安全 | 依赖/镜像无 High/Critical；越权/注入用例通过 |
| AI 评测 | 黄金集指标不低于基线；安全用例全过 |
| 性能 | 首字/接口/报告指标达标或有批准的例外 |
| 文档 | 代码变更同批更新对应文档；索引登记 |

## 7. 禁止清单

1. 直接 push `main` 或跳过 review。
2. 提交密钥、Token、真实简历、未脱敏用户数据。
3. 前端 `any`、`v-html` 未净化、在组件里直接 `fetch`、`src` 内留 `console`/`debugger`。
4. 后端在事务内调用 LLM/长 HTTP；先提交事务再调用或发消息。
5. 用 Redis 计数器作为额度唯一依据；DB 流水才是事实源。
6. 用 `FLOAT` 存金额；用字符串拼 SQL；用 `SELECT *`。
7. 在网关开启 SSE 响应缓冲/压缩；在 URL 放长期 Access Token。
8. 重试非幂等写请求；重复扣额度或重复生成报告。
9. 在 `reviews/` 写长期执行规范；结论必须回写 `design/` 或 `dev/`。
10. 同一结论在多个文档各写一份。
11. 生产环境发布 `SNAPSHOT` 制品；把 `/internal/**` 暴露到公网网关。
12. 用异常做流程控制；事务内 `catch` 后不回滚；DAO/Repository 打业务日志。

## 8. 文档维护

- 文档规范、索引、待决清单见 [../README.md](../README.md) §3/§4。
- 改动即更新：架构、契约、表结构、部署、额度规则的变更必须同批更新文档。
- 结论归位：决策确定后写入对应正文，并从待决清单删除；历史走 git。
- 新增文档：放入 `design/`、`dev/`、`ops/` 或 `reviews/`，并在索引登记；文件名小写英文 + 中划线。