# 全栈代码审查（2026-11-07）

> 状态：留档 ｜ 方式：5 个子代理只读并行审查（前端、认证安全、额度/数据库、中间件/运维、跨层契约）
> 范围：`frontend/`、`backend/`、Flyway/实际 PostgreSQL、Redis、Gateway、Compose、脚本与文档。
> 结论：**无 P0**；M0 主链路可用、契约大体一致；P1 集中在认证边界、数据生命周期、发布基建与前端草稿/多账号隔离。
> **修复进展**：第一批 P1（认证核心、软删/流水、生产边界、前端三处）已于同日修复并验证，见 §7；剩余项见 §5。

## 1. 结论摘要

- M0（注册/登录/刷新/登出/me/quota/plans/网关）真实冒烟 7/7 通过，错误码、ID 字符串化、时间格式与 `api.md` 基本一致。
- 额度行锁、幂等唯一键、条件扣减、流水余额计算正确；实际库无负余额、无孤儿数据、无脏数据。
- 前端评分公式、跳过题 0 分、报告聚合、XSS 面、路由分包、Dialog 焦点与多数 44px 热区已验证正确。
- 需要优先处理的 P1 主题：
  1. **认证边界**：无限流/失败锁定；prod 可回退临时 RSA 密钥；吊销 fail-open；Refresh 无家族撤销；网关不校验 JWT 且 AI 路由直通；app 8081 对 0.0.0.0 暴露。
  2. **数据生命周期**：软删与唯一键/CHECK 冲突；初始额度不写流水，破坏“流水为事实源”。
  3. **发布基建**：无 CI、无 Dockerfile/mvnw；app Prometheus 401；Compose 与既有容器冲突。
  4. **契约缺口**：`DELETE /users/me` 与隐私字段未实现；`NON_NULL` 吞掉文档示例中的 null；网关 403 不走 `ApiError`。
  5. **前端**：多账号共享一份本地状态；重置演示数据删除全部账号；3 秒内切题/离开丢草稿。

## 2. P1 问题（按主题合并）

