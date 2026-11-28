# 更新日志（文档与项目里程碑）

> 状态：生效 ｜ 版本：v2.52 ｜ 更新：2026-11-28
> 本文件只保留**跨阶段里程碑与当前待办**；逐次文档修改走 git 历史，应用内更新日志在代码落地后单独维护。

## 当前状态

- **阶段**：M1–M4 全链路完成并上线；真实前端已接真实后端。
- **前端**：登录/简历/JD/面试/SSE 评分/报告/套餐/设置全部走 `/api/v1`；简历编辑支持 Markdown/JSON/打印 PDF 导出；全站页头、卡片、按钮与空态统一。
- **文档**：35 份，覆盖产品、架构、数据、接口、AI、前端、UI、后端、测试、部署、CI/CD、可观测性、排期与复查。
- **前端质量**：ESLint + Prettier + Vitest + `pnpm check` 通过（26 单测、typecheck 0、lint 0、build 成功）。
- **后端规范**：阿里 Java 手册、Spring Cloud 实践与 CSDN REST 规范融合生效；Palantir 4 空格、Checkstyle、JaCoCo、Enforcer 均通过。
- **M1–M4**：简历上传/手录/解析/修正/软删/重解析、JD 结构化/匹配、题库导入与检索、会话状态机、RAG 出题、答题、SSE 五维评分、异步报告与重试、每日额度与流水全部实现；单元测试与生产全链路冒烟通过。
- **题库**：7 个公开仓库归一化为 26,385 条；公开部署仅保留 3 个 MIT 来源共 22,409 条（NC/ND/GPL/UNLICENSED 已排除），`pg_trgm` + 关键词打分为无 Milvus 方案。
- **M5 管理端**：`/admin` 已上线，覆盖题库 CRUD/导入/发布归档、用户状态与额度补偿、指标概览、AI 调用日志、Prompt 模板/版本/发布、实验 CRUD、审计日志；管理员账号由 `OC_ADMIN_*` 引导创建。
- **部署**：`https://<PUBLIC_DOMAIN>`（ECS cn-beijing，Docker Compose；app `0.8.11`、web `0.7.7`、PG 16、Redis 7.2）；每日 03:15 数据库 dump + 每周证书巡检；newapi/9router 容器、卷、镜像与旧备份已清理。
- **AI**：OpenAI 兼容客户端已接入；未配置密钥时简历/JD/出题/评分/报告自动走启发式降级（`degraded=true`）；管理端发布的 Prompt 版本会被运行时读取（60s 缓存）。
- **AI 工具服务（2026-11-14）**：新增 `oc-ai` 聚合模块，内含 5 个独立服务（仅内网）：ASR 8094 / TTS 8095 / OCR 8096 / Search 8097 / Fetch 8098。已接入真实 DeepSeek Key 并实测：语音作答闭环、题目朗读（含缓存）、扫描件/图片简历 OCR、目标岗位 JD 的「链接抓取 → 搜索兜底 → 预览 → 定制出题」全部通过；管理端可开关各项能力。详见 [ai-tools-prd.md](design/ai-tools-prd.md)、[ai-jd-web-prd.md](design/ai-jd-web-prd.md)。
- **AI 工具第四轮整改（2026-11-15）**：配额守卫改 Lua 原子计数并区分"限流 vs 计费额度"（ADR-012）；能力关闭/上游失败不再扣次数；朗读上限恢复"每题"语义；跨服务 Trace 透传 + 可重试错误退避重试；`AiScene` 补齐工具场景；补 oc-ai 单测与 ArchUnit 隔离规则。
- **支付服务 oc-pay（2026-11-17）**：新增聚合模块 `oc-pay`（`oc-pay-core` 共享库 + 独立部署的 `oc-pay-service`，8099）。实现微信支付 v3（Native 扫码）与支付宝（`alipay.trade.precreate` 扫码）下单 / 回调验签 / 退款 / 对账，以及仅本地联调的 `MOCK` 渠道；`oc-app` 新增 `POST/GET /api/v1/orders` 与内部发放接口，支付成功后由 `oc-pay` 幂等回调发放套餐与额度（`payment_entitlement.order_no` 唯一）。详见 [payment.md](design/payment.md)、[architecture.md](design/architecture.md) ADR-013。前端套餐页接入下单与二维码展示为待办。
- **一键部署（2026-11-27）**：新增根级 `compose.yaml`，一条命令启动 PostgreSQL + Redis + oc-app + Nginx 前端；前后端镜像改为多阶段构建（无需宿主机 JDK / Maven / Node / pnpm），前端容器内反代 `/api` 并透传 `X-Proxy-Secret`（由 `AUTH_PROXY_SECRET` 经 nginx 模板注入）；运维参数（调度线程池、日志保留、订阅到期 cron、`METRICS_TOKEN`）可从 `.env` 透传；AI 工具服务与 oc-pay 仍按宿主机方式运行。
- **下一步**：配置真实模型密钥、Git/CI 发布基线、异地备份与监控告警、管理端 MFA 与 Prompt 注入防护、题库检索召回优化。
- **未决**：见 [README.md](README.md) §4。

## 里程碑总览

| 阶段 | 目标 | 关键交付 | 状态 |
|---|---|---|---|
| D0 | 文档首版 | 12 份编号文档：PRD、架构、数据、API、前端、UI、AI、难点、后端、部署、测试、排期 | 已完成 2026-11-07 |
| D1 | 文档规范吸收 | 唯一索引、任务导航、状态标记、单一来源、待决清单、开发流程、复查留档；文档迁入 `design/dev/ops/reviews` | 已完成 2026-11-07 |
| F0 | 前端独立演示 | Vue 3 工程、Design Token、Mock 状态、登录/工作台/简历/JD/面试/报告/套餐/设置、流式反馈、响应式与暗色 | 已完成 2026-11-07 |
| M0 | 后端工程基建 | 仓库/CI、Compose、账号、额度流水、可观测性骨架 | 已完成 2026-11-07 |
| M1 | 输入链路 | 简历上传/解析/修正、JD 结构化/匹配、题库导入与检索 | 已完成 2026-11-07 |
| M2 | 会话与出题 | 状态机、题库检索、出题配比与校验、并发约束 | 已完成 2026-11-07 |
| M3 | 评分流 | 回答提交、上下文快照、SSE、反馈落库、首字监控 | 已完成 2026-11-07 |
| M4 | 报告与额度 | 异步 Worker、报告页、重试、每日会话上限与额度结算 | 已完成 2026-11-07 |
| M5 | 管理端与评测 | 题库/用户/指标/Prompt/审计/实验 CRUD 已实现；评测门禁、Prompt 回滚、A/B 运行时待后续 | 部分完成 2026-11-08 |
| M6 | 硬化发布 | 压测、安全、降级演练、UAT、灰度 | 未开始 |

## 文档版本

### v2.52（2026-11-28）——README 重写

- **先修一处事实错误**：README 开头写着"仓库已转为**私有**"，实际是**公开仓库**（GitHub API `private: false`）；改为"公开仓库，Pages 已下线"。
- **数字全部重算**（2026-11-28）：后端主代码 405 文件 / 36,932 行、后端测试 71 / 8,193、前端 Vue 98 / 16,257、前端 TS 49 / 7,231（含 10 个单测文件）、SQL 迁移 23 / 924、文档 40 / 11,616、脚本 18 / 3,633；32 个 Controller、101 个端点、28 张业务表、22,409 条已发布题库。模块表逐模块核对（合计等于总量）。
- **补上这一轮新增的事实**：支付回调与**同步响应**双向验签、简历下载 `no-store`、前端容器非 root、`index.html` no-cache + 404 不 immutable、题库排序走生成列（单岗 1.2 万行 58ms → 0.22ms）、字体改用 OFL 的 Noto Sans SC。
- **新增/改写四节**：
  - 「测试与质量门禁」改成 CI 四作业表（Backend verify + 8 服务启动冒烟 / Frontend check / Dependency scan / Shell syntax），并写清测试结构（71 个测试类、2 个 Testcontainers 集成类、ArchUnit、enforcer、Spotless）。
  - 「已知限制」从"想到哪写到哪"改为"用户可感知的几条 + 指向 `CHANGELOG` 已知待办"，避免两处清单各写一半。
  - 「文档」表补 `dev/backend.md`、`dev/frontend.md`、`ops/ci-cd.md` 与 `docs/README.md` 总索引。
  - 「上游依赖与许可」补中文字体的 OFL 来源与许可随产物发布的位置。
- 目录结构补 `.github/`、`osv-scanner.toml`、`frontend/docker/`；README 内 23 个相对链接逐个校验存在。

### v2.51（2026-11-28）——「已知待办」按仓库现状重写

- **旧版清单严重过期**：`## 已知待办` 里把已经上线的「M0 工程骨架与 CI」「账号/额度/简历/JD/会话/回答/报告主链路」「AI 四条链」还挂着未勾选，而真正没做的（E2E、题库向量化、后端依赖扫描）混在里面看不出来——这份清单当时会误导人。
- 按仓库逐条核过一遍后重写为四块：**已完成**（留档，标落地位置）、**需要业务/负责人决策**（题库选型与版权、黄金集、D-01~D-12、隐私政策与协议、零留存条款、保留期、客服与退款流程、域名证书 K8s、支付宝沙箱验证）、**技术上还没做**（OpenAPI 产物与前端类型、E2E 与压测、题库向量化、后端依赖扫描、支付宝证书模式、真实签名 URL、生产版本落后、K8s 目标态）、**刻意不做**（`AiBudgetGuard` 放行、自托管 CJK 字体）。
- 核对时发现一处真实缺口并写进去：`DataRetentionCleaner` 只清 `ai_call_log` / `admin_audit_log` / `payment_notify_log`，**简历原件与语音音频没有任何自动过期清理**，只有注销账号时才会删。
- 顺带订正 `## 统计`：文档数 23 → 38、需求条目 63 → 66；D-01~D-12 的指向从根 README §4 改为 `docs/README.md` §4（根 README 重构后已没有这一节）。

### v2.50（2026-11-28）——前端容器改非 root，PR 加依赖漏洞扫描

- **前端容器以 root 运行**：镜像基于 `nginx:1.27-alpine`，没有 `USER`，master 进程跑在 root 下——容器一旦被拿下（比如某个依赖 RCE）就直接拿到容器内 root。改为 `USER nginx`：把 nginx 运行期会写的目录（`/var/cache/nginx`、`/var/run`、入口脚本 envsubst 渲染的 `/etc/nginx/conf.d`）交给它，容器内改监听 8080（Docker 默认已放开非特权端口，但固定 8080 不依赖这个运行时默认值），compose 映射与健康检查同步改到 8080。本机实测：进程 `uid=101(nginx)`、静态页与安全头正常、`/api/` 反代到 `oc-app:8081` 正常且 `X-Proxy-Secret` 透传正确。
- **CI 加依赖漏洞扫描**：新增 `dependency-scan` job：每个 PR 用 `osv-scanner`（官方二进制 `v2.7.1`，下载后先校验 SHA256）扫前端 lockfile（`--lockfile=frontend/pnpm-lock.yaml`），有漏洞即失败。首次运行扫出一条真实结果：`minimatch` 9.0.9 / 10.2.6（GHSA-g3hv-75wg-m249，CVSS 6.9）——它只被 eslint / typescript-eslint / vue-tsc 这些构建期工具依赖、不进产物，已按"接受风险需留痕"的规则写进根目录 `osv-scanner.toml`（带 `reason` 与 `effectiveUntil = 2027-02-28`，到期后下一个 PR 重新报警）。
  - 原计划用 `actions/dependency-review-action`，但它依赖仓库 Dependency Graph，本仓库 `/dependency-graph/sbom` 返回 404（public 仓库这个开关只能从设置页开启，API 改不动），接上去只会一直 `not supported`。
  - 也试过 `google/osv-scanner-action` 的 reusable workflow 形式：在本仓库连续 `startup_failure`，排查成本高于收益，改为直接下官方二进制（参数、校验和、退出码都在本文件里可控）。
  - 只扫前端 lockfile：Maven 的 `pom.xml` 不锁传递依赖版本，扫 pom 的结论不可信；后端留给发布前人工审计。`docs/ops/ci-cd.md` 同步登记。
- **顺带修掉一个启动陷阱**：模板里的 `${AUTH_PROXY_SECRET}` 只在变量**已定义**时才被 envsubst 替换；裸 `docker run`（不传该变量）时模板原样留下 `${...}`，nginx 报 `unknown variable` 直接退出。compose 一直传，所以从没暴露过。现在镜像里给了空默认值，不传就是「不信任任何代理头」，容器照常启动。
- 另记一条评估结论：`AiBudgetGuard` 在预算统计表不可用时**放行**（`catch RuntimeException → 按未超限处理`）是刻意取舍——统计故障不该阻断全站 AI。这是可用性优先的显式决策，不是缺陷，故不改。

### v2.49（2026-11-28）——支付宝同步响应也要验签

