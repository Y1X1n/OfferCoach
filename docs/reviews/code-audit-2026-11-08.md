# 全量代码审计（2026-11-08）

> 范围：后端认证与安全、面试核心域、前端关键流程、数据/运维/配置。方式：4 个只读子代理并行审计，未修改任何文件；行号为审计时快照。
> 分支：`fix/concurrency-idempotency`（含并发压测修复与认证/报告改造）。结论已回写：本轮修复见 §1，未修复项进入 §2 与 [CHANGELOG](../CHANGELOG.md)。

## 1. 本轮已修复

| 问题 | 位置 | 修复与验证 |
|---|---|---|
| 登出/改密与刷新并发时，宽限补发可能重建已吊销家族 | `RefreshTokenService` | 吊销先写家族墓碑（TTL 覆盖 Refresh 生命周期），签发前后各检查一次，竞态补发会被回收；复测登出后旧/新 Token 均 401 |
| 管理员引导按邮箱直接提升 ADMIN，不校验配置密码，先抢注邮箱者可提权 | `AdminDataInitializer` | 已存在账号必须与 `oc.admin.password` 匹配才提升，否则拒绝并告警 |
| 1 题 + MIXED 时难度分配出现负数，`nCopies(-1)` 抛异常，出题必失败 | `QuestionPlan` | 难度分配改为非负递减；新增 `QuestionPlanTest`；真实接口 1 题会话 READY |
| 闲置 24h 自动放弃时只看回答数，题目已生成也退款，与手动放弃口径相反 | `SessionIdleAbandoner` | 回答与题目均为 0 才退款 |
| 看门狗将超时简历无条件置 FAILED，可能覆盖并发完成的 READY | `StaleStateWatchdog`、`ResumeMapper` | 新增 `failIfParsing`，仅 PENDING/PARSING 可置失败 |
| 作答推进 `current_seq` 返回 0 行未处理，回答落库但进度不前进；跳过未事务化 | `AnswerService`、`InterviewSessionService.skip` | 检查推进结果并回滚；skip 加事务 |
| 仓库残留真实域名 | `verify-bank-selection.mjs`、`deployment.md`、`CHANGELOG` | 默认地址改本地，真实域名替换占位符 |

验证：`mvn verify` 通过（新增 `QuestionPlanTest` 与 2 个认证集成测试；本机 Docker npipe 不可用时集成测试自动跳过）；本地真实接口复测通过。

## 2. 审计时未修复问题（按优先级；后续状态见 §4）

### P0

1. **备份仅单机**：每日 03:15 `pg dump` 保留 7 天、无异地；简历原文件 `/opt/offercoach/data` 未纳入备份（`docs/ops/deployment.md`）。宿主/磁盘故障即不可恢复。建议 dump + WAL 归档 + 文件目录同步到异地对象存储，并做恢复演练。
2. **本地 Compose 默认暴露**：`backend/compose.yaml` 的 PG/MQ/MinIO 使用 `change_me_local` 且端口绑 `0.0.0.0`，Redis 无密码。若在公网主机直接 `up`，等于数据库/对象存储对公网开放。建议默认绑 `127.0.0.1`、强口令、Redis `requirepass`。

### P1 安全

3. **限流 IP 可伪造**：`oc-gateway` 不剥离转发头，`AuthController` 在私网来源下信任 `X-Real-IP`/`X-Forwarded-For` 首段。生产若只有宿主 Nginx 覆写可缓解；建议网关/代理固定覆写并剥离客户端头，应用仅信任固定代理。
4. **停用/降权不撤 Access**：`SecurityConfig` 只信 JWT `role`，Access TTL 15 分钟；被降权管理员在 15 分钟内仍可调用管理端。建议 `tokenVersion` 或按用户拉黑。
5. **DOCX 解压炸弹**：`DocumentTextExtractor` 仅设 `minInflateRatio`，未限条目/解压总量/文本量，10MB 文件可膨胀到 GB 级并耗尽 JVM。建议 `maxEntrySize`/总大小/时长上限与隔离解析。
6. **AI SSRF**：`AiRuntimeConfigService` 仅字符串判内网，可被内网域名、DNS 重绑定绕过；管理端“测试”会把已存 Key 发往该地址。建议解析后校验全部 A/AAAA 为公网、禁重定向、供应商白名单。
7. **邀请/设备防刷**：`DeviceFingerprint` 直接信任 `X-Device-Id`，邀请日/总上限为读后写。换头 + 伪造 IP 可批量互邀超发额度。建议服务端复合指纹与原子占额。
8. **固定验证码默认开启**：`application.yml` 默认 `dev-verify-code.enabled=true`（`123456`），仅 prod profile 校验拦截；误用 local 启动即绕过人机校验。建议默认关闭。