| ID | 层 | 位置 | 问题 | 影响 | 建议 |
|---|---|---|---|---|---|
| AUTH-1 | 后端 | `AuthService.java:99-132`、`RedisKeys.java:15-21`、`application.yml:70-83` | 登录/注册无限流、无失败锁定与审计；固定验证码与演示账号由默认 local profile 启用 | 公网误用即可撞库/批量注册/接管演示号 | 接 IP+账号+设备限流与锁定；非 local 强制关闭固定码/演示号并启动校验 |
| AUTH-2 | 后端 | `JwtKeyProvider.java:45-54`、`application-prod.yml` | prod 未配置 PEM 时仍生成临时 RSA 密钥，仅 WARN | 重启全员掉线；多副本互不认 Token | prod 缺密钥启动失败；支持 kid 双 key 轮换 |
| AUTH-3 | 后端 | `TokenBlacklistValidator.java:36-44`、`AuthService.java:160-164`、`RefreshTokenService.java:83-87` | Redis 异常时黑名单放行；登出删除失败仍返回 204 | 已登出 access 可能继续有效 15m，refresh 残留 7d | 吊销失败返回 5xx/告警；补短期 user 级 token version 兜底 |
| AUTH-4 | 后端 | `RefreshTokenService.java:56-74` | 仅单次 GETDEL，无 token family/重放检测/按用户全端吊销 | 被盗 refresh 先轮换后，攻击者持续可用、受害者被踢 | 引入 familyId；复用即撤销全家族；补改密/注销吊销 |
| AUTH-5 | 网关 | `oc-gateway/application.yml:11-19`、`oc-gateway/pom.xml` | 网关无 JWT 校验；`/api/v1/ai/**` 无鉴权直转（AI 未起实测 500）；app 8081 绑 0.0.0.0 可绕过网关 | AI/SSE 面暴露，绕过限流与访问策略 | 网关统一 JWT/限流；AI 路由加 streamToken；app 仅内网监听 |
| AUTH-6 | 后端 | `SecurityConfig.java:46-59`、`InternalPathBlockFilter.java:25-33` | `/internal/**` 仅网关前缀拦截；app 只要求登录，任意 USER 可命中未来内部接口 | 内网接口一旦实现即越权 | app 侧拒绝 `/internal/**` 或强制服务 Token；测试编码/`..` 变体 |
| AUTH-7 | 配置 | `application.yml:9,67,71,79-83` | 默认 profile=local，prod 未激活时静默启用 demo/固定码/非 Secure Cookie/CORS localhost | 漏设环境变量即“安全降级” | prod 缺配置 fail-fast；启动打印安全 profile 断言 |
| DB-1 | 数据库 | `V202611070001:6-8,24`、`data-model.md:36,697` | `email/phone_hash` 全量 UNIQUE + `CHECK(email OR phone_hash)`，但文档要求注销清空邮箱且软删不占唯一键 | 注销必失败；邮箱永久占用；`existsByEmail` 含软删导致无法重注册 | 改部分唯一索引 `WHERE deleted_at IS NULL`；注销写匿名占位或放宽 CHECK |
| DB-2 | 数据库 | `QuotaService.java:58-66`、`AuthService.java:95`、`DemoDataInitializer.java:83` | 初始/演示额度只写 `user_quota`，不写 `quota_ledger`（实库 ledger=0） | “流水为事实源”失效，对账全量误报，无法审计发放 | 初始化同事务写 `PLAN_GRANT` 流水；对账定义无流水基线 |
| OPS-1 | 运维 | 仓库根（无 `.github/`、Jenkinsfile） | 无 CI；lint/test/契约/扫描/镜像均无门禁 | M0 “CI 绿”未达成，发布不可重复 | 最小 CI：`mvn verify` + smoke + 镜像构建 |
| OPS-2 | 运维 | `backend/`、`deployment.md:127-141` | 无 Dockerfile、无 mvnw，文档命令不可执行 | 无法产出镜像，K8s/灰度阻塞 | 补 Dockerfile/mvnw/.dockerignore，或文档标未实现 |
| OPS-3 | 观测 | `SecurityConfig.java:54` | 实测 app `/actuator/prometheus` = 401，gateway = 200 | Prometheus 抓不到 app 指标 | 放行 prometheus 或加内网白名单；prod 关 swagger |
| OPS-4 | 本地运行 | `compose.yaml:17,39`、`README.md:48` | 推荐 `compose up postgres redis`，但本机同名容器占 5432/6379（非 compose 管理） | 一键启动端口冲突或起空库，迁移/种子分裂 | 检测宿主容器则跳过并提示；统一容器名/外部卷 |
| CT-1 | 契约 | `api.md:184-186`、`UserController.java` | `DELETE /users/me`、`allowRawResumeToLlm` 未实现，契约已标记生效 | 前端按契约实现会 404/字段丢失 | M1 实现或标“规划”并降级契约 |
| CT-2 | 契约 | `application.yml:29`、`api.md:398,464` | `default-property-inclusion: non_null` 删除 null 字段，文档示例显式返回 null | 前端无法区分“缺失”与“null” | 统一序列化策略并加契约测试 |
| FE-1 | 前端 | `stores/app.ts:43,101`、`persistence.ts` | 多账号共用一份 `oc_mock_state_v1`，登录只换 user | B 登录可见 A 简历/回答/报告；注册清空 A 数据 | 按 userId 命名空间；或明示单账号限制 |
| FE-2 | 前端 | `SettingsPage.vue:340,363-373`、`stores/app.ts:370` | “重置演示数据”调用 `clearAccounts()` 删除全部注册账号，文案未提示 | 误操作永久丢账号 | 保留账号表，或明确告知并二次确认 |
| FE-3 | 前端 | `useInterviewRoom.ts:144-163,207-236,406` | 停笔 3 秒内切题/离开/刷新会丢草稿；卸载只清 timer 不落盘 | 用户内容丢失 | 切题/卸载/`beforeunload` 立即 flush |

## 3. P2 摘要（按主题）

**认证/安全**