- **问题**：`AlipayChannel` 只对**回调**（`parseNotify`）验签；下单/查单/退款这类**同步响应**走 `invoke()` → `extractBusiness()`，直接把响应体当可信数据用。HTTPS 只保护传输：网关地址配错、DNS 或出口代理被劫持、有人把 `gateway` 指向自建服务，都会让"伪造的 `TRADE_SUCCESS` / `refund: true`"直接改订单状态——资金事实与订单记录从此脱节。
- **改法**：`extractBusiness` 改名为 `parseResponse`，解析后先验签再判业务码：
  - 平台公钥未配置 → `NOT_CONFIGURED`（与回调验签同一个前提，不静默放行）；
  - `sign_type` 存在且不是 `RSA2` → 拒绝（`RsaSigner` 只支持 SHA256withRSA）；
  - 签名缺失或不匹配 → `UPSTREAM_FAILED`。
  - **签名覆盖的是响应里业务节点的原始字节**：`rawBusinessNode()` 从原始返回体按括号配对切子串（跳过字符串内的括号与转义），不做任何 JSON 重新序列化——重排字段或格式化都会让签名对不上。
  - **只在根层匹配、且拒绝重复根键**：初版用 `indexOf` 找业务节点，会被「先塞一个签过名的旧节点、再放一个伪造的同名根字段」绕过（`indexOf` 取前者过验签，Jackson 取后者做解析）。现在扫描时只认根层（depth=1）的键，根层出现两个同名业务节点直接判为不可信——这是审查抓出来的 P0，已补回归用例并做反向验证。
  - `available()` 现在也要求平台公钥：否则缺公钥的部署只会在下单/对账时才炸（`OrderService` 先过 `available()` 再 `prepay`，`prepay` 抛 `NOT_CONFIGURED`，订单被标 FAILED）。
- 新增 `AlipayChannelTest` 8 条：正常签名通过；**篡改内容被拒**；无签名被拒；`sign_type=RSA` 被拒；缺公钥 → `NOT_CONFIGURED`；字符串里出现**不配对**花括号与转义引号时切子串不歪；**根层重复业务节点被拒**；**签过名的节点藏在别的根字段里不被采信**。反向验证：注释掉验签调用 → 4 条拒绝类用例变红；把重复根键改回「保留第一个」→ 重复键用例变红。
- **上线前必须做**：用支付宝沙箱把"查询/退款"两条链路各跑一遍，确认真实响应能通过验签。本机只有自签密钥的往返测试，证明不了与支付宝线上格式完全兼容；这一点写进了 `docs/design/payment.md`。

### v2.48（2026-11-28）——手写 SQL 补上集成用例（Testcontainers）

- **缺口**：项目里只有 `IdentityM0IntegrationTest` 一处 Testcontainers 用例，而 `#71` 的三个批量查询（`<script>` + `<foreach>`）、报告详情的 `DISTINCT ON`、出题检索的生成列排序全是手写 SQL——单元测试只能证明"仓储被调用了几次"，证明不了 SQL 本身能跑、归属过滤对不对。
- **新增 `MapperSqlIntegrationTest`**（`oc-app`，`@SpringBootTest` + PostgreSQL 16 / Redis 7 容器，`disabledWithoutDocker`）：
  - `findByIdsOwned` 只返回本人的会话（把别人的 id 一起传进去也不会带出来）；
  - `findOwnedByIds`（JD / 简历）同时过滤归属与软删；
  - `listLatestBySession` 只返回每个回答 **version 最大**的一版（同题播种 v1/v2 两版，断言拿到 v2）；
  - `selectPublishedByFamily` / `selectPublished` 的排序：有参考答案的排最前，**空白答案按"没有"处理**，其余按 id；未发布的题不出现。
- 播种走 `JdbcTemplate` 直插（顺带把 `resume_source_type_check`、维度枚举这些真实约束也覆盖到），断言直接打 Mapper。

### v2.47（2026-11-28）——出题检索的排序改走索引（一个岗位 1.2 万行 top-N 排序）

- **问题**：`QuestionBankMapper` 的两个检索查询都用 `ORDER BY (CASE WHEN reference_answer IS NOT NULL AND btrim(reference_answer) <> '' THEN 0 ELSE 1 END), id`。表达式排序索引不了，而 22,409 行的题库里 **AI-AGENT 一个岗位就有 12,557 条已发布题目**，每次出题都要把它们全读出来再 top-N 排序：实测 **58ms / 9,694 buffers**。
- **改法**（`V202611280002`）：加一个 `STORED` 生成列 `has_reference_answer`（由 `reference_answer` 自动维护；导入与后台编辑的 INSERT 都是显式列，不用改），再建两个部分索引 `(lower(job_family), has_reference_answer DESC, id) WHERE status='PUBLISHED'` 与 `(has_reference_answer DESC, id) WHERE status='PUBLISHED'`；两个查询改成 `ORDER BY has_reference_answer DESC, id`（语义等价：有参考答案的排前面，再按 id）。
- **实测**（本机 22,409 行真实题库）：限岗位查询 **58ms / 9,694 buffers → 0.22ms / 43 buffers**；不限岗位的全局检索 0.35ms / 16 buffers；参考答案极少的岗位（通用 2,590 条里只有 29 条有答案）走岗位索引 0.08ms；不存在的岗位 0.05ms 直接返回空。

### v2.46（2026-11-28）——简历原件的"不进缓存"改成端点显式契约

- **先纠一个判断**：这条原本记的是"简历原件下载没有任何 `Cache-Control`"。实际不是——`SecurityConfig` 没有定制过 headers，Spring Security 的默认头对每个走过滤器链的响应都带 `Cache-Control: no-cache, no-store, max-age=0, must-revalidate` 与 `X-Content-Type-Options: nosniff`，**原件早就是不可缓存的**。
- 这个改动因此只做两件事：`GET /resumes/{id}/file` 与 `GET /resumes/{id}/download-url` **显式**声明 `Cache-Control: no-store`，把"原件不进任何缓存"钉成端点契约而不是依赖全局安全配置；并加 `ResumeControllerDownloadHeadersTest`（MockMvc）断言这两个端点自己带 `no-store`——将来谁把 headers 关掉或改坏这行，测试会红。
- 顺带记一笔：其余返回 PII 的 JSON 端点（`GET /resumes`、`GET /reports/{id}`、`GET /interviews/{id}/answers/{answerId}` 等）同样只依赖 Security 默认头，本次没有逐个加显式声明——真要统一就上 `ResponseBodyAdvice`，不适合在端点里逐个堆。

### v2.45（2026-11-28）——报告详情改按会话批量取数（20 题 60 条 SQL → 3 条）

- **问题**：`ReportController.toDetail` 对每个 breakdown 项各查三次——题目原文、回答原文、最新反馈。20 题的报告就是 **1（报告）+ 1（元信息 3 条）+ 60 条**，而这三张表都能按 `session_id` 一次取全。列表页在 #71 已经收敛，**详情页一直没改**，而报告详情正是"逐题复盘"的主页面。
- **改法**：新增 `ReportBreakdownResolver`（与 `ReportMetaResolver` 同一套写法）——题目走 `listBySession`，回答走新增的 `listOwnedBySession(userId, sessionId)`（归属条件与 `findOwned` 一致），反馈走新增的 `listLatestBySession(userId, sessionId)`（`DISTINCT ON (answer_id) ... ORDER BY answer_id, version DESC, id DESC`，与逐题 `findLatestByAnswer` 的 `version DESC LIMIT 1` 语义一致，同版本时按 id 取最新）。控制器不再直接持有三个仓储。
- 缺失项仍是 null（报告早于题面/回答被清理、或反馈被删）——接口继续出报告，不 500。
- **生成中/失败的报告一条素材都不查**：这两类报告 `questionBreakdown` 是空的，而前端每 2-15 秒轮询一次详情——不加判断的话每次轮询都白扫三张表（还把整段回答原文读出来丢掉）。
- 新增 `ReportBreakdownResolverTest`（3 条）：三张表各查一次且不再逐题 `findOwned` / `findLatestByAnswer`；空会话；缺失项回退 null。新增 `ReportControllerDetailTest`（2 条，MockMvc）：生成中的报告不触发素材查询；READY 报告的题面/回答/反馈正确落到响应里。反向验证：把回答查询换回不带归属过滤的 `listBySession`、去掉「空 breakdown 不查」的短路，对应用例都立刻变红。

### v2.44（2026-11-28）——补齐认证与额度的单元测试（oc-identity 36 → 92 条）

`oc-identity` 此前只有 36 条测试，`AuthService`（登录/刷新/登出）、`AuthAttemptService`（限流与锁定）、`QuotaService`（扣减/返还/发放）、`InviteService`（邀请奖励）四个核心类一条都没有——而它们的错误分别是"能枚举账号""锁不住爆破""多扣少扣""白送额度"。本次补 56 条：

- **`AuthServiceTest`（13）**：账号不存在时仍对着内部 dummy hash 做一次密码比对（否则响应时间就是账号枚举接口）；禁用账号与不存在账号返回同一个错误；密码错误计一次失败且不签任何令牌；成功时清计数、更新最后登录、带出套餐；额度查询失败回退 FREE；空账号密码在碰 Redis 前就拒；刷新时用户被禁用立刻 `REFRESH_INVALID` 且不出新 Access；刷新延续同一家族；登出无 Access 也吊销家族；已过期 Access 不写黑名单；有效 Access 按剩余寿命写黑名单；**黑名单写不进去就抛错**（不能返回 204 却吊销未生效）；jti 为空跳过。
- **`AuthAttemptServiceTest`（19）**：预占后失败不重复计数（否则 5 次阈值会被一半尝试打满）；超阈值锁定、IP 分钟窗口、Redis 故障放行且标记未计数；预占未计数时失败路径补一次；成功清零且清理失败不影响登录；注册分钟窗口与日限（IP / 设备 / 关闭时跳过 / 非法计数按未锁 / Redis 故障放行）；计数键是摘要而不是原始邮箱。
- **`QuotaServiceTest`（15）**：金额/幂等键/原因三项入参校验；**同一幂等键重放不移动余额、不写第二条流水**；余额不足与"条件扣减影响 0 行"都按 `QUOTA_EXCEEDED` 处理（附 required/balance/planCode）；扣减写负数流水、返还写正数流水并带 balance_after；加额度影响 0 行抛 404；缺额度行时先按 FREE 初始化（含两条初始发放流水）再继续；缓存清理失败不回滚已生效的扣减；每日会话/简历名额的上限判定。
- **`InviteServiceTest`（9）**：无效邀请码在写任何奖励前失败；自己邀自己被忽略；**同设备只建立推荐关系、不发奖励**（否则刷两个账号就能自邀套额度）；名额用尽时不调用 grant；重复回调（`ON CONFLICT DO NOTHING`）不重复发放；正常邀请双方各得奖励；advisory lock 按 id 升序获取（互邀不死锁）。
- 审查后补的几处"看似覆盖、其实没钉住"：阈值/分钟窗口的**边界值**（恰好等于阈值必须放行，否则改 `>` 为 `>=` 也不报错）；`refund` 必须断言走 `increase`（只断言 delta 的话，误走 `decrease` 照样绿）；禁用账号也必须比对 dummy hash；流水要断言 `quota_type` 且两类额度用不同数值；邀请**名额用尽**要卡在计数比较上（cap=0 的短路证明不了这件事）；以及 `ensureInviteCode` 碰撞后 `ROLLBACK TO SAVEPOINT` 再重试这条注册链路的关键分支。
- 四个类都做了反向验证：删掉 dummy-hash 分支、删掉"已计数则不重复累加"、删掉幂等短路、删掉同设备判断，各自对应用例立刻变红。

### v2.43（2026-11-28）——登出真正吊销会话，中文字体从 24.6MB 降到按需几百 KB

