# OfferCoach 后端全量架构审查（2026-11-08）

> 审查对象：`E:/My_project/backend` 全部 8 个 Maven 模块（260 个 Java 文件，约 20,374 行）、7 个 Flyway 迁移、`docs/design` 与 `docs/dev` 契约。
> 审查方式：只读静态审查。未构建、未运行测试、未连接数据库、未调用任何写接口；结论来自源码与文档交叉核对，凡未直接验证的运行期行为均标注“待验证”。
> 代码快照：2026-11-08 01:00（CST）。审查期间 `InterviewController`、`ReportController`、`AnswerService`、`FeedbackGenerationListener` 于 00:56 有改动，本报告以改动后版本为准。仓库未包含 `.git` 元数据，无法给出 commit hash。
> 严重级别：P0 数据损坏/安全失陷/全站不可用；P1 核心链路错误、计费错误、可导致用户或资金损失；P2 明显缺陷或架构偏离，有规避手段；P3 规范、可维护性与优化项。

## 1. 结论摘要

1. **未发现 P0；整体可运行，但存在 8 项 P1。** 认证、额度行锁与幂等、会话条件更新、答题唯一约束、资源归属校验、错误信封等基础能力实现质量较好；风险集中在异步执行、计费闭环、出题相关性和管理端补偿。
2. **计费闭环不完整。** FREE/PRO 的“每月额度”只在注册/初始化时发放一次，`period_end` 与 `user_subscription` 从未参与续期或到期判断；管理员补额度无上限；退款失败被静默吞掉且无补偿任务。`quota_ledger` 目前不能独立支撑对账。
3. **异步执行模型是最大系统性风险。** 单一 `sseExecutor`（core 2 / max 8 / queue 64）同时承载长连接 SSE、出题、简历解析和评分预生成；`AFTER_COMMIT + @Async` 事件不持久化；`PREPARING/READY/PARSING/SCORING` 均无超时兜底；报告生成与评分完成存在竞态，可永久 `REPORT_FAILED`。
4. **题库 26k 与出题相关性名不副实。** 检索只取 `ORDER BY id LIMIT 200` 后在 Java 内打分，`pg_trgm` 索引完全未使用；JD 结构化从不产出 `jobFamily`，导致岗位族恒为空；AI 评分实际是确定性规则评分，`degraded` 恒为 `true`。
5. **模块形态与文档契约明显漂移。** `oc-report-worker` 为空模块；`oc-gateway` 无 JWT、无限流、无 SSE 路由；`oc-interview/oc-admin` 直接依赖 `oc-identity` 的 application/security 类型；ArchUnit 仅 4 条规则且未覆盖模块白名单与 Controller→Repository；测试与 CI 门禁远低于文档承诺。

## 2. 发现清单

### 2.1 P1（必须修复）

> 本次审查未发现 P0。以下 8 项为 P1，均可在不改表结构或仅做小迁移的前提下修复。

| 编号 | 级别 | 证据（文件:行） | 影响 | 建议修复（详见 §3） |
|---|---|---|---|---|
| AR-01 | P1 | `backend/oc-identity/src/main/java/com/offercoach/identity/application/quota/QuotaService.java:52-73,137`；`backend/oc-identity/src/main/java/com/offercoach/identity/infrastructure/persistence/UserQuotaMapper.java:23-40`；迁移 `V202611070001__account_and_quota.sql` 中 `user_subscription` 无任何 Java 引用 | 注册时一次性发放 3/3，`period_end` 只读展示；第二个月不再补发，用尽后 FREE 用户额度为 0；PRO 无到期、无降级；`user_subscription` 是死表。与 `application.yml:82-85`“free-monthly”语义不符 | 增加月度续发任务与订阅到期判定（§3.1） |
| AR-02 | P1 | `backend/oc-identity/src/main/java/com/offercoach/identity/application/AuthAttemptService.java:45-56,61-75`；`backend/oc-admin/src/main/java/com/offercoach/admin/infrastructure/persistence/AdminUserRepositoryImpl.java:93-104`；`backend/oc-admin/src/main/java/com/offercoach/admin/application/AdminUserService.java:126-158` | 登录锁定完全由 Redis 计数实现，`failed_login_count/locked_until` 只写不读；管理端“解锁”只改数据库，用户仍被 429 最长 15 分钟；重置密码不吊销 Refresh 家族，旧会话可继续续期 | 解锁清理 Redis 键或将锁改为 DB 事实源；重置密码吊销全部 Refresh 家族（§3.2） |
| AR-03 | P1 | `backend/oc-interview/src/main/java/com/offercoach/interview/config/AsyncConfig.java:19-29`；`SessionPreparingListener.java:20-24`；`ResumeParseRequestListener.java:20-24`；`FeedbackGenerationListener.java:25-36`；`InterviewController.java:227-234`；`FeedbackStreamController.java:47-51` | 2~8 个线程同时服务：30s~300s 的长连接 SSE、出题（AI 最长 60s）、PDF/DOCX 解析、评分预生成。少量并发 SSE 即可占满线程，出题/解析/预评分在队列排队或提交失败；`execute` 被拒时 SSE 返回 500 而非 429；`@Async` 在事务提交后才入池，拒绝后任务丢失且无补偿 | 拆分线程池；SSE 用虚拟线程/独立池并限制每用户 2 条；拒绝显式返回 429；异步任务改为持久化队列（§3.3） |
| AR-04 | P1 | `InterviewSessionService.java:164-176`；`SessionIdleAbandoner.java:40-53`（只处理 IN_PROGRESS/PAUSED）；`ResumeParseExecutor.java:52-67`；`ResumeService.java:296-302` | 提交后异步任务丢失、进程重启、线程池拒绝都会使会话永久停在 `PREPARING`；`READY` 无任何清理；简历停在 `PENDING/PARSING` 后，`reparse` 又拒绝该状态，用户无法自救。`uq_session_active` 会在用户不主动 `abandon` 时持续阻塞新会话（前端无入口即等同卡死） | 增加状态超时回收任务（PREPARING/READY/PARSING/SCORING），失败退款幂等；长期方案用任务表（§3.4） |
| AR-05 | P1 | `backend/oc-interview/src/main/java/com/offercoach/interview/report/application/ReportService.java:123-126,181-188`；`ReportGenerationWorker.java:34-49`；`InterviewReportMapper.java:57-73,98-113` | `generate` 在“尚无任何 feedback”时抛普通异常，worker 每 5s 重试一次并累加 attempts，约 15–20s 后 `markFailed` 永久 FAILED；若评分预生成因 AR-03/AR-08 延迟或失败，后续 feedback 到达也不会自动恢复。`finish` 只要求 answered≥1，不要求已评分。另：`claim` 仅刷新 `updated_at`，不是带租约的抢占；生成耗时超过 5s 时多实例可能重复领取（当前聚合为毫秒级，风险低，待验证） | 区分“可延迟”和“真失败”；按 answered/feedback 数量对齐后再生成；以 feedback 提交事件触发，轮询仅兜底（§3.5） |
| AR-06 | P1 | `backend/oc-interview/src/main/java/com/offercoach/interview/qbank/QuestionBankMapper.java:22-39`；`QuestionBankRetriever.java:21,44-48`；`QuestionContextBuilder.java:36`；`JdStructurer.java:25-34`；`data/question-bank/normalized/questions.jsonl`（26,385 行，`jobFamily` 为中文） | 每次检索最多只有 200 行参与打分，且优先 `ORDER BY id` 的前 200 行；JD 结构化 schema 不含 `jobFamily`，`QuestionContextBuilder` 读到的岗位族恒为 null，直接走 `selectPublished(200)`。26k 题库绝大部分永不命中，所有用户题目高度同质；DB 中 `pg_trgm` GIN 索引未使用 | 用 SQL 侧 trgm/全文排序替代“前 200 行”；补齐 JD→岗位族规范化；建立召回评测集（§3.6） |
| AR-07 | P1 | `backend/oc-admin/src/main/java/com/offercoach/admin/application/AdminUserService.java:164-199`；`AdminUserDtos.java:32-36`；`QuotaService.java:249-252`；`docs/dev/backend.md:253`（delta 上限 100/次） | 补/扣额度只校验 `delta != 0`，单次可写入任意额度；`quota_ledger.reason` 固定为 `ADMIN_COMPENSATION`，客服填写的原因只进审计 JSON，无法按原因对账；幂等重放会重复写审计 | 限额、原因入账、二次确认（§3.7） |
| AR-08 | P1 | `InterviewSessionService.java:296-315`；`ResumeParseExecutor.java:103-131`；`QuotaService.java:164-168` | `refundQuietly` 捕获所有异常仅 WARN，会话已置 FAILED/ABANDONED 或简历已失败后，退款可能永久不执行；无后台补偿与对账。用户被扣费但未获得服务，`user_quota` 与 `quota_ledger` 长期不一致 | 退款任务表 + 定时补偿 + 日对账（§3.8） |