- 重复邮箱提示可枚举账号；不存在账号跳过 BCrypt，登录耗时差 0.02s vs 0.26s。
- 登出后旧 access 实测返回 `UNAUTHENTICATED`，集成测试却断言 `TOKEN_EXPIRED`（Docker 可用时会失败）。
- 签发无 `nbf`，解码不按 `kid` 选 key，密钥轮换不可用。
- 未知路径（含 `/internal`）落到 catch-all 返回 500；应 404。
- 密码按字符限 64，BCrypt 72 字节截断；登录密码无上限。
- 无 `userId` MDC/登录审计；登出无 access 时 401 且不清 Cookie；刷新每次续 7d 无绝对寿命。
- Prometheus 401、swagger 全 profile 公开、gateway 错误体非 ApiError。

**额度/数据库**

- 每日会话计数无递增（M1 创建会话时补），`oc:quota:daily` 未使用。
- `LocalDate.now()` 用 JVM 时区，未用用户时区。
- `oc:quota:cache` 只 delete 从未写入，缓存实现缺失。
- `plan_code` 无 FK；演示 PRO 无 ACTIVE 订阅；PRO `resumeCredits=9999` 与“无限”语义冲突。
- `pgcrypto` 未使用；`version` 只自增无乐观锁；plan 的 credits/period 无 CHECK；`user_subscription` 无 `updated_at`。
- 列注释仅 14/54；领域映射丢 `phone/emailVerifiedAt/failedLoginCount/lockedUntil`。

**发布/运维**

- 无 JaCoCo check 阈值；Testcontainers 本地因 Docker 29 跳过（假绿风险），RYUK 全局关闭。
- 镜像 `minio/minio:latest`；无日志轮转/资源限制；`.env.example` 缺 MinIO、AI/APP URL、端口、demo、连接池等键。
- AI 路由上游不可用返回 500 而非 503；网关无 SSE/超时配置；CORS 双写且默认不一致。
- 文档声称 Prometheus/Grafana/Tempo/Loki/Makefile，仓库未实现。
- 脚本硬编码本机 JAVA_HOME/Maven 路径，只 kill Maven 父进程；smoke 依赖固定验证码/演示密码。
- `overview.md:112` 仍写 Cloud 2024；`roadmap.md:7` 仍称 M0 暂缓；CHANGELOG 文档数 24（实际 26，含本报告）。

**前端**

- 流式每 22–35ms 追加并触发渲染，未做 50ms 节流。
- 每次切题重置剩余时间，可跳题往返刷新限时。
- 跳过题 `rewrittenAnswer=null` 被渲染成“升级 PRO”，未判断套餐。
- md 按钮 40px、抽屉链接约 36px；正文 `text-danger` 对比度约 3.8:1。
- localStorage 读写缺 try/catch（禁用存储可白屏）；`loadState` 只验数组不验 user/quota；版本不符直接丢档。
- 上传任意简历复制种子联系方式，且无“勿传真实简历”提示。
- 文档称房间状态在 Pinia/有剩余时间，实际为 composable 局部 ref；侧栏无“目标岗位”入口。

## 4. 已验证良好

- **认证**：RS256、iss/aud/exp、access 15m、jti 黑名单、Refresh 256-bit + SHA-256 + GETDEL 轮换；Cookie HttpOnly/SameSite=Lax/Path。
- **额度**：`FOR UPDATE` + `credits >= amount` 条件更新 + 锁内幂等 + 流水同事务 + `balance_after` 正确；SQL 全参数化。
- **数据库**：5 张表与 `data-model.md` 字段/类型/CHECK/索引/注释基本一致；迁移额外约束更严；Flyway 2 条记录与仓库一致；无脏数据、无负余额。
- **网关/配置**：`WebFilter` 路由前 `/internal` 403；CORS 白名单实测生效；prod 关闭 demo/固定码、Cookie Secure、CORS 无默认。
- **前端**：评分/报告一致；跳过题 0 分；无 `any`/`v-html`/组件内 fetch；22 个单测、4 组浏览器脚本通过；390px 溢出 0；Dialog 焦点与多数 44px 热区已验证。
- **契约**：M0 路径/方法/状态码/错误码/DTO 命名/ID 字符串化与 `api.md` 一致；FREE 3/3 与每日 3 四方一致。