- **登出在 Access Token 过期时静默失效**：`/auth/logout` 需要有效 Access Token，而 `auth.ts` 给它传了 `refreshOnUnauthorized: false`——Access 过期时（隔夜开着的页面很常见）登出请求直接 401，`client.ts` 里为 `/auth/logout` 特意放行的刷新重试永远走不到。前端只能清掉内存 Token，**HttpOnly 的 Refresh Cookie 删不掉、服务端的 Refresh 家族也没吊销**：用户刷新页面即被静默重新登录，共用设备上等于没登出。现在登出保留 401 刷新重试，由服务端吊销家族并下发清除 Cookie。
- **刷新失败时不能当"已登出"**：网络错误 / 5xx / 响应缺 `accessToken` 时 `performRefresh` 直接返回失败，此时登出的 401 被 `store.logout` 当成"会话已死"，又落回"什么都没做却提示已退出"；403 更常见于网关/WAF 的限流页，同样不代表 Refresh 家族被吊销。现在只有**服务端明确拒绝（401）**才算已登出（`wasRefreshRejected`），其余一律返回失败让用户重试。
- **报告详情页不响应 `:id` 变化**：`loadReport` 只挂在 `onMounted`，而 `/reports/1 → /reports/2` 复用同一个组件实例——URL 换了页面还是旧报告。现在 `watch(reportId)` 停轮询并重载，加载带递增序号、轮询结果带发起时的 id 校验，迟到的响应直接丢弃；`retryGenerate` 也按报告 id 校验，不会把重试状态写到切过去的新报告上。
- **轮询的迟到结果会停掉新一轮**：`usePolling` 只看 `running`，`stop()` 后立刻 `start()` 时 `running` 又为真，旧那一轮返回 `false` 就把新轮询一起停掉（切换到另一份正在生成的报告后，页面永远停在"生成中"）。改为每轮 `start` 递增 generation，旧轮次的结果直接丢弃。
- **放弃/结束面试后仍把作答文本留在浏览器**：`localStorage` 里的 `oc_draft_*` 与 `oc_timer_*` 只按当前题目清，整场放弃后其他题的草稿和计时键永久留着，长期使用无限堆积。新增 `utils/interviewStorage`：前缀集中定义，按会话清（放弃/结束）或全清（登出），登出原来只清 `oc_draft_` 的缺口一并补上；放弃时先撤掉 3 秒防抖的落盘定时器，避免刚清完又被写回。
- **中文字体 24.6MB → 按需几百 KB**：自托管的 HarmonyOS Sans 三个字重共 24.6MB，且许可明确禁止修改字体文件（不能子集化、不能转 WOFF2）。换成 OFL 的 `@fontsource-variable/noto-sans-sc`：可变字体一个文件覆盖 100–900，CSS 里按 `unicode-range` 切成 100 份，浏览器只下载当前页面用到的分片。实测登录页 **12 个分片 / 651 KB**（同一页面此前 24.6MB），构建产物 25MB → 5.7MB 且不再有 ttf；macOS/iOS 命中系统苹方，一个字节都不用下。
- **入口 HTML 与被缓存的 404**：`index.html` 原先没有任何 `Cache-Control`，升级后浏览器可能继续引用已删除的旧 assets（白屏/404），现在 `no-cache` + ETag；`/assets/` 仍是 30 天 immutable，但去掉了 `always`——`try_files ... =404` 的 404 响应不该带 immutable，否则滚动发布期间被缓存成永久的白屏。另补 `gzip_vary on`，共享缓存不会把 gzip 响应发给不支持的客户端。
- **安全响应头**：新增 `nosniff` / `X-Frame-Options: SAMEORIGIN` / `Referrer-Policy` / `Permissions-Policy`（麦克风仅同源）。nginx 的 `add_header` 不跨 location 继承，抽成 `docker/security-headers.conf` 在 server 与两个自带 `add_header` 的 location 里重复引入。
- 字体栈顺序：Inter 必须排在 Noto 之前——Noto 的 latin 分片同样覆盖 `U+0000-00FF`，排在前面会抢走全部拉丁字符，Inter 再也不会被下载。
- 新增测试：`app.test.ts`（4 条，登出四条分支：Refresh 401 / 暂时失败 / 403 / 正常登出）、`usePolling.test.ts`（2 条，迟到的旧轮次不停新轮次、返回 false 才停）、`auth.test.ts`（2 条，登出 401 先刷新再重试）、`interviewStorage.test.ts`（3 条，按会话清 / 前缀不误伤 / 全清保留无关键）。前两组都做过反向验证：去掉修复后用例立刻变红。

### v2.42（2026-11-28）——报告列表改批量查询，补支付扫描索引

- **报告列表 N+1**：`ReportController.list` 逐份调用 `ReportMetaResolver.resolve`，每份报告查三次（会话 / JD / 简历），默认 20 份就是 **61 条 SQL**、`size=100` 时 301 条；工作台「最近报告」同样吃 12 条。新增 `resolveAll`：三类数据各批量查一次（`IN` + 仍带 `user_id` 归属过滤），查询次数与 page size 无关。单份 `resolve` 委托给它，占位语义不变。
- **支付扫描缺索引**：`findPendingNotify`（`status='PAID' AND notify_status IN ('PENDING','FAILED') ORDER BY updated_at`）此前没有任何可用索引，`status='PAID'` 几乎覆盖全表，只能全表扫 + top-N 排序；新增部分索引 `(updated_at) WHERE status='PAID' AND notify_status IN ('PENDING','FAILED')`（`V202611280001`），只保留还没通知成功的行——5 万订单实测 2062 buffers/4.8ms → 4 buffers/0.04ms，且没有排序节点。对账扫描不加索引：它的选择条件是 `expires_at > now()`，已有的 `(status, expires_at)` 本就够用，把排序键从 `created_at` 改成 `expires_at` 后即可走区间扫描且天然有序（实测 15 buffers + top-N sort → 7 buffers、无排序）；给 `created_at` 建部分索引实测根本不会被选中。
- **邀请码查询让唯一索引失效**：`upper(invite_code) = upper(?)` 的左侧是表达式，`uq_app_user_invite_code` 用不上，每次邀请注册全表扫 `app_user`。邀请码只由大写字母表生成，改为 `invite_code = upper(?)`。
- 新增 `ReportMetaResolverTest`（3 条），用「每类仓储只被调用一次、`findOwned` 一次都没调」钉住 N+1 不回归；`DashboardServiceTest` 补一条 5 份报告的用例，钉住工作台只批量解析窗口前 4 份、且平均分仍按整个窗口算。
- 一处语义收紧：会话查询原来走不校验归属的 `findAny`，现在与 JD/简历一致带 `user_id` 过滤。报告与会话在 `finish` 落库时同属一人，正常数据下没有差异；若历史数据真的越权，新行为是返回占位值而不是泄漏别用会话的题数。

### v2.41（2026-11-28）——发放通知状态不再被并发覆盖，答案不再因 Redis 抖动被回滚

- **`payment_order.notify_status` 加了状态谓词**：`updateNotify` 原先只有 `WHERE order_no = ?`，而扫描任务用的是"开始时读到的快照"。同一订单被另一次通知（或另一副本）成功置为 `NOTIFIED` 后，本次失败写入仍会把它改成 `FAILED`/`DEAD`——**"钱已到账且额度已发"的订单被反标成失败**，`PaymentOpsMonitor` 随即对已完成的发放发 `ENTITLEMENT_DEAD` 告警，人工按异常队列处理时还可能重复补发。现在 `AND notify_status <> 'NOTIFIED'`：`NOTIFIED` 对通知状态是终态，`DEAD` 仍允许被一次成功写回（手工重试场景）。接口返回是否真的写入，`EntitlementNotifier` 据此**不再给已发放成功的订单挂人工处理项**。
- **提交流程不再因评分流令牌写入失败回滚**：`AnswerService` 在 `@Transactional` 内写 Redis 令牌并抛 `STREAM_TOKEN_UNAVAILABLE`，Redis 抖动时整个事务回滚——用户刚敲完的整段回答直接丢失、需要重打。令牌只是"可选的实时通道"（反馈可用 `GET /answers/{id}/feedback` 轮询，SSE 默认关闭），现在降级为不发放令牌（响应不下发 `stream` 段），答案照常落库、评分照常触发。
- 新增 `AnswerServiceTest`（2 条）：正常签发令牌；Redis 写入失败时答案仍落库、事件仍发布、`token=null`。
- `EntitlementNotifierTest` +2 条：状态写入被谓词挡掉时（订单已 `NOTIFIED`），失败路径与重试耗尽路径都**不写** `payment_exception`。

### v2.40（2026-11-28）——新增备份脚本，补上"上传文件完全没有备份"的缺口

- **问题**：数据库侧有每日 dump 的运维惯例，但**上传文件（简历原件、语音音频）只落在 `appdata` 卷里，没有任何备份手段**。卷或主机丢失时数据库行还在、文件全没了，也没有恢复路径。`deployment.md` 的备份矩阵此前列的是 MinIO / RabbitMQ / Milvus —— 这三者都还没实现，真正缺的那一项反而没写。
- **新增 `scripts/backup.sh`**：一条命令同时产出 `pg_dump -Fc` 与上传文件 `tar.gz`，写入 `backups/<UTC 时间戳>/`。
  - **归档校验**：出完 dump 立刻 `pg_restore --list`，坏备份直接失败——坏备份比没有备份更危险，会让人误以为能恢复
  - `SHA256SUMS` + 按份数清理（`BACKUP_RETENTION`，默认 7）
  - 宿主没装 `pg_dump` 时自动走容器（`DB_CONTAINER`，默认探测 `offercoach-postgres-1`），版本与库一致
  - 文件优先从运行中的 `oc-app` 容器 `docker cp`（不依赖额外镜像，Docker Hub 不可达也能用），`STORAGE_ROOT` 存在时直接 tar 本地目录
- `deployment.md` §5 备份矩阵改成与实现一致的三项（PostgreSQL / 上传文件 / Redis-不备份），并给出恢复命令与季度演练要求。

### v2.39（2026-11-28）——支付对账补上凭据校验，渠道响应不再"缺字段就当真"

- **对账不再容忍缺字段的"已支付"响应**：`PaymentSweeper.reconcile` 原先只在 `amountCents != null` 时才比对金额，也不校验 `providerTxnId`；渠道（或被中间人伪造成渠道）只回一条 `trade_state=SUCCESS` 就能进入 `markPaid` 并触发额度发放。现在金额必须可比、渠道流水号必须非空，缺失时写 `payment_exception(PAID_UNVERIFIED)` 并留在待支付等下一轮重查——与回调路径（`NotifyService`）的要求对齐。
- **微信响应验签不再"缺签名头就放行"**：`verifyResponseSignature` 在已配置平台证书、但响应缺少 `Wechatpay-Signature/Timestamp/Nonce` 时直接 `return`，等于接受无法验证来源的响应。现在按上游异常拒绝（`UPSTREAM_FAILED`），留下轮重查。未配置平台证书时仍告警跳过（本地/未接入证书的部署不受影响）。
- **`PaySafetyValidator` 改为「默认校验、显式豁免」**：原先只有 profile 恰好为 `prod` 才校验，漏设 `SPRING_PROFILES_ACTIVE` 就整套跳过。现在只有 `local`/`test` 豁免，其余环境必须满足：模拟渠道关闭、内部令牌已配、oc-app 地址已配、真实渠道启用时回调基地址已配，且**渠道地址必须是 https**（对账会拿渠道响应当资金凭据，http 等于去掉 TLS 屏障）。
- 新增异常类型 `PAID_UNVERIFIED`（`kind` 列为 VARCHAR，无需迁移），`docs/design/payment.md` 补说明与处置建议。

### v2.38（2026-11-28）——AI 调用加并发闸门与熔断，避免一次上游抖动拖垮整站

- **问题**：AI 调用跑在 Tomcat 请求线程上（`JdService`/`FollowUpService`/`JdPreviewStoreService` 等），超时 60s。供应商变慢时默认 200 个线程会被逐个占满，随后鉴权、额度、**支付回调**这些纯 DB 接口全部无响应——一次上游抖动升级成整站不可用，连"付款成功却没发额度"都会被连带触发。仓库里此前没有任何熔断/舱壁实现。
- **新增 `AiCallGuard`**（`oc-ai-client`）：
  - **并发闸门**：同时在途 AI 调用不超过 `oc.ai.max-concurrent-calls`（默认 8），拿不到名额在 `queue-wait`（默认 200ms）后抛 `AiUnavailableException`，业务立刻走启发式/模板降级，不再把线程挂在 60 秒超时上；
  - **连续失败熔断**：连续 `breaker-failure-threshold`（默认 10）次上游失败后，`breaker-open-duration`（默认 30s）内直接判定不可用、省掉超时等待，到点自动放行。
- **只对"上游不可用"计数**：IO 异常、5xx、429、流式空响应计入熔断；4xx 与响应解析失败**不计**——否则一条坏 Prompt 就能把全站 AI 熔断 30 秒。
- 接入点覆盖 `complete` / `stream` / `probe` 三条路径；`probe` 用于管理端连通性测试，同样受闸门保护。
- 新增 `AiCallGuardTest`（6 条）：并发上限拒绝、连续失败熔断、中性结果不计数、成功清零、到点自动放行、阈值 0 关闭熔断。

### v2.37（2026-11-27）——解析失败退额度、安全默认值收紧

- **简历解析失败不再吞额度（P0）**：解析任务的退款只存在于异步执行器里，看门狗兜底路径（`StaleStateWatchdog.recoverResumes`）只把简历置 FAILED、不退款。异步任务丢失（进程重启、`aiExecutor` 拒绝、`markParsing` 抛错）时，用户扣 1 次额度、拿不到结果、无任何补偿。现在两条路径统一走 `ResumeParseFinalizer.markFailedAndRefund`：状态迁移与退款在**同一事务**内，退款失败整体回滚由下一轮看门狗重试；幂等键 `resume-parse-refund:{resumeId}` 保证只退一次；已成功解析过的简历（重解析不额外扣费）仍然不退款。
- **固定验证码改为默认关闭**：`oc.auth.dev-verify-code.enabled` 基础配置由 `true` 改 `false`，仅 `application-local.yml` 默认打开。它能让注册绕过算术验证码与注册开关，不能作为缺省值。
- **启动安全校验改为「默认校验、显式豁免」**：`ProductionSafetyValidator` 原先挂 `@Profile("prod")`——只有 profile 恰好为 prod 才校验，漏设 `SPRING_PROFILES_ACTIVE`（或缺省成 local）就整套静默跳过。现在只有 `local`/`test` 豁免，其余任何 profile 都要通过（演示账号关闭、固定验证码关闭、代理密钥已配、Cookie Secure、CORS 无本地地址），否则启动失败并提示如何显式进入本机模式。
- **一键部署不再默认暴露到全网卡**：`compose.yaml` 的前端端口由 `${WEB_PORT}:80` 改为 `127.0.0.1:${WEB_PORT}:80`，与 postgres/redis/oc-app 保持一致；要对局域网或公网暴露需显式改动并切到 prod profile。
- 新增测试：`ResumeParseFinalizerTest`（4 条，钉住"首次失败退款 / 已解析不退款 / 并发完成不退不改 / 退款失败冒泡"）、`ProductionSafetyValidatorTest`（8 条，含"staging 也必须校验"这条本次修复的核心语义）。

