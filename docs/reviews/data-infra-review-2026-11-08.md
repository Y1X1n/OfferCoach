# 数据 / 基础设施 / 运维审查（2026-11-08）

> 状态：留档 ｜ 方式：只读审查代码、Flyway、Compose/Dockerfile、配置与文档；未修改代码、未部署、未重启、未连接生产数据库。
> 范围：`backend/`（迁移、持久化、Redis 使用、存储、报告/出题 worker、管理端指标与审计）、`frontend/` 容器与宿主入口配置、`docs/ops/*`、`docs/design/data-model.md`、题库构建产物与许可清单。
> 结论：**P0 3 项、P1 11 项、P2 14 项、P3 6 项**。核心业务数据模型和“软删/幂等/额度行锁”等细节质量较好；最大风险集中在**发布与变更基线、备份/恢复、题库许可**三条硬伤，其次是 Redis 单点与驱逐策略、可观测性空缺、启动期题库导入、检索未使用 trigram、PII 生命周期缺失。

## 1. 摘要

- 生产实际形态与文档描述不一致：文档主体是“K8s + Helm + staging + 灰度 + PDB”（`docs/ops/deployment.md:4,171-183`），实际是**单台 cn-beijing ECS + Docker Compose + 宿主 Nginx**（`docs/ops/deployment.md:278-306`）。仓库中既无生产 Compose、宿主 Nginx 配置，也无 `.git`、CI 流水线、迁移 Job。文档中的“回滚/灰度/PDB”无法照做。
- 数据层：21 张业务表、约 50 个索引/唯一约束，`TIMESTAMPTZ`、`NUMERIC`、`VARCHAR + CHECK`、部分唯一索引、生成列去重、额度行锁与流水幂等实现较规范；但**月度额度/订阅到期没有任何调度任务**，`LocalDate.now()` 跑在 UTC JVM 上，`quota:*` 缓存只删不读，形成“计费语义与实现不一致”。
- 题库：26,385 条、27.9 MB JSONL；其中 3,393 条 GPL-3.0、428 条 CC-BY-NC-SA、155 条 CC-BY-NC-ND，导入即 `PUBLISHED`，无许可过滤、无署名页。**法务风险高于技术风险**。
- 检索：`QuestionBankRetriever` 实际取 `status='PUBLISHED' ORDER BY id LIMIT 200` 后在 Java 侧 bigram 打分；JD 结构化结果里没有 `jobFamily` 字段，导致岗位族恒为 `null`。26k 题库在运行时等价于“固定前 200 条”，两个 trigram GIN 索引对用户检索无效，只在管理端 `ILIKE` 时可能被使用。
- 运维：无 Git/CI/镜像扫描；每日 03:15 本机 dump 保留 7 天，无异地、无加密、无 PITR、无恢复演练，简历原文件不在备份范围；无 Prometheus 抓取、无告警、无访问日志与容器日志轮转；应用启动时内联 Flyway + 逐行导入 26,385 条题库，重启即长时间不可用且没有可执行的回滚方案。
- 优先动作：先建立可回滚的变更基线（Git/CI/版本化部署）+ 异地加密备份与恢复演练 + 题库许可止血；随后处理 Redis 可用性、监控告警、存储与保留、成本计量。

## 2. 审查方法与证据边界

**已直接核对（代码/迁移/仓库配置）**

- `backend/oc-app/src/main/resources/db/migration/V*.sql` 共 7 个迁移；`application.yml` / `application-prod.yml`；`backend/compose.yaml`；`oc-app/Dockerfile`；`frontend/Dockerfile`、`frontend/docker/default.conf`。
- 持久化与查询：`oc-identity`、`oc-interview`、`oc-admin` 下的 Mapper/Repository/Service；Redis Key 常量；认证、额度、SSE、报告 worker、题库导入/检索、文件存储、审计。
- 文档与产物：`docs/ops/deployment.md`、`observability.md`、`ci-cd.md`、`docs/design/data-model.md`、`docs/CHANGELOG.md`、`data/question-bank/LICENSES.md`、`data/question-bank/normalized/manifest.json`，并用 Node 统计了 JSONL 的许可与岗位族分布。

**仅来自文档、无法在仓库侧验证**

- 宿主 `/opt/offercoach` 下的 Compose、`offercoach.env`、JWT PEM、`.admin-credentials`、`.ai-secret`、`backups/`、`cert-check.sh` 与 cron；宿主 Nginx 的 TLS/SSE 配置；Certbot 续期实际状态；ECS 规格、磁盘、AZ、swap 之外的调优；PostgreSQL/Redis 实际参数与数据量；生产库中题库许可分布是否与 JSONL 一致。
- 这些内容全部标为“文档声称/未验证”，不据此判定为已实现。

**严重度定义**

- P0：可在数小时内造成全量数据不可恢复、法律下架或发布不可控。
- P1：显著影响可用性、成本、合规或核心功能正确性，需在 30 天内处理。
- P2：中长期风险或运维效率问题，60 天内处理。
- P3：规范、可维护性与文档一致性问题。

---

## 3. 发现清单

### 3.1 P0

#### P0-1 发布与变更基线缺失：无 Git、无 CI、无版本化生产编排

- 证据：`E:/My_project` 下 `git rev-parse` 返回 `not a git repository`；仓库无 `.github/workflows`、Jenkinsfile、Argo/Helm 清单。镜像由本地 Maven 产物直接 COPY：`backend/oc-app/Dockerfile:8`（`target/oc-app-0.1.0-SNAPSHOT.jar`），而生产镜像是 `offercoach-app:0.4.0`（`docs/ops/deployment.md:287`）。`docs/ops/ci-cd.md:16-38` 描述的流水线、`ci-cd.md:61` 的 SBOM/签名、`ci-cd.md:65` 的独立迁移 Job 均未落地。
- 风险：无法回答“生产跑的是哪次提交”；任何回滚只能靠人工记得旧镜像；迁移与应用一起发布且无灰度；镜像内容不可复现（jar 预先构建，Dockerfile 不构建）；供应链无扫描、无签名、无许可证门禁。单机 ECS 上的手工 `docker compose up` 是唯一发布方式，变更审计为零。
- 建议：
  1. 立即 `git init` + 私有远端，提交当前工作区并打 `prod-0.4.0` 标签；把 `/opt/offercoach` 的 Compose、Nginx、cron、env 模板（脱敏）纳入仓库。
  2. 建最小 CI：后端 `mvn -q verify`（含 ArchUnit/Checkstyle/单测）、前端 `pnpm check`、镜像构建（多阶段，从源码构建而非 COPY jar）、Trivy/OSV 扫描、`git tag` 触发版本镜像。
  3. 生产部署改为“拉取带版本 tag 的镜像 + `docker compose up -d` + 迁移前置/后置脚本”，并在部署记录中写入 commit、镜像 digest、迁移版本、操作人。
  4. 迁移从应用启动中拆出（独立 one-shot 容器或发布脚本），应用启动只校验 `flyway_schema_history` 版本。

#### P0-2 备份与恢复不可用：单机、无异地、无 PITR、文件未备份、未演练

- 证据：文档承认“每日 dump 只在本机”“无异地备份/监控告警”（`docs/ops/deployment.md:303`）；实际策略为 `/opt/offercoach/backups` 每日 03:15 全库 dump、保留 7 天（`deployment.md:295`）。文档中的 WAL 归档/PITR 7 天、MinIO 跨区复制、季度恢复演练（`deployment.md:249-256`）在仓库中没有任何脚本或配置。简历原文件与题库 JSONL 位于 `/opt/offercoach/data`（`deployment.md:288,290`），dump 不包含文件；`LocalFileStorage` 只写本地磁盘（`backend/oc-interview/src/main/java/com/offercoach/interview/storage/LocalFileStorage.java:24-42`）。
- 风险：单块云盘/单台 ECS 故障即同时丢数据库与全部简历文件；RPO 实际为 24 小时（文档承诺 5 分钟）；dump 未加密，含简历/回答正文与账号 PII；保留 7 天意味着静默损坏超过一周即不可恢复；从未做过恢复演练，备份是否可恢复未知；备份失败无告警。
- 建议：
  1. 每日 `pg_dump -Fc` 后立即 `age`/GPG 加密，上传到**另一可用区/另一账号的 OSS**，生命周期 30 天；本地仅留 2 份。
  2. 开启 WAL 归档（或至少每日 `pg_basebackup` + WAL）到异地，目标 RPO ≤ 15 分钟、RTO ≤ 2 小时；若成本受限，先明确承诺 RPO 24h 并写入对外 SLA。
  3. 简历文件与 qbank JSONL 用 `rsync`/OSS 同步到异地，版本化 + 对象锁；文件 key 与 DB 行必须同批次可对齐。
  4. 每月自动恢复演练：把最新 dump 恢复到临时 PG，跑登录/简历/面试/报告 smoke；记录耗时与校验和。备份任务失败、备份文件缺失、最新备份超过 26 小时均告警。
  5. 备份与恢复脚本纳入仓库并 code review；备份目录 700/文件 600，密钥与备份分离存放。

#### P0-3 题库许可：3,976 条 NC/ND/GPL 内容直接对用户发布