### 2.2 P2（应修复）

| 编号 | 级别 | 证据（文件:行） | 影响 | 建议修复方向 |
|---|---|---|---|---|
| AR-09 | P2 | `oc-interview/src/main/java/.../session/application/InterviewSessionService.java:5-7`（import identity）；`InterviewController.java:5` 等 5 处 `CurrentUser`；`oc-admin/.../AdminUserService.java:11-15`（依赖 identity）；`oc-app/src/test/java/.../ArchitectureTest.java:16-42` | 与 `docs/design/architecture.md:299-304`“identity/interview/admin 互不依赖、跨域只走 API/事件”相悖；interview 直接调用 identity 的 `QuotaService`，admin 直接使用 identity 的 `UserRepository`/`QuotaService`/`PasswordPolicy`（含 `AdminDataInitializer` 提权与建号）。ArchUnit 只查 4 条规则，未强制模块依赖白名单、未禁止 `api→application.port`；`ReportController` 直接依赖 3 个仓储端口（`ReportController.java:38-40`） | 抽 `identity-api`（DTO + 内部端口）或事件/内部 HTTP；补 ArchUnit `moduleDependencies` 与 `apiShouldNotDependOnRepositoryPorts`；gateway 移出 app 分析范围的问题一并处理 |
| AR-10 | P2 | `backend/oc-report-worker/src/main/java/com/offercoach/report/package-info.java:1-4`（仅 package-info）；`ReportGenerationWorker.java:14-49` 实际在 oc-interview；`oc-gateway/src/main/resources/application.yml:11-17`；`oc-gateway/.../InternalPathBlockFilter.java` | `oc-report-worker` 为空依赖，app 仍打包一个空模块；报告 worker 实际跑在 app 进程内，无法独立扩缩/隔离故障。gateway 只是无鉴权反向代理：无 JWT、无限流、无 SSE 路由、无 `/internal` 之外的访问控制，与文档“统一入口 + JWT/限流”不符 | 删除空模块，或把 worker 迁入并支持 `worker` profile 独立启动；gateway 要么实现 JWT/限流/SSE，要么在文档中降级为“边缘反代”并明确 app 是唯一鉴权点 |
| AR-11 | P2 | `oc-admin/.../AdminQuestionService.java:139-205`（`sourcePath="admin-import:"+batchId+":"+index`，行 170）；`AdminQuestionRepositoryImpl.java:242-272`；`AdminQuestionService.java:95-106` | 管理端导入的幂等键包含 `batchId`，同一批内容重复导入会生成新行，违背“导入幂等”目标（仅启动文件导入按 `(repo,path,stem_hash)` 幂等）；进程中断时批次可能永久停在 `PENDING`（无恢复任务）；已 `PUBLISHED` 题目可原地改题干，与 api.md“发布后修改需升版本”不符，且旧会话已复制文本，无法追溯 | 幂等键改用 `(repo, stem_hash)` 或内容哈希；发布后写操作仅允许创建新版本/新行；对已发布题目禁止 `stem/dimension/difficulty` 原地更新 |
| AR-12 | P2 | `oc-admin/.../audit/AuditLogger.java:28-48` | `REQUIRES_NEW` 独立事务在主事务提交前先提交审计：若主事务随后回滚，审计仍记录“已完成”；写库失败仅 WARN，管理端写操作可能完全没有审计；`admin_audit_log.ip` 列从未写入 | 审计与业务同事务（或 outbox + 可靠投递）；失败进入补偿队列并告警；补 IP（可信代理解析）与操作者角色；对管理端写接口做契约测试断言审计必存在 |
| AR-13 | P2 | `backend/oc-app/src/test/java/.../IdentityM0IntegrationTest.java:35`（`disabledWithoutDocker=true`）；全仓仅约 42 个 `@Test`；`backend/pom.xml:154-173`（JaCoCo 只有 report，无 check/阈值） | 20k 行核心代码没有 `QuotaService`、`SessionStateMachine`、`AnswerService`、`ReportService`、`ResumeService`、`AdminUserService` 的单测；唯一端到端测试在无 Docker 环境静默跳过；文档承诺的覆盖率门禁未落地 | 为核心域补单测（状态机/额度/报告聚合/幂等）；IntegrationTest 增加 `@Tag("docker")` 并在 CI 显式运行或失败；JaCoCo 增加 `check` 规则（域 70%、整体 55%）；CI 增加迁移空库/升级库用例 |
| AR-14 | P2 | `ReportController.java:56-66`（列表逐项 `toSummary`）；`:134-153`（`meta` 每项 3 次查询）；`:38-40`（Controller 依赖仓储端口） | `GET /reports?size=20` 最坏 1+20×3 次 SQL（session/JD/resume），列表页明显放大延迟；Controller 直接读仓储，绕过 application 层 | 仓储提供 `findMetaByReportIds(ids)` 批量查询（1 次 JOIN），在 application 组装 DTO；Controller 只依赖 ReportService |
| AR-15 | P2 | `backend/oc-app/Dockerfile:18`（`-Duser.timezone=UTC`）；`QuotaService.java:102,114,149`（`LocalDate.now()`）；`AdminMetricsRepositoryImpl.java:57-66`（`date_trunc('day', now())`） | 每日会话上限按 UTC 零点重置（北京时间 08:00），与用户 `app_user.timezone` 无关；“今日活跃”也是 UTC 日；跨时区用户体验与运营报表偏差 | 统一以用户时区计算 `daily_session_date`（`ZonedDateTime.now(ZoneId.of(user.timezone))`），或在额度服务显式接收 zone；运营指标按可配置报表时区（默认 Asia/Shanghai）；补跨日边界测试 |
| AR-16 | P2 | `UserQuotaMapper.java:85-115`（只有 reset/increment，无 decrement）；`InterviewSessionService.java:250-259`（abandon 不释放每日名额） | 创建即占用每日名额；放弃/出题失败/超时也不返还，用户 3 次尝试用完即被限流，但实际未消费服务；与“每日会话上限”语义不符 | 在 abandon/FAILED/IDLE_ABANDON 时同事务 `daily_session_count = greatest(0, count-1)`（仅当当天且未答题）；或改为“完成答题才算一次”的计量口径并写入文档 |
| AR-17 | P2 | 仅 `AuthAttemptService.java:38-56` 有注册/登录限流；`ResumeService.java:378-393` 有重解析限流；`InterviewController.java:71-88` 创建会话、`JdController.java:43-50` 创建 JD、`ResumeController.java:56-64` 上传均无限流 | 文档承诺创建会话 5/min、JD 30/hour、简历 10/hour；当前可被脚本高频创建 JD（每次触发 AI 结构化）与上传简历，造成 AI 成本与存储滥用 | 增加统一 Redis 令牌桶过滤器/注解（按 userId + 路由），对 AI 场景加全局预算（日 token/调用上限）；超限返回 429 + `Retry-After` |
| AR-18 | P2 | `SecurityConfig.java:56-61`（`/actuator/prometheus`、`/v3/api-docs/**`、`/swagger-ui/**` permitAll）；`oc-gateway/src/main/resources/application.yml:28-32`（暴露 prometheus） | 生产公网可抓取 JVM/HTTP/池指标与完整 OpenAPI，泄露内部端点与容量信息；文档要求公网只暴露网关且健康检查最小化 | Prometheus 端点仅内网/带 token；Swagger 在 prod 关闭或要求 ADMIN；网关侧限制 `/actuator/**` 仅内网 |
| AR-19 | P2 | `QuestionGenerationWorker.java:146-156`（状态按 `available()` 推断）；`FeedbackService.java:124-135`（固定 `heuristic/rules-v1/DEGRADED/0 token`）；`FeedbackStreamService.java:65-97`（真实 AI 流无日志）；`ResumeStructurer.java:118-122`、`JdStructurer.java:81-101`、`JdMatcher.java:64-82` 无 `ai_call_log` | 6 个 AI 场景枚举中，仅 `QUESTION_GEN` 与 `FEEDBACK`（且只记启发式结果）写日志；`RESUME_PARSE`、`JD_ANALYZE`、`JD_MATCH` 与 SSE 真实模型调用全部无日志；`REPORT` 场景无任何 AI 调用；SSE 真实模型调用完全不计费不计 token；`QUESTION_GEN` 即使 AI 输出非法走了模板也记 SUCCESS；管理端成本看板必然失真 | 在 `AiProvider`/`AiGateway` 层统一记录 scene、provider、model、status、tokens、TTFT、latency、error；业务层不再自行推断；增加成本聚合指标 |
| AR-20 | P2 | `ResumeService.java:98-104`（仅扩展名 + 客户端 Content-Type）；`:395-404`；`DocumentTextExtractor.java` 无 magic number 校验 | 任意内容改名为 `.pdf/.docx` 即可入库并触发解析/退款流程；无病毒扫描；`application/octet-stream` 直接放行 | 读取文件头校验 `%PDF-`/`PK\x03\x04`；增加 ClamAV 或至少大小/页数/压缩炸弹防护；失败返回 415/422 且不扣额度（当前流程会先扣后失败再退款） |
| AR-21 | P2 | `AdminDataInitializer.java:55-67`（已存在账号直接 `updateRole(ADMIN)`，不校验密码）；`application.yml:88-92`（demo 默认开启）；`ProductionSafetyValidator.java:19-21` 仅 `@Profile("prod")` | 任何非 prod profile（staging/preview）默认写死演示账号密码、固定验证码；若管理员邮箱被普通用户先注册，引导程序会直接把该账号提为 ADMIN，形成账号抢占 | 管理员引导仅在“无任何 ADMIN 且显式开关”时执行，并校验/重置密码；demo/固定验证码改为“仅 local profile”生效（`@Profile("local")`），不要依赖 prod 黑名单 |
| AR-22 | P2 | `docs/design/architecture.md:28-65`（Python/RabbitMQ/Milvus/MinIO/Outbox）；`api.md:653`（自述未实现项）与新增 `POST /interviews/{id}/answers/{answerId}/feedback`（`InterviewController.java:205-212`，文档无此端点）；`backend/README.md:5`（仍写 M1 未开始） | 文档描述的是目标态，读者无法区分“已实现”与“规划”；新增端点未入契约，前端生成类型会漏；README 与 CHANGELOG 版本/阶段互相矛盾，交接成本高 | 建立“文档-代码”单一事实源：OpenAPI 由 springdoc 生成并提交快照；architecture.md 标记“目标态/已实现”并按模块列实际依赖；README 只保留启动与当前状态，历史进 CHANGELOG |
| AR-23 | P2 | `V202611070001__account_and_quota.sql`（`user_subscription`）；全仓无 `plan_code` 更新语句（grep 无结果）；`QuotaService.java:156-168` | 套餐只剩“初始化时写一次”的字符串：无购买、无升级、无到期、无降级、无管理端改套餐；PRO 判定仅 `planCode.equalsIgnoreCase("PRO")`（`FeedbackService.java:156-162`），与订阅事实源脱节；`features` 随套餐下发但无权益校验点 | 引入 `SubscriptionService`：以 `user_subscription` 为事实源判断 PRO/到期；管理端增加改套餐（审计 + 幂等）；额度发放与套餐变更同事务写 ledger |
| AR-24 | P2 | `ResumeStructurer.java:118-122`、`JdMatcher.java:66-69`、`QuestionGenerationWorker.java:216-220` 将简历原文/摘要送外部模型；`docs/design/api.md:187` 的 `allowRawResumeToLlm` 字段与 `UserService.java:43` 均未实现 | 隐私开关是文档承诺但代码缺失，用户无法阻止原文出境；与“PII 不外发”的安全目标冲突 | `app_user` 增 `allow_raw_resume_to_llm`（默认 false/true 需产品定），`PATCH /users/me` 支持；调用 AI 前按开关选择“摘要”或“原文”；审计与日志记录开关状态 |

