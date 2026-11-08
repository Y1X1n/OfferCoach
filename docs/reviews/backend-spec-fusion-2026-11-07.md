# 后端规范融合评审（2026-11-07）

> 状态：已验收（2026-11-07） ｜ 范围：后端工程结构、编码、异常、日志、测试、REST/网关
> 结论：外部规范按「采纳 / 适配 / 不采纳」并入 [backend.md](../dev/backend.md)、[conventions.md](../dev/conventions.md)、[api.md](../design/api.md)、[testing.md](../dev/testing.md)、[observability.md](../ops/observability.md)。
> 规范已于 2026-11-07 验收并生效；代码重构已按 §6 执行，验证见 §8。

## 1. 来源与效力

| 编号 | 来源 | 日期 | 覆盖范围 | 效力 |
|---|---|---|---|---|
| S1 | [阿里巴巴 Java 开发手册](https://developer.aliyun.com/article/1589859) | 2024-08-19 | 命名、常量、格式、OOP、集合、并发、控制语句、注释、异常、日志、单元测试、安全、MySQL、工程结构 | 高：强制/推荐/参考三级 |
| S2 | [SpringCloud 微服务开发最佳实践规范](https://developer.aliyun.com/article/1254545) | 2023-06-18 | Maven 父子模块、core/starter、API/实现拆分、Feign、REST 访问分级、网关灰度 | 中：偏微服务形态，需按单体现状适配 |
| S3 | [Spring Boot 项目开发规范（CSDN）](https://blog.csdn.net/2301_80035882/article/details/158535778) | 2026-03-06 | 前后端分离、REST 资源命名、状态码、接口文档与联调 | 低：标题大于内容，且与 S2 部分冲突 |

> S1 是主要来源；S2 只采纳与当前“模块化单体 + 网关”一致的部分；S3 仅用于佐证 REST/契约先行，不作为强制依据。

## 2. 本地现状（融合前）

- [backend.md](../dev/backend.md) v1.1：JDK 21、Boot 3.3、Cloud 2024、MyBatis-Plus、Flyway、LangChain4j；DDD 分层；BizException/ErrorCode；额度行锁与幂等；Outbox；JSON 日志；ArchUnit/Spotless。
- [conventions.md](../dev/conventions.md) v1.2：分支/提交、模块边界、API 摘要（`/api/v1`、复数资源）、DB 口径、门禁。
- [api.md](../design/api.md) v1.1：REST + SSE 契约、错误码、幂等、内部接口。
- [testing.md](../dev/testing.md) v1.1：测试金字塔、Testcontainers、契约测试、E2E、性能。
- [observability.md](../ops/observability.md) v1.1：指标、JSON 日志、Trace、告警。

## 3. 采纳 / 适配 / 不采纳

### 3.1 工程结构（S1 §6 / S2 §1）

| 规则 | 决策 | 落点 |
|---|---|---|
| 统一 parent POM、dependencyManagement 锁版本 | 采纳（本地已具备） | backend.md §1 |
| 子模块不得出现同一 GAV 多版本 | 采纳 | backend.md §1 |
| 二方库版本用统一属性变量 | 采纳 | backend.md §1 |
| 生产制品禁止 SNAPSHOT | 采纳（S1；S2 的“推荐 Snapshot”仅限开发期） | conventions.md §1 |
| 父模块拆 core/starter/rate-limit | 适配：`oc-common` 承担 core；暂不新建 starter/rate-limit | backend.md §1 |
| `core` 依赖全部 provided | 不采纳：单体运行时会导致类缺失；改为“最小依赖 + 不传递泄漏” | backend.md §1 |
| 微服务拆 `xxx-api` / `xxx-service` | 暂缓：当前模块化单体；拆分时按此命名 | backend.md §1 |

### 3.2 分层（S1 §6.1）

本地 `api → application → domain → infrastructure` 与 S1 的 Web/Service/Manager/DAO 可一一映射，**保留本地分层**，只补强制约束：

- Web/API：只做协议、校验、DTO；不写业务、不碰 Mapper、不声明事务。
- Service/Application：用例编排、事务、幂等；不返回 DO/Entity。
- Domain：状态机、额度、评分规则；禁止依赖 Web/Redis/MyBatis。
- Manager/Infrastructure：封装第三方与持久化；DAO/Repository **不写业务判断、不打业务日志**。
- 外部 HTTP 统一 `ApiError`；内部 RPC（未来）用 `Result<T>` + 错误码，不抛裸异常。

### 3.3 命名与格式（S1 §1.1–1.3）

| 规则 | 决策 |
|---|---|
| 类 UpperCamel；方法/变量 lowerCamel；常量 UPPER_SNAKE | 采纳 |
| 包名全小写、单数、无拼音/中文 | 采纳 |
| Service/DAO 暴露接口，实现类 `Impl` | 采纳（仓储可用 `Repository` 命名，实现 `Impl`） |
| POJO 布尔字段不加 `is` 前缀 | 采纳 |
| DO/DTO/VO 后缀；禁止 `xxxPOJO` | 采纳；本地 DO/DTO 已符合 |
| CRUD 方法 get/list/count/save/remove/update | 适配：应用服务按 API 语义用 `get/list/create/update/delete`；Repository 保留 Spring 的 `find*` |
| 无魔法值；long 用 `L`；常量按域拆分 | 采纳 |
| 4 空格缩进、120 列、UTF-8、LF | **采纳（与现工具冲突，见 §4.1）** |
| 空块单行、`{` 前不换行、运算符两侧空格 | 采纳 |
| 类/公共方法/枚举 Javadoc；TODO/FIXME 带人与日期 | 采纳；内部显然代码不写冗余注释 |
| POJO 用包装类型、不设默认值、实现 toString、构造器无业务 | 采纳 |
| 集合/并发红线 | 采纳，汇总到 backend.md §17 |

### 3.4 异常与日志（S1 §2）

| 规则 | 决策 |
|---|---|
| 可预判的 RuntimeException 先判空/越界，不靠 catch | 采纳 |
| 不用异常做流程控制；捕获具体类型；不吞异常 | 采纳 |
| 事务内 catch 后必须回滚；finally 不放 return；try-with-resources | 采纳 |
| 对外 HTTP 用错误码；内部 RPC 用 Result | 采纳（HTTP 用 ApiError，见 §3.2） |
| 日志必须 SLF4J；占位符；`isDebugEnabled`；异常带场景+堆栈 | 采纳 |
| ERROR 只用于系统/重要错误；参数错误用 WARN | 采纳 |
| 日志保留 ≥15 天；禁止重复 appender/PII | 采纳；本地 30 天更严格 |
| 文件日志命名 `appName_logType_logName.log` | 适配：当前统一 JSON 到 stdout，由采集端分文件；不新增文件 appender |

### 3.5 并发、集合与测试（S1 §1.5–1.6 / §三）

- 线程池必须 `ThreadPoolExecutor`（禁用 `Executors`）、命名线程、有界队列；`Timer` 改 `ScheduledExecutorService`；`SimpleDateFormat` 改 `DateTimeFormatter`。
- 多资源锁固定顺序；锁内禁止 RPC/LLM/长 I/O；乐观锁冲突重试 ≥3；`volatile` 只解决可见性；`ThreadLocal` 用 `static`。
- 集合：`equals/hashCode` 成对、foreach 删除用 Iterator、Comparator 满足三性质、Map 用 entrySet、预估容量、`subList`/`Arrays.asList` 只读限制。
- 测试 AIR：自动、独立、可重复；禁止 `System.out`；测试类 `<被测类>Test`；不依赖外网/共享环境（用 Testcontainers）。

### 3.6 REST、版本与网关（S2 §3–4 / S3）

| 规则 | 决策 |
|---|---|
| 资源用名词、动作走 `POST /{id}/<动作>` | 采纳（本地已符合） |
| 资源复数（`/interviews`） vs 单数（`/ticket`） | **保留复数**：S3 与本地契约一致，且前端/OpenAPI 已冻结；S2 单数不采纳 |
| 嵌套 ≤ 2 层 | 采纳 |
| 访问分级 `pb/pt/pv/df` 写入 URL | **不采纳路径改造**；映射为网关策略：公开=pb、登录=pt、内部=pv、加密=df（df 暂不启用，TLS+JWT 替代） |
| 版本按服务整体升级，旧版本保留并标 deprecated | 采纳；本地 `/api/v1` → `/api/v2` 过渡，旧端点 OpenAPI 标 `deprecated` |
| 网关负责访问控制与灰度 | 采纳：`/internal/**` 禁止入站；灰度用 Header/用户分桶；SSE 灰度需先排空 |
| 服务间 SDK/Feign/Nacos | 暂缓：当前单体；拆分时再引入，并保留 FallbackFactory |
| 接口文档先行、前后端并行 | 采纳；继续用 springdoc + OpenAPI bundle，Apifox/Postman 仅作可选调试工具 |
| 402 `QUOTA_EXCEEDED` | 保留：业务语义明确且契约已冻结；在 api.md 标注为非标准状态码 |

## 4. 关键冲突与裁决

### 4.1 格式工具与 4 空格

S1 强制 4 空格/120 列；本地门禁写的是 `Spotless + google-java-format`（2 空格）。**裁决：采用 4 空格/120 列，后端重构时把格式化器切换为 Palantir Java Format（4 空格、120 列，兼容 google 风格）**，保留 Spotless/Checkstyle/ErrorProne。

### 4.2 Spring Boot 与 Spring Cloud 版本

本地同时写 Boot 3.3.x + Cloud 2024.0.x，两者不兼容。**裁决：Boot 3.3.5 + Cloud 2023.0.3（Leyton）**；若要 Cloud 2024，需整体升级 Boot 3.4.x，另开 ADR。

### 4.3 资源单复数

S2 单数、S3 复数、本地复数。**裁决：保留复数**，理由：契约冻结、REST 社区惯例、前端路由与类型已按复数生成；在 api.md 记录“不采纳 S2 单数”。

### 4.4 SNAPSHOT

S1 禁止生产 SNAPSHOT；S2 推荐内部 SDK 用 SNAPSHOT。**裁决：开发/联调允许 SNAPSHOT，生产发布必须 release，并在 CI 阻断 `-SNAPSHOT` 镜像**。

### 4.5 内部接口返回形态

S1 建议跨应用 RPC 用 `Result`，本地 HTTP 用 `ApiError` 无信封。**裁决：HTTP（含 `/internal/v1`）继续用 `ApiError`，避免双形态；未来改 gRPC/Feign 时再用 `Result`。**

## 5. 已改文档

| 文档 | 版本 | 改动 |
|---|---|---|
| [backend.md](../dev/backend.md) | v1.2 生效 | 补工程/命名/格式/异常/并发/日志/测试约束；Cloud 版本修正；格式化器改为 4 空格 |
| [conventions.md](../dev/conventions.md) | v1.3 生效 | 生产禁 SNAPSHOT；后端命名/实现类/注释指针；访问分级指针 |
| [api.md](../design/api.md) | v1.2 生效 | 增加资源/访问分级/版本与弃用规则；保留复数与 402 的裁决 |
| [architecture.md](../design/architecture.md) | v1.2 生效 | Cloud 版本修正；网关灰度与内部接口边界 |
| [testing.md](../dev/testing.md) | v1.2 生效 | AIR 原则、测试命名、禁止 System.out |
| [observability.md](../ops/observability.md) | v1.2 生效 | SLF4J/占位符/异常场景+堆栈/ERROR 语义/去重 |

## 6. 已执行的代码动作

1. [x] M0 后端恢复到主目录；Spotless + Palantir 4 空格/120 列、Checkstyle、JaCoCo、Java 21 Enforcer 接入。
2. [x] 账号层：新增 `UserAccount/NewUser`、`UserRepository` 端口与实现；Auth/User/Demo 不再直连 Mapper。
3. [x] 额度层：新增 `QuotaAccount/QuotaLedgerEntry/SubscriptionPlan`、`QuotaRepository` 端口与实现；`QuotaType` 迁入 domain；扣减/幂等语义不变。
4. [x] ArchUnit 增加 `domain ↛ application/api/infrastructure`、`application ↛ infrastructure` 规则。
5. [x] 网关：`WebFilter` 在路由前拦截 `/internal/**` 返回 403；新增 `application-prod.yml` 与 `check-release.sh`。
6. [x] 异常/日志沿用 `BizException + ApiError`，日志保持占位符与脱敏；未新增重复 appender。
7. [ ] 并发细则（线程池/锁顺序）待 M3 SSE/报告并发代码落地时按 §3.5 执行；当前 M0 无相关代码。

## 8. 执行验证（2026-11-07）

| 项 | 结果 |
|---|---|
| `mvn test` | 7 个测试通过（PasswordPolicy 2、网关过滤器 2、ArchUnit 4）；3 个 Testcontainers 用例在无 Docker 环境跳过 |
| `mvn -DskipTests verify` | Spotless、Checkstyle、JaCoCo、Enforcer 全部通过 |
| app/gateway 启动 | 健康检查 200；Flyway 已应用；演示账号可用 |
| `GET /internal/v1/feedbacks`（经网关） | 403 |
| `GET /api/v1/plans` | 200 |
| `node scripts/smoke.mjs` | 注册→me→quota→refresh→logout→令牌失效 7/7 通过 |

> Testcontainers 跳过原因：本机 Docker 29.7.2 与 docker-java 的 `/info` 握手返回 400；测试已用 `disabledWithoutDocker=true`，在兼容 Docker 的 CI 中会正常执行，本地以真实服务冒烟覆盖。

## 7. 待你验收的 5 项

| # | 决策 | 推荐 |
|---|---|---|
| A1 | 后端格式化改为 4 空格/120 列（Palantir） | 同意 |
| A2 | Boot 3.3.5 + Cloud 2023.0.3（修正文档中的 Cloud 2024） | 同意 |
| A3 | 保留复数资源与 `/api/v1`；`pb/pt/pv/df` 只做网关策略，不改 URL | 同意 |
| A4 | 开发允许 SNAPSHOT，生产 release 且 CI 阻断 SNAPSHOT | 同意 |
| A5 | HTTP/内部接口继续用 `ApiError`，不引入统一 `Result` 信封 | 同意 |

确认后我把上述文档状态从「评审中」改为「生效」，再开始代码重构。