## 5. 建议修复顺序

1. **上线前安全**（P1）：prod 密钥与 profile fail-fast；网关 JWT + `/internal` 双端兜底；吊销语义；登录/注册限流与锁定。
2. **数据正确性**（P1）：软删部分唯一索引 + 注销匿名化；初始化额度写流水；M1 每日计数。
3. **发布基建**（P1）：CI + Dockerfile/mvnw；Prometheus 暴露；Compose 共存策略。
4. **契约收口**（P1/P2）：`DELETE /users/me`/隐私字段；null 序列化；网关 ApiError；修正 Cloud/roadmap/文档数。
5. **前端体验**（P1/P2）：按账号隔离 mock 状态；重置账号二次确认；草稿 flush；流式节流；计时 deadline。
6. **测试补齐**：CI 跑 Testcontainers 并修正 `TOKEN_EXPIRED` 断言；JWT/吊销/并发刷新/限流/越权用例；前端 composable 与多账号用例。

## 6. 覆盖与边界

- 本次只审 M0；简历/JD/面试/SSE/报告/管理端未实现，相关“缺失”不计缺陷。
- 审查阶段未改代码、未重启、未写入数据库、未创建账号。
- 前端仍为 mock-first，未接后端；契约差异已列，接入时应先统一映射。

## 7. 第一批修复（2026-11-07）

| 已修复 | 结果 |
|---|---|
| AUTH-1（部分） | 注册 3/min/IP、登录 10/min/IP、账号 5 次失败锁定 15m；实测第 6 次登录 429 `RATE_LIMITED` |
| AUTH-2 | prod 缺 PEM 启动失败；prod 启用 demo/固定码/非 Secure Cookie/localhost CORS 时 `ProductionSafetyValidator` 阻止启动 |
| AUTH-3 | 黑名单/Refresh 吊销 Redis 失败不再吞异常；吊销校验 fail-closed |
| AUTH-4 | Refresh 家族 + used 标记；旧 Token 重放实测整族失效（新 Refresh 也 401） |
| AUTH-5（部分） | 移除网关无鉴权 AI 直连路由，`/api/v1/ai/**` 无 token 实测 401；app 默认绑定 127.0.0.1；网关 JWT 留待 M3 |
| AUTH-6 | app 侧 `/internal/**` denyAll，带 USER token 直连实测 403 |
| AUTH-7 | 见 AUTH-2 的 prod 安全校验 |
| DB-1（部分） | V3 改部分唯一索引 + `DELETED` 允许清空联系方式；`existsByEmail` 排除软删；`DELETE /users/me` 仍待实现 |
| DB-2 | `initializeQuota` 写 `PLAN_GRANT`；V3 回填存量流水（实测 INTERVIEW/RESUME 各 6 条） |
| DB-3（新增） | V3 应用成功；约束/索引与文档同步 |
| OPS-3 | app `/actuator/prometheus` 实测 200 |
| CT-2 | 移除 `NON_NULL`，null 字段显式输出 |
| CT-3（网关） | `/internal` 403 返回完整 ApiError（requestId/traceId/timestamp） |
| FE-1/2/3 | 账号状态按 userId 隔离；重置不删账号；草稿切题/卸载/离开立即 flush；前端 28 测试通过 |

**验证**：后端 `mvn test`（7 通过 + 3 跳过）与 `verify`；冒烟 7/7；Refresh 重放、登录锁定、404、内网 403、Prometheus、V3 流水均实测；前端 `pnpm check` 与四组 browser 脚本通过。

**仍未修复（下一批）**：网关 JWT/限流（M3）、CI + Dockerfile/mvnw、Compose 与宿主容器共存、`DELETE /users/me` 与隐私字段、登录审计/userId MDC、Swagger prod 关闭、网关 CORS 默认、JaCoCo 阈值、每日计数（M1）、`LocalDate.now` 时区、额度缓存实现。