### 2.3 P3（建议修复）

| 编号 | 级别 | 证据（文件:行） | 影响 | 建议修复方向 |
|---|---|---|---|---|
| AR-25 | P3 | `SessionStateMachine.java:13-26`（`FINISHED` 仅可去 `REPORT_GENERATING`，但 `finish` 直接从 IN_PROGRESS/PAUSED 置 REPORT_GENERATING，`InterviewSessionMapper.java:124-130`）；`advanceAfterAnswer` 把 `current_seq` 推到 `question_count+1`（`:164-173`） | `FINISHED` 不可达；详情页 `currentSeq` 可显示 9/8；答完最后一题不会自动结束，前端需自行判断 | 删除 `FINISHED` 或让 `finish` 走两段迁移；`current_seq` 上限设 `question_count`，另用 `answered_count` 表示进度；补状态机测试 |
| AR-26 | P3 | `AnswerService.java:92-94`（幂等键只校验长度，不要求、不参与去重）；`InterviewSessionService.java:374-389`（重放固定 `charged=1`） | 文档要求提交回答 `Idempotency-Key` 必填；实际仅靠 `question_id` 唯一约束返回 409，不是“回放首次结果”；创建会话重放会返回 `charged=1`，前端可能误判再次扣费 | 明确二选一：要么实现通用幂等记录（method+path+userId+body hash），要么在 api.md 改为“以 questionId 去重并返回 409”；重放响应 `charged=0` 并带 `replayed=true` |
| AR-27 | P3 | `ResumeController.java:200-206` / `JdController.java:105-111`（非数字 ID → 404）vs `InterviewController.java:365-371` / `ReportController.java:161-167`（→ 400）；`GlobalExceptionHandler.java:84-90`（`DataIntegrityViolationException` 落到 500）；api.md:483（回答长度写 422，代码返 400） | 同一类错误在不同资源返回不同状态码；唯一键竞争（如并发改题干、默认简历）返回 500；契约测试会失败 | 统一 ID 解析工具（400 VALIDATION_FAILED）；增加 `DataIntegrityViolationException` → 409（按约束名映射）；把长度错误改为 422 或在文档改 400 |
| AR-28 | P3 | `InterviewController.java:236-296`（每 500ms 查库、无 ping、30s 超时、调用 `questions()` 会顺带把 READY 置 IN_PROGRESS）；`FeedbackStreamService.java:65-97`（无 ping，300s） | 出题 SSE 对每个客户端产生约 60 次 detail + 题目查询；无心跳，前端 30s 收不到事件需自行重连；仅观察进度就会“开始面试”；与 api.md §6.2/§7.2 的 `ping` 契约不符 | 改为状态变更事件驱动（Redis pub/sub 或短轮询 1s）；增加 15s `ping`；把 READY→IN_PROGRESS 只放在显式 `start/answers` 动作；SSE 超时与 token 生命周期对齐 |
| AR-29 | P3 | `QuestionBankImporter.java:30-31,62-100`（`ApplicationRunner` 同步逐行） | 若配置了 `oc.qbank.import-path`（生产部署文档称已配置），26,385 行每次启动全量扫描 + 逐行 `INSERT ... ON CONFLICT`，启动时间显著拉长；每次启动写一条 `question_import_batch`（几乎全 skipped），污染批次列表 | 改为独立 CLI/管理端导入任务；或先按文件 mtime/内容哈希判断是否需要导入；批次写入加“启动导入”类型并去重 |
| AR-30 | P3 | `UserQuotaDO` 的 `version` 从不参与更新条件（`QuotaRepositoryImpl.java:103-116` 未映射）；`interview_session.expires_at` 只入不出；`failed_login_count/locked_until` 只写不读；`user_subscription`/`payment_order` 无代码 | 死列/死表误导后续维护者，`version` 让人误以为有乐观锁 | 删除或落地：`version` 用于 CAS 或删除；`expires_at` 接入会话超时；登录锁改 DB 后清理列；未实现表在迁移中标注 `-- reserved` 或延后创建 |
| AR-31 | P3 | `backend/oc-app/Dockerfile:14-19`（`-Xmx320m` + SerialGC）；`application.yml:20-23`（Hikari 20）；无 `statement_timeout`/`idle_in_transaction_session_timeout` | 320MB 堆承载 10MB PDF/DOCX 解析、8 个 SSE 线程、26k 启动导入，存在 OOM 风险；多副本 20 连接易打满 PG 默认 100；无慢 SQL/长事务保护 | 按压测调堆（≥768MB）或改 G1；Hikari 按副本数收敛并加 PG `statement_timeout=30s`、`idle_in_transaction_session_timeout=60s`；增加 OOM/池耗尽告警 |
| AR-32 | P3 | `PromptProvider.java:44-62`；`AdminPromptService.java:118-141`；`PromptVersion.model_config`/`variables_schema` 未被消费；`AdminExperimentService.java:21-24` 自述未接运行时 | Prompt 只能改 system 文本，变量与模型参数不生效；发布无评测门禁（文档要求“需评测通过”）；A/B 只 CRUD，`ab_assignment` 无写入；管理端看起来“可运营”但实际影响面很小 | 在 `PromptProvider` 渲染变量（或明确 v1 不支持并在 UI 标注）；发布前校验 variables 与模板占位符；接入 `model_config`（temperature/maxTokens）；A/B 在 `AiGateway` 分桶并记录 `variant` |
| AR-33 | P3 | `OpenAiCompatibleProvider.java:233-239`（非 2xx 打印上游 body 前 300 字）；`AdminAiCallController.java` 只有 `GET` 列表，缺 `GET /admin/ai-calls/{id}`（api.md:746） | 上游错误体可能回显用户输入/Prompt 片段，进入日志即泄露；管理端无法查看单次调用详情 | 只记录状态码与上游 `error.code`，不记录 body；如需排障，采样后脱敏入库；补详情端点或从文档删除 |
| AR-34 | P3 | `oc-interview/.../api/dto/PageResponse.java:8-12` 与 `oc-admin/.../api/dto/PageResponse.java:8-12` 完全重复；`AdminQuestionService.java` 369 行、`ResumeHeuristicParser.java` 720 行、`InterviewSessionService.java` 506 行；`AsyncConfig.java:23-26` 魔法数 | 重复 DTO/超大类增加漂移与理解成本；魔法数无法按环境调优 | `PageResponse` 下沉 `oc-common`；拆分 `AdminQuestionService`（查询/写入/导入）、`ResumeHeuristicParser`（按 section 拆分）；线程池参数走 `oc.async.*` 配置 |
| AR-35 | P3 | `backend/oc-app/src/main/resources/db/migration/` 只有版本化正向脚本，无回滚脚本；`application.yml:30-33` 未设 `cleanDisabled`/`validateOnMigrate`；仓库根无 CI workflow（仅 `data/question-banks` 里第三方仓库的 workflow） | 发布回滚只能手工反向 SQL；文档 `docs/ops/ci-cd.md` 描述的流水线在仓库中不存在，门禁不可复现 | 每个迁移附 `rollback-*.sql` 并在演练环境验证；显式 `validateOnMigrate=true`、`cleanDisabled=true`；补 GitHub Actions/GitLab CI：verify + test + 迁移 + oasdiff |
| AR-36 | P3 | `oc-gateway/src/main/resources/application.yml:11-26`；`InternalPathBlockFilter.java` 只对 `/internal` 设 requestId/traceId；网关不生成 `X-Request-Id`/不透传 `traceparent`；CORS 头未含 `X-Device-Id` | 经过网关的正常请求由 app 生成 requestId，网关日志与 app 日志无法关联；前端若传 `X-Device-Id` 预检失败 | 网关增加 GlobalFilter：校验/生成 `X-Request-Id`、透传 `traceparent`、补 `X-Device-Id` 到 allowedHeaders；统一响应头 |

## 3. P1 可执行修复方案

### 3.1 AR-01 月度额度续发与订阅到期

1. 迁移新增字段（可选）：`ALTER TABLE user_quota ADD COLUMN period_index SMALLINT NOT NULL DEFAULT 0;` 用于幂等键。
2. 新增 `QuotaRenewalJob`（`@Scheduled(cron = "0 5 0 * * *", zone = "Asia/Shanghai")`）：
   ```sql
   SELECT user_id, plan_code, period_start, period_end
   FROM user_quota
   WHERE period_end <= CURRENT_DATE
   ORDER BY user_id
   LIMIT 500
   FOR UPDATE SKIP LOCKED;
   ```
3. 对每个用户在一个事务内：读取套餐额度；`UPDATE user_quota SET interview_credits=?, resume_credits=?, period_start=CURRENT_DATE, period_end=(CURRENT_DATE + INTERVAL '1 month')::date, daily_session_date=NULL, daily_session_count=0 WHERE user_id=?`；插入两条 `PLAN_GRANT` 流水，幂等键 `plan-grant:{period_start}:{TYPE}:{userId}`（`UNIQUE(user_id, idempotency_key)` 天然防重）。
4. PRO 到期：以 `user_subscription(status='ACTIVE', expires_at)` 为准；`expires_at <= now()` 时在同一任务中 `UPDATE user_subscription SET status='EXPIRED'`、`user_quota.plan_code='FREE'`，再按 FREE 发放。`FeedbackService.isPro` 改为调用 `SubscriptionService.isPro(userId)`（查 ACTIVE 且未过期），不要只看 `plan_code`。
5. 对账与告警：任务输出 `renewed`、`failed`、`skipped` 计数到日志/指标；失败按用户重试并告警。
6. 测试：把 `period_end` 置为昨天，执行任务两次，断言余额回到套餐值且 `quota_ledger` 只多两条。

### 3.2 AR-02 解锁与重置密码

1. 最小改动（保留 Redis 锁）：
   - `AdminUserService` 注入 `StringRedisTemplate`；`unlock` 在 DB 更新成功后 `redis.delete(RedisKeys.loginFailAccount(email))`，Redis 异常时抛 `INTERNAL_ERROR`（不能返回成功）。
   - 增加 `AdminUserRepository.findById` 返回 email 用于清键。
2. 推荐改动（DB 事实源）：
   - `AuthAttemptService.checkLoginAllowed` 增加 `locked_until > now()` 判断（登录前查 `app_user`）。
   - 失败时 `UPDATE app_user SET failed_login_count=failed_login_count+1, locked_until = CASE WHEN failed_login_count+1 >= 5 THEN now()+interval '15 min' ELSE locked_until END WHERE id=?`；成功后清零。
   - Redis 仅保留 IP 分钟窗口，故障放行不再影响账号锁。
3. 重置密码吊销会话：
   - `RefreshTokenService` 维护 `oc:auth:user-families:{userId}` 集合（issue 时 SADD），新增 `revokeAllForUser(userId)` 删除所有家族与 token。
   - `AdminUserService.resetPassword` 调用它；如需立即踢掉 Access，增加 `oc:auth:user-epoch:{userId}`，JWT 签发写入 `epoch` claim，校验器比对（或接受 15 分钟窗口并在文档说明）。
4. 测试：连续 5 次错误密码 → 429；管理员 unlock → 立即登录 200；重置密码后用旧 Refresh 刷新 → 401 `REFRESH_INVALID`。

### 3.3 AR-03 线程池与拒绝策略

1. 拆分为三个 bean（参数走 `oc.async.*`）：
   - `ssePumpExecutor`：JDK21 虚拟线程 `Executors.newVirtualThreadPerTaskExecutor()`，或 `ThreadPoolTaskExecutor(core=32,max=64,queue=0)`；每个用户并发流 ≤2（Redis 计数，超限 429 `STREAM_LIMIT_EXCEEDED`）。
   - `aiTaskExecutor`：`core=4,max=8,queue=256,RejectedExecutionHandler=CallerRunsPolicy`，专用出题/解析/预评分。
   - `scheduler`：`spring.task.scheduling.pool.size=2`，报告/回收/续期互不阻塞。
2. 所有 `@Async("sseExecutor")` 改为 `@Async("aiTaskExecutor")`（`SessionPreparingListener`、`ResumeParseRequestListener`、`FeedbackGenerationListener`）。
3. SSE 端点提交失败时捕获 `TaskRejectedException` → `BizException(STREAM_LIMIT_EXCEEDED)`；不要落到全局 500。
4. 线程池优雅停机：`setWaitForTasksToCompleteOnShutdown(true)`、`setAwaitTerminationSeconds(20)`；配合 `server.shutdown=graceful`。
5. 指标：active/queue/remaining/rejected；超过阈值告警。
6. 长期：把 `AFTER_COMMIT` 事件改为 `async_task` 表（type、payload、status、attempts、next_at），worker `FOR UPDATE SKIP LOCKED` 领取；这同时解决 AR-04/AR-05/AR-08。

### 3.4 AR-04 状态看门狗

1. 新增 `SessionRecoveryJob`（每分钟）：
   ```sql
   -- 出题超时
   UPDATE interview_session
   SET status='FAILED', failure_reason='GEN_TIMEOUT', updated_at=now()
   WHERE status='PREPARING' AND updated_at < now() - interval '5 minutes'
   RETURNING id, user_id;
   -- READY 长期未开始
   UPDATE interview_session
   SET status='ABANDONED', updated_at=now()
   WHERE status='READY' AND last_active_at < now() - interval '24 hours'
   RETURNING id, user_id;
   ```
   对返回行调用幂等退款（`interview-refund:{id}`）。
2. 简历：`UPDATE resume SET parse_status='FAILED', parse_error='PARSE_TIMEOUT' WHERE parse_status IN ('PENDING','PARSING') AND updated_at < now()-interval '3 minutes'`；`ResumeService.reparse` 允许 `PENDING/PARSING` 且 `updated_at` 超过 3 分钟的重试。
3. 评分：`SELECT id FROM interview_answer WHERE status='SCORING' AND submitted_at < now()-interval '1 minute'` → 重新 `feedbackService.prepare`；连续 3 次失败置 `FAILED` 并在反馈接口返回可重试错误。
4. 指标与告警：`preparing_count`、`parsing_count`、`scoring_count`、`ready_stale_count`。
5. 测试：直接插入一条 10 分钟前的 PREPARING 会话，跑任务，断言 FAILED + 退款流水一条；重启 app 后 READY 会话可被回收。

### 3.5 AR-05 报告与评分对齐

1. `ReportService.generate` 改为：
   ```java
   long answered = session.answeredCount();
   long feedbacks = feedbackRepository.countDistinctAnswersBySession(session.id());
   if (feedbacks < answered) {
       throw new ReportDeferredException("waiting-feedback");
   }
   ```
   新增 `ReportDeferredException`，worker 捕获后**不增加 attempts**，只把 `updated_at` 置为 `now() - 4s` 以便下一轮再试（或新增 `next_attempt_at`）。
2. 仅对真实错误（DB/序列化/聚合异常）调用 `fail`；`attempts >= max` 才 FAILED。
3. `finish` 时若 `answered > scored`，报告状态仍可为 `GENERATING`，但 worker 延迟到对齐；前端已有 90s 轮询，可接受。
4. 更优：`FeedbackService.prepare` 成功后发布 `FeedbackReady` 事件，若该会话已 `REPORT_GENERATING` 且所有回答已评分，立即触发一次生成；worker 作为兜底。
5. 手动重试独立计数：`interview_report.manual_attempts`（迁移新增），最多 3 次/报告，避免无限重试。
6. 测试：提交回答后立即 finish，mock 评分延迟 30s，断言报告最终 READY 且 attempts 未因等待而耗尽。

### 3.6 AR-06 题库检索与岗位族

1. 岗位族规范化：
   - `JdStructurer` 的 JSON schema 与 `JdHeuristicStructurer.structure` 增加 `jobFamily`（枚举：`BACKEND/FRONTEND/ALGORITHM/DATA/QA/SYSTEM_DESIGN/AI_AGENT/GENERAL`）。
   - 迁移/导入脚本把现存中文 `job_family` 映射为枚举并写回；或在查询时用映射表 `job_family_alias(alias, code)`。
2. 检索 SQL（使用已有 `idx_question_title_trgm`、`idx_question_stem_trgm`）：
   ```sql
   SELECT q.*, (similarity(q.title, #{q}) * 2 + similarity(q.stem, #{q}) * 1.5
                + CASE WHEN lower(q.job_family)=lower(#{family}) THEN 1.5 ELSE 0 END
                + COALESCE(q.quality_score,0) * 0.5) AS score
   FROM question_bank q
   WHERE q.status='PUBLISHED'
     AND (#{family} IS NULL OR lower(q.job_family)=lower(#{family}))
     AND (q.title % #{q} OR q.stem % #{q} OR q.tags && #{tags})
   ORDER BY score DESC, q.id
   LIMIT #{limit};
   ```
   去掉 `ORDER BY id LIMIT 200` 的候选截断；`%` 阈值用 `pg_trgm.similarity_threshold` 会话级设置。
3. 维度/难度配比在 SQL 结果之后仍由 `QuestionSelection` 处理；若同维度候选不足，记录 `bank_shortfall` 指标，AI/模板补齐但返回 `meta.degraded=true`。
4. 建立 50 条 query 的召回评测（离线脚本）：recall@10、维度覆盖、岗位族命中率；CI 生成报告，低于基线阻断发布。
5. 测试：导入 26k 后查询“Kafka 幂等”“缓存一致性”，断言 top10 含相关题且不同岗位族结果有差异。

### 3.7 AR-07 管理端额度补偿

1. 在 `AdminUserDtos.QuotaRequest` 增加 `@Min(-100) @Max(100) Integer delta`（或服务层 `Math.abs((long) delta) > max` 抛 VALIDATION_FAILED）；上限配置 `oc.admin.max-quota-delta: 100`。
2. 迁移 `quota_ledger` 增加 `note VARCHAR(128)`（或 `ref_detail JSONB`），`QuotaService.applyDelta` 增加 `note` 参数；管理端写入 `note = reason`，`reason` 保持 `ADMIN_COMPENSATION`。`quota_ledger` 查询/对账可按 note 过滤。
3. 超过阈值（如 >50）要求二次确认字段 `confirmToken` 或双人审批；至少记录操作者、目标用户、变更前后余额、原因、IP。
4. 幂等重放时不再写审计（判断 `result.replayed()`）。
5. 测试：`delta=101` → 400；`delta=-1000` → 400；同 key 重放 → 只一条 ledger、一条审计。

### 3.8 AR-08 退款补偿与对账

1. 迁移新增：
   ```sql
   CREATE TABLE quota_refund_task (
     id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
     user_id BIGINT NOT NULL REFERENCES app_user(id),
     quota_type VARCHAR(16) NOT NULL,
     amount INT NOT NULL CHECK (amount > 0),
     reason VARCHAR(32) NOT NULL,
     ref_type VARCHAR(24), ref_id BIGINT,
     idempotency_key VARCHAR(128) NOT NULL,
     status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
       CHECK (status IN ('PENDING','DONE','FAILED')),
     attempts SMALLINT NOT NULL DEFAULT 0,
     last_error VARCHAR(128),
     created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
     UNIQUE (user_id, idempotency_key)
   );
   ```
2. `abandon`/`markGenerationFailed`/`ResumeParseExecutor.fail` 在状态变更的同一事务内插入 `quota_refund_task`（`ON CONFLICT DO NOTHING`），不再直接调用 `refundQuietly`。
3. `RefundRelay` 每 10s：`SELECT ... WHERE status='PENDING' AND updated_at < now()-interval '5 seconds' ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED`；调用幂等 `quotaService.refund`；成功后 `DONE`，失败 `attempts+1`、`last_error`，超过 5 次 `FAILED` 并告警。
4. 日对账 SQL：`user_quota` 余额 vs `quota_ledger` 累计（以初始 0 为基线）差异 >0 告警；另核对 `SESSION_CHARGE` 与 `SESSION_REFUND` 数量、`PARSE_CHARGE` 与 `PARSE_REFUND`。
5. 测试：注入失败（如临时改错表名/mock 异常），断言任务保留并最终退款成功、ledger 无重复。

## 4. 已核对（未发现阻断性问题）

以下区域逐文件核对，未发现需要单列的问题：

1. **统一错误信封**：`ApiError` 字段与 api.md §1.5 一致；`ErrorCode` 24 个码与 §1.6 一一对应；null 字段显式输出（`application.yml:34-37`）。
2. **认证安全**：BCrypt(12)、RS256、iss/aud/jti/exp 校验、Refresh 家族轮换与重放整族吊销、Access 黑名单 fail-closed、prod 缺 PEM/宽松 Cookie/本地 CORS 启动失败（`JwtKeyProvider.java:49-69`、`SecurityConfig.java:35-74`、`ProductionSafetyValidator.java:40-58`）。
3. **额度扣减/返还主路径**：`SELECT ... FOR UPDATE` 内做幂等检查、`WHERE credits >= amount` 条件更新、`CHECK (credits >= 0)`、`UNIQUE(user_id, idempotency_key)`，事务内不 catch 唯一键冲突（`QuotaService.java:205-256`）。
4. **账号唯一性与软删**：CITEXT 邮箱、未删除行部分唯一索引、DELETED 允许联系方式为空、`existsByEmail` 与索引口径一致。
5. **会话并发**：状态迁移均带 `WHERE status=期望值`；`uq_session_active` 兜底单活跃会话；创建幂等键唯一；`interview_answer.question_id` 唯一；题目可见性 SQL 只返回 `seq <= current_seq` 或已答/已跳过（`InterviewSessionMapper.java:72-183`、`InterviewQuestionMapper.java:43-48`）。
6. **资源归属/越权**：session/answer/report/resume/JD 的读写均带 `user_id`；报告详情通过 report 归属再查 session；反馈接口先校验 answer 归属；管理端类级 `@PreAuthorize("hasRole('ADMIN')")`。
7. **SSE 评分令牌**：32 字节随机、Redis 5 分钟、值绑定 `userId:answerId`、Redis 异常 fail-closed；不以 URL 传长期 Access Token。
8. **SQL 注入/路径穿越**：MyBatis 全部 `#{}`、JdbcTemplate 全部 `?`；唯一字符串拼接是 `.last("LIMIT " + int)` 与整数分页（`UserRepositoryImpl.java:95`），不构成注入；`LocalFileStorage.resolve` 校验 `startsWith(root)`。
9. **API Key 安全**：AES-GCM、随机 IV、`v1:` 前缀、接口只回末 4 位、审计不含明文、未配置 `OC_AI_SECRET` 拒绝保存（`SecretCipher.java:41-108`、`AdminAiConfigService.java:44-57`）。
10. **迁移结构**：classpath 下 7 个版本唯一、无重复；`uq_question_source_stem` 使用生成列 `md5(stem)`，启动导入按 `(repo,path,stem_hash)` 幂等；`prompt_version` 每模板仅一个 PUBLISHED 由部分唯一索引兜底。
11. **报告落库幂等**：`insertIfAbsent(session_id)`、`markReady WHERE status='GENERATING'`、`session.complete WHERE status='REPORT_GENERATING'`，重复执行不会产生第二份报告或重复完成。
12. **日志脱敏基线**：登录失败账号做掩码、简历解析只记长度/异常类型、反馈日志只记分数、不打印简历/回答/Prompt 正文（`AuthAttemptService.java:136-146`、`ResumeParseExecutor.java:78-86`、`FeedbackService.java:136`）。
13. **评分重复执行幂等**：异步预生成与 SSE 都调用 `FeedbackService.prepare`，`answer_feedback.request_id=fb:{answerId}` + `ON CONFLICT DO NOTHING`，不会重复写反馈、不会重复扣费；`markScored` 幂等。代价只是同一答案可能被计算两次（`FeedbackService.java:89-138`、`AnswerFeedbackMapper.java:33-48`）。
14. **健康检查**：`management.endpoint.health.probes.enabled=true`，prod `show-details=never`；DB/Redis 默认 HealthIndicator 可用。指标端点的公网暴露问题另见 AR-18。
15. **代码规范门禁**：Spotless + Palantir（4 空格/120 列/UnusedImports）、Checkstyle（Tab、行宽、UnusedImports、`Executors` 禁用、`System.out` 禁用）、Enforcer JDK 21 均已配置；抽查未发现 star import、Tab 缩进、`System.out` 或手写线程池。
16. **空指针与脏数据边界**：`JsonCodec`/`SessionConfig.fromMap` 对非法 JSON 有降级；`ResumeService`/`JdService` 对 null 标题/原文有校验；`Map.of` 使用点均能保证值非空。未发现可直接触发的 NPE；外部 JSON 的隐式结构假设主要由测试盲区（AR-13）放大风险。

## 5. 待验证项

1. `@Async` + `@TransactionalEventListener(AFTER_COMMIT)` 在线程池拒绝时，异常是否向已提交事务的调用方传播；不同 Spring 版本行为需实测（影响 AR-03 的用户可见症状）。
2. 生产是否真的配置了 `oc.qbank.import-path=/data/qbank/questions.jsonl`：仓库内 `application.yml`/`run-dev.sh`/`.env.example` 均无此配置，仅 `docs/ops/deployment.md:290` 与 CHANGELOG 提及。
3. 生产是否已配置真实 AI Key：`docs/CHANGELOG.md` v2.0 写“未配置”，v2.2 写“已设置 OC_AI_SECRET”，但“下一步”仍写“配置真实模型密钥”，需以运行环境为准。
4. Testcontainers 在 CI 是否实际执行：仓库无 CI workflow；本地 `disabledWithoutDocker=true` 会静默跳过。
5. 线上 `question_bank.job_family` 的实际分布是否与 `data/question-bank/normalized/questions.jsonl` 一致（推断一致，未查库）。
6. 多实例部署时 `PromptProvider`（60s）与 `AiRuntimeConfigService`（30s）的本地缓存不一致是否可接受；当前只有单实例部署，未验证。

## 6. 建议修复顺序

1. **第一周（阻断性）**：AR-03 线程池拆分 + AR-04 状态回收 + AR-08 退款任务；这三项决定“用户会不会被卡死/扣钱不办事”。
2. **第二周（计费与报告）**：AR-01 月度续发/订阅到期、AR-02 解锁与吊销、AR-05 报告对齐、AR-07 补偿限额。
3. **第三周（产品核心质量）**：AR-06 题库检索重构 + 召回评测；同时补 AR-19 AI 日志与 AR-15 时区。
4. **第四周（架构与门禁）**：AR-09 模块边界/ArchUnit、AR-10 模块归位或文档降级、AR-13 测试与 CI、AR-11/AR-12 管理端一致性。
5. **随迭代**：其余 P2/P3；其中 AR-20 文件校验、AR-18 指标暴露、AR-24 隐私开关建议提前到安全专项。