### P1 面试域

9. **报告生成无 owner 租约**：`claim` 仅按 `updated_at` 5 秒条件更新，无 owner_token；慢生成或多实例会重复 claim/generate。建议 owner 条件完成。
10. **报告状态更新未校验行数**：`markReady`/`complete`/`markReportGenerating` 忽略影响行数，竞态可留下 READY 报告 + `REPORT_GENERATING` 会话。建议校验行数并回滚。
11. **简历文件删除竞态**：上传失败清理按 `sha256` 共享路径删除，可能删掉并发成功请求的文件。建议每请求独立临时键，成功后再定稿。
12. **空反馈报告永久失败**：全部回答评分失败时 `feedbacks.isEmpty()` 恒抛异常，6 次重试后报告 FAILED 且手动重试也恒失败。建议允许空报告或明确终态提示。

### P1 前端

13. **登出失败仍清本地态**：`stores/app.ts` 捕获异常后仍 `clearSession()`，Refresh Cookie 未吊销，刷新页面可能自动重登。建议失败重试并提示，服务端应答必清 Cookie。
14. **fetch 无超时**：`api/client.ts` 未使用 `AbortController`，提交/轮询可永久挂起，20s 反馈超时失效。
15. **报告轮询遇错停止**：`ReportDetailPage` 轮询 catch 直接停止，瞬时断网后永久“生成中”。
16. **限时可绕过/失败后不恢复**：刷新页面把剩余时间重置为满值；提交/跳过失败后计时器已清且不重启。
17. **放弃失败仍跳转**：`InterviewRoomPage` 放弃接口失败仍回工作台，会话留 IN_PROGRESS，新建会话撞 409。
18. **草稿常驻**：回答草稿写 localStorage，登出/放弃/结束后不清。
19. **刷新 5xx 也登出**：仅 `REFRESH_INVALID` 应登出，网络/5xx 应提示重试。

### P1 运维与数据

20. **Redis 淘汰策略**：若生产沿用 `allkeys-lru`，登出黑名单/Refresh 家族可能被淘汰。建议 `volatile-lru` 或 auth 独立实例/DB。
21. **CI 缺质量门禁**：仅 GitHub Pages 流水线，后端 `mvn verify`、前端 `pnpm check`、依赖扫描未执行。
22. **索引与查询不匹配**：`lower(job_family)` 使筛选索引失效；watchdog 的 `last_active_at/updated_at` 查询缺索引；迁移建的部分 trgm 索引无查询使用。
23. **`allow_raw_resume_to_llm` 开关未落地**：默认 true 且无 Java 读取，用户关不掉简历原文送模型。
24. **依赖与构建**：Spring Boot 3.3.5 已 EOL；前端依赖均为 caret；`minio:latest`、基础镜像无 digest；无 SBOM/依赖扫描。
25. **日志与审计保留**：`ai_call_log`/`admin_audit_log` 无清理任务；Docker json-file 无轮转；审计 before/after 可能含邮箱手机号。
26. **Redis 键含 PII**：登录失败键直接用邮箱/IP 拼接；活跃用户家族集合 7 天累积全部 Token 摘要。
27. **迁移内物理删除题库**：`V202611080003` 直接 DELETE，无归档/回滚；建议先归档再离线清理（新环境可接受，生产需人工审计）。
28. **软删旧 CHECK 约束按名删除**：`V202611070003` 按自动约束名删除，名字不符时注销清空联系方式会失败；建议按 `pg_constraint` 查名。

## 3. 建议顺序

1. 异地备份与恢复演练（P0），同时收敛 Compose 暴露面。
2. 认证加固：代理头剥离、tokenVersion、固定验证码默认关闭。
3. 报告 owner 租约与状态行数校验；空反馈终态。
4. 前端登出/超时/计时器/轮询恢复。
5. CI 门禁、索引、依赖与日志保留。

## 4. 复核修复记录（2026-11-09，app 0.8.6 / web 0.7.3）