- 证据：`data/question-bank/LICENSES.md:6-14` 标注 `codejeet` 为 GPL-3.0、`campus-interview-questions` 为 CC-BY-NC-SA-4.0、`system-design-101` 为 CC-BY-NC-ND-4.0，并明确“ND 禁止演绎”“NC 禁止商业使用”“GPL 传染”（`LICENSES.md:74-80`）。JSONL 统计：MIT 22,409 条；GPL-3.0 3,393；CC-BY-NC-SA 428；CC-BY-NC-ND 155。导入器把来源许可原样写入但**不做任何过滤**，且默认状态 `PUBLISHED`（`backend/oc-interview/src/main/java/com/offercoach/interview/qbank/QuestionBankMapper.java:52`，`QuestionBankImporter.java:140-154`）。运行时会把这些题干/参考答案直接展示给用户并送入 AI Prompt（题库题落库见 `QuestionGenerationWorker.java:272-296,339`，Prompt 拼接见 `FeedbackStreamService.java:101-103`）。
- 风险：商业产品使用 NC 内容、对 ND 内容做 Markdown 清洗/结构化（演绎）、分发 GPL 题解，均可能构成侵权；用户面与 AI 输出都没有署名/来源提示。被投诉时需整体下架题库，影响核心卖点。
- 建议：
  1. 立即在检索/管理端 SQL 加许可白名单（只 `MIT`），或在导入阶段把非 MIT 标为 `ARCHIVED`/内部评测库，禁止 `PUBLISHED` 进入用户检索。
  2. 建立 `source_license` → 可用场景矩阵（用户可见/仅内部评测/禁止），导入 Job 强校验；增加许可与署名页面，MIT 保留版权声明。
  3. 法务复核 GPL/NC/ND 三条来源；无法取得授权则从公开产物中移除并重建 JSONL。
  4. 把许可扫描加入 CI（对 JSONL 做 `sourceLicense` 断言），防止回归。

### 3.2 P1

#### P1-1 Redis 单实例同时承载认证状态，`allkeys-lru` 会驱逐会话，故障时全站 401

- 证据：`backend/compose.yaml:29-48` 单 Redis、512 MB、`--maxmemory-policy allkeys-lru`、AOF；Refresh Token/家族/已用标记/黑名单全部存该实例（`RedisKeys.java:7-23`）。黑名单校验在每个已认证请求上执行且 fail-closed（`TokenBlacklistValidator.java:37-45`）；Redis 异常直接拒绝 Token。文档登记了“Redis 故障吊销 fail-closed”（`docs/CHANGELOG.md` v1.8），但没有 HA。
- 风险：内存达到 512 MB 时 LRU 可能驱逐 `oc:auth:refresh:*`（用户被登出）或 `oc:auth:blacklist:*`（已吊销 Token 重新可用）；Redis 容器重启/故障期间，所有带 JWT 的 API 因黑名单不可用而拒绝，形成全站不可用，而不仅是“缓存失效”。
- 建议：认证与缓存分离为两个实例/两个 logical DB；认证实例 `maxmemory-policy noeviction` + AOF `everysec` + 副本/Sentinel；黑名单改为“短期本地缓存 + Redis”，Redis 不可用时按 Token 剩余寿命降级（或至少对读接口放行、写接口拒绝）；为该 Redis 增加内存、驱逐、连接数告警。

#### P1-2 可观测性实质空缺：有 Actuator，但没有采集、看板、告警、业务指标

- 证据：`application.yml:45-53` 暴露 `health,info,prometheus`，`oc-app/pom.xml` 含 `micrometer-registry-prometheus`；但仓库无 Prometheus/Grafana/Alertmanager 配置，文档承认“无监控告警”（`deployment.md:303`）。`docs/ops/observability.md:17-29` 列出的 `oc_sse_first_token_ms`、`oc_ai_tokens_total`、`oc_ai_cost_micros_total`、`oc_sse_active_connections` 等在代码中没有任何 Micrometer 注册（全仓搜索 `MeterRegistry/Counter/Timer` 无业务实现）。5xx/P95、证书、备份失败、磁盘、Redis 驱逐、PG 连接数均无告警规则。
- 风险：故障只能靠用户投诉发现；无法判断“SSE 首字 27ms”是否持续；无法定位 5xx 是应用、PG 还是 Nginx；备份连续失败、证书过期、磁盘写满都无感知；SLO/错误预算（`observability.md:93-99`）没有数据来源。
- 建议：
  1. 先在**独立小主机**（不与业务同机）部署 Prometheus + Grafana + Alertmanager，抓取 app/gateway `/actuator/prometheus`、node_exporter、postgres_exporter、redis_exporter、Nginx stub_status。
  2. 最低告警集：5xx > 1% 持续 5 分钟、P95 > 1s、磁盘 > 80%、证书 < 14 天、最新备份 > 26 小时、Redis evicted_keys > 0、PG 连接 > 85%、容器重启、`up == 0`。
  3. 在代码中补业务指标：SSE 首字/断流、AI 调用（scene/model/status/tokens/cost）、报告生成时延、额度拒绝原因；标签禁止 userId/sessionId。
  4. 管理端指标改为读预聚合表/物化视图，不在请求时扫全表（见 P2-4）。

#### P1-3 日志只写 stdout，无访问日志、无轮转，磁盘可被日志写满

- 证据：`logback-spring.xml:15-27` 非 local 仅 Console JSON；无 file appender、无滚动策略。`backend/compose.yaml` 未设置 `logging: options: max-size/max-file`，Docker 默认 json-file 无上限（除非宿主 daemon 另行配置，仓库不可见）。`RequestIdFilter` 只把 requestId 写入 MDC/响应头（`RequestIdFilter.java:25-44`），没有访问日志（method/path/status/duration）。文档提到过“资源/IO 卡顿导致 Cloud Assistant 掉线”（`deployment.md:304`，`CHANGELOG.md:73`）。
- 风险：容器日志无限增长会写满根盘，导致 PG/Redis 写失败、容器重启；没有访问日志则无法从日志侧计算 5xx/P95、无法按 requestId 复盘完整请求；JSON 日志也没有 `service/env/version` 之外的请求维度。
- 建议：生产 Compose 给 app/web/nginx 配 `json-file` `max-size: 10m`、`max-file: 5`；宿主 Nginx 开 `access_log` 并记录 `$request_id`、`$upstream_response_time`；应用增加一个结构化访问日志（或依赖 Micrometer HTTP server metrics + Prometheus）；增加磁盘使用率告警与日志采集（Loki/Vector，二选一）。

#### P1-4 应用启动内联 Flyway 与 26,385 行逐行题库导入，发布即长时间停机且无回滚

- 证据：Flyway 随应用启动（`application.yml:30-33`）；`QuestionBankImporter` 是 `ApplicationRunner`，启动时逐行读取 JSONL 并逐条 `INSERT ... ON CONFLICT`（`QuestionBankImporter.java:63-100,140-154`），导入器无 `@Transactional`/批处理，等于 26,385 次自动提交与索引维护（含 2 个 trigram GIN、1 个 tags GIN）。`oc.qbank.import-path` 未出现在 `application.yml`，只在 `@Value` 里有空默认（`QuestionBankImporter.java:54`），文件缺失仅打 WARN（第 69-72 行）。`ci-cd.md:65` 声称迁移由独立 Job 执行，未实现；`Dockerfile` 无 HEALTHCHECK。
- 风险：每次重启/发布都要扫全文件并尝试 26k 次写，云盘上可能持续数十秒到数分钟；期间新 Pod/容器已监听端口但业务未就绪，健康检查/网关超时；Flyway 失败会让应用直接启动失败且没有独立迁移回滚路径；未来多副本会并发导入（唯一键兜底但放大 DB 压力）；qbank 环境变量漏配时静默空库，出题退化为模板。
- 建议：把导入改为独立 one-shot Job（管理端“导入批次”已有能力），启动路径仅在库为空且显式 `OC_QBANK_IMPORT_ON_BOOT=true` 时执行；使用 `COPY`/批量 `INSERT ... VALUES`（每批 500-1000）并包事务；迁移独立 Job + 发布前备份 + `flyway info` 门禁；启动就绪探针覆盖 qbank 导入完成；导入失败/跳过要 ERROR + 告警。

#### P1-5 题库检索退化：固定前 200 行 + `jobFamily` 恒为空，trigram 索引对用户检索无效

- 证据：`QuestionBankRetriever` 在岗位族为空或查询结果为空时退回 `selectPublished(200)`（`QuestionBankRetriever.java:44-47`），SQL 为 `WHERE status='PUBLISHED' ORDER BY id LIMIT 200`（`QuestionBankMapper.java:28-39`）；随后在 Java 内做关键词/bigram Jaccard 打分（`QuestionBankRetriever.java:68-99`）。岗位族来自 `jdStructured.get("jobFamily")`（`QuestionContextBuilder.java:36`），但 JD 结构化 schema 只有 `title/company/seniority/responsibilities/mustHave/niceToHave/techStack/keywords/confidence`（`JdStructurer.java:24-33`，`JdHeuristicStructurer.java:55-63`），从不产生 `jobFamily`。
- 风险：26k 题库在运行时只考虑按 id 排序的前 200 条（已核对 JSONL：前 200 条全部来自 `didilili/mianstack`），题目覆盖与岗位匹配严重失真；同一批题反复出现；`idx_question_stem_trgm`、`idx_question_title_trgm`、`idx_question_tags` 对用户检索完全未用，只增加导入写放大与磁盘；文档“题库占比 4/4”无法证明题目相关性。管理端关键词搜索才可能用到 title/stem trigram（`AdminQuestionRepositoryImpl.java:320`）。
- 建议：在 SQL 层检索：`WHERE status='PUBLISHED' AND (job_family = ? OR ...)` 并用 `stem ILIKE '%kw%'`/`similarity(stem, ?)` 排序，配合 trigram 索引；从 JD 标题/关键词映射出岗位族（映射表或 AI 输出增加 `jobFamily` 且校验枚举）；按 `embedding_status` 的语义要么实现向量检索，要么删除该字段与 Milvus 文档；对同一用户近期题目做去重/加权随机。若短期不改造，至少改为 `ORDER BY random()` 或按 `quality_score` 取样，避免固定 200 条。

#### P1-6 简历文件只存本地盘：未加密、无生命周期、删除不删文件、不在备份内

- 证据：`LocalFileStorage` 以 `${oc.storage.root}` 为根写本地文件（`LocalFileStorage.java:24-42`），生产根目录挂载 `/opt/offercoach/data`（`deployment.md:288,290`）；上传时 `fileStorage.store(fileKey, content)`，key 为 `resumes/{userId}/{sha256}.{ext}`（`ResumeService.java:117-118`）；软删只 `markDeleted`，不删除文件（`ResumeService.java:281-289`）；`data-model.md:690-699` 设计的 S3 私有桶/SSE-S3/预签名 URL 未实现；备份不含该目录（见 P0-2）。
- 风险：简历是强 PII，明文落云盘；用户删除后文件永久留存；PRO 计划 `unlimitedResume=true`（`V202611070002__seed_plans.sql:8`）但没有存储配额，磁盘可被单个用户填满；文件与 DB 生命周期不一致，恢复时会出现“有行无文件/有文件无行”。
- 建议：迁移到私有 OSS/S3（SSE-S3/KMS），DB 只存 key；下载改为 5-15 分钟预签名 URL；简历删除/到期任务同步删除对象；设置用户存储配额与单文件上限；对象存储开版本化 + 跨区复制；在应用层对文件做服务端加密或至少依赖云盘加密并记录。

#### P1-7 注销、导出、保留期删除均未实现，备份中的数据无法删除

- 证据：`UserController` 只有 `GET/PATCH /api/v1/users/me`（`backend/oc-identity/src/main/java/com/offercoach/identity/api/UserController.java:18-34`），无 `DELETE /me`/导出；管理端可编辑状态明确排除 `DELETED`（`AdminUserService.java:31-32,258-263`），尽管 DB 允许 `status='DELETED'` 且允许清空联系方式（`V202611070003__soft_delete_and_quota_ledger.sql:16-27`）。`data-model.md:701-716` 承诺的“注销 7 天后删除、简历 180 天、语音 30 天、AI 日志 90 天、审计 3 年、幂等 24h”没有对应 Job（全仓 `@Scheduled` 只有报告 worker 与会话放弃）。
- 风险：无法履行“删除权/可携带权”；用户注销只能靠人工 SQL，且不知如何删除备份；简历/JD/回答无期限保留，与隐私政策不一致；备份 7 天内仍含已删除用户数据；监管问询时无法给出删除证据。
- 建议：实现 `DELETE /api/v1/users/me`（冷却期 + 二次确认 + 邮件/验证码）与 `GET /api/v1/users/me/export`；注销流程匿名化 `app_user`、删除/脱敏 resume/JD/answer/feedback/report、删除对象存储、写审计；建立保留期 Job（每日扫描 `deleted_at`、`created_at`、`expires_at`）；备份侧定义“删除请求进入 7 天备份窗口”的对外说明与密钥销毁策略。

#### P1-8 生产凭据与运维信息写入仓库文档

- 证据：`deployment.md:282-297` 公开了实例 ID、公网 IP、域名、容器版本、数据/配置路径、演示账号与密码 `demo@<redacted> / <redacted>`（也在 `application.yml:88-92`）、管理员邮箱 `admin@<redacted>`、`OC_AI_SECRET` 文件路径；`deployment.md:291` 说明演示账号在 prod 通过 SQL 种子写入且额度为 30/9999。
- 风险：任何能读仓库的人都知道生产入口、管理员邮箱、演示账号口令与密钥文件位置；演示账号若仍有效，可被用于上传文件、消耗额度、探测系统；结合 P0-1 的无 Git 审计，无法知道谁改过这些内容。
- 建议：轮换/停用演示账号与文档中的口令；把运维手册中的敏感值替换为占位符，实际值放密码管理器/Secret Manager；管理员账号启用 MFA 与 IP 白名单；把“文档泄露凭据”纳入安全扫描（gitleaks/trufflehog）。

#### P1-9 AI 成本不可见、无预算熔断，SSE 可重放，缺少每用户并发限制

- 证据：`AiCallLogger` 只插 `scene/provider/model/status/tokens_in/tokens_out/...`（`AiCallLogger.java:34-50`），但 FEEDBACK 与 QUESTION_GEN 调用处把 tokens 固定写 0，FEEDBACK 状态固定为 `DEGRADED`、QUESTION_GEN 按“AI 是否 available”而非真实调用结果写 SUCCESS/DEGRADED（`FeedbackService.java:124-134`，`QuestionGenerationWorker.java:146-156`）；简历解析、JD 结构化、JD 匹配的 AI 调用（`ResumeStructurer.java:121`，`JdStructurer.java:85`，`JdMatcher.java:70`）完全不写日志。全仓没有 cost/budget/熔断实现。SSE 令牌在 5 分钟内可重复使用（`FeedbackStreamService.java:49-59` 只 GET 不消费），每用户并发未限制（`RedisKeys` 无 stream-active key），全局只有 8 个 SSE 线程（见 P2-3）。
- 风险：AI 开启后无法按 scene/用户核算成本，无法发现异常用户；单用户可对同一答案反复建流或在 5 分钟内并发建流，占满 8 个线程并放大模型费用；文档承诺的“单场/用户日/全站小时三级预算熔断”不存在，账单只能事后看到。
- 建议：所有 AI 场景统一走一个 `AiCallRecorder`，记录 usage、cost_micros、request_id、prompt_version、latency、degraded；token 计费按模型单价表换算；实现 Redis 计数器（用户日/全局小时）与熔断阈值；stream token 改为一次性（GETDEL）或绑定 `answerId+一次性 nonce`；每用户最多 2 条活跃流（`SET NX` + TTL）；SSE 增加心跳与断流计数。

#### P1-10 额度/订阅语义错误：月度额度不重置、订阅不过期、跨日按 UTC

- 证据：免费/Pro 额度在注册时一次性写入并记 `PLAN_GRANT` 流水（`QuotaService.java:52-95`），此后只有每日会话计数的懒重置（`QuotaService.java:109-131`），没有按 `period_end` 重置月度额度的任务；`user_subscription` 表无任何代码引用，没有到期扫描；`LocalDate.now()` 使用 JVM 默认时区（`QuotaService.java:61,102,180`），而生产 JVM 强制 UTC（`oc-app/Dockerfile:18`），`app_user.timezone` 未参与计算。`oc:quota:cache` 只 delete、从未写入（`QuotaService.java:277-283`），`oc:quota:daily` 常量未使用（`RedisKeys.java:45-47`）。
- 风险：用户消耗完 3 次免费额度后永远不再获得“每月 3 次”；订阅到期不会降级；北京时间 0-8 点的“今日”实际是 UTC 前一天，每日 3 次上限在早晨 8 点重置，与用户认知不符；缓存层是死代码，文档承诺的 Redis 快速拒绝不存在。
- 建议：增加每月额度重置 Job（按用户时区计算 `period_start/period_end`，写 `PLAN_GRANT` 流水，幂等键带周期）；订阅到期 Job 降级为 FREE 并保留次数包；统一使用 `ZoneId.of(user.timezone)` 计算业务日；删除或实现 quota cache（读路径先查 Redis，写路径删缓存），并让 Redis 日计数与 DB 对账。

#### P1-11 单实例、无蓝绿/停机窗口、无资源限制与 staging

- 证据：生产为单 ECS、单 app 容器、单 web 容器（`deployment.md:282-289`），无 staging（环境矩阵 `deployment.md:9-14` 声称有，但无对应清单/配置）；仓库 Compose 无 `deploy.resources`、无 app 健康检查、无日志上限（`compose.yaml:10-148`）；文档的 K8s 副本/HPA/PDB/灰度（`deployment.md:171-208`）不可执行；文档记录的 1 GB swap 与“资源/IO 卡顿导致重启”（`CHANGELOG.md:73`，`deployment.md:304`）说明单机已发生过容量事件。
- 风险：任何发布都是停机（`compose up -d` 重建容器 + Flyway + 26k 导入），没有回滚窗口；单实例无冗余，PG/Redis/app 同机争抢 CPU/IO；无法在发布前验证迁移；资源上限缺失时一个 SSE/AI 峰值或导入可拖垮整机。
- 建议：短期用“双 app 容器 + Nginx upstream 切换”实现蓝绿/滚动（8081a/8081b），发布前先在 staging（可用同一 ECS 的第二套 Compose + 独立 DB/Redis）跑迁移与 smoke；为 app/web/PG/Redis 设 CPU/内存上限与保留；把 26k 导入移出启动路径（P1-4）；压测后给出单实例容量上限与扩容阈值。

### 3.3 P2

#### P2-1 已认证请求逐次访问 Redis，角色/状态变更不吊销既有 Token

- 证据：`TokenBlacklistValidator` 对每个 JWT 执行 `hasKey`（`TokenBlacklistValidator.java:37-45`），Redis `timeout: 2s`（`application.yml:24-29`）；`AdminUserService.updateStatus/updateRole/resetPassword` 只改 DB 与审计，不写黑名单（`AdminUserService.java:73-160`）。
- 风险：每个 API 都多一次网络往返，Redis 抖动直接放大 P95；禁用/降权用户最多 15 分钟仍可用旧 access token（refresh 可能续签新角色）；管理员重置密码后旧会话不失效。
- 建议：黑名单加本地 Caffeine 缓存（TTL 数秒）或改为 user-level `token_version` 校验（JWT 带 `ver`，DB/Redis 版本不符即拒绝）；状态/角色/密码变更时递增版本并吊销 refresh 家族；把 Redis 超时与熔断指标接入告警。

#### P2-2 Refresh family 是无界 Set + 滑动 TTL，吊销用 `SMEMBERS` 全量删除

- 证据：每次签发都 `SADD` 到 `oc:auth:refresh-family:{familyId}` 并把 TTL 重置为 7 天（`RefreshTokenService.java:102-118`）；每个被消费 token 还写一个 7 天的 `refresh-used` 标记（第 65-70 行）；吊销时 `SMEMBERS` 取全家族再批量 `DEL`（第 120-129 行）。
- 风险：活跃用户每 15 分钟刷新一次，单家族 7 天可累积约 672 个成员；Set 永不收缩，内存随刷新次数增长；`SMEMBERS` 在家族异常大时会阻塞 Redis；家族键在最后一次签发后 7 天才过期，登出后仍残留。
- 建议：家族只保留“当前 token hash + 上一代 hash”，轮换时删除旧成员；限制每家族最大成员数；吊销改用 `SSCAN`/分批删除；为 family/used 键设置绝对上限与清理任务；在 Redis 监控中加入 `bigkeys`/`MEMORY USAGE` 巡检。

#### P2-3 SSE 容量与安全细节：全局 8 线程、无心跳、token 在 query、重连无换票

- 证据：`sseExecutor` core 2/max 8/queue 64（`AsyncConfig.java:19-28`）；SSE 超时 300s（`FeedbackStreamController.java:23`），token 放在查询参数（第 39 行）；token 只读不消费（`FeedbackStreamService.java:49-59`）；无 `event: ping`/心跳；`chunk` 用 `Thread.sleep(18ms)` 降级输出（`FeedbackStreamService.java:145-157`）；前端注释称“断线重连前重新申请 streamToken”，但没有换票端点（`frontend/src/api/stream.ts:46,78`）。
- 风险：8 条并发流即占满线程池，后续请求排队甚至 `RejectedExecutionException`（500）；单用户可重放 token 占满全局容量；无心跳时中间代理可能在 60s 空闲断开；token 出现在 Nginx access log/Referrer 中；重连超过 5 分钟无法重新开流。
- 建议：线程池与 Tomcat 线程隔离并按实例配置上限；每用户并发 2（Redis 计数器）；token 一次性或短 TTL + `GETDEL`；增加 15-30s 心跳事件；提供 `POST /answers/{id}/stream-token` 换票；token 改放 Header/短期 Cookie；Nginx 对 `/api/v1/ai/` 关闭 `proxy_buffering` 并设 `proxy_read_timeout 300s`。

#### P2-4 管理端指标每次请求 8 次全表 `COUNT`，无缓存、缺时间索引、口径未定义

- 证据：`AdminMetricsRepositoryImpl.overview` 串行执行 8 个查询，包括 `COUNT(*) FROM interview_session WHERE created_at >= ...`、`interview_report`、`ai_call_log`、`COUNT(DISTINCT user_id)` 的 UNION（`AdminMetricsRepositoryImpl.java:21-76`）；`AdminMetricsService` 直接透传无缓存（`AdminMetricsService.java:20-22`）。迁移只在 `interview_session(user_id, created_at)`、`interview_report(user_id, created_at)` 建索引（`V202611070004__resume_jd_interview_report.sql:86,186`），没有单独的 `created_at` 索引；`activeUsersToday` 把“登录过”也算活跃。
- 风险：数据增长后管理端首页变慢并给 PG 增加 8 次扫描；同一指标在不同页面可能口径不一致；无法回答“昨日/上周趋势”，只能看窗口总数。
- 建议：建 `interview_session(created_at)`、`interview_report(created_at)`、`app_user(created_at)` 索引（`CREATE INDEX CONCURRENTLY`）；增加 60s Redis/本地缓存与“按日预聚合表”；明确并文档化指标口径（活跃=登录 or 会话）；给管理端指标加 `range` 校验与查询超时。

#### P2-5 AI 调用日志与管理口径失真

- 证据：见 P1-9；此外 `ai_call_log` 无 `cost_micros/request_id/trace_id/prompt_version_id/experiment_id`（`V202611070004__resume_jd_interview_report.sql:242-255`，`data-model.md:510` 也承认简化）；`user_id` 无外键/索引（同文件 244、257-258）。`AdminAiCallRepositoryImpl` 支持按 `userId` 过滤但无索引（`AdminAiCallRepositoryImpl.java:65-80`）。
- 风险：管理端“AI 失败率/调用量”只覆盖 2 个场景且 tokens 恒 0；无法按用户/版本核算；按用户查日志会全表扫描；成本告警没有数据。
- 建议：补列与索引（`(user_id, created_at)`、`(status, created_at)`）；所有场景统一记录 usage/cost；把 `ai_call_log` 按月分区并保留 90 天；管理端口径写进文档并在 UI 标注“仅统计已接入场景”。

#### P2-6 报告 worker 内嵌在 API 进程，单调度线程 + 5 秒 stale claim，独立 worker 模块为空

- 证据：`ReportGenerationWorker` 用 `@Scheduled(fixedDelay=5000)` 在应用内领取 `updated_at < now()-5s` 的 `GENERATING` 报告（`ReportGenerationWorker.java:34-49`）；`claim` 只更新 `updated_at`，生成过程中若超过 5 秒会被再次视为 stale（`InterviewReportMapper.java:57-73`）；与 `SessionIdleAbandoner`（`@Scheduled` 10 分钟，`SessionIdleAbandoner.java:40`）共用默认单线程调度器；`oc-report-worker` 模块只有 `package-info.java`。
- 风险：单实例下长任务会阻塞其他定时任务；未来多副本会重复生成同一报告（claim 窗口太短）；报告生成与 API 争抢线程/DB；文档中的队列/DLQ/worker 扩容（`observability.md:44,128-134`）不存在。
- 建议：把报告生成移到独立进程/容器（启用 `oc-report-worker`），用 SKIP LOCKED 领取 + 租约（lease_until）+ 心跳续租；stale 阈值改为大于最长生成时间；调度线程池显式配置；多副本部署前必须完成幂等领取。

#### P2-7 文档拓扑与真实拓扑漂移，导致 Runbook 不可执行

- 证据：文档主体为 K8s/Helm、3 副本、HPA、PDB、Ingress 注解（`deployment.md:4,171-208`），实际单 ECS Compose + 宿主 Nginx（`deployment.md:282-306`）；`ci-cd.md:12,32-37` 的“生产金丝雀 10% 30 分钟”无法在单实例执行；`deployment.md:14` 的 staging 不存在。
- 风险：事故时按文档执行回滚/扩容会失败；新成员误以为有 PDB/多副本，做出错误容量假设；文档与代码的漂移会掩盖真实风险（例如“RPO 5min”“3 副本”）。
- 建议：把文档拆成“当前实现（As-Is）”与“目标架构（To-Be）”，Runbook 只写可执行步骤；所有命令以仓库中的 Compose/Nginx/脚本为准；每季度做一次文档-环境对账。

#### P2-8 宿主 Nginx、TLS 续期与 SSE 代理配置未入库，web 容器无安全头/健康检查

- 证据：文档称证书 Let's Encrypt 至 2027-02-05、Certbot 自动续期、每周一 cert-check 写日志（`deployment.md:285-296`），但仓库无 Nginx 配置、无 renewal hook、无 cert-check 脚本；`frontend/docker/default.conf:1-21` 只有静态文件与 gzip，没有 `proxy_buffering`、安全头、`client_max_body_size`；`frontend/Dockerfile` 无 HEALTHCHECK。
- 风险：SSE 经宿主 Nginx 时若未关闭缓冲/未放大 `proxy_read_timeout`，首字被缓冲、长流被 60s 切断；证书续期失败只有日志没有告警；缺少 HSTS/CSP/X-Frame-Options 等头；web 容器异常时 Nginx 返回 502 而无自动恢复判断。
- 建议：把宿主 Nginx site 配置、Certbot renewal、cert-check 脚本纳入仓库；对 `/api/` 设 `proxy_read_timeout 300s`、`proxy_buffering off`（仅 SSE 路径）、`client_max_body_size 12m`；加 HSTS/CSP/Referrer-Policy；证书到期 <14 天告警；web/app 容器加 HEALTHCHECK。

#### P2-9 PostgreSQL 未调优、无慢查询与统计扩展，应用账号是超级用户

- 证据：`compose.yaml:11-27` 用 `POSTGRES_USER=oc` 作为容器超级用户，未配置 `command`（shared_buffers/work_mem/max_connections/log_min_duration_statement 全默认），未启用 `pg_stat_statements`；应用 Hikari 最大 20（`application.yml:20-23`），无泄漏检测/指标告警；应用直接执行 Flyway DDL，违反“应用无 DDL 权限”的文档基线（`deployment.md:266`）。
- 风险：慢查询不可见，26k 导入/管理端 8 次 COUNT/检索 200 行都可能悄悄拖慢；连接耗尽无告警；应用被攻破即拥有 DDL/超级用户能力；`shared_buffers` 默认 128MB 在 26k+ 数据下偏小。
- 建议：拆分为 `oc_app`（DML only）与 `oc_migrator`（DDL）账号；设置 `shared_buffers≈25%`、`work_mem`、`maintenance_work_mem`、`max_connections`、`log_min_duration_statement=200ms`；启用 `pg_stat_statements` 与 `auto_explain`（采样）；Grafana 增加连接池/慢查询面板；Flyway 用独立账号与独立 Job。

#### P2-10 供应链：Spring Boot 3.3.5（2024-11）已过维护期，基础镜像未固定 digest，无 SCA/SBOM/签名

- 证据：`backend/pom.xml:9-10` Spring Boot 3.3.5；`oc-app/Dockerfile:1` `eclipse-temurin:21-jre` 未 pin digest；`frontend/Dockerfile:1` `nginx:1.27-alpine`；`compose.yaml:69` `minio/minio:latest`；无 Dependabot/Renovate/Trivy/Syft/cosign 配置（P0-1）。
- 风险：已知 CVE 不会自动修复；`latest` 与浮动基础镜像导致构建不可复现、回滚不可信；无 SBOM 无法在漏洞爆发时快速定位受影响镜像。
- 建议：升级到受支持的 Spring Boot 3.5/4.x（先跑契约与集成测试）；所有基础镜像 pin digest；CI 加 Trivy + OSV + `mvn dependency-check`，High/Critical 阻断；生成 CycloneDX SBOM 并对 release 镜像 cosign 签名；`latest` 只允许本地。

#### P2-11 上传/下载安全细节：只校验扩展名与声明 MIME，下载整文件入内存，预签名 URL 名不副实

- 证据：上传只检查扩展名 `pdf/docx` 与 `Content-Type`（`ResumeService.java:98-103`），未做文件头嗅探/病毒扫描；`/file` 用 `ByteArrayResource` 一次性读入内存（`ResumeController.java:128-137`），最大 10 MB；`download-url` 返回固定相对路径 `/api/v1/resumes/{id}/file`，却声明 900s（`ResumeService.java:308-313`）。
- 风险：伪造扩展名/MIME 的恶意 PDF/DOCX 可进入解析器（PDFBox/POI），存在解析漏洞/zip bomb 风险；大文件并发下载占内存；前端以为拿到的是短时 URL，实际仍是长期 JWT 受保护路径，容易误导。
- 建议：用 Apache Tika/文件魔数校验真实类型；在上传后异步跑 ClamAV；限制 PDF 页数/解压大小与解析超时；下载改为对象存储预签名 URL（真正短时）或流式输出；记录下载审计。

#### P2-12 数据保留与归档全部未实现

- 证据：`data-model.md:701-716` 定义了会话/回答/报告 24 个月、简历 180 天、语音 30 天、AI 日志 90 天、审计 3 年、幂等 24 小时；代码中没有任何清理/归档任务（全仓 `@Scheduled` 仅 `ReportGenerationWorker` 与 `SessionIdleAbandoner`），`ai_call_log` 为非分区普通表（`V*.sql:242-255`），`admin_audit_log` 无归档，`idempotency_record/outbox/notification` 表本身未创建。
- 风险：PII 与日志无限增长；磁盘与备份持续膨胀；无法证明“到期删除”；审计数据没有冷备，3 年后仍占主库。
- 建议：按月分区 `ai_call_log`（pg_partman 或自建 Job），90 天后 detach 到 OSS；会话/报告 24 个月后导出冷备并删除明细；审计日志 3 年后归档；简历文件 180 天删除；为每个保留策略写 Job + 审计 + 告警；幂等记录若实现则 24h 清理。

#### P2-13 题库导入缺许可/质量/大小门禁，MANUAL 去重失效，唯一索引含 TEXT 有行宽风险

- 证据：`QuestionBankImporter` 只校验维度/难度/非空 stem（`QuestionBankImporter.java:113-131`），不校验 `sourceLicense`、长度上限、答案质量，导入即 `PUBLISHED`、`embedding_status='READY'`（`QuestionBankMapper.java:41-54`）；管理端手工创建把 `source_repo/source_path` 置 NULL（`AdminQuestionRepositoryImpl.java:113-125`），而唯一索引 `(source_repo, source_path, stem_hash)` 对 NULL 不去重（`V202611070005__question_source_stem_unique.sql:5-9`）；`source_path TEXT` 参与 btree 唯一索引，超长路径可能触发索引行宽错误。
- 风险：手工题可无限重复；无许可来源可被人工/脚本导入并发布；空答案/低质量题进入用户面；单行超长路径导致导入批次失败；`embedding_status=READY` 误导管理端。
- 建议：导入 Job 强校验许可白名单、stem/答案长度、重复率；MANUAL 题使用稳定 `source_path`（如 `manual:{uuid}`）或单独唯一键；`source_path` 改 `VARCHAR(512)` 或对唯一键用 `md5(source_path)`；`embedding_status` 在无向量时置 `PENDING` 或删除该字段。

#### P2-14 管理端权限模型不一致、无 MFA/IP 白名单、bootstrap 会自动提升既有账号

- 证据：URL 层允许 `ADMIN/SUPPORT` 访问 `/api/v1/admin/**`（`SecurityConfig.java:54-55`），但每个 Controller 类级 `@PreAuthorize("hasRole('ADMIN')")`（`AdminUserController.java:33` 等），SUPPORT 实际不可用；文档称“IP 白名单 + MFA（V1.1）”（`deployment.md:268`）未实现；`AdminDataInitializer` 在启动时把同邮箱的既有账号直接提升为 ADMIN（`AdminDataInitializer.java:59-64`），不校验邮箱验证/来源。
- 风险：角色语义混乱（SUPPORT 名存实亡）；管理员仅靠密码，凭证文件与 DB 同机；如果 `ADMIN_EMAIL` 配成一个普通用户邮箱，会在下次重启被静默提权。
- 建议：明确 SUPPORT 只读/限定端点，或删除该角色；管理端加 MFA（TOTP）与 IP 白名单/ VPN；bootstrap 只创建新账号，不提升既有账号，或需显式 `ADMIN_PROMOTE=true` + 审计告警；管理员登录单独限流与告警。

### 3.4 P3

#### P3-1 Schema 与文档漂移、死字段

- 证据：`data-model.md` 中的 `payment_order`（167-183）、`outbox_event/idempotency_record/notification/audit_log`（544-600）、`interview_report.pdf_key/pdf_expires_at`（379-380）均未在迁移中创建；实际 `interview_report` 无 PDF 字段（`V202611070004:166-184`）；`interview_answer.audio_key`（122）无音频链路；`app_user.phone/phone_hash`（V1:7-8）未使用；`user_quota.version`（V1:45）只自增、不参与乐观锁；`failed_login_count/locked_until`（V1:19-20）不参与登录锁定（仅管理端解锁时清零）；`interview_session.current_seq/expires_at` 使用不完整。
- 风险：读者按文档建表/写接口会踩空；死字段增加维护成本与误解；报告 PDF 能力被文档高估。
- 建议：以实际迁移为准重写 data-model 的“实现状态”列；删除或标注“规划”；每个字段注明读写方；CI 增加迁移与文档 DDL 的 diff 检查（如 `schemaspy`/自定义脚本）。

#### P3-2 迁移治理：草稿重复迁移、target 旧副本、非 CONCURRENTLY、迁移与应用耦合

- 证据：`backend/.drafts/m5-duplicate-migrations/V202611070006__admin_console.sql` 与正式 V6 同版本号；`oc-app/target/classes/db/migration/*` 保存旧副本；V5 直接在 26k 表上建唯一索引/生成列，未用 `CONCURRENTLY`（`V202611070005:5-9`）；Flyway 随应用启动（`application.yml:30-33`）。
- 风险：误把草稿放入 classpath 会导致 Flyway 校验失败/重复执行；target 旧副本进入镜像会造成版本混淆；大表后续加索引会锁表；迁移失败阻断应用启动。
- 建议：删除 `.drafts` 或移出构建上下文；`.dockerignore` 排除 `target/classes`；新增大表索引一律 `CONCURRENTLY`（拆到独立迁移）；CI 检查迁移文件命名/重复版本/不可变校验和。

#### P3-3 AI 配置与密钥治理：无历史版本、固定 kid、无出站白名单

- 证据：`ai_runtime_config` 单行覆盖，仅靠 `admin_audit_log` 留 before/after（`V202611080001:2-16`，`AdminAiConfigService.java:76-85`）；`application.yml:68` `key-id: oc-local` 固定，`JwtKeyProvider` 单密钥无轮换；管理端允许任意 https `baseUrl`（`AiRuntimeConfigService.java:178-202`），没有出站域名白名单。
- 风险：无法回溯“某天用的是哪个模型/URL”；密钥无法平滑轮换；被攻破的管理员可把 AI 出站指向任意域名，带走简历/JD 原文。
- 建议：`ai_runtime_config` 增加版本表/历史表与 `effective_from`；JWT 支持多 kid 与双密钥轮换；AI base URL 加域名白名单与 DNS/IP 校验；密钥 90 天轮换并审计。

#### P3-4 暴露面与部署冗余：gateway 生产未用、Actuator/Swagger permitAll、无 HSTS/CSP

- 证据：生产宿主 Nginx 直接把 `/api/` 转 app 8081（`deployment.md:286`），gateway 8080 未部署；`SecurityConfig.java:56-61` 对 `/actuator/health`、`/actuator/prometheus`、`/v3/api-docs/**`、`/swagger-ui/**` `permitAll`；`frontend/docker/default.conf` 无 HSTS/CSP/X-Frame-Options。
- 风险：一旦 8081 被误映射到公网，Prometheus/Swagger 直接暴露；gateway 代码成为无人维护的死组件；浏览器侧缺少纵深防御。
- 建议：生产关闭 swagger 或仅内网；Actuator 加独立管理端口 + 内网/认证；删除或明确 gateway 的定位；宿主 Nginx 统一加安全头与访问控制。

#### P3-5 Redis Key 规范漂移与死键

- 证据：`RedisKeys` 定义了 `quota:daily`、`resumeReparse`（`RedisKeys.java:45-47,54-57`），但 `ResumeService` 另写死 `oc:resume:reparse:` 前缀（`ResumeService.java:50-52`）；`quota:cache` 从未写入；文档中的 `oc:stream:active`、`oc:cost:*`、`oc:ab:*` 等 key 未实现（`data-model.md:603-631`）。
- 风险：key 规范失效，运维无法按前缀审计/清理；成本与并发控制缺少数据载体。
- 建议：所有 key 必须经 `RedisKeys`；删除死常量或实现对应功能；建立 key 清单（前缀/TTL/大小/所有者）并纳入巡检。

#### P3-6 题库导入路径静默缺失、批次表无索引

- 证据：`oc.qbank.import-path` 未在 `application.yml` 声明（仅 `@Value` 空默认，`QuestionBankImporter.java:54`），文件不存在只 WARN（69-72）；`question_import_batch` 无任何索引（`V202611070004:227-240`），管理端按时间列表会全表扫。
- 风险：环境变量漏配时生产空题库，只有 debug 日志；批次列表随启动次数增长变慢。
- 建议：把该配置写入 `application.yml` 并在 prod 启动校验非空/文件存在（缺失即失败或显式关闭）；为 `question_import_batch(created_at DESC)` 建索引；批次写入只在有变更时执行。

---

## 4. 专题核对

### 4.1 PostgreSQL 架构与索引

**表与约束（已核对）**

| 主题 | 实现 | 评价 |
|---|---|---|
| 主键/类型 | `BIGINT GENERATED ... IDENTITY`；时间 `TIMESTAMPTZ`；分数 `NUMERIC`；金额分 | 符合 data-model 规则 |
| 枚举 | `VARCHAR + CHECK`（角色、状态、维度、难度、来源、场景等） | 可演进，但新增码值需迁移 |
| 软删 | `app_user/resume/job_description.deleted_at` + 部分唯一索引（`V3:7-14`，`V4:35-39`） | 正确；查询均带 `deleted_at IS NULL` |
| 唯一/幂等 | 邮箱/手机号部分唯一；`(user_id, idempotency_key)`；`(session_id, seq)`；`(answer_id, version)`；`request_id` 唯一；`uq_session_active`；`uq_user_active_subscription`；`uq_prompt_version_published` | 关键不变量有 DB 兜底 |
| 外键/级联 | 会话→题目/回答 `ON DELETE CASCADE`；反馈/报告引用无级联 | 会话不物理删除，当前无害；若未来清理会因反馈外键失败 |
| JSONB | `structured_json/config/reference_points/dimensions/radar` 等整体读写；未建 GIN | 符合“不按内部字段高频过滤”原则；无 `jsonb_typeof` 校验，坏数据只能靠应用 |
| 数组 | `question_bank.tags TEXT[]` + GIN | 索引未被业务使用（见下） |
| `question_bank` 唯一键 | `(source_repo, source_path, stem_hash)`，`stem_hash=md5(stem)` 生成列（`V5:5-9`） | 去重思路正确；NULL source_path 不去重、TEXT 参与 btree 有行宽风险 |
| `ai_runtime_config` | 单行 `CHECK(id=1)`，API Key 密文，空字段回退 env（`V7`） | 简洁；无历史版本；列注释不全 |
| admin 表 | `admin_audit_log`（before/after/request_id/ip），`ab_experiment/ab_assignment`，`prompt_template/version` | 审计覆盖写操作；`ip` 从未写入；无归档 |

**索引盘点**

- 有用且经常命中：`resume(user_id, created_at) WHERE deleted_at IS NULL`、`uq_resume_file/default`、`jd(user_id, created_at)`、`session(user_id, created_at)`、`uq_session_active`、`answer(session_id, submitted_at)`、`feedback(answer_id, version)`、`report(user_id, created_at)`、`report_pending`、`question_filter`（部分场景）、`prompt_version(template_id, status)`、`admin_audit(created_at/admin,created_at)`。
- **未使用/低效**：`idx_question_tags`、`idx_question_company`（无查询使用）；`idx_question_stem_trgm`、`idx_question_title_trgm` 只可能被管理端 `ILIKE` 使用，用户检索不用；`idx_question_filter` 因检索用 `lower(job_family)` 且 `ORDER BY id` 基本失效；`idx_feedback_answer(answer_id, version DESC)` 与唯一约束 `(answer_id, version)` 重复。
- **缺失**：`interview_session(created_at)`、`interview_report(created_at)`、`app_user(created_at)`（管理端指标）；`ai_call_log(user_id, created_at)`（管理端按用户过滤）；`question_import_batch(created_at)`；若实现保留任务，还需要 `resume(deleted_at)`、`interview_report(status, updated_at)`（已有 partial）。
- **建议 SQL（独立迁移、CONCURRENTLY）**：

```sql
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_session_created ON interview_session (created_at);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_report_created ON interview_report (created_at);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_user_created ON app_user (created_at);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_ai_call_user_time ON ai_call_log (user_id, created_at DESC);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_qbank_import_time ON question_import_batch (created_at DESC);
-- 若保留 trigram 检索：
-- CREATE INDEX CONCURRENTLY ... ON question_bank USING GIN (stem gin_trgm_ops);
```

**慢查询/容量风险**

- 管理端 `overview` 串行 8 个聚合：数据量到 10 万级后每次请求秒级；建议预聚合。
- 题库检索 `ORDER BY id LIMIT 200` 稳定但选择性差；若改 `ORDER BY random()` 会在 26k 上排序，需配合过滤条件。
- `ai_call_log` 每个回答写 1 行 + 每个会话 1 行，非分区，随使用线性增长。
- 备份 `pg_dump` 期间与业务写并发，需关注 I/O；无 WAL 归档时 dump 是唯一恢复点。

### 4.2 Redis

**Key 清单（实际实现）**

| Key | TTL | 实际用途 | 风险 |
|---|---|---|---|
| `oc:auth:refresh:{sha256}` | 7d | 刷新令牌→`userId:familyId:jti` | LRU 可驱逐；单点 |
| `oc:auth:refresh-family:{familyId}` | 7d（每次续签重置） | Set 存全部 token hash | 无界增长；`SMEMBERS` 吊销 |
| `oc:auth:refresh-used:{sha256}` | 7d | 重放检测 | 7 天内 key 数量随刷新增长 |
| `oc:auth:blacklist:{jti}` | 剩余时效 | 登出/改密 | 每请求查；LRU 可驱逐 |
| `oc:login:fail:acct:{account}` | 15m | 失败锁定 | fail-open（Redis 故障放行） |
| `oc:ratelimit:login/register:{ip}` | 60s | IP 限流 | fail-open |
| `oc:ai:stream:{token}` | 300s | SSE 令牌 | 可重放；query 参数 |
| `oc:resume:reparse:{userId}:{resumeId}` | 1h | 重解析限流 | 前缀硬编码，未走常量 |
| `oc:quota:cache:{userId}` | 60s（文档） | 仅 delete | 死代码 |
| `oc:quota:daily:{userId}:{date}` | 2d（文档） | 未使用 | 日计数只在 DB |

- **内存策略**：`allkeys-lru` 对“认证状态”是错误的；应 `noeviction` 或分离实例；AOF 在该策略下也只能保证已写数据，不能防驱逐。
- **大 key**：family Set 是唯一潜在大 key；按 15 分钟刷新/7 天 TTL，单家族可达数百成员；`SMEMBERS` + 批量 `DEL` 是 O(N) 阻塞命令。
- **故障降级**：限流 fail-open（可用性优先）、黑名单 fail-closed（安全优先）组合结果是“Redis 故障=全站 401”；额度快照不依赖 Redis（好），SSE 令牌依赖 Redis（评分流不可用）。
- **缓存一致性**：额度缓存只删不读，当前无一致性问题；若未来实现读缓存，必须继续“先写 DB 后删缓存”，并接受 60s 窗口。
- **建议**：认证/缓存分实例；认证实例 `maxmemory-policy noeviction`、`appendfsync everysec`、副本 + Sentinel；对 family 做上限与增量清理；黑名单本地缓存；监控 `evicted_keys`、`used_memory`、`blocked_clients`、`latency`。

### 4.3 备份与恢复矩阵

| 组件 | 文档承诺 | 实际情况 | 差距 |
|---|---|---|---|
| PostgreSQL | 每日全量 + WAL，PITR 7 天，RPO 5min，RTO 1h | 每日 03:15 本机 dump，保留 7 天（`deployment.md:295,303`） | 无 WAL/PITR；RPO 24h；单机；无演练 |
| 备份加密 | 备份加密（`deployment.md:267`） | 未见脚本，dump 未加密 | PII 暴露风险 |
| 异地/双副本 | MinIO 跨区复制 | 无对象存储；dump 只在本机 | 单盘/单机即全失 |
| 简历文件 | 180 天生命周期 | 本地盘，无备份、无删除 | 恢复后大量 404 |
| 题库 JSONL | 可由源仓库重建 | `/opt/offercoach/data/qbank`，不在 dump | 依赖本机文件；源仓库可重建但耗时 |
| Redis | AOF everysec + 每日快照 | AOF，命名卷 | 认证状态丢失=全员重新登录 |
| 配置/密钥 | GitOps + Secret 备份 | `/opt/offercoach` 600 文件 | 未纳入仓库/备份，轮换无记录 |
| 恢复演练 | 每季度一次 | 无 | 恢复能力未知 |

**建议目标**：先达到“每日加密异地 dump + 简历文件异地 + 月度恢复演练”，再升级到 WAL/PITR；备份失败、备份文件校验失败、备份年龄超限三类告警必须上线。

### 4.4 部署拓扑（As-Is）

| 项 | 实际 |
|---|---|
| 计算 | 单台 ECS `<ECS_INSTANCE_ID>`，cn-beijing，Ubuntu 22.04（`deployment.md:282`） |
| 入口 | 宿主 Nginx 80/443，`/`→127.0.0.1:8082，`/api/`→127.0.0.1:8081（`deployment.md:286`） |
| 应用 | `offercoach-app:0.4.0`、`offercoach-web:0.4.0`；`restart=unless-stopped` |
| 数据 | 命名卷 `pgdata`、`redisdata`；`/opt/offercoach/data`→`/data`（UID 10001）；5432/6379 仅 127.0.0.1 |
| TLS | Let's Encrypt，有效期至 2027-02-05，Certbot 自动续期（文档） |
| 资源 | 1 GB swap；无 CPU/内存上限；无 app 健康检查；无日志上限 |
| 发布 | 手工构建镜像 + `compose up -d`；无蓝绿/灰度；迁移随启动 |
| 监控 | 无 Prometheus/Grafana/告警 |
| 备份 | 本机每日 03:15，7 天 |

**与文档目标（K8s 3 副本/HPA/PDB/金丝雀/staging）差异巨大**，见 P2-7。

### 4.5 可观测性覆盖

| 信号 | 现有 | 缺口 |
|---|---|---|
| HTTP RED | Micrometer 默认 `http_server_requests`，`/actuator/prometheus` | 无抓取、无看板、无 5xx/P95 告警；无访问日志 |
| JVM | 默认 JVM/GC/线程指标 | 无堆/GC 告警；无堆外/容器内存 |
| PG | Actuator health | 无连接池、慢查询、锁等待、复制延迟 |
| Redis | Actuator health | 无内存/驱逐/命中率/延迟 |
| SSE | 无 | 无首字、活跃连接、断流、队列深度 |
| AI | `ai_call_log`（2 场景、tokens=0） | 无 cost、request_id、prompt_version、失败率告警 |
| 业务 | 管理端按需 SQL | 无漏斗、留存、报告成功率、额度拒绝 |
| 日志 | JSON stdout + requestId/traceId/userId | 无 access log、无采集、无轮转、无保留策略 |
| Trace | 生成/透传 traceId | 无 OTel SDK/Collector/后端；无 Span |
| 告警 | 无 | 文档 10 条告警规则全部未实现；证书/备份/磁盘尤其空白 |
| 管理端口径 | `overview` 8 个 COUNT | 无口径文档、无缓存、无趋势、活跃定义含登录 |

### 4.6 成本与容量

**已知量**

- 题库：26,385 条，JSONL 27.9 MB；平均约 1.06 KB/条，`reference_answer` 占大头。
- 索引：2 个 trigram GIN + 1 个 tags GIN + 2 个 btree（filter/company）+ 唯一 btree；导入每行都要维护，运行时用户检索不用前三个。
- SSE：全局 8 线程、队列 64、单流最长 300s；按 60s/流估算，单实例评分流并发上限 8。
- 报告：单线程调度，每 5s 领 5 条；生成是确定性聚合（`ReportService.java:120-178`），未做并发压测，耗时应远小于 AI 场景，但会与 API 争抢同一进程资源。
- DB 连接：Hikari 20；PG 默认 `max_connections=100`。
- 磁盘：简历 10 MB/份；FREE 3 份/人；PRO `unlimitedResume=true` 无存储配额；dump 7 天；Redis 512 MB；AOF 与 volumes 同盘。
- AI：仅在 FEEDBACK/QUESTION_GEN 记录且 tokens=0；RESUME_PARSE/JD_ANALYZE/JD_MATCH 无记录；无预算。按单次评分 Prompt 估算（题目+参考点+最多 3,500 字回答 ≈ 1-2k input tokens，输出 500-800），4 题/场，成本随供应商单价线性增长，当前无法核算。

**容量结论**

- 当前数据规模（26k 题库、少量用户）不是瓶颈；瓶颈是**单实例应用 + 8 条 SSE + 26k 启动导入 + 管理端全表扫描**。
- 真正的容量风险是“无计量、无上限”：PRO 无限简历、AI 无预算、SSE 无每用户限制、日志/备份无轮转，任一滥用都可打满磁盘/内存/线程。
- 压测缺口：仓库只有 smoke（登录/简历/JD/4 题/SSE/报告），无 k6/JMeter 脚本、无 SLO 压测报告、无并发/长流/大文件/管理端指标场景。

**建议容量基线（待压测验证）**

- 单实例目标：100 并发在线、20 并发评分流（需要扩 SSE 线程/多实例）、P95 API < 500ms、SSE 首字 P95 < 1s。
- 以 2 实例 + 负载均衡为 90 天目标；在此之前给出“超过 X 用户/日 Y 场”的扩容触发条件。

### 4.7 供应链与许可

- 代码/镜像：无 Git/CI（P0-1）；Spring Boot 3.3.5（2024-11）已非当前受支持线；基础镜像未 pin；`minio:latest`；无 SCA/SBOM/签名/不可变 tag。
- 许可证：MIT 22,409；GPL-3.0 3,393；CC-BY-NC-SA 428；CC-BY-NC-ND 155；`bat_code` 13 条全部被脚本跳过（`manifest.json`），但 `LICENSES.md` 仍列为高风险来源。
- 密钥：JWT PEM、`.admin-credentials`、`.ai-secret` 在主机 600 文件；无 KMS/Secret Manager、无轮换记录；`OC_AI_SECRET` 一旦丢失，库内 API Key 密文不可解（会回退空 Key，AI 降级）。
- 变更审计：`admin_audit_log` 覆盖管理端写操作（before/after/request_id），但不含 IP；基础设施变更（Compose/Nginx/环境变量）无审计；文档 `CHANGELOG.md` 是手工记录。

### 4.8 数据合规

- **PII 面**：`app_user.email/phone`、`resume.raw_text/structured_json/file`、`job_description.raw_text`、`interview_answer.answer_text`、`answer_feedback`、`interview_report.summary`。应用层仅对 `phone` 做 AES-GCM（且未使用），其余明文入库；简历文件明文落盘；备份未加密。
- **访问控制**：简历/JD/报告接口均按 `userId` 校验归属（`ResumeService.loadOwned` 等）；文件路径有 `normalize + startsWith` 防穿越（`LocalFileStorage.java:62-71`）；管理端只允许 ADMIN（方法级），但无 MFA/IP 白名单。
- **日志**：登录失败账号做掩码（`AuthAttemptService.java:136-146`）；未发现简历/回答正文进日志；但 IP、userId 在日志中无保留期；无访问日志导致无法做安全审计。
- **第三方 AI**：启用后会发送简历原文/JD 原文/回答正文（`ResumeStructurer.java:115-117`、`JdMatcher.java:60-64`、`FeedbackStreamService.java:101-103`），没有用户同意开关、没有 DPA/数据处理说明、没有“禁止原文出境”配置（历史契约中的 `allowRawResumeToLlm` 未实现）。
- **删除/导出**：未实现（P1-7）；备份中的数据删除无流程；题库版权与署名缺失（P0-3）。
- **建议**：做数据分类分级（P0=PII/简历，P1=回答/报告，P2=日志），为每类定义加密、保留、访问、导出、删除；AI 请求默认脱敏（姓名/电话/邮箱/公司）并提供开关；与模型供应商签 DPA；对外隐私政策写明第三方处理与保留期。

---

## 5. 30/60/90 天改进优先级

### 30 天（止血，目标是“可回滚、可恢复、可发现、可合规售卖”）

| # | 动作 | 关联 | 完成标准 |
|---|---|---|---|
| 1 | Git 私有仓库 + 当前状态基线 + 生产 Compose/Nginx/cron 入库（脱敏） | P0-1 | `git log` 可追溯；`prod-0.4.0` 标签存在；新成员可按仓库重建 |
| 2 | 最小 CI：后端 `mvn verify`、前端 `pnpm check`、镜像构建、Trivy/OSV、许可扫描 | P0-1/P2-10 | PR 必须绿；High/Critical 阻断 |
| 3 | 加密异地备份（PG + 简历文件 + qbank）+ 恢复演练 + 失败告警 | P0-2 | 从最新备份恢复到临时库并跑通全链路；RPO/RTO 有实测值 |
| 4 | 题库许可止血：非 MIT 全部下架/仅内部；导入白名单 | P0-3 | 用户检索 SQL 只返回 MIT；LICENSES 与署名页上线 |
| 5 | 轮换/停用演示账号与文档中的口令；清理文档敏感值 | P1-8 | 旧口令失效；gitleaks 无命中 |
| 6 | 监控最小集：Prometheus/Grafana/Alertmanager + 5xx/P95/磁盘/证书/备份/Redis 驱逐/PG 连接告警 | P1-2 | 告警能真实触发到值班渠道；有 Runbook 链接 |
| 7 | 日志轮转 + Nginx access log + SSE buffering/timeout 配置 | P1-3/P2-8 | 单容器日志有上限；SSE 经 Nginx 首字正常、300s 不断流 |
| 8 | 迁移/题库导入移出启动路径（独立 Job + 批量） | P1-4 | 重启 < 60s；qbank 导入可重跑、可观测 |
| 9 | 缺失索引 + 管理端指标缓存（先加 `created_at` 索引与 60s 缓存） | P2-4 | 管理端 overview P95 < 200ms |

### 60 天（可用性与数据生命周期）

| # | 动作 | 关联 | 完成标准 |
|---|---|---|---|
| 10 | Redis 认证/缓存分离、`noeviction`、副本/Sentinel；黑名单本地缓存 | P1-1/P2-1/P2-2 | 单 Redis 故障不再导致全站 401；无 evicted auth key |
| 11 | 对象存储（OSS/S3）+ SSE + 预签名 URL + 生命周期 + 删除联动 | P1-6/P2-11 | 简历不走本地盘；删除/到期即删对象；有下载审计 |
| 12 | 注销/导出/保留期 Job（会话/简历/AI 日志/审计/幂等） | P1-7/P2-12 | 注销 7 天内完成匿名化；到期数据可证明删除/归档 |
| 13 | 额度/订阅：月度重置、到期降级、用户时区、Redis 日计数对账 | P1-10 | 免费额度每月恢复；订阅到期自动降级；跨日按用户时区 |
| 14 | AI 计量与预算：全场景 usage/cost、用户日/全局小时熔断、一次性 stream token、每用户 2 并发 | P1-9/P2-3 | 成本可按用户/场景查询；超预算拒绝并告警 |
| 15 | 题库检索切到 SQL trigram（或向量）+ 岗位族映射；清理未用索引 | P1-5/P2-13 | 26k 题库参与检索；无固定 200 条；索引有 EXPLAIN 证据 |
| 16 | PG 调优 + `pg_stat_statements` + 应用最小权限账号 + 独立迁移账号 | P2-9 | 慢查询可见；应用无 DDL；连接/锁有看板 |
| 17 | staging（第二套 Compose + 独立 DB/Redis）+ 蓝绿/滚动发布 + 回滚演练 | P1-11/P2-7 | 发布前 staging 跑迁移/smoke；回滚 < 5 分钟 |
| 18 | 管理端 MFA + IP 白名单 + 角色模型收敛；审计补 IP | P2-14/P3-1 | 管理员登录需 MFA；SUPPORT 权限明确；审计可按 IP 查 |

### 90 天（韧性、合规与成本）

| # | 动作 | 关联 | 完成标准 |
|---|---|---|---|
| 19 | 2 实例 + LB（或 K8s），Redis HA，PG 备份/PITR，对象存储跨区 | P1-1/P0-2 | 单实例/单 AZ 故障可切换；RPO ≤ 15min、RTO ≤ 2h |
| 20 | 季度 DR 演练 + SLO/错误预算 + 容量压测（100 并发/20 SSE/大文件/管理端） | P1-2/P1-11 | 有演练报告与压测报告；扩容阈值明确 |
| 21 | SBOM + cosign + 依赖升级（Spring Boot 受支持版本）+ 不可变 tag | P2-10 | 发布镜像可验证签名；无 High/Critical |
| 22 | 数据分类分级 + 隐私政策/DPA + AI 脱敏与用户同意 | 4.8 | 对外文档齐备；AI 请求默认脱敏 |
| 23 | 题库许可复核与重建（法务签字）+ 署名/来源页 | P0-3 | 公开产物只含合规来源；有法务记录 |
| 24 | 成本模型：单场/单用户成本、毛利率、月度复盘 | 4.6 | 成本看板与预算告警上线 |

---

## 6. 已核对项（通过）

以下项经代码/迁移/配置核对，未发现需要立即整改的问题：

1. **时间/金额/枚举基础**：全库时间点 `TIMESTAMPTZ`、分数 `NUMERIC`、金额用分、枚举 `VARCHAR + CHECK`，无 `FLOAT` 结算字段（`V202611070001`、`V202611070004`）。
2. **软删与唯一键**：`app_user` 邮箱/手机号部分唯一、`resume` 文件/默认简历部分唯一，软删不占键；查询均带 `deleted_at IS NULL`（`V202611070003:4-14`，`V202611070004:35-39`，`ResumeMapper`）。
3. **活跃唯一性**：`uq_session_active`、`uq_user_active_subscription`、`uq_prompt_version_published` 正确限制“同一用户一个进行中会话/一个 ACTIVE 订阅/一个模板一个发布版本”（`V4:88-89`，`V1:105-106`，`V6:34-35`）。
4. **额度一致性**：扣减/返还使用 `SELECT ... FOR UPDATE` + 流水唯一键 `(user_id, idempotency_key)` + 余额非负 CHECK；事务内不吞唯一键冲突（`QuotaService.java:205-255`，`V1:54-66`）。
5. **题库去重**：`stem_hash = md5(stem)` 生成列 + `(source_repo, source_path, stem_hash)` 唯一索引 + `ON CONFLICT DO NOTHING`，重复导入幂等（`V5:5-9`，`QuestionBankMapper.java:53`）。
6. **AI Key 保护**：AES-GCM、12 字节随机 IV、`v1:` 前缀、根密钥 SHA-256 派生、未配置根密钥拒绝保存、管理端只返回掩码、审计不含明文（`SecretCipher.java:41-95`，`AdminAiConfigService.java:72-84`）。
7. **管理端审计**：用户状态/角色/解锁/重置密码/额度、题库、Prompt、实验、AI 配置均写 `admin_audit_log`，含 before/after/request_id，独立事务且失败不阻断主操作（`AuditLogger.java:28-48`，各 Service）。
8. **管理端权限**：所有 Admin Controller 类级 `@PreAuthorize("hasRole('ADMIN')")`，`@EnableMethodSecurity` 已开启（`SecurityConfig.java:28`，`AdminUserController.java:33` 等）；URL 层 `SUPPORT` 允许是冗余但未造成越权。
9. **生产安全 fail-fast**：prod 缺 JWT PEM、demo 开启、固定验证码开启、Cookie 非 Secure、CORS 含 localhost 任一即启动失败（`ProductionSafetyValidator.java:39-58`，`JwtKeyProvider.java:40-70`，`application-prod.yml`）。
10. **认证边界**：Refresh 家族轮换 + 重放整族吊销 + `GETDEL` 原子消费；登出/黑名单 Redis 故障 fail-closed；登录/注册 IP 限流与账号失败锁定（`RefreshTokenService.java:60-79`，`AuthAttemptService.java:92-110`）。
11. **SSE 鉴权**：JWT + 短期 stream token 双校验，token 绑定 `userId:answerId`，TTL 300s（`FeedbackStreamController.java:39-52`，`AnswerService.java:114-126`）。
12. **文件安全**：上传 10 MB、仅 pdf/docx、SHA-256 去重、`normalize + startsWith` 防路径穿越、按用户归属校验、进行中会话阻止删除（`ResumeService.java:91-148,281-289`，`LocalFileStorage.java:62-71`）。
13. **请求标识**：`X-Request-Id` 校验/生成/回写，`traceparent` 解析，MDC 清理；非 local JSON 日志含 requestId/traceId/userId（`RequestIdFilter.java:25-44`，`logback-spring.xml:15-27`）。
14. **错误处理**：未知异常统一 `INTERNAL_ERROR` 并记录堆栈，业务错误按 `ErrorCode` 返回，响应带 requestId/traceId（`GlobalExceptionHandler.java:27-90`）。
15. **容器基础**：PG/Redis/MQ/MinIO 均有 healthcheck，Redis AOF，命名卷持久化，app 默认仅监听回环（`compose.yaml:21-26,43-48,60-65,79-84`，`application.yml:5`）。
16. **报告幂等**：`interview_report(session_id)` 唯一 + `INSERT ... ON CONFLICT`，领取用条件更新，失败重试有上限（`InterviewReportMapper.java:26-32,66-121`）。
17. **会话/题目状态机**：`UNIQUE(session_id, seq)`、`(session_id, question_id)` 唯一回答、状态 CHECK、24h 空闲放弃任务（`V4:91-135`，`SessionIdleAbandoner.java:40-45`）。
18. **题库来源可追溯**：`source_repo/source_path/source_url/source_license` 入库，JSONL manifest 记录每仓库 kept/skipped 与许可（`data/question-bank/LICENSES.md`、`manifest.json`）。

## 7. 未验证项 / 假设

1. 宿主 Nginx 配置是否关闭 SSE 缓冲、`proxy_read_timeout` 是否 ≥300s、是否设置安全头与 `client_max_body_size`。
2. Certbot 续期是否真实成功；`cert-check.sh` 是否只在周一写日志而无告警。
3. `/opt/offercoach/backups` 的实际脚本、权限、加密与上传；是否包含 `/opt/offercoach/data`。
4. ECS 的 CPU/内存/磁盘/AZ、swap 之外的 sysctl、Docker daemon 的 `log-opts`、PG/Redis 实际参数与数据量。
5. 生产库中 `question_bank` 的 `source_license` 分布是否与 JSONL 一致（是否有历史导入残留）。
6. 生产是否仍有 `demo@<redacted>` 与已知口令；`admin@<redacted>` 是否启用 MFA。
7. 是否存在仓库之外的 CI/Git 远端（本次仅能确认工作区无 `.git`）。
8. `oc-report-worker` 是否在其他环境有独立实现；`docs/ops/deployment.md` 的 staging/K8s 是否另有仓库。
9. 宿主是否配置了 Prometheus/Grafana（文档明确“无监控告警”，按未配置处理）。
10. Redis 是否设置密码/ACL、是否只绑定 127.0.0.1（文档称 127.0.0.1，仓库 Compose 是 0.0.0.0）。

以上未验证项建议在 30 天内补齐证据（配置截图/命令输出/演练记录），并回写本报告。

---

**审查边界声明**：本报告只写文件，未修改任何代码、迁移、Compose、Nginx 或生产配置，未执行部署/重启/备份/恢复操作。