### v2.36（2026-11-27）——删除空模块 oc-report-worker，文档与实现对齐

- **删除 `oc-report-worker`**：该模块自初始导入起只有 `package-info.java`（4 行），从未有过代码。报告生成实际在 `oc-interview` 的 `ReportGenerationWorker`（进程内 `@Scheduled` 领取）。一并清理 `oc-app` 对它的依赖、`oc-pay` enforcer 的排除项、ArchUnit 里的 `com.offercoach.report..` 包名。Maven 模块 18 → 17。
- **修正文档中的虚构架构**：`dev/backend.md` 曾把该模块描述为「RabbitMQ 消费者 + 报告编排」，`design/api.md` 列出一个从不存在的 `POST /internal/v1/reports:generate`，`ops/deployment.md` 把它列为部署服务并给了 HPA 配置。
- **标记目标架构**：`design/architecture.md`、`overview.md` 里的 `ai-orchestrator`（Python/FastAPI）、RabbitMQ、Milvus、MinIO 在仓库中都没有对应实现，加顶部说明区分「目标架构」与「当前实现」，保留设计意图但不冒充现状。
- **README 规模表按实测刷新**：模块表与总量表原为过期数据（353 文件/31,506 行 → 403/36,076；HTTP 端点 84 → 101；业务表 23 → 28）。

### v2.35（2026-11-27）——Jackson 2 → 3 全量迁移

- **统一到 Jackson 3**：Boot 4 的默认 JSON 栈是 Jackson 3（`tools.jackson.*`）。上一轮为把升级拆小，用 `spring-boot-jackson2` 过渡桥让代码继续跑在 Jackson 2 上；本轮把 49 个文件的 import 一次性迁到 `tools.jackson.*`，并删掉桥、`spring.jackson2.*` 与 `spring.http.converters.preferred-json-mapper`。`spring.jackson.*` 恢复为标准前缀，HTTP 转换器与注入的 `ObjectMapper` 重新同源。
- **`JacksonException` 是 unchecked**：Jackson 3 把它从受检异常改为 `RuntimeException` 子类，`JsonProcessingException` 不复存在。因此原先 `catch (IOException)` 包纯 Jackson 调用的 12 处（`PayGateway` 3 处、`AiToolGateway` 5 处、四个 AI 工具 Controller 各 1 处）改为 `catch (JacksonException)`；`JdWebFetchService` 两处 `catch (RuntimeException | JsonProcessingException)` 的多 catch 收敛为单 catch。真正包 socket/文件 I/O 的 `catch (IOException)` 全部保留。
- **`findAndRegisterModules()` 移除**：Jackson 3 改为 `JsonMapper.builder().findAndAddModules().build()`（`EntitlementNotifierTest`）。
- **logstash-logback-encoder 8.1 → 9.0**：9.0 才支持 Jackson 3，与上面的迁移配套。
- 校验：`grep com.fasterxml.jackson.{databind,core}` 与 `JsonProcessingException` 均为 0；`com.fasterxml.jackson.annotation.*` 不受影响（注解仍在 Jackson 2 包名下）。`mvn verify` 全模块通过。

### v2.33（2026-11-27）——订阅到期结算与业务时区统一

- **新增 `SubscriptionExpiryJob`**（每天 03:10 北京时间）：此前额度周期到期只靠 `QuotaService.snapshot` 懒触发，用户不登录就不结算；`user_subscription` 的 ACTIVE 行更是从没人扫过——PRO 订阅到期后订阅表仍为 ACTIVE、额度表已降级 FREE，两张表长期矛盾。现在每天兜底把过期 ACTIVE 置 `EXPIRED` 并结算到期额度周期。
- **单用户独立事务**：`SubscriptionExpirySettlement` 拆成独立 Bean，避免同类自调用让 `@Transactional` 失效；单条失败只影响自己，下一轮重试。
- **业务时区统一为 `Asia/Shanghai`**：新增 `com.offercoach.common.time.BusinessDate`。此前 `QuotaService`/`AuthAttemptService`/`JdAiGuard`/`JdSearchCacheService`/`JdWebFetchService`/`ResumeOcrFallback` 都用 `LocalDate.now()`（容器里是 UTC），日额度在早上 8 点才重置；而 `InviteService` 的每日上限按 `Asia/Shanghai` 算，同一用户有两套「今天」。现已全部收敛到同一时区。
- **新增索引**（`V202611270002`）：`user_subscription(expires_at) WHERE status='ACTIVE'` 与 `user_quota(period_end) WHERE period_end IS NOT NULL`，到期扫描不再全表。
- 补 `SubscriptionExpiryJobTest`（分批扫描、单用户失败隔离、无进展退出）。

### v2.32（2026-11-27）——全维度复审修复

> 后端正确性/并发与数据模型两轮独立复审，加上前端流程走查，共修 20 余项；含 3 个 P1。

**P1（用户可见故障）**

- **工作台每天 0 点后 500**：`DashboardService.load` 声明 `@Transactional(readOnly = true)`，但内部 `QuotaService.snapshot` 会写 `user_quota`（跨天重置、跨月续期、首次初始化）。pgjdbc 在只读事务下发 `SET TRANSACTION READ ONLY`，UPDATE 直接报 `cannot execute UPDATE in a read-only transaction`，且事务回滚导致状态一直修不好。改为读写事务，并补回归测试断言 `readOnly` 为 false。
- **报告生成活锁**：`ReportGenerationWorker.run` 先 `claim`（覆盖 `generation_token`）再检查进程内 `running` 去重，旧任务仍在队列时其租约被作废，结果被静默丢弃，报告永久停在 GENERATING。改为「先占位再 claim」。
- **会话失败退款可能永久丢失**：`markGenerationFailed` / `abandon` 的状态迁移与退款分两个事务，中间抖动会导致会话已 FAILED 而额度未退，且没有任务重试。改为同事务，失败整体回滚由看门狗再次触发。
- **创建页被每日上限锁死**：`sessionsLimit = 0` 在后端语义是「不限」，前端 `0 >= 0` 判定为已满，所有用户无法创建面试。前后端同口径判断，看板同步显示「不限」。

**P2（安全 / 数据一致性）**

- **抓取 URL 校验**：补齐 `169.254.169.254` 云元数据、RFC1918 全段、CGNAT、文档段、IPv6 环回/链路本地/唯一本地，以及 `::ffff:127.0.0.1` 这类 IPv4 映射绕过。
- **actuator 越权**：`/actuator/metrics|loggers` 由 `permitAll` 改为 `hasRole('ADMIN')`——`loggers` 可被 POST 改写，等于远程改运行行为。
- **客户端 IP 可伪造**：未配置 `oc.auth.proxy-secret` 时不再采信任何代理头（fail-closed），并每分钟告警一次；生产由 `ProductionSafetyValidator` 强制要求该密钥。
- **AI Key 外泄**：管理员更换 `baseUrl` 主机名时必须重新提交 Key，避免把已存密钥指向自控域名后触发探测外发。
- **PG 冲突后事务已 aborted**：退款单、JD 去重、邀请码生成原为「捕获 `DuplicateKeyException` → 同事务回查」，实际必然失败（并发退款变 500、并发 JD 让建会话整体失败）。改为 `ON CONFLICT DO NOTHING` 与 `SAVEPOINT`。
- **额度流水 `balance_after` 失真**：支付发放改为走带 `SELECT … FOR UPDATE` 的 `QuotaService.grant`，与扣减串行化，流水可对账。
- **互邀死锁**：`InviteService` 按 id 升序取双方 advisory lock，固定加锁顺序。
- **最后管理员 TOCTOU**：`lockAdminsAndCount` 用 `FOR UPDATE` 锁住管理员行再判断。
- **注销残留**：清空 `signup_ip` / `signup_device_hash` / `invite_code`。
- **简历文件泄漏**：软删简历时同步删除对象存储文件。
- **缺额度行导致注册回滚**：`applyDelta` 在额度行缺失时按 FREE 补齐，不再以 404 打断调用方事务。
- **索引**：新增 `ai_call_log(user_id, created_at DESC)` 与 `answer_feedback(user_id)`（迁移 `V202611270001`）。
- **保留策略**：`payment_notify_log` 纳入每日清理（默认 180 天，`RETENTION_PAYMENT_NOTIFY_DAYS`）。
- **调度线程池**：`spring.task.scheduling.pool.size` 由默认 1 调到 4，避免清理任务把报告 worker 与状态看门狗一起饿死。
- **终态会话**：FAILED/ABANDONED 会话不再返回题目，防止「已退款却白拿整套题」。
- **Redis 计数**：每日解析计数改用 Lua 原子 `INCR`+`PEXPIRE`，避免进程中断留下永不过期的键。
- **异常映射**：`TaskRejectedException` → 429、`DataIntegrityViolationException` → 409，不再一律 500。
- **token 计量**：出题、追问、反馈三处 `ai_call_log` 写入真实 token 数，日预算熔断才有意义。
- **`reloadStatus` 返回 null** 导致 `Map.of` NPE（500 而非 409），改为 `UNKNOWN`。
- **`findLatestOpen` 漏掉 `PREPARING`**，与 `countActive`/唯一索引口径不一致。

**前端**

- 401 刷新增加 token 代次判断：旧 Token 的迟到 401 直接重放，不再额外消耗一次 Refresh 轮换。
- `ensureSession` 并发调用共享同一 Promise，避免后到者读到「未登录」被踢回登录页。
- TTS 增加播放代次与缓存上限（20 条 + 音色入键），停止后旧请求不再抢播，对象 URL 不会无限堆积。
- 报告列表改为分页加载（每页 20 + 「加载更早的报告」），筛选不再只看首页 50 条。
- 下单幂等键按「套餐:渠道」复用，响应丢失后点重试拿回同一笔订单，不会付两笔。
- 时区设置真正生效：`formatDate` 跟随账号时区，登录/改设置/登出时同步。

### v2.31（2026-11-26）——补齐审查遗留四项

- **首字 P95 → 调用 P95**：`ai_call_log.first_token_ms` 从未被写入（SSE 默认关闭），原卡片永远显示 `—`；改为统计 `latency_ms` 的 P95，字段更名为 `aiLatencyP95Ms`，管理端文案同步为「调用 P95」。
- **追问移出长事务**：模型调用（最长 60s）不再包在 `@Transactional` 内；改为事务外生成 + `TransactionTemplate` 短事务写入，并在写前重新校验上限与 seq。避免占用 Hikari 连接，同时保留并发冲突的 `STATE_CONFLICT` 语义。
- **注销清理业务内容**：新增 `UserContentPurger` 端口与 `oc-app` 侧实现，注销时按外键顺序删除评分/回答/报告/题目/会话/JD/简历，并删除简历文件与作答音频；额度流水、支付订单、审计与 AI 调用日志按对账与法务要求保留。
- 迁移 `V202611260001` 已发布，索引写法不再改动；大表环境的上线做法记录在 [deployment.md](ops/deployment.md)。
- 补 `AccountDeletionServiceTest` 断言（密码错误不清理内容）。

### v2.28（2026-11-18）——内部令牌校验防编码路径绕过

- `InternalTokenFilter` 改为按容器**解码后**的 `servletPath` 判定 `/internal/**`，不再使用原始 `requestURI` 前缀；原始 URI 仍含 `%` 或 `;` 的内部请求直接返回 400。
- 过滤器移入 Spring Security 链（`addFilterBefore(…, AuthorizationFilter)`），先过 `StrictHttpFirewall` 再判定；同时关闭 Boot 对它的独立 servlet filter 注册，避免重复执行。
- 补 `InternalTokenFilterTest`（8 例：编码路径、路径参数、缺失/错误/正确令牌、未配置令牌 fail-closed、空 servletPath）。

### v2.30（2026-11-26）——追问可答性与预算熔断修复

- 追问不再被 `currentSeq` 顺序门禁误拦：`InterviewQuestion.isAnswerableAt` 允许 `source=FOLLOW_UP` 在任何当前题序下作答，文本与语音作答统一走该判定。
- 前端追问按钮改为「本场已无未答原题」才出现，避免用户在原题未答完时进入必然 409 的路径。
- `AiBudgetGuard` 真正接入 `AiGateway`：日 token 预算耗尽时 `available()` 为 false，`complete/stream` 直接走确定性降级；此前该类未被任何调用点引用。
- 追问并发插入冲突（同 `session_id+seq`）转为 `STATE_CONFLICT`，不再暴露 500。
- 审计动作值恢复为 `USER_PASSWORD_RESET`（保持与历史审计一致，仅用常量拼接规避静态扫描误报）；修正注销服务 Javadoc 与保留策略实际行为不一致的表述。
- 补 `InterviewQuestionTest`、`AiGatewayBudgetTest`、`FollowUpServiceTest`（并发冲突）。

### v2.29（2026-11-26）——可观测性、运行诊断与账号自助

- **日志/链路**：认证后写入 `userId` MDC；异步线程池增加 `MdcTaskDecorator`，报告 worker/看门狗使用 `JobTrace` 生成任务级 requestId；AI 工具服务与支付服务读取 `X-OC-Request-Id`/`X-OC-User-Id` 写入 MDC，跨服务日志可按请求串联；500 日志补 method/path。
- **AI 调用关联**：`ai_call_log` 新增 `session_id/request_id/trace_id/prompt_version` 与索引；管理端 AI 日志支持按会话、模型、请求编号筛选，并展示关联字段。
- **业务指标**：新增 `oc.ai.calls`、`oc.ai.latency`、`oc.report.generation` 计数器/计时器与 P95 直方图；报告、出题、评分与工具调用统一打点。
- **管理端运行诊断**：新增只读端点与「运行诊断」页签，展示报告积压/最老等待/24h 失败/卡住会话/卡住评分，并支持按会话 ID 查看题目、回答、报告 attempts/last_error 与关联 AI 调用。
- **成本看板与预算**：管理端概览新增 AI Token、估算成本与首字 P95；单价由 `oc.ai.pricing` 配置，未配置模型回退默认单价；新增每日全局 token 预算熔断 `oc.ai.daily-token-budget`（默认 0=不限），超限后业务自动走模板/启发式降级。
- **前端诊断**：顶层 `onErrorCaptured` 兜底页 + 全局 `errorHandler`/`unhandledrejection`；5xx/网络错误文案追加请求编号，响应头 `X-Request-Id`/`X-Trace-Id` 作为兜底；报告轮询改为指数退避 + 页面隐藏暂停 + 慢任务提示，并支持浏览器完成提醒。
- **账号自助**：新增 `POST /api/v1/users/me/delete`（密码确认、软删、清空联系方式、吊销全部登录态）与 `GET /api/v1/users/me/export`（资料、额度流水、简历/JD、会话、回答与报告 JSON）。
- **产品能力**：新增单题追问（每场最多 2 次，计入题量、不额外扣额度，AI 不可用时确定性降级）；报告页新增分数趋势与弱项专项练习入口；平台降级时顶部提示 AI/工具能力状态。
- 补 `TraceMdcFilterTest`、`UserMdcFilterTest`、`MdcTaskDecoratorTest`、`AccountDeletionServiceTest`、`FollowUpServiceTest`。

### v2.27（2026-11-17）——JD AI 每日限额与调用记账

- 新增 `JdAiGuard` 与配置 `oc.interview.jd.ai-daily-limit`（默认 20，0=不限）：按用户按天计数（Redis key `oc:quota:daily-jd-ai:{userId}:{date}`，TTL 2 天），覆盖「手工创建 JD 结构化 / JD 匹配 / 网页导入预览」三个真正触发模型的入口；确认预览结果不重复计数，Redis 故障时降级放行。
- `JdStructurer` / `JdMatcher` 接入 `AiCallLogger`：成功记 `SUCCESS` + 真实 token/latency，降级记 `DEGRADED`（`UNAVAILABLE/EMPTY/INVALID_JSON`），异常记 `FAILED`；`provider/model` 为 NOT NULL，未实际调用时用 `unknown` 占位，保证降级行也能入库。
- 目的：JD 三条 AI 路径不再「零配额、零记账」，管理端 AI 调用日志与成本统计可以覆盖到这部分消耗。
- 补 `JdAiGuardTest`（限额 0 不限、未超限放行、超限 429、Redis 异常放行）。

### v2.26（2026-11-17）——订单生命周期对齐与取消订单

- 前端删除 5 分钟轮询上限，改用订单自身 `expiresAt`（缺失时 30 分钟兜底，只取首次下单值）；轮询网络异常改为暂停 + 「继续刷新」，不再谎报订单过期。
- 新增 `POST /api/v1/orders/{orderNo}/cancel`：仅 `CREATED` 订单调用 oc-pay 关单，终态幂等返回；渠道关单未确认时返回 409 与原因，前端明确提示「旧订单仍可支付」。
- 关闭弹窗、切换支付方式、返回选择、重新下单、组件卸载时 best-effort 关闭旧订单；下单按钮增加防连点，下单期间关闭弹窗会对迟到结果自动关单。
- 补 `PaymentOrderServiceTest` 与 `orders.test.ts`（截止时间计算、过期边界、`needsCancel` 状态矩阵、cancel 路径）。

### v2.25（2026-11-17）——发放额度前回查订单事实

- `oc-identity` 发放套餐前回查 `payment_order`：订单必须存在、`status=PAID`，且用户、套餐、金额与内部通知完全一致；不一致直接拒绝并记 ERROR，不再仅凭内部令牌与请求体授信。
- 新增只读端口 `PaymentOrderFactsPort` + `PaymentOrderFactsAdapter`（application 不依赖 infrastructure，满足 ArchUnit 分层规则）。
- 补 `PaymentEntitlementServiceTest`（订单缺失、未支付、三项不一致、重复通知幂等）。

### v2.24（2026-11-17）——发放失败终态与死信

- `notification_status` 增加终态 `DEAD`：`FAILED` 且本次为最后一次尝试时原子升级为 `DEAD`，`findPendingNotify` 只捞 `PENDING/FAILED`，避免终态被反复重试。
- 发放通知被 oc-app 以 4xx（除 408/429）永久拒绝时直接置 `DEAD` 并入队 `ENTITLEMENT_REJECTED`；重试耗尽入队 `ENTITLEMENT_DEAD`。两者都输出 ERROR，由 `PaymentOpsMonitor` 汇总告警。
- 目的：已付款订单不会再出现「8 次重试后静默放弃」的黑洞。

### v2.23（2026-11-17）——关单确认与回调交叉校验

- 关单语义收紧：`PaymentChannel.close()` 改为返回「渠道是否确认不可再支付」，微信/支付宝在渠道未启用或关单失败时返回 `false`；`PaymentSweeper` 只有「渠道明确未支付 + 关单确认」才置 `CLOSED`，不确定时保留 `CREATED` 重试，超过 `close-grace`（默认 24h）置 `FAILED` 并入队 `CLOSE_UNCERTAIN`，避免用户事后支付成功但本地已关单。
- 回调交叉校验：验签之外必须通过「订单存在 + 渠道一致 + 金额一致 + 渠道流水号非空」；微信增加 300s 时间戳窗口（`oc.pay.ops.notify-max-skew-seconds`），支付宝增加 `app_id` 校验。校验失败不改订单状态、写审计与 `payment_exception`、返回失败让渠道重试。
- 对账补记增加渠道金额比对；`/pay/mock` 增加运行期 prod profile 拒绝（启动期校验之外的第二道防线），`.env.example` 的 `OC_PAY_MOCK_ENABLED` 默认改为 `false`。

### v2.22（2026-11-17）——支付异常队列与巡检

- 新增迁移 `V202611180001__payment_reconciliation.sql`：`payment_exception` 人工处理队列表（同一订单同一类型唯一，重复命中保留首次现场），并把 `payment_order.notify_status` 放宽到包含终态 `DEAD`。
- 新增 `PaymentExceptionKind`（七类异常码）、`PaymentExceptionRecorder`（写库失败只告警，不中断回调应答）与 `PaymentOpsMonitor`（默认每 30 分钟统计 `OPEN` 异常并输出 ERROR 日志，作为告警接入点）。
- 目的：把「资金已发生但权益未发放/订单状态无法收敛」从一行 WARN 变成可查询、可告警、可人工收敛的记录；处理口径见 [payment.md](design/payment.md) §6.1。

### v2.21（2026-11-17）——套餐页接入支付下单与二维码

- 新增 `GET /api/v1/pay-channels`（登录态）：代理 `oc-pay` 渠道可用性，前端据此展示可用支付方式。
- 前端新增 `api/orders.ts`（下单 / 查单 / 查渠道，`Idempotency-Key`）、`components/plans/PlanCheckoutDialog.vue`（渠道选择 → 下单 → 二维码 → 2.5s 轮询 → 成功），`PlansPage` 升级按钮接通该弹窗并在支付成功后刷新额度；引入 `qrcode` 渲染二维码。本地模拟渠道提供“打开模拟支付页 / 复制链接”。
- 端到端验证（本地真实前后端 + oc-pay 模拟渠道）：下单 → 模拟支付 → 回调 → 额度发放，`user_quota` 由 FREE 3/3 变为 PRO 33/10002，`notify_status=NOTIFIED`；重复回调不产生重复流水（幂等命中）。
- 文档同步 [design/payment.md](design/payment.md) §9 与 [design/api.md](design/api.md) §3.2；README 限额与规模更新。

### v2.20（2026-11-17）——支付服务 oc-pay

- 新增聚合模块 `oc-pay`：`oc-pay-core`（契约 DTO、配置、内部令牌工具、`PayGateway`）+ `oc-pay-service`（订单生命周期、微信 v3 / 支付宝渠道适配与验签、退款、对账扫描、额度发放通知）。
- 渠道：微信支付 v3（Native 扫码，商户私钥 `SHA256withRSA` 签名、平台证书验签、APIv3 密钥 AES-GCM 解回调）、支付宝（`alipay.trade.precreate` 扫码、RSA2 签名与验签）、`MOCK`（仅本地，生产禁用）。
- `oc-app` 集成：`POST /api/v1/orders`（`Idempotency-Key` 必填，金额以服务端套餐为准）、`GET /api/v1/orders/{orderNo}`、内部 `POST /internal/v1/payments/notify`（令牌校验），支付成功幂等发放套餐与额度。
- 数据库：新增 `payment_order` / `payment_refund` / `payment_entitlement` / `payment_notify_log`（迁移 `V202611170001__payment.sql`）。
- 质量：oc-pay 单测 16 项（PEM 往返、微信/支付宝签名验签、AES-GCM 解密、回调解密、下单幂等、退款超额拒绝）；全量 `mvn test` 通过（含 Testcontainers 迁移校验与 ArchUnit）。
- 新文档 [design/payment.md](design/payment.md)；同步 [architecture.md](design/architecture.md)（ADR-013）、[api.md](design/api.md) §3.2、[data-model.md](design/data-model.md) §3、[deployment.md](ops/deployment.md)。
- 待办：前端套餐页接入下单与二维码展示；微信 JSAPI/H5 与支付宝电脑网站跳转支付。

### v2.18（2026-11-11）——UI 极简重构与设计系统 v1.4

- 设计 Token：中性墨灰表面 + 单一 ember 强调色（`--accent 14 60% 41%`），新增 `--text-2` 次级文本；语义色降饱和，分数四档语义色（≥8.5 success / 7.0–8.4 accent / 6.0–6.9 warning / <6.0 danger）保留。
- 字体：移除 Inter/JetBrains Mono，改系统字体栈；`text-2xs` 11→12px、`text-3xs` 10→11px；字距收敛。
- 组件：卡片/徽章/提示去阴影去渐变；移除 `shimmer` 与 `shadow-focus`，焦点环改为全局 `:focus-visible` accent outline；骨架屏改用 `pulse-soft`。
- 审查修复：恢复分数语义色（ScoreRing/DimensionBars/ReportQuestionItem/报告列表与最近报告）、输入控件焦点环、移动端 ≥44px 触控目标、浅色 `--muted` 对比度、theme-color 明暗双值、工作台视口填充与昵称文案。
- 工作台标题由时段问候改为当前周期；产品副标题去「AI」字样；报告详情恢复「报告 #id」标识。
- [dev/ui-system.md](dev/ui-system.md) 升级 v1.4（§2 Token/字体/阴影/动效、§6.4 工作台、§9 暗色、§10 文案同步）。
- 本主题按 [dev/pr-workflow.md](dev/pr-workflow.md) 尺寸门禁拆分多个 PR 顺序提交，本条目覆盖整个主题；审查记录随各 PR 附上。

### v2.17（2026-11-09）——PR 流程与门禁固化

- 新增 [dev/pr-workflow.md](dev/pr-workflow.md)：分支、PR 尺寸硬上限、独立评审、CI、合并、发布与例外规则。
- 新增 `.github/pull_request_template.md`：强制填写验证、风险/回滚、迁移、安全与尺寸声明。
- 仓库启用 `main` 分支保护：必须 PR、Backend/Frontend 两项 CI、分支最新、squash-only、禁止强推/删除；新增 `size:large`、`risk:high`、`hotfix` 标签。
- conventions/workflow/README 索引与门禁入口同步。

### v2.16（2026-11-09）——剩余审计项收尾与框架升级

- Spring Boot `3.3.5 → 3.5.16`、Spring Cloud `2023.0.3 → 2025.0.3`、MyBatis-Plus `3.5.16`、springdoc `2.8.16`；CI 与本地 `mvn verify` 通过，Testcontainers 集成测试恢复执行。
- 可信代理：应用仅当 `X-Proxy-Secret` 匹配 `AUTH_PROXY_SECRET` 时才采信 `X-Real-IP`；生产 Nginx 已配置该头，伪造 IP 不再影响限流。生产 profile 启动强制要求代理密钥，缺失/不匹配时每分钟告警并回退直连地址。
- 认证加固：未知邮箱执行 dummy BCrypt 以对齐耗时；限流计数改为 Lua `INCR+PEXPIRE` 原子写入；邀请名额检查加事务级 advisory lock。
- 管理端：可选 IP 白名单（`ADMIN_ALLOWED_IPS`）、可选 Prometheus Token（`METRICS_TOKEN`）、审计日志 append-only 触发器、每日数据保留清理（AI 日志 90 天/审计 3 年）。
- 管理端 UI：tab 键盘导航与 ARIA、平台策略保存确认、ADMIN 授予以停用确认对话框。
- 前端：单题 deadline/暂停剩余时间持久化；提交幂等键复用；PRO 无改写提示与重试文案修正；草稿随登出清理。
- 发布 `offercoach-app:0.8.11`、`offercoach-web:0.7.7`；生产迁移 `V202611090002` 已应用；代理密钥、审计不可修改验证通过。
- 未决：MinIO 官方镜像已从 Docker Hub 撤下，Compose 中 `minio:latest` 无法固定有效 tag，需改用其他镜像仓库或对象存储；管理端 MFA 仍待产品决策。

### v2.15（2026-11-09）——审计剩余项清理

- 报告生成租约：`interview_report.generation_token` 绑定 Worker；完成/失败写入必须携带当前 token，慢任务与重试不会互相覆盖；空反馈直接终态，不再空转 6 次。
- Access Token 版本化：JWT 写入 `ver`，停用/降权/重置密码时递增 Redis 版本，旧 Access 立即失效。
- 简历文件改为每次上传唯一 key，失败/重复清理只删自己的文件，避免并发互删。
- Redis PII 收敛：登录/注册限流与失败计数键改用 SHA-256 摘要；生产与本地 Redis 淘汰策略改为 `volatile-lru`；本地 Compose 端口绑回环。
- 隐私开关落地：`allow_raw_resume_to_llm` 支持保存与 `GET /users/me` 返回；关闭时简历解析跳过模型调用、仅走本地启发式，原文不外发。
- 新增迁移 `V202611090001`：补充报告 token、答题评分、会话清理、简历解析与题库岗位族表达式索引。
- 前端：单题剩余时间按 deadline 持久化，刷新按实际剩余恢复；暂停保留剩余时间、超时后不会重复触发；提交幂等键按题复用；PRO 无改写时不再误示升级；报告重试文案与后端一致。
- CI：新增 `.github/workflows/ci.yml`（后端 verify + 前端 check）与 Dependabot。
- 发布 `offercoach-app:0.8.9`、`offercoach-web:0.7.6`；生产迁移 `V202611090001` 已应用，Redis 策略切为 `volatile-lru`；隐私开关关闭时简历原文不再进入模型，计时器刷新/暂停行为修正；登录、工作台聚合、登出冒烟通过。
- 仍未完成：异地备份需要用户提供对象存储/远端；可信代理头需与 Nginx 部署配置同步；Spring Boot 等大版本升级另行评估。

### v2.14（2026-11-09）——审计遗留项复核修复

- 文档解析硬化：DOCX 增加 POI 单条目 32MB 限制与 30 万字符抽取上限，PDF 文本同样限制 30 万字符；新增超大 DOCX 拒绝测试。
- AI 网关 SSRF 收紧：配置保存与每次请求前解析主机，拒绝任一 A/AAAA 为回环/内网/CGNAT/链路本地/组播地址，并禁止 HTTP 客户端跟随重定向；新增地址分类测试。
- 前端请求超时：普通请求 30s、上传 120s，超时返回明确网络错误；刷新请求 10s 超时，且网络/5xx 不再清空会话，仅 401/403 触发失效跳转。
- 登出语义修正：服务端吊销失败时保留本地会话并提示重试；Access Token 过期时先刷新再登出，401/403 视为已失效本地清理；登出/会话失效时清理 `oc_draft_*` 草稿。
- 报告轮询：瞬时错误继续轮询，仅 404 停止；面试提交/跳过失败恢复计时器；放弃失败不再跳转工作台。
- 发布 `offercoach-app:0.8.7`、`offercoach-web:0.7.4`；生产登录、工作台聚合与登出冒烟通过。

### v2.13（2026-11-09）——评分算法 rules-v3

- 重做确定性评分：要点覆盖率、题目相关性、证据/取舍/边界为主要信号，长度只作弱信号；不再因关键词多或回答长而自动加分。
- 内容：参考要点覆盖 4 分主导，题目技能相关性、指标数量、技能/领域术语为辅助；完全跑题且有参考要点时 `content ≤ 3`、总分 ≤ 4。
- 结构：层次标记、分段/列表、句数、目标/行动/结果/复盘要素；单段超长文本扣分。
- 深度：原理/因果、替代方案与取舍、异常与边界、失败/复盘、量化指标、领域术语；不再以数字数量简单堆分。
- 匹配：按 JD 关键词命中数与覆盖率计算；无 JD 时给出中性分，避免所有题都被固定抬高。
- 语言：句数、平均句长、填充词与重复句扣分；< 40 字各维上限 5，空答接近 0。
- 校准样例：空答 0.1、普通回答 4.9、结构完整回答 7.6、专家级但未落到 JD 技能 6.9；关键词堆砌显著低于结构化回答。
- 跑题判定收紧：有参考要点但零覆盖且题目相关性为零即按跑题处理，单个技能词或数字不再能绕过 `content ≤ 3`、总分 ≤ 4；跑题时不再输出“没有明显跑题”的回退文案。
- 匹配分受内容分约束（`fit ≤ content + 1`），纯关键词堆砌的匹配分不会高于实际内容质量。
- 新增 6 个评分边界/校准测试（空答、<20 字、<40 字、跑题、要点覆盖、关键词堆砌、口头禅/重复句）；评分版本 `rules-v3`；发布 `offercoach-app:0.8.5`。

### v2.12（2026-11-09）——工作台聚合接口

- 新增 `GET /api/v1/dashboard`：一次返回额度、简历/JD 就绪状态、最近活跃会话、最近 4 份报告与近 20 份报告平均分；前端工作台不再请求简历/JD/面试/报告四个分页列表。
- 前端登录后刷新工作台的 API 调用收敛为 `refresh / me / quota / dashboard`，页面指标、最近报告、快捷步骤与额度展示保持一致。
- 抽取 `ReportMetaResolver`，报告列表/详情与工作台共用标题、公司、岗位推导，避免两套逻辑漂移。
- 发布：`offercoach-app:0.8.3`、`offercoach-web:0.7.2`；生产 `/dashboard` 冒烟返回 PRO 额度、4 个 JD、3 份报告、平均分 73.3；`index-C42T_-IH.js` 已上线。

### v2.11（2026-11-09）——工作台按钮与卡片重叠修复

- 根因：`AsyncState` 为多根组件，`class="mt-6"` 无法透传，工作台内容紧贴页头，右上按钮行视觉压住概览卡上沿。
- 修复：`DashboardPage` 显式包裹 `mt-6 flex flex-1` 容器，加载/错误/空态分支统一；其他页面用法保持不变。
- 发布：`offercoach-web:0.7.1`；1366px 视口实测页头底部 163px、卡片顶部 187px（24px 间距）；前端 typecheck/lint/26 项单测/构建全部通过。

### v2.10（2026-11-08）——生产发布 0.8.2 与旧服务清理

- 生产发布 `offercoach-app:0.8.2`：完成数据库备份、jar 校验、镜像构建、Compose 切换与启动检查；无新增 Flyway 迁移。
- 发布验证：演示/管理员登录、me/quota、刷新宽限补发（200）、登出后旧 Token 与新 Token 均 401、公共端点与 HTTPS 首页 200。
- 修复引导误报：已存在账号为 ADMIN 时不再要求配置密码匹配；非 ADMIN 存量邮箱仍需凭证匹配才提升。
- 清理 ECS 上的 `newapi`、`9router`：删除容器、`newapi-data`/`9router-data` 卷、两个镜像与 `/root/9r-backup` 备份（21 个文件）；保留 `ofm` 与其他服务。
- 回滚基线：保留 `offercoach-app:0.8.1`、`0.8.0` 镜像与 `compose.yaml.bak-before-0.8.2`，jar 备份在服务器 `/opt/offercoach/build/app/target/oc-app-0.1.0-SNAPSHOT.jar.prev-0.8.1`。

### v2.9（2026-11-08）——四路代码审计与第二批修复

- 四路只读子代理并行审计（认证安全、面试域、前端、数据运维），输出分级问题清单与建议顺序；报告：`docs/reviews/code-audit-2026-11-08.md`。
- 修复刷新吊销竞态：吊销先写 1 小时家族墓碑，`issueInFamily` 检查墓碑；登出/改密后并发补发无法重建家族。
- 修复管理端引导提权：已存在邮箱必须匹配 `oc.admin.password` 才提升 ADMIN。
- 修复 1 题 MIXED 出题崩溃：`QuestionPlan` 难度分配改为非负，新增单测；真实 1 题会话 READY。
- 修复闲置放弃退款口径（题目已生成不退）、看门狗覆盖并发 READY（条件更新）、作答/跳过推进失败未回滚（skip 事务化）。
- 脱敏：仓库内真实域名全部替换为本地地址/占位符。
- 未修复项按 P0/P1 记录于审计报告（异地备份、Compose 暴露面、代理头剥离、tokenVersion、SSRF、前端超时与计时器等）。

### v2.8（2026-11-08）——认证并发阈值、刷新宽限与报告即时生成

- 登录失败阈值严格化：账号维度改为“原子预占再校验”，并发错误密码从 17 次校验收敛为严格 5 次（20 并发复测 5×401 + 15×429）。
- Refresh 轮换增加 30 秒服务端宽限：Lua 脚本原子消费并写 used 标记；宽限内重复提交视为并发/多标签页刷新，同族补发，20 并发全部 200；登出/改密/停用后家族键删除，宽限不能绕过吊销。
- 报告生成即时触发：结束会话、评分落库、手动重试即 `kick` Worker，5 秒轮询仅兜底；评分仍在进行时改为可等待状态（不计失败次数，全部评分失败时报告失败并保留手动重试）；复测 3 账号并发报告约 0.63s、答题后立即结束 235ms 就绪。
- 回归测试：新增登录并发阈值与刷新宽限集成测试（Testcontainers；本机 Docker npipe 不可用时自动跳过，同类场景已用真实接口复测）。
- 报告：`docs/reviews/load-test-2026-11-08.md` 增补第二轮修复与复测。

### v2.7（2026-11-08）——本地并发压测与幂等修复

- 新增本地压测脚本：同邮箱注册、错误密码登录、并发创建会话、同题提交、反馈生成/读取、Refresh 轮换、500 并发读、并发生成报告、每日额度。
- 修复同题并发提交 500：唯一键冲突后事务 aborted（25P02）；回答插入改为 `ON CONFLICT DO NOTHING`，冲突统一返回 409 `DUPLICATE_ANSWER`。
- 修复并发反馈生成 500：`ON CONFLICT (request_id)` 未覆盖 `(answer_id, version)` 唯一键；改为无目标 `ON CONFLICT DO NOTHING`，并发生成全部幂等 200。
- 压测结果：500 并发读会话/题目全部 200，P95 251ms / 124ms；3 账号并发报告均 READY（约 6.2s）；每日额度串行验证为 3 次后 402。
- 未修复：登录失败阈值并发下可放宽（17 次后才锁定）、跨标签页并发刷新会登出失败标签页、报告 Worker 固定 5 秒扫描延迟。
- 报告：`docs/reviews/load-test-2026-11-08.md`。

### v2.6（2026-11-08）——题库审计、岗位族与出题相关性

- 题库审计（22,409 条）：无重复题干、无链接题；89% 为 DEPTH、56% 为 AI-Agent；19,682 条无参考答案；缺口主要是岗位族与维度分布，而不是脏数据。
- 岗位族修复：JD 启发式结构化写入 `jobFamily`；旧 JD 在出题上下文按标题/技能/简历摘要回退推断并归一化；检索按“主岗位族 + 通用 + 系统设计”召回，AI 题不再因岗位族为空而挤占后端 JD。
- 检索质量：优先有参考答案题目，参考要点与质量分加权；每维度保留基础候选，避免 top N 被 DEPTH 占满导致 PROJECT/BEHAVIOR 只能走模板。
- 模板去重：PROJECT 按项目与焦点轮换，FIT 按技能轮换，同一场面试不再出现重复模板题。
- 评估（top 20）：后端核心命中 65%→100%、非本域 50%→0%；前端 0%→95%、70%→0%；数据 20%→85%、60%→0%；AI 非本域 5%→0%。
- 真实会话验证：后端 JD 修复后 5/5 题来自后端岗位族（Java、Spring、Redis 项目、Kafka/QPS 项目、Maven 冲突），不再出现 AI 题或重复题。
- 产物：`scripts/qbank/clean-and-evaluate.mjs`、`data/question-bank/audit-report.md`、`questions-clean.jsonl`（含质量分与岗位族归一化）。

### v2.5（2026-11-08）——逐题反馈场景化

- 评分文案从“所有题一套模板”改为按场景生成：项目经历题看个人职责/结果/取舍；技术深度题看原理/取舍/边界；行为面试题看冲突/行动/复盘；岗位匹配题看动机/匹配证据/落地计划。
- 技术题再细分排查、性能、设计、原理四类焦点，各自给出不同追问方向；行为题区分协作冲突与复盘类。
- 参考答案要点会做语义覆盖判断（如“落地计划”可由 30/60/90 天计划覆盖），缺失要点会生成针对该题的改进项，而不是通用套话。
- 证据引用按场景挑选高信息量原句，并给出场景化理由；同一题的多条证据避免重复理由。
- 报告逐题拆解、反馈叙事与 PRO 改写模板同步更新；评分公式保持不变。
- 验证：新增 5 个场景单测；四类题目输出的一级优点/改进不再相同；生产 0.7.7 冒烟 4 题反馈分别指向个人职责、机制解释、评分契约与个人职责，长参考答案不再被误判为已覆盖；后端 `verify` 通过。
- 真实用户模拟（2026-11-08，林浩 / 张三简历 / 云帆科技 JD，5 题）：注册→上传 DOCX→JD 匹配 92.5→面试 5 题→报告 62.4；逐题反馈分别指向机制、Spring 生命周期、数据口径、接口契约、故障处置。模拟暴露并修复：结果指标未识别“下降/降到”、项目题未识别“我先/我在”、长参考要点被误判未覆盖、行为题在非冲突场景强推“冲突处理”、项目/行为兜底建议不随题目变化。已知：5 题中 2 题与后端岗位语义不匹配（题库检索按维度召回，缺岗位族/语义过滤）。

### v2.4（2026-11-08）——邀请注册、注册风控、运行时额度与审查修复

- 邀请注册：邀请码改为可选项；新增 `GET /users/me/invite` 邀请名片与 `/register?invite=CODE` 预填；邀请成功后邀请人 +1 次面试、被邀请人 +1 次解析（额度、每日/累计上限可由管理端调整）；同设备注册只记录关系不发奖励。
- 防脚本注册：注册页默认要求一次性算术验证码（`POST /auth/captcha`，Redis 5 分钟、单次消费、单 IP 每小时限发）；新增单 IP / 单设备每日成功注册上限与 `X-Device-Id` 设备指纹；注册开关改为运行时策略。
- 管理端：新增“平台策略”标签页与 `GET/PUT /admin/settings/quota`，可在线调节每日模拟面试/解析次数、月度免费额度、邀请奖励与注册风控阈值，写审计；策略 30 秒内生效。
- 额度：每日解析次数上限接入三条建档路径；周期到期自动续发月度免费额度并降级为 FREE（`PLAN_RENEWAL` 流水，幂等）。
- 审查修复：出题/解析/评分改用独立 `aiExecutor`，报告生成异步化不再占用调度线程；新增状态看门狗（PREPARING 超时置失败并退款、PARSING 超时置失败、SCORING 超时标记失败）；报告生成等待评分完成，避免提前结束导致缺题；报告逐题拆解补回答原文、五维分与证据/改写；题库导入与数据库剔除 UNLICENSED/NC/ND/GPL 来源（保留 22,409 条 MIT）。
- 数据：V8 `app_user` 邀请/设备字段、`invite_reward`、`platform_settings`；V9 清理非宽松许可题库。
- 前端：注册页按策略展示固定验证码/算术验证码/可选邀请码；设置页新增邀请卡（复制链接、奖励与剩余名额）；管理端新增平台策略面板。
- 验证：后端编译与测试通过；前端 26 单测、typecheck、lint、build 通过；题库过滤脚本保留 22,409 条。
- 仍未处理：无 Git/CI、备份仅本机、Redis 单实例与告警、管理端 MFA、Prompt 注入防护、压测与灰度。

### v2.3（2026-11-08）——四路审查、反馈去 SSE、报告导出文件名

- 四路独立审查报告：后端架构、前端 UX、红队安全、数据/运维，见 `docs/reviews/*-2026-11-08.md`。
- 反馈交付：答题后服务端异步预生成反馈，前端轮询 `GET feedback` 并一次性展示；SSE 默认关闭（`oc.ai.sse-enabled=false`），兼容接口保留。
- 面试恢复：题目接口返回 `answerId/answerText/durationMs`，刷新或重进后可恢复已答原文、时长与反馈；面试详情补 `jdTitle/jdCompany/reportId`。
- 报告导出：默认文件名 `公司-岗位-YYMMDD`（如 `云帆科技-高级后端工程师-261108`）；打印时展开逐题拆解、临时切浅色主题并隐藏应用外壳。
- 安全修复：X-Real-IP 取客户端 IP；管理员解锁清理 Redis 失败计数；停用/改密/改角色吊销 Refresh 家族；AI Base URL 禁止内网/本机（可配置）；放弃会话仅在未生成题目时退款；上传校验 PDF/DOCX 魔数、DOCX inflate ratio、PDF 页数上限。
- 凭据治理：文档移除演示口令/管理员邮箱；生产演示账号口令已轮换并保存到服务器 600 权限文件。
- 部署：app/web `0.6.1`；新增 `backup-before-final-20261108.dump`。
- 验证：轮询反馈 4/4、刷新恢复原文与反馈、报告字段、管理端 AI 配置/角色/解锁/重置密码；前端 26 单测、typecheck/lint/build；后端测试通过。
- 审查仍未处理：无 Git/CI、备份仅本机、题库许可（NC/ND/GPL）、月度额度续发、线程池隔离、状态看门狗、报告与评分对齐、题库检索仅前 200 行、报告逐题回答/证据、管理端 MFA 等。

### v2.2（2026-11-08）——LLM 在线切换与管理员权限扩展

- AI 运行时配置：新增 V7 `ai_runtime_config`；管理端 `GET/PUT /admin/ai-config` 与 `POST /admin/ai-config/test`，可在线切换启用状态、Base URL、主/小模型与 API Key，保存后立即失效缓存、约 30 秒内生效；DB 空字段回退 `oc.ai.*` 环境值。
- Key 安全：AES-GCM 加密存储（`v1:base64(iv||密文)`），根密钥来自 `OC_AI_SECRET`；接口只返回末 4 位掩码；保存/清除写审计，不含明文。
- 管理员权限：新增角色调整（USER/SUPPORT/ADMIN）、账号解锁、重置密码；禁止修改自己角色、禁止撤销最后一个管理员；用户列表返回剩余额度。
- 前端 `/admin` 新增 AI 配置标签；用户面板增加角色、解锁、重置密码与额度展示。
- 部署：app/web `0.4.0`；ECS 已设置 `OC_AI_SECRET`。
- 验证：AI 配置保存/掩码/密文/清除、测试接口 503（无 Key）、角色切换、解锁、重置密码登录、自我降级 403、demo 访问 403 均通过。

### v2.1（2026-11-08）——M5 管理端上线

- 后端 `oc-admin`：7 组管理 API（题库/用户/指标/AI 日志/Prompt/实验/审计），类级 `@PreAuthorize("hasRole('ADMIN')")`，写操作审计与额度补偿流水；管理员账号通过 `OC_ADMIN_*` 引导创建。
- 前端 `/admin`：概览、题库、用户、AI 日志、Prompt、审计六个标签；仅 ADMIN 可见入口与路由，非 ADMIN 返回 403/工作台提示。
- 数据：V6 新增 `prompt_template/prompt_version/ab_experiment/ab_assignment/admin_audit_log`，并预置 5 个 Prompt 模板。
- AI：评分/出题写入 `ai_call_log`；管理端发布的 Prompt 版本被 `PromptProvider` 以 60 秒缓存读取。
- 运维：`/opt/offercoach/backup.sh` 每日 03:15 全库 dump 保留 7 天；`cert-check.sh` 每周一检查 30 天到期；管理员凭据与邀请码分别存于服务器 600 权限文件。
- 验证：管理端登录、题库/用户/指标/Prompt/审计/AI 日志接口 200；Prompt 建版发布生效；demo USER 访问管理端 403；生产 M1–M4 冒烟继续通过。
- 已知：Prompt 评测/回滚、实验运行时与指标、题库重建索引、CI 发布链路未实现。

### v2.0（2026-11-07）——M1–M4 真实全栈上线

- 后端：M1 简历（PDF/DOCX/手录结构化、AI/启发式解析、置信度、软删、重解析限流）、JD 结构化/匹配；M2 会话状态机、题库 26,385 条导入、PostgreSQL trigram + 关键词检索、维度配比出题；M3 回答幂等、短期流令牌、SSE 五维评分与证据；M4 异步报告 Worker/重试、每日会话上限与额度结算。
- 前端：移除 mock 数据依赖，新增 `src/api` 客户端（内存 Access Token、401 单次刷新、并发去重、SSE fetch 流）；真实登录/注册/简历/JD/面试/报告/套餐/设置；简历导出 Markdown/JSON/PDF；全站页头与状态组件统一。
- 数据：V4 新增简历/JD/会话/题目/回答/反馈/报告/题库/AI 日志；V5 题库按 `(repo, path, md5(stem))` 去重。
- 部署：ECS 重建 app `0.2.0` / web `0.2.2`；新增 `/opt/offercoach/data` 持久卷与 `/data/qbank/questions.jsonl` 启动导入；生产注册改为邀请码。
- 验证：后端单测通过；本地与生产 HTTPS 冒烟均通过（注册/登录→简历→JD→出题→4 题作答→SSE→报告 READY→额度）；生产 SSE 首字 27ms。
- 已知：AI 密钥未配置，当前走启发式降级；题库 `campus`/`system-design-101` 为 NC/ND、`codejeet` 为 GPL，公开分发前需复核（`data/question-bank/LICENSES.md`）。

### v1.9（2026-11-07）——ECS 部署上线

- 域名 `<PUBLIC_DOMAIN>` 解析到 ECS `<ECS_PUBLIC_IP>`；Let's Encrypt 证书签发（有效期至 2027-02-05）。
- 前端、后端、PostgreSQL、Redis 打包为 Docker 镜像，Compose 启动；Nginx 反代 `/` 与 `/api/`。
- 后端 prod profile + 独立 JWT 密钥；演示账号通过 SQL 种子写入，额度 30/9999，`PLAN_GRANT` 流水各 1 条。
- 按用户要求停用 9router/newapi（restart=no，数据保留）；其他个人服务保留。
- 公网验证：首页/登录/报告/创建面试/套餐均 200；登录→me→quota→refresh→logout→失效全链路通过；app 8081 不对公网开放。
- 事故与恢复：资源/IO 卡顿导致 Cloud Assistant 掉线，经授权重启实例后恢复；已加 1GB swap。

### v1.8（2026-11-07）——全栈审计第一批修复

- 认证：注册 3/min/IP、登录 10/min/IP、5 次失败锁定 15 分钟；Refresh 家族与旧 Token 重放整族吊销；登出/黑名单失败不再伪装成功；Redis 故障吊销 fail-closed。
- 生产：prod 缺 JWT PEM 或启用 demo/固定验证码/非 Secure Cookie/localhost CORS 时启动失败；app 默认仅绑定 127.0.0.1，prod 显式 0.0.0.0。
- 边界：app 拒绝 `/internal/**`；网关移除无鉴权 AI 直连路由（恢复 M3 时再加 JWT）；403 返回完整 ApiError；Prometheus 端点可抓取；未知路径 404；null 字段显式输出。
- 数据：`app_user` 改部分唯一索引、注销允许清空联系方式；初始/存量额度补 `PLAN_GRANT` 流水（V3）。
- 前端：账号状态按 userId 隔离、重置不再删除账号、草稿切题/卸载/离开立即落盘；测试 28 例。
- 验证：后端 7 测试 + verify（Testcontainers 3 例因本机 Docker 版本跳过）；真实冒烟 7/7；Refresh 重放整族失效、登录锁定 429、404、内网 403；前端 28 测试与四组浏览器脚本。

### v1.7（2026-11-07）——后端规范生效与 M0 重构

- 后端规范融合验收生效：backend.md v1.2、conventions.md v1.3、api.md/architecture.md/testing.md/observability.md v1.2。
- 工程门禁：Spotless + Palantir（4 空格/120 列）、Checkstyle、JaCoCo、Java 21 Enforcer；Testcontainers 升 1.21.3。
- 账号层：`UserRepository` 端口 + 实现，Auth/User/Demo 不再直连 Mapper。
- 额度层：`QuotaRepository` 端口 + 实现，领域类型入 `domain/quota`；行锁、幂等、流水语义不变。
- 网关：`WebFilter` 在路由前拒绝 `/internal/**`（403）；新增 `application-prod.yml` 与 release SNAPSHOT 检查脚本。
- 验证：7 个测试通过、verify 通过、app+gateway 冒烟 7/7、内网 403、计划接口 200；Testcontainers 因本机 Docker 版本跳过，CI 可运行。

### v1.6（2026-11-07）——前端结构重构

- 面试间 831→298 行：抽出 `useInterviewRoom`（计时/流式/草稿/提交）与 RoomHeader、QuestionPanel、AnswerComposer。
- 创建面试页 718→158 行：拆出 ResumePicker、JdPicker、InterviewConfigPanel、SetupSummary，radiogroup 语义不变。
- 简历编辑器 529→223 行：六个分区组件化，校验/保存留在父组件。
- store 698→413 行：评分与报告聚合下沉 `domain/scoring.ts`，持久化与 mock 账号拆到 stores 子模块；`Resume.confidence` 收紧为分区键。
- 可访问性：设置开关热区 64×44、报告筛选/清除移动端 44px、“查看详情”触屏常显、hash 滚动尊重 reduced-motion。
- 验证：typecheck/lint 0、22 单测、build、四组浏览器脚本；草稿刷新恢复、热区与 390px 溢出断言通过。

### v1.5（2026-11-07）——前端规范对照与质量工具链

- 三路并行审查前端全量代码，对照现有规范与阿里《前端开发规范》，新增 [frontend-spec-audit-2026-11-07.md](reviews/frontend-spec-audit-2026-11-07.md)。
- 落地 ESLint 10 flat + Prettier（Tailwind 类名排序）+ Vitest，新增 `lint/format/test/check` 脚本；开启 `exactOptionalPropertyTypes` 与 `verbatimModuleSyntax`。
- 修复注册后无法再次登录、主题“跟随系统”失效、移动抽屉焦点逃逸、流式反馈无过程播报、语义色对比度不足、移动触控目标偏小。
- 字号/字距/缩放收敛为 Token（`text-2xs/3xs`、`tracking-*`、`scale-98`）；面试间三栏改为 `room`(1400px) 断点；修复 390px 下题号导航导致的横向溢出（`grid-cols-1` + `contain-layout` + `overflow-x: clip`）。
- 面试回答草稿停笔 3 秒入 `sessionStorage`；删除 `StatCard`/`todayIso`/`ApiError` 等死代码。
- 文档：frontend.md v1.3、ui-system.md v1.3、conventions.md v1.2，写入采纳/适配/不采纳清单与演示态例外。
- 验证：typecheck / lint（0 error 0 warning）/ 10 个单测 / build / 四组浏览器脚本全部通过；375px 与 1280px 无横向溢出。

### v1.4（2026-11-07）——工作台减法与简历版式/编辑

- 工作台 4 张彩色图标统计卡改为单条中性 `MetricStrip`，去掉“AI 模板感”；报告区新增 2 份种子报告并 `flex-1` 撑满左栏。
- 修复工作台 375px 下 6px 横向溢出（网格子项 `min-w-0`）。
- 简历新增 4 套**版式模板**（不是内容模板）：经典单栏、现代双栏、紧凑商务、极简留白；切换只改排版，实时预览。
- 简历新增内容编辑：标题、摘要、基本信息、教育、工作、项目、技能、证书；保存后置信度归 1，刷新保持。
- 修复公共 Dialog 的 `scale-in` 动画覆盖居中位移问题，改为淡入；Reka UI 焦点陷阱与关闭还原仍通过。
- 验证：typecheck/build 通过；核心 8 题流程、12 题、注册/上传/JD、暗色、移动端、版式切换、内容编辑全部通过，控制台 0 错误。

### v1.3（2026-11-07）——确定 UI 组件库与中性主题

- 选定 **shadcn-vue** 作为全站 UI 基座（Reka UI + Tailwind，源码归项目所有），不引入 Element Plus / Ant Design Vue / PrimeVue 全量库。
- 设计 Token 切为中性黑白 + 单一靛蓝强调色：主操作近黑/近白，`accent` 只做数据高亮；白底、细边框、弱阴影。
- Dialog 重构为 Reka UI 原语，焦点陷阱、ESC、关闭后焦点还原与 `aria-describedby` 由底层保证。
- 减法打磨：移除侧栏重复 CTA 卡、登录页网格/渐变装饰与指标卡，统一为更克制的排版。
- 重新构建并通过两轮 Playwright 验收（8 题完整流程、12 题、注册/上传/JD、暗色、移动端、Dialog 焦点），控制台 0 错误。
- 设计规范同步更新：`docs/dev/ui-system.md` v1.2。

### v1.2（2026-11-07）——前端独立演示版

- 新增 `frontend/`：Vue 3 + TypeScript + Vite + Pinia + Tailwind，mock-first，可脱离后端完成全流程。
- 页面：登录、注册、工作台、简历管理、目标岗位、创建面试、面试间、报告列表、报告详情、套餐、设置、404。
- 能力：8/12 题生成、计时、逐字流式反馈、停止/重试、跳过按 0 分计入、报告雷达/逐题拆解/改进计划。
- 设计：Design Token、亮/暗主题、响应式（375px 无横向溢出）、可见返回键与常驻顶层导航、Dialog 焦点陷阱。
- 验证：`pnpm typecheck`、`pnpm build`、Playwright Chrome 全流程 + 移动端 + 暗色 + 12 题 + 上传/注册，控制台 0 错误。
- 复查：[frontend-review-2026-11-07.md](reviews/frontend-review-2026-11-07.md) 记录 P1 修复与剩余 P2。

### v1.1（2026-11-07）——吸收 Lamburger ERP 文档规范

- 新增唯一索引 [README.md](README.md)：按“你要做的事”导航、全量地图、状态标记、维护规则、12 项待决清单。
- 新增 [overview.md](overview.md)（10 分钟总览）、[requirements.md](requirements.md)（一条需求一行，R-01~R-88）、[reference.md](reference.md)（借鉴来源与取舍）。
- 新增 [dev/conventions.md](dev/conventions.md)、[dev/workflow.md](dev/workflow.md)：跨端规范与契约冻结/变更流程。
- 文档重组：`design/`（产品/架构/数据/API/AI/难点）、`dev/`（规范/前端/UI/后端/测试/排期）、`ops/`（部署/CI-CD/观测）、`reviews/`（复查留档）。
- 拆分原“部署运维与可观测性”为部署、CI/CD、可观测性三份；补齐发布门禁与 Runbook 的独立入口。
- 数据模型补命名/类型/索引/软删/建表自查；后端补不变量与联调前自测清单；UI 补跨页面交互铁律。
- 一致性修复：额度事务唯一键处理、反馈版本与幂等、报告失败状态、Token 预算、PDF/支付/语音版本边界、账号注销与外键生命周期。
- 新增 [reviews/doc-consistency-review-2026-11-07.md](reviews/doc-consistency-review-2026-11-07.md)，记录修复项与遗留项。

### v1.0（2026-11-07）——首版

- 12 份文档：产品需求、系统架构、数据库与存储、API、前端规范、UI 设计系统、AI 与 Prompt、关键难点、后端实现、部署运维、测试验收、任务排期。
- 关键结论：Vue 3 + Spring Cloud + 独立 Python AI 服务；评分 SSE 网关直连；RAG 混合检索 + 重排；额度行锁 + 流水幂等；报告 Outbox 异步。

## 已知待办

> **只列当前还没做完的事。** 2026-11-28 按仓库现状逐条核过一遍——旧版把已经上线的工程骨架、主链路、管理端都还挂着未勾选，真没做的（E2E、向量化、依赖扫描）反而看不出来。

### 已完成（留档，不再当待办追踪）

| 事项 | 落地位置 |
|---|---|
| 工程骨架与 CI | `backend/` 17 个 Maven 模块；`.github/workflows/ci.yml`（后端 `mvn verify` + 8 服务启动冒烟、前端 typecheck/lint/unit/build、脚本语法、PR 依赖漏洞扫描） |
| 账号 / 额度 / 简历 / JD / 会话 / 回答 / 报告主链路 | `oc-identity` + `oc-interview`，端点见 [api.md](design/api.md) |
| AI 四条链（解析 / 出题 / 评分 / 报告） | `ResumeParseExecutor`、出题链路、`FeedbackService`、`ReportGenerationWorker`（含并发闸门与熔断） |
| 管理端：题库导入、Prompt 版本、实验、审计日志、AI 调用与指标 | `oc-admin`：`AdminQuestionService` / `AdminPromptService` / `AdminExperimentService` / `AdminAuditLogService` / `AdminMetricsService` |
| SSE 首字监控 | `ai_call_log.first_token_ms` + 管理端指标；SSE 默认关闭，反馈可轮询 |
| 支付（微信 v3 / 支付宝 / MOCK）与对账 | `oc-pay`，见 [payment.md](design/payment.md) |
| 根级 Compose 一键启动 | [compose.yaml](../compose.yaml)，见 [deployment.md](ops/deployment.md) §2 |

### 需要业务 / 负责人决策（不是代码问题）

| 事项 | 现状 | 谁来做 |
|---|---|---|
| 题库冷启动「300 人工 + 200 AI 生成后审核」与版权确认 | 库里已有 22,409 条已发布题目，但没按这个口径做过选型与版权确认 | 内容负责人 |
| 评分黄金集 200 条与标注指南 | 没有黄金集，评分质量目前靠抽检与回归用例 | 内容负责人 |
| D-01 ~ D-12 待决事项 | 12 条仍未关闭（供应商、定价、存储形态等） | 见 [docs/README.md](README.md) §4 |
| 隐私政策 / 用户协议 / 数据删除入口 | 代码侧已有数据导出与注销（`AccountDataController`、`AccountDeletionService`），法务文本没有 | 法务 |
| LLM 供应商零留存 / 不训练条款 | 未确认 | 采购 / 法务 |
| 简历与音频保留期 | **代码侧没有任何自动过期清理**：`DataRetentionCleaner` 只清 `ai_call_log` / `admin_audit_log` / `payment_notify_log`，简历原件与语音音频只有"注销账号"时才会删。保留期数值与清理策略都要先定 | 法务 + 后端 |
| 客服补偿与退款流程 | 退款接口与对账已就绪，处理流程未定 | 运营 |
| 域名 / 证书 / K8s 集群与 Secret 清单 | 生产是 Compose + 宿主 Nginx；K8s 清单只是目标架构 | 运维 |
| 支付宝沙箱验证 | 同步响应验签已实现，但只在自签密钥下证明过自洽；**上线前必须用沙箱跑一遍查询与退款** | 运维 / 支付 |

### 技术上还没做

| 事项 | 现状 |
|---|---|
| OpenAPI 文件与前端生成类型 | 运行时 `/v3/api-docs` 有（springdoc），仓库里没有落产物，也没生成前端类型 |
| E2E 主干 15 条与压测脚本 | 没有 E2E 目录、没有 k6 / JMeter；只有 `frontend/scripts/visual-*.mjs` 的视觉检查 |
| 题库向量化 / 语义检索 | 只有 `embedding_status` 列，没有 embedding 服务与向量库；检索靠 trigram + 标签 |
| 后端依赖漏洞扫描 | CI 只扫前端 lockfile；Maven 不锁传递依赖版本，后端留发布前人工审计 |
| 支付宝证书模式验签 | 只支持公钥模式 |
| `download-url` 的真实签名 URL | 现在返回需要 Bearer 的固定路径，却标 `expiresIn=900`，语义上是"临时签名 URL"却不是 |
| 生产版本落后 `main` | 线上版本落后：AI 工具能力与并发加固只在本地 / 真实 API 验过，未发布 |
| K8s 目标态落地 | `readOnlyRootFilesystem`、Secret 清单、金丝雀与自动回滚都没落 |

### 刻意不做（有理由，别当缺陷"修"）

- **`AiBudgetGuard` 预算统计故障时放行**：把统计故障放大成业务故障更糟；要改成 fail-closed，得先有"预算表高可用"这个前提。
- **自托管 CJK 字体**：HarmonyOS Sans 的许可禁止修改字体文件，已改为 OFL 的 `@fontsource-variable/noto-sans-sc`（`unicode-range` 分片）；不要再往仓库塞整段字体。

## 统计

| 项 | 数值 |
|---|---|
| 文档数 | 38（根级 5 + design 10 + dev 8 + ops 3 + reviews 12） |
| 设计文档 | 10 |
| 开发文档 | 8（含 conventions/workflow） |
| 运维文档 | 3 |
| 复查留档 | 12 |
| 需求条目 | 66（R-01~R-88 编号分段） |
| 待决事项 | 12 |

> 统计随文档变更更新；以文件系统与需求表实际条目为准。