| 审计项 | 状态 | 修复 |
|---|---|---|
| DOCX 解压炸弹 | 已修复 | POI 单条目 32MB 限制，抽取文本上限 30 万字符；超大非常规文本 DOCX 拒绝测试 |
| AI SSRF | 已修复（DNS + 请求前复查） | 保存与每次请求前解析全部 A/AAAA，拒绝回环/内网/CGNAT/链路本地/组播；禁止重定向；解析与连接之间的 TOCTOU 窗口仍理论存在 |
| 前端请求无超时 | 已修复 | JSON 30s、上传 120s、刷新 10s；超时返回明确网络错误 |
| 登出失败仍清本地态 | 已修复 | 服务端吊销失败保留会话并提示重试；Access Token 过期时先刷新再登出，401/403 本地清理 |
| 草稿常驻 | 已修复 | 登出与会话失效时清理 `oc_draft_*` |
| 报告轮询遇错停止 | 已修复 | 瞬时错误继续轮询，仅 404 停止 |
| 放弃失败仍跳转 | 已修复 | `abandon()` 返回结果，失败留在面试间 |
| 提交/跳过失败计时器不恢复 | 已修复 | 失败分支恢复计时 |
| 刷新遇 5xx/断网即登出 | 部分修复 | 请求期不再清会话；启动恢复仍按未登录处理 |
| Access Token 停用/降权 | 已修复 | JWT `ver` + Redis 版本，管理端停用/降权/重置密码时递增 |
| 报告生成 owner 租约 | 已修复 | `generation_token` 条件完成/失败，慢任务不覆盖新结果 |
| 空反馈报告 | 已修复 | 无可聚合评分直接终态，不再空转 6 次重试 |
| 简历文件删除竞态 | 已修复 | 每次上传唯一 key，失败/重复只清理自己的文件 |
| 限时刷新重置 | 已修复 | 单题 deadline 存 localStorage，刷新按剩余时间恢复 |
| Redis PII / 淘汰策略 | 已修复 | 登录注册键 SHA-256 摘要；生产与本地 `volatile-lru` |
| 隐私开关失效 | 已修复 | `allow_raw_resume_to_llm` 支持保存与返回；关闭时简历解析跳过模型、原文不外发 |
| Compose 暴露面 | 已修复 | 本地 Compose 中 PG/Redis/MQ/MinIO 端口绑回环 |
| 索引缺失 | 已修复 | `V202611090001` 增加评分/清理/解析/题库表达式索引 |
| CI 缺失 | 已修复 | `.github/workflows/ci.yml`（backend verify + frontend check）与 Dependabot |
| 前端幂等键/PRO 提示/重试文案 | 已修复 | 按题复用幂等键；PRO 无改写不再误示升级；重试文案与后端一致 |
| 可信代理头 | 已修复 | 应用要求 `X-Proxy-Secret` 匹配才采信 `X-Real-IP`；生产 Nginx 已配置，伪造头被忽略；生产启动强制校验、漏配每分钟告警 |
| 邀请名额原子上限 | 已修复 | 事务级 `pg_advisory_xact_lock` 串行化名额检查与奖励写入 |
| 账号枚举耗时 | 已修复 | 未知邮箱执行 dummy BCrypt 校验 |
| 限流计数原子性 | 已修复 | Redis Lua `INCR + PEXPIRE` |
| 管理端审计不可篡改 | 已修复 | `V202611090002` append-only 触发器，仅保留策略可带标记清理 |
| 数据保留策略 | 已修复 | AI 日志 90 天、审计 3 年，每日分批清理 |
| 管理端可选 IP 白名单 / 指标鉴权 | 已修复 | `ADMIN_ALLOWED_IPS`、`METRICS_TOKEN` 可选启用 |
| 管理端键盘与危险操作确认 | 已修复 | tab 键盘/ARIA、策略保存与 ADMIN 授予二次确认 |
| Spring Boot 3.3 EOL | 已修复 | 升级 Boot 3.5.16 / Cloud 2025.0.3 / MyBatis-Plus 3.5.16 / springdoc 2.8.16，CI 与 verify 通过 |
| 管理端 MFA | 未修复 | 需要产品交互、密钥托管与恢复流程，保留为后续功能 |
| MinIO 镜像固定 | 未修复 | 官方 Docker Hub 镜像已撤下，无法确认可用 tag，需更换仓库/对象存储 |
| 异地备份 | 未修复 | 需要用户提供对象存储/远端，当前仍为单机 dump |
