# 前端架构与 UX 独立审查（2026-11-08）

> 范围：`E:/My_project/frontend` 全量源码、`docs/` 设计与规范、必要的后端 DTO/接口对照。
> 方式：只读审查；未启动浏览器、未部署、未修改业务代码。命令级验证（typecheck/lint/test/build）未由本次执行。
> 快照：2026-11-08 01:07。审查期间工作树被并发修改：`00:57–01:01` 已落下“反馈轮询 + 报告文件名”初版，`01:03` 又调整了 `useInterviewRoom`；本报告以该时点后的文件为准。`dist/` 为 01:04 的产物。
> 结论：未发现 P0；P1 4 项，P2 25 项，P3 25 项。

## 0. 摘要（最多 5 条）

1. **轮询反馈已替换 SSE，但最关键的恢复场景仍不可用**：`answerId` 只保存在内存，刷新/离开再进入面试间后，所有已答题的反馈都无法加载；用户看到“本题已完成”，却永远没有评分内容（P1）。
2. **报告打印目前不具交付质量**：暗色主题会以浅色文字打印到白纸，应用页头/移动底栏/额度控件进入纸张，`<details>` 只展开第一题，逐题拆解在 PDF 中缺失（P1）。
3. **两处契约缺口直接削弱报告与面试间的可信度**：面试详情不返回 `jdTitle/jdCompany/reportId`，页头永远是“模拟面试/目标公司”，刷新后“查看报告”入口消失；报告的 `questionBreakdown` 不返回题目/回答/反馈，页面承诺的“回答、证据、建议”落空（P1）。
4. **报告文件名初版已可用，但不可靠**：日期从 UTC ISO 截取（东八区凌晨会差一天）、只在点击按钮时改 `document.title`（Ctrl+P 不生效）、1 秒后恢复可能与打印预览/路由标题相互覆盖；浏览器也不保证把 `document.title` 作为保存文件名（P2）。
5. **安全基本面较好**（无 `v-html`、导出 HTML 全量转义、Access Token 仅内存、401 并发去重有单测），但答题草稿长期留在 `localStorage` 且结束/放弃后不清除；设置页“允许使用简历原文”在后端没有对应字段，开关实际空转（P2）。

## 1. 方法与判定口径

- 先看 `src/api` 客户端与 `docs/design/api.md`、`docs/design/admin-api.md`，再逐页核对页面状态机与组件边界；关键契约用后端 DTO/Controller 复核，避免只信前端类型。
- 重点场景：空数据、显式 `null`、401 刷新竞态、非 ADMIN 管理端、报告生成中刷新、反馈分析中切题/刷新、打印/另存 PDF。
- 级别：P0 阻断或数据丢失；P1 核心流程错误或承诺无法兑现；P2 明确缺陷、边界错误或安全/隐私风险；P3 体验、可维护性与工程债。
- 说明：`src/api/stream.ts` 已不再被业务引用，但文件与导出仍在；本报告将其视为“死代码”，而不是当前运行时路径。

## 2. 发现清单

### 2.1 架构与状态

**A-01｜P2｜刷新/静默 reload 会把单题计时与作答时长重置**
- 证据：`frontend/src/composables/useInterviewRoom.ts:345`（每次 `reload` 都把 `remaining` 重置为完整时限）、`:304-305`（切题重置 `questionStartedAt`）、`:383`（`durationMs` 由内存起点计算）；`frontend/src/pages/InterviewRoomPage.vue:139-142`（重新进入只 `reload`）。
- 影响：答题中途刷新，倒计时回到 180 秒，`durationMs` 明显偏小；暂停/恢复虽保留了剩余秒数，但刷新即丢失真实进度。报告中的“用时”因此不可信。
- 建议：把 `questionStartedAt`/`remaining` 按 `sessionId+questionId` 写入 `sessionStorage` 并在恢复时校验；更稳妥的是后端在题目/会话 DTO 返回 `askedAt` 或 `elapsedSec`，前端倒计时以服务端时间为基准。

**A-02｜P2｜题目列表失败被吞掉，页面呈现“暂无题目”而非错误**
- 证据：`frontend/src/composables/useInterviewRoom.ts:338-341`（`getQuestions(...).catch(() => [])`），`InterviewRoomPage.vue:341-352` 的空态。
- 影响：题目接口 403/500/网络失败时，会话已加载但题目为空，用户看到“本场面试暂无题目”，无法区分“仍在生成”与“请求失败”，也没有重试按钮指向真实错误。
- 建议：分别保存会话与题目错误；题目失败时展示 `AsyncState` 错误并提供“重试题目”；仅在 `PREPARING` 且题目为空时展示生成中空态。

**A-03｜P3｜`ensureSession` 的 `restoreAttempted` 在 await 前置位，存在启动竞态**
- 证据：`frontend/src/stores/app.ts:121-130`；`frontend/src/router/index.ts:118-121`。
- 影响：首屏恢复期间若发生第二次导航，第二个守卫看到 `restoreAttempted=true` 而直接以“未登录”判定，可能把本可恢复的会话弹回登录页。
- 建议：把 `restoreAttempted` 改为 `restorePromise`，所有守卫 `await` 同一个 Promise；恢复失败后再置为已尝试。

**A-04｜P3｜401 重放仍 401 时不会清理会话，可能形成刷新风暴**
- 证据：`frontend/src/api/client.ts:222-235`（只重放一次）、`:182-189`（并发去重）、`:191-216`（刷新失败才清会话）。
- 影响：刷新成功但新 Token 仍被服务端拒绝（时钟偏移、角色/密钥轮换）时，每个轮询请求都会再次触发 `/auth/refresh`；页面保持“已登录”假象。若刷新 Cookie 已轮换，还可能产生额外刷新。
- 建议：重放 401 时清空 Token 并走 `sessionExpiredHandler`；记录失败请求使用的 Token，若全局 Token 已被其他请求刷新则跳过再次刷新。

**A-05｜P3｜刷新失败（含离线）直接强制登出，体验与离线能力偏硬**
- 证据：`frontend/src/api/client.ts:211-215`（`catch` 内无论 401 还是网络错误都 `clearAccessToken` + `notifySessionExpired`）。
- 影响：地铁/弱网下刷新请求失败即被送去登录页，尽管原会话可能仍在有效期内；用户需要重新登录才能继续。
- 建议：仅 401/403 触发会话失效；网络错误保留内存 Token 与页面，提示“网络异常，正在重试”，由下一次请求或手动重试恢复。

### 2.2 接口契约对齐

**C-01｜P1｜面试详情/列表缺少 `jdTitle`、`jdCompany`、`reportId`，页头与报告入口失真**
- 证据：`backend/oc-interview/.../dto/interview/SessionDetailResponse.java:8-24` 与 `.../SessionSummaryResponse.java` 均无上述字段；`frontend/src/api/interviews.ts:23-39`、`:92-119` 仍按这些字段映射；`frontend/src/components/interview/RoomHeader.vue:13-15,50-52`；`frontend/src/pages/InterviewRoomPage.vue:112-119,273`；`frontend/src/pages/DashboardPage.vue:263-267`。
- 影响：面试间永远显示“模拟面试 / 目标公司”，失去公司与岗位上下文；刷新后 `completedReportId` 为 `null`，“查看报告”按钮不出现，只能绕到报告列表；工作台“继续上次面试”同样无公司/岗位。
- 建议：后端在 `SessionDetailResponse`/`SessionSummaryResponse` 增加 `jdTitle`、`jdCompany`、`resumeTitle`、`reportId`（会话已聚合这些信息）；前端映射保持并补测试。短期可由前端按 `jdId/resumeId` 再查一次，但会引入额外请求，不推荐。

**C-02｜P1｜报告 `questionBreakdown` 只有摘要，页面承诺的“回答/证据/建议改写”不存在**
- 证据：`backend/oc-interview/.../dto/report/ReportDetailResponse.java:23,31-38`（`BreakdownItem` 仅 `questionId/answerId/feedbackId/seq/score/dimension/summary`）；`frontend/src/types/index.ts:417-428` 声明了可选的 `question/answer/feedback`；`frontend/src/components/report/ReportQuestionItem.vue:40-45,93-105,107-203` 依赖这些字段。
- 影响：接真实后端后，逐题拆解只显示“第 N 题”、分数与一句 summary；“我的回答”“做得好的地方/建议改进/证据引用/建议改写”全部为空或占位。报告页的核心卖点（可复盘证据）无法兑现。
- 建议：二选一并在契约中固定：后端把 `question`、`answer`、`feedback`（五维、strengths、improvements、evidence、rewrittenAnswer）放入 breakdown；或前端改为按 `answerId/feedbackId` 二次请求（逐题 N+1，不推荐）。若数据量考虑，可要求后端返回精简 feedback（证据 + 建议）。

**C-03｜P2｜报告详情契约/文档落后于后端与前端的新依赖**
- 证据：`backend/.../ReportDetailResponse.java:12-15` 已返回 `title/company/role/questionCount`；`frontend/src/types/index.ts:449-480`、`frontend/src/api/reports.ts:61-84` 已映射；`docs/design/api.md:584-611` 的示例仍无这些字段。
- 影响：新加入的“公司-岗位”文件名与报告标识依赖未文档化字段；其他消费者按文档实现会缺失。文档与代码继续漂移。
- 建议：更新 `docs/design/api.md` §8.1 示例与字段表，明确 `title/company/role/questionCount` 的兜底规则（后端已把空公司回填为“目标公司”、空岗位回填为“模拟面试”）。

**C-04｜P2｜管理端 `SUPPORT` 权限三方不一致**
- 证据：`docs/design/api.md:80` 与 `docs/design/admin-api.md` 开头均声明“ADMIN/SUPPORT 可读，写操作仅 ADMIN”；`backend/oc-admin/.../AdminUserController.java:33`（以及其余 7 个 Admin Controller）全部 `@PreAuthorize("hasRole('ADMIN')")`；`frontend/src/router/index.ts:124-131`、`frontend/src/components/common/AppShell.vue:37`、`.../navigation.ts:29` 只允许 `ADMIN`。
- 影响：SUPPORT 账号在文档承诺下应能只读进入管理端，但前端直接拦截、后端实际也 403；三方行为不一致，运营/客服无法按设计排障。
- 建议：产品先定版——若保持 ADMIN-only，修改两份文档；若要支持 SUPPORT 只读，后端读接口改为 `hasAnyRole('ADMIN','SUPPORT')`，前端引入权限矩阵（`canReadAdmin/canWriteAdmin`），隐藏或禁用写操作，并为 `/admin` 增加只读提示。

**C-05｜P2｜“去 SSE 改轮询”的契约与文档尚未同步**
- 证据：`frontend/src/composables/useInterviewRoom.ts:251-253` 已声明“不读取/渲染 token 流”；`backend/.../FeedbackGenerationListener.java:25-29` 在提交后异步预生成；但 `docs/dev/frontend.md:278,385` 仍以 SSE 为关键实现，`docs/design/api.md:526-575` 仍把 SSE 作为权威路径；`backend/.../AnswerService.java:114-124` 仍签发 stream token 并在响应中返回。
- 影响：后续开发者/联调方会继续按 SSE 设计；前端虽不再消费，但每次提交仍多一次 Redis token 写入，且 `/ai/feedback/stream` 仍可被调用，形成双路径。
- 建议：在本次改造收尾时同步文档与后端：标注 SSE 为兼容/待移除；`AnswerService` 去掉 stream token 与响应 `stream` 字段；保留一个版本周期的降级开关，避免旧客户端失联。

**C-06｜P3｜其余契约小漂移**
- 证据：`docs/design/admin-api.md:54` 写题库导入 multipart，实际 `backend/.../AdminQuestionController.java:113-119` 与 `frontend/src/api/admin.ts:232-247` 都是 JSON body；`docs/design/admin-api.md:32` 写 `24h|7d|30d`，`frontend/src/components/admin/OverviewPanel.vue:36-39` 用 `1d`，后端注释为 `1d/7d/30d`；`docs/design/api.md:604-608` 的 `match/pdf` 字段后端详情未返回。
- 影响：联调/验收时按文档构造请求会失败；报告详情缺失 `match/pdf` 时前端静默降级，用户看不到匹配前后对比。
- 建议：统一文档为实际契约；删除或标注未实现的 `match/pdf`；管理端 range 统一为 `1d`。

### 2.3 反馈交付（当前轮询实现）

**F-01｜P1｜刷新或离开再进入面试间后，已答题反馈不可恢复**
- 证据：`frontend/src/composables/useInterviewRoom.ts:48`（`answerIds` 仅内存）、`:288-292`（无 `answerId` 直接 return）、`:311-313`（已答题仅在有 `answerId` 时加载）；`backend/.../QuestionItemResponse.java:8-17` 不返回 `answerId`；`backend/.../FeedbackService.java:140-144` 的 GET 只接受 `answerId`。
- 影响：刷新页面、路由离开后重进、或提交后组件被重建，所有已答题显示“本题已提交/已答”，反馈面板却是空的“待作答”状态；用户无法补看评分，也不能“重新评分”（没有 id）。这是本轮改造最需要补的 P1。
- 建议：契约层给题目增加 `answerId`（以及可选的 `feedbackReady`），或提供 `GET /interviews/{id}/answers?questionId=`；前端在 `resetQuestionState` 中据此恢复并启动一次反馈轮询。过渡期可把 `sessionId -> questionId -> answerId` 存入 `sessionStorage`（不含回答正文），但只能解决同一浏览器刷新，不能解决换设备/清缓存。

**F-02｜P2｜切题或离开会取消正在进行的反馈轮询**
- 证据：`frontend/src/composables/useInterviewRoom.ts:298-300`（切题 `resetQuestionState` 调 `abortStream`）、`:239`（全局 generation +1）、`:264,267,271`（轮询每一步检查 generation）。
- 影响：用户提交后在“分析中”点左侧题目导航，等待被取消；原题反馈不会再拉取，直到回到该题。若用户不再返回，反馈永远不会出现。
- 建议：轮询应按 `questionId` 独立管理（`Map<questionId, { generation, controller }>`），切题只影响 UI 展示，不取消后台题目的轮询；同一题重复触发时才取消旧任务；卸载时统一取消。

**F-03｜P2｜`submitting` 覆盖整个 20 秒分析期，提交按钮长时间转圈**
- 证据：`frontend/src/composables/useInterviewRoom.ts:379` 置 `true`、`:395` `await waitForFeedback`、`:420` 才置 `false`；`frontend/src/components/interview/AnswerComposer.vue:82-85` 用 `:loading="submitting"`。
- 影响：POST 早已返回，但按钮仍显示“提交回答”加载态，最长 20 秒；用户会误以为回答没有提交，反复点击（被 guard 拦住但无反馈），同时“跳过”也被禁用。
- 建议：拆分 `submitting`（POST 生命周期）与 `analyzing`（反馈轮询）；POST 成功立即结束 loading，改为反馈面板/按钮区的“分析中”状态。

**F-04｜P2｜轮询缺少退避、取消与瞬时错误恢复**
- 证据：`frontend/src/composables/useInterviewRoom.ts:21`（固定 700ms）、`:280`（不可取消的 `setTimeout`）、`:262`（固定 20s 截止）、`:270-278`（除 404 外任何错误立即终止）；`frontend/src/api/client.ts:145-154` 支持 `signal` 但此处未使用。
- 影响：最坏一次答题约 28 次 GET；后台 Tab 仍轮询；网络抖动一次就显示失败（后端可能已生成）；卸载后仍可能多跑一轮。
- 建议：用 `AbortController` 贯穿请求与 sleep；退避 400ms → 800ms → 1600ms（上限 3s）；404 继续等待，5xx/网络重试 2 次后报错，401/403/422 立即终止；超时改为 30–45s 或从服务端 `estimatedReadyInMs` 推导。

**F-05｜P3｜SSE 遗留状态与“停止”入口未清干净，可能出现空白“完成”面板**
- 证据：`frontend/src/composables/useInterviewRoom.ts:16`（仍含 `streaming`）、`:43`（`streamText`）、`:544-548`（`stopStream` 可把 `connecting` 置 `done`，但不设置 `activeFeedback`）；`frontend/src/components/interview/StreamingFeedback.vue:11-18,73-76,125`（`done` 仅在 `feedback` 存在时渲染内容，`streaming` 分支已删除）；`InterviewRoomPage.vue:357-363` 仍传 `text`/`@stop`。
- 影响：虽然当前 UI 不再展示“停止”按钮，`stopStream` 仍可从残留绑定触发；一旦触发，面板显示“已完成”但内容区为空，`canAdvance` 又允许下一题，用户会丢失本题反馈。
- 建议：删除 `streamText`、`stopStream`、`streaming` 分支与父子 props；状态改为 `idle | analyzing | done | error`；`done && !feedback` 显示明确错误与重试，不允许静默空白。

**F-06｜P3｜`DUPLICATE_ANSWER` 的 `answerId` 只接受字符串，异常形态会死锁**
- 证据：`frontend/src/composables/useInterviewRoom.ts:406-414`；`:554-556`（无 `answerId` 时 `retryFeedback` 也直接 return）。
- 影响：若错误体把 `details.answerId` 序列化为数字（或缺少），`answerIds` 不会写入，之后既不能加载反馈也不能重试，“下一题”永远禁用。
- 建议：`String(details.answerId ?? '')` 归一化；无 id 时调用后端按 `questionId` 查询/补拉接口，并给出可操作的错误提示，而不是 `idle`。

**F-07｜P3｜异步反馈生成失败只写后端日志，前端只能等满 20 秒**
- 证据：`backend/.../FeedbackGenerationListener.java:27-35`（`catch` 后仅 `log.warn`）；前端 `:262-285` 只能超时报错。
- 影响：监听器线程池耗尽/异常时，所有答题都要等 20 秒才失败；“重新评分”通过 POST 同步补算可恢复，但用户不知道发生了什么。
- 建议：后端记录可观测指标并考虑失败重试/落库状态；前端错误文案说明“分析服务繁忙，可点击重新评分”，并在超时后自动改用 `generateAnswerFeedback` 一次（不扣额度、幂等）。

### 2.4 报告导出与文件名

**R-01｜P1｜打印/另存 PDF 的输出不完整且暗色主题不可读**
- 证据：`frontend/src/styles/main.css:1-96` 与 `tokens.css` 全文件无 `@media print`/`@page`；`tokens.css:24-45` 暗色 `--text: 210 40% 96%`；`ReportDetailPage.vue:194,232,246,263,277` 仅有零散 `print:border-0`；`AppShell.vue:87-175` 页头、`:227-256` 移动底栏没有 `print:hidden`；`ReportDetailPage.vue:293-298` 只有第一题 `open`；`ReportQuestionItem.vue:49-55` 使用原生 `<details>`。
- 影响：暗色模式下打印是“浅色字 + 白纸”（多数浏览器默认不打印背景），页头/额度/主题按钮/底部导航进入纸张；逐题拆解中除第一题外全部折叠，8 题报告只打印 1 题。PDF 无法作为复盘交付物。
- 建议：新增全局打印样式（见 §3.2），在打印前临时移除 `html.dark` 或覆盖打印 Token；`AppShell` 的 header/sidebar/footer/bottom-nav、`QuotaBadge`、Toast 全部 `print:hidden`；打印前展开所有 `details` 并在 `afterprint` 恢复；加 `@page { size: A4; margin: 12mm }` 与 `print-color-adjust: exact`。

**R-02｜P2｜文件日期按 UTC 截取，东八区凌晨会差一天**
- 证据：`frontend/src/utils/reportExport.ts:31-39`（对 ISO 字符串正则取前 10 位）；`:8-16`；`ReportDetailPage.vue:95` 传入 `generatedAt`；后端 `ReportController` 用 `toInstant().toString()`（UTC）。
- 影响：2026-11-08 00:30（Asia/Shanghai）生成的报告，`generatedAt` 为 `2026-11-07T16:30Z`，文件名写成 `...-261107`，与用户看到的报告日期不一致；用户设置里的时区完全未参与。
- 建议：`buildReportFileName` 增加 `timeZone` 参数，用 `Intl.DateTimeFormat('en-CA', { timeZone, year:'numeric', month:'2-digit', day:'2-digit' })` 取本地日期后再拼 `YYMMDD`；从 `store.user.timezone` 传入；补 UTC/+08:00 跨日单测。

**R-03｜P2｜只有点击页面按钮才改标题，Ctrl+P/浏览器菜单打印仍是通用名**
- 证据：`frontend/src/pages/ReportDetailPage.vue:91-106`（仅在 `onPrintReport` 内改 `document.title`）；`frontend/src/router/index.ts:148-150` 的标题固定为“报告详情 · OfferCoach”。
- 影响：用户用 Ctrl+P、菜单“打印”、或分享到系统打印时，默认文件名仍是“报告详情 · OfferCoach.pdf”；需求“公司-岗位-YYMMDD”只在一条入口生效。
- 建议：报告 `READY` 后把 `document.title` 设为 `buildReportFileName(...)`（或在其后追加产品名），卸载/路由变化时恢复；这样所有打印入口一致，也能让标签页直接体现报告身份。

**R-04｜P2｜1 秒恢复标题会与打印预览/路由标题赛跑，严重时覆盖新页面标题**
- 证据：`frontend/src/pages/ReportDetailPage.vue:96-105`（`afterprint` + 1s 兜底）、`:112-114`（卸载只停轮询，不恢复标题）；`router/index.ts:148-150` 在导航后写标题。
- 影响：部分浏览器打印预览初始化超过 1 秒，标题已被恢复，保存文件名退回默认；若用户在 1 秒内离开页面，兜底定时器仍会把旧标题写回，覆盖新路由标题。重复点击还会叠加监听器/定时器。
- 建议：删除 1s 兜底，改为 `afterprint` + `visibilitychange`/30–60s 长兜底；恢复前校验 `router.currentRoute` 仍是当前报告且 `document.title` 仍是本次生成值；在 `onBeforeUnmount` 中清理监听与定时器并恢复 `previousTitle`。

**R-05｜P2｜浏览器不保证 `document.title` 就是保存文件名，且没有确定性导出通道**
- 证据：`ReportDetailPage.vue:91-106`；`docs/design/api.md:616` 仅规划 V1.1 `POST/GET /reports/{id}/export`，后端当前无对应实现；`docs/dev/ui-system.md:454` 要求的打印浅色主题也未实现。
- 影响：Chromium/Firefox 通常用标题作为“另存为 PDF”默认名，但 Safari/iOS 与部分企业浏览器可能使用页面标题、URL 或“未命名”；用户最终文件名不可控。需求若要求严格的文件名，仅靠 `window.print()` 不可交付。
- 建议：把“确定性文件名”落到服务端 PDF 导出（`Content-Disposition: attachment; filename*=UTF-8''公司-岗位-YYMMDD.pdf`）；短期在 UI 文案中说明“浏览器可能修改文件名”，并保留打印兜底。若不做后端，评估引入客户端 PDF 库的体积与中文字体成本。

**R-06｜P2｜报告页/打印页没有展示 `company/role/title`，与文件名身份脱节**
- 证据：`ReportDetailPage.vue:119`（页头固定“面试报告”）、`:208`（Badge 固定“模拟面试”）；`Report` 已有 `title/company/role`（`types/index.ts:449-480`、`api/reports.ts:61-84`）。
- 影响：用户拿到 `云帆科技-高级后端工程师-261107.pdf`，但报告正文只有“报告 #60001”，无法从纸张确认公司与岗位；打印件缺少身份信息。
- 建议：页头/封面增加 `company · role · generatedAt`，打印样式保留该行；Badge 用 `role`，标题用 `report.title`；这也让文件名有可见依据。

**R-07｜P2｜同一路由不同 `:id` 复用组件时不重新加载，可能展示上一份报告**
- 证据：`frontend/src/pages/ReportDetailPage.vue:28-31,108-114`（仅 `onMounted` 加载，无 `watch(route.params.id)`）；`AppShell.vue:185-189` 的 `<component :is="Component" />` 未加 `:key`。
- 影响：报告 A → 报告 B 后浏览器后退/前进，URL 已是 B，页面仍是 A 的数据且轮询也可能停/错；`notFound` 状态同样不复位。
- 建议：`watch(reportId, () => { poller.stop(); report=null; loadReport() })`，或给路由视图加 `:key="route.fullPath"`；面试间同样存在该风险。

**R-08｜P2｜报告轮询失败静默、无上限，可能与文档节奏不一致**
- 证据：`ReportDetailPage.vue:40-50`（`catch { return false }` 直接停轮询，页面仍显示“生成中”）、`ReportListPage.vue:79-88`（同样静默停止，最多 50 条中的任一 GENERATING 会持续 3s 轮询）；`docs/design/architecture.md:261` 要求 2s/3s/5s、最多 90s。
- 影响：网络抖动一次后报告永远停在“生成中”，用户只能手动刷新；列表页一个卡住的 GENERATING 会导致整页长期轮询，移动端耗电/流量。
- 建议：轮询错误计数 + 指数退避，连续 3 次失败展示“状态未知，点击刷新”；总时长 90s 后停止并提示；页面隐藏时暂停（`visibilitychange`）。

**R-09｜P3｜文件名清理的 Windows 边界与总长度未处理**
- 证据：`frontend/src/utils/reportExport.ts:19-29`（只替换非法字符、折叠空白与连字符，`slice(0,60)`）。
- 影响：`CON`/`PRN` 等保留名、结尾点/空格（Windows 不允许）、两个 60 字符片段叠加超过常见 120–150 字符的舒适区；某些系统会截断或拒绝。
- 建议：trim 结尾 `.`/空格；对保留名加前缀；总长度限制在 ~100 字符；对 `company/role` 只保留可打印字符；补表单测试。

**R-10｜P3｜报告详情的统计口径与空值文案**
- 证据：`ReportDetailPage.vue:223-225` 用 `breakdown.length` 作为“覆盖题目”，而已有 `questionCount`；`:52-57` 在 `overallScore === null` 时按 0 分给出“建议先打磨”；`ImprovementPlanList.vue:9-11` 静默截断到 5 条；`StreamingFeedback.vue:198-200` 无 evidence 时仍渲染空引号。
- 影响：覆盖题目与实际配置题数不一致；空分数被赋予负面结论；改进计划超过 5 条被隐藏；反馈证据区出现空白引号。
- 建议：优先用 `questionCount`；`null` 显示“暂无总分”；改进计划显示“仅展示前 5 条，共 N 条”或分页；evidence 为空时显示“本题无原文证据”。

### 2.5 UI/UX 一致性

**U-01｜P2｜管理端表格在移动端仍横向滚动，与设计规范“md 以下转卡片”不符**
- 证据：`UserPanel.vue:429-430`（`min-w-[980px]`）、`AuditLogPanel.vue:74-75`（980px）、`AiCallPanel.vue:166-167`（1060px）、`QuestionBankPanel.vue:531-532`（960px）、`PromptPanel.vue:330-331`；`docs/dev/ui-system.md:445` 要求 md 以下转卡片。
- 影响：375px 下必须横向拖动 2–3 屏才能看到操作列，首列（用户/题目）很快滚出视野；触屏用户难以完成停用、改角色等操作。
- 建议：为管理端提供卡片布局（`md:hidden` 卡片 + `hidden md:block` 表格）或至少首列 sticky；本次可先做 `sticky left-0` + 行内操作折叠菜单，降低改造成本。

**U-02｜P2｜反馈区自动滚动阈值 1279px 与三栏断点 1400px 不一致**
- 证据：`InterviewRoomPage.vue:283`（`room:grid-cols-[...]`，`room=1400px`）、`:396`（`max-width:1279px` 才滚动到反馈）；`StreamingFeedback.vue` 在 1280–1399 位于答题区下方。
- 影响：1280–1399 的笔记本提交后看不到反馈区，误以为没有反馈；用户需要手动下滑。
- 建议：阈值改为 `(max-width: 1399px)`（与 `room` 对齐），或用 `window.matchMedia('(max-width: 1399px)')`；更稳妥是检测反馈元素是否在视口外再滚动。

**U-03｜P2｜轮询反馈没有持续的无障碍状态播报**
- 证据：`StreamingFeedback.vue:95-112`（分析中无 `role=status`）、`:223-225`（`aria-live` 区域仅在 `done && feedback` 时插入）；`AnswerComposer.vue:49-59` 状态行无 live 属性；原 SSE 的 2 秒节流播报已随实现删除。
- 影响：读屏用户点击提交后没有“正在分析”的反馈，完成后动态插入的 live region 也可能不被播报；只能靠 Tab/探索发现结果。
- 建议：在 `StreamingFeedback` 根节点放一个常驻 `role="status" aria-live="polite"` 的 `sr-only` 区域，状态变化时写入“正在分析第 N 题”“反馈已生成，本题 X 分”“分析失败，可重新评分”；避免逐字播报，保持一次性。

**U-04｜P2｜报告列表的搜索/筛选只作用于前 50 条，与“共 N 份”口径冲突**
- 证据：`ReportListPage.vue:81,94` 固定 `size:50`；`:65-75` 本地过滤；`:46` 指标用后端 `total`；`:233-235` 文案用 `filteredReports.length`。
- 影响：报告超过 50 份时，搜索找不到更早的报告、时间筛选结果不完整；页面同时展示“共 50 份（过滤后）”和“报告数：total”，数据自相矛盾。
- 建议：搜索/时间范围交给后端查询（`keyword/from/to`），或至少显示“仅搜索最近 50 份”并分页；统计文案统一用 `total`/`filtered.length` 的明确语义。

**U-05｜P2｜工作台部分接口失败被静默吞掉，且遗漏 PREPARING 会话**
- 证据：`DashboardPage.vue:175-204`（5 个请求部分失败时 `loadError=null`，各区块显示 0/空态）；`:88-95` 的 `activeSession` 只筛选 `IN_PROGRESS/PAUSED/READY/CREATED`；`InterviewSetupPage.vue:177-185` 的冲突恢复列表却包含 `PREPARING`。
- 影响：额度/报告请求失败时，工作台显示“0 次额度”“还没有报告”，用户误以为数据丢失；出题中的会话在工作台不可见，但创建新面试会被 409 拦住，用户找不到“继续”入口。
- 建议：每个区块独立错误/重试态；`activeSession` 纳入 `PREPARING`（显示“出题中”并链接房间）；对部分失败显示轻量警告条。

**U-06｜P3｜375px 顶栏控件拥挤，额度在加载前显示 0**
- 证据：`AppShell.vue:87-140`（返回、菜单、标题、额度、主题、用户）、`QuotaBadge.vue:9-10`、`stores/app.ts:9-17` 的 `EMPTY_QUOTA`。
- 影响：标题被压缩；额度未加载时显示“0”和低额度警示色，造成误导。
- 建议：`QuotaBadge` 在 `quotaLoaded=false` 时显示骨架/“—”；375px 隐藏额度文字只留图标，或移入用户菜单。

**U-07｜P3｜部分触控目标仍小于 44px**
- 证据：`Dialog.vue:58-65`（关闭按钮 `h-8 w-8`）、`Input.vue:65-73`（密码显隐 `h-8 w-8`）、`Pick` 类图标按钮；`docs/dev/ui-system.md` 要求 ≥44×44。
- 影响：移动端关闭弹窗、切换密码可见性容易误触；弹窗关闭按钮尤其关键。
- 建议：统一 `max-sm:h-11 max-sm:w-11`（Button 已采用该模式），或扩大伪元素热区。

**U-08｜P3｜11px 弱化文字在浅色 `surface-2` 上对比度接近下限**
- 证据：`tokens.css:7`（`--muted: 215 16% 47%`）、`:4`（`--surface-2: 210 20% 96%`）；`tailwind.config.ts:51` 的 `2xs: 11px`；`Badge.vue`、`MetricStrip.vue`、各表格大量使用 `text-2xs text-text-muted`；`Alert.vue:29-30` 描述使用 `opacity-80`。
- 影响：按 HSL 估算，muted 在 surface-2 上约 4.3:1，低于 WCAG AA 对普通文本 4.5:1；11px 字号进一步降低可读性。Alert 描述再叠加 80% 透明度更弱。
- 建议：浅色把 `--muted` 调深至约 42–44% 亮度，或为 `surface-2` 上的 caption 使用 `text-text`/专用 `--muted-strong`；Alert 描述不要用 opacity。

**U-09｜P3｜主题在首屏可能闪光，且 `theme-color` 与品牌色不一致**
- 证据：`index.html:1-18` 无首屏内联主题脚本；`main.ts:8` 的 `initTheme()` 在模块加载后执行；`index.html:6` `theme-color=#4f46e5` 而浅色品牌为近黑。
- 影响：深色系统用户首帧可能看到白底；移动端浏览器地址栏颜色与页面不统一。
- 建议：在 `<head>` 加一段内联脚本读取 `oc_theme_pref`/`prefers-color-scheme` 并设置 `documentElement.classList`；`theme-color` 按主题动态更新。

**U-10｜P3｜JD 匹配结果刷新即丢失，`matchScore` 被忽略**
- 证据：`JdPage.vue:38,50-55,145-147`（`matches` 仅内存）；`api/jds.ts:32-41,61-63`（`mapJd` 不读 `matchScore`，详情也不返回完整 match）；后端 `JdDetailResponse` 仅返回 `matchScore`。
- 影响：刷新后“已分析 0 / N”，已选岗位回到“还没有匹配分析”，但服务端其实存有分数；用户会重复运行匹配（可能重复计费/耗时）。
- 建议：`Jd` 增加 `matchScore` 并展示“上次匹配 X 分（查看详情需重新运行或后端返回完整 match）”；若产品要求完整匹配持久化，后端需存 match 详情。

**U-11｜P3｜题目编辑看不到已有参考答案；简历编辑器关闭不提示未保存**
- 证据：`api/admin.ts:168-184` 列表映射含 `referenceAnswer`，但后端 `AdminQuestionController.toResponse` 不返回该字段；`QuestionBankPanel.vue:175-186` 打开编辑时只能拿到空值。`ResumeEditor.vue:251-252` 取消直接关闭，无 dirty 确认。
- 影响：管理员无法查看/修改已发布题目的参考答案（保存时会保留空值不覆盖，但看不到）；简历大表单误关即丢失草稿。
- 建议：题目详情接口返回 `referenceAnswer`（或编辑时单独 GET）；编辑器记录 dirty 并在关闭/ESC/遮罩点击时确认。

**U-12｜P3｜生成中的报告在“最近报告”显示 0 分环形图**
- 证据：`RecentReportItem.vue:18` `:score="report.overallScore ?? 0"` + `label="总分"`；列表页已用“生成中”标签（`ReportListPage.vue:253-256`）。
- 影响：用户看到 0 分，误以为报告已出分且极低。
- 建议：`overallScore === null` 时显示占位（“—”）或隐藏环形图，label 用状态。

**U-13｜P3｜Toast 在登出/切换账号后仍可能残留**
- 证据：`composables/useToast.ts:5-12`（全局数组，无 clear）；`stores/app.ts:132-140` 登出不清理。
- 影响：旧账号的操作提示（含报告标题、邮箱等）在新账号页面短暂出现，属于跨账号信息泄露的边缘场景。
- 建议：`clearSession()`/logout 时清空 toasts；敏感描述避免放邮箱/标题。

### 2.6 安全

**S-01｜P2｜答题草稿长期保存在 `localStorage`，结束/放弃后不清除**
- 证据：`useInterviewRoom.ts:19,154-180,191-207`（`oc_draft_<session>_<question>` 明文）；`:518-541`（finish）、`:502-516`（abandon）均未清理所有草稿；`:591-596` 卸载只 flush 当前题。
- 影响：回答原文（可能含公司内部信息、个人经历）长期留在共享电脑/浏览器配置中；已结束会话的草稿永远不会被读取，却持续暴露。隐私政策若声明“草稿仅本地临时保存”将不准确。
- 建议：为草稿加 TTL（如 7 天）与清理策略；finish/abandon 成功后清除该会话全部 `oc_draft_*`；登出时清理；在设置页提供“清除本地草稿”。

**S-02｜P2｜“允许使用简历原文”开关后端无字段，UI 承诺空转**
- 证据：`SettingsPage.vue:34,84,246-269`（开关与“关闭后仅保留结构化摘要”的文案）；`stores/app.ts:142-152`（本地合并，刷新即回退）；`types/index.ts:22-24`；后端 `MeResponse.java:8-17` 与 `UpdateMeRequest.java:9-13` 均无 `allowRawResumeToLlm`。
- 影响：用户以为关闭后简历原文不会进入 LLM，实际 PATCH 被 Jackson 忽略（Spring 默认忽略未知字段），刷新后开关回到开启；这是隐私控制层面的错误承诺。
- 建议：要么后端实现字段与执行链路（并在 `MeResponse` 返回），要么前端移除开关与文案；在契约未实现前不要展示该承诺。可先改为 disabled + “即将开放”。

**S-03｜P3｜登录回跳地址的反斜杠变体未拒绝**
- 证据：`LoginPage.vue:60-65`（只拒绝不以 `/` 开头与 `//` 开头）。
- 影响：`/\evil.com` 这类值在部分浏览器/中间层可能被归一化为协议相对 URL；当前是 SPA `router.push`，风险低，但属于纵深防御缺口。
- 建议：拒绝以 `/\\` 开头、包含控制字符或 `\\` 的 redirect；只接受站内已知路由白名单更稳妥。

**S-04｜P3｜SSE 遗留代码仍把短期 token 放 URL 查询串**
- 证据：`src/api/stream.ts:63-73`（`url.searchParams.set('token', info.token)`）；`src/api/index.ts:11` 仍导出；`AnswerService.java:114-124` 仍签发。
- 影响：当前无业务引用，但一旦被复用，短期 token 会进入代理/网关访问日志；与“不要把长期 Token 放 URL”的规范相邻，容易被误用。
- 建议：随轮询改造删除 `stream.ts`、`api/index.ts` 导出与 `FeedbackStreamInfo`/`AnswerResult.stream`，后端同步停止签发。

**S-05｜P3｜审计日志前后快照可能包含未脱敏 PII**
- 证据：`AuditLogPanel.vue:106-121` 直接展示 `before/after` JSON（截断 + title）；`docs/design/admin-api.md:11` 声称已脱敏。
- 影响：邮箱、昵称、额度原因等可能出现在表格 title 中；管理端大屏/截图外泄风险。
- 建议：后端负责脱敏并在契约中列明字段；前端对 `before/after` 提供“查看详情”弹窗而非 title 全文，避免悬停即泄露。

**S-06｜P3｜跨域部署假设未在代码/文档中显式约束**
- 证据：`api/client.ts:193,257` 对所有请求 `credentials:'include'`；`docs/design/api.md:79` Refresh Cookie `SameSite=Lax`、Path `/api/v1/auth`。
- 影响：若 `VITE_API_BASE_URL` 指向不同站点，Lax Cookie 不会随跨站 fetch 发送，刷新必然失败；当前前端没有检测/提示。
- 建议：部署文档明确“前端与 API 必须同站（同 registrable domain）”；启动时检测 `new URL(API_BASE).origin !== location.origin` 并在控制台/健康检查中告警。

**S-07｜P3｜缺少 CSP/Referrer-Policy 等安全头（服务端职责）**
- 证据：`index.html:1-18` 无 CSP meta；前端无 `Referrer-Policy` 配置。
- 影响：若未来引入 Markdown/富文本渲染，缺少 CSP 纵深防御；报告/简历页面 URL 可能通过 Referer 外泄 ID。
- 建议：由网关/静态托管下发 CSP（至少 `default-src 'self'`、`connect-src` 白名单）、`Referrer-Policy: strict-origin-when-cross-origin`；前端避免内联脚本（当前 Vite 产物满足）。

> 正向核对：全仓无 `v-html`/`innerHTML`/`insertAdjacentHTML`（ESLint `vue/no-v-html` 为 error）；`utils/resumeExport.ts:23-31,150-294` 对所有用户/解析内容转义后再 `document.write`，`data-layout` 也转义；管理端 Prompt 内容使用 `<textarea>`，审计 JSON 使用文本插值。未发现可利用的 HTML 注入点。

### 2.7 性能与工程

**E-01｜P2｜轮询/反馈状态机没有自动化测试，新增代码风险最高**
- 证据：`frontend/src` 仅 5 个测试文件（`api/admin.test.ts`、`api/client.test.ts`、`utils/format.test.ts`、`utils/reportExport.test.ts`、`utils/resumeExport.test.ts`）；`vitest.config.ts:5-8` 只收 `src/**/*.test.ts`；`package.json` 的 `check` 依次 typecheck/lint/test/build。
- 影响：新增的 404-as-pending、20s 超时、切题取消、DUPLICATE_ANSWER、刷新恢复均无回归保护；后续调整极易破坏核心答题流程。
- 建议：用 fake timers 为 `useInterviewRoom` 写单测（404→200、超时、卸载、切题、重复提交、answerId 缺失）；为 `buildReportFileName` 补时区跨日测试；为打印标题恢复写纯函数测试（把 title 管理抽成 composable）。保留 `client.test.ts` 的并发刷新测试。

**E-02｜P2｜`usePolling` 的 `start()` 在旧 tick 未完成时会启动第二条链**
- 证据：`composables/usePolling.ts:24-47`（`start` 先 `stop` 再置 `running=true`；旧 `tick` 在 `await task()` 后看到 `running=true` 会继续 `setTimeout`）。
- 影响：简历页上传/重解析、报告页重试等场景在旧轮询进行中再次 `start` 时，会出现两个并行轮询链，请求翻倍。
- 建议：引入 `runId`/generation，`tick` 完成时校验；或 `start` 返回 Promise 并等待旧 tick 退出；同时增加 `document.hidden` 暂停。

**E-03｜P2｜报告/会话轮询没有总时长上限或页面隐藏暂停**
- 证据：`ReportDetailPage.vue:50`、`ReportListPage.vue:88`、`InterviewRoomPage.vue:86-89`，`usePolling` 本身无上限；文档要求报告最多 90s（`architecture.md:261`）。
- 影响：后台生成卡住、报告长期 GENERATING 时，页面无限请求；移动端耗电/流量，服务端无意义负载。
- 建议：轮询控制器支持 `maxDurationMs` + 退避；`visibilitychange` 隐藏时暂停、恢复时立即补一次；失败连续 3 次转为手动刷新态。

**E-04｜P3｜包体可接受但缺少 vendor 拆分，报告/面试页仍偏大**
- 证据：`dist/assets`（01:04）`index-*.js` 约 189KB、`ResumePage-*.js` 约 143KB、`AdminPage-*.js` 约 82KB、`InterviewRoomPage-*.js` 约 45KB；`vite.config.ts:5-22` 无 `manualChunks`。
- 影响：首屏与路由切换需要重复下载 Vue/Router/Reka 等公共依赖；缓存粒度差。当前量级尚可，但随着报告/编辑器增长会恶化。
- 建议：`manualChunks` 拆 `vue-vendor`、`reka-ui`；对简历编辑器/四种布局按需加载；移除 `stream.ts` 死代码；确认 `Card` 公共 chunk（约 22KB）没有重复打包其依赖。

**E-05｜P3｜重复逻辑与超大组件**
- 证据：`QuestionBankPanel.vue` 821 行、`UserPanel.vue` 718 行、`PromptPanel.vue` 457 行；页面内多处重复的链接按钮 class（`ReportListPage.vue:151,173`、`ReportDetailPage.vue:127`、`DashboardPage.vue:50-55`、`InterviewSetupPage.vue:274-289`）；`RecentReportItem`/`ReportList` 分数渲染逻辑重复。
- 影响：按钮/链接的 hover、focus、移动端尺寸容易漂移；管理端面板职责过多，后续改动风险高。
- 建议：抽出 `LinkButton`（或 `Button as-child`）统一链接样式；把 `QuestionBankPanel` 的筛选/表格/编辑器拆成子组件；把报告卡片抽成共享组件。

**E-06｜P3｜文档与变更记录未随本轮改造更新**
- 证据：`docs/dev/frontend.md:7,278,385`、`docs/design/api.md:526-575`、`docs/CHANGELOG.md` 仍描述 SSE/逐字流式；`docs/design/api.md:584-611` 未含报告 `company/role/title`；`docs/design/architecture.md:219-234` 仍以 SSE 为主链路。
- 影响：新人/联调方按文档实现会与当前轮询代码冲突；文件名约定没有写入任何契约，后续无法验收。
- 建议：本轮收尾同步：前端架构文档标注轮询状态机；API 文档增加异步反馈生成与 404-as-pending 语义；报告导出章节写明文件名规则与浏览器限制；CHANGELOG 记录“去 SSE / 文件名”。

## 3. 两项改造的具体文件级清单

> 说明：两项改造已在工作树中出现初版（`00:57–01:01`）。下列清单是“补齐到可交付”的剩余工作，不是从零方案。

### 3.1 去 SSE、改轮询反馈

**前置契约（不做则刷新恢复无解）**
1. `backend/oc-interview/src/main/java/com/offercoach/interview/api/dto/interview/QuestionItemResponse.java`：增加 `String answerId`（可空）与 `String feedbackStatus`/`boolean feedbackReady`；`InterviewController.toQuestion` 从 `sessionService.questions` 的视图补齐。
2. `backend/.../dto/interview/SessionDetailResponse.java` 与 `SessionSummaryResponse.java`：增加 `jdTitle`、`jdCompany`、`resumeTitle`、`reportId`（当前缺失，见 C-01）。
3. `backend/.../AnswerService.java:114-124`：停止签发 stream token；`AnswerSubmitResponse` 移除 `stream`（或保留一版 deprecated 开关）。`FeedbackGenerationListener.java` 已提供异步预生成，保留并加指标；若监听失败，GET 永远 404，前端只能靠超时/重试。

**前端数据与类型**
4. `frontend/src/types/index.ts`：`Question` 增加 `answerId?: string`、`feedbackReady?: boolean`；删除 `FeedbackStreamInfo` 与 `AnswerResult.stream`（或标注 deprecated）；`Feedback` 不变。
5. `frontend/src/api/interviews.ts`：`QuestionDto`/`mapQuestion` 透传 `answerId`；`submitAnswer` 不再映射 `stream`；保留 `generateAnswerFeedback`（重试），新增 `getAnswerByQuestion(interviewId, questionId)`（若后端选择查询接口而非在题目里带 id）。
6. `frontend/src/api/normalize.ts` 无需改；`frontend/src/api/stream.ts` 删除；`frontend/src/api/index.ts:11` 移除 `export * from './stream'`。

**`frontend/src/composables/useInterviewRoom.ts`（核心）**
7. 状态重命名：`streamState` → `feedbackState: 'idle'|'analyzing'|'done'|'error'`；删除 `streamText`、`stopStream`、`isFeedbackDone`、全局 `feedbackWaitGeneration`。
8. 每道题独立任务：`feedbackJobs = new Map<questionId, { generation:number; controller:AbortController }>()`；`startFeedback(questionId, answerId)` 可重复调用但同题先取消旧任务；切题不取消其他题；`onBeforeUnmount` 统一 cancel。
9. `waitForFeedback`：`AbortController.signal` 传给 `getAnswerFeedback`（`RequestOptions.signal` 已有）；`sleep(ms, signal)` 可中断；退避 `400/800/1600` 上限 3s；总超时 30–45s；404 继续，401/403/422 终止并提示，5xx/网络重试 2 次；成功后 `applyFeedback`。
10. `submitAnswer`：POST 成功立即 `submitting=false`，设置 `question.status='ANSWERED'`、清草稿、`void reload(true,true)`，然后 `void startFeedback(...)`（不要 `await` 20s）；“下一题”仍由 `feedbackState` 控制。若产品允许“下一题不等反馈”，把 `canAdvance` 改为 `isAnswered`，反馈完成后回到该题仍能看到。
11. 刷新恢复：初始化时从 `sessionStorage['oc_answers_'+sessionId]` 读取 `questionId→answerId`（不存正文）；若能读到，已答题按需启动反馈；结束/放弃时清除该 key。若后端已返回 `answerId`，优先用后端。
12. 计时/草稿：`reload` 不再重置 `remaining`（只在题目切换时重置）；把 `questionStartedAt`/`remaining` 按题持久化；`endInterview`/`abandon` 成功后清除该会话全部 `oc_draft_*`；`clearSession` 时清理。
13. 错误与播报：`feedbackState='error'` 时保留 `feedbackError` 文案；`retryFeedback` 先 POST（可同步补算），失败再提示；如果无 `answerId`，提示“无法定位本题评分，请刷新题目”而不是静默。

**组件**
14. `frontend/src/components/interview/StreamingFeedback.vue`：props 改为 `{ feedback, state, error }`；删除 `text`、`stop` emit、`streaming` 分支与 `props.text` watch；`done && !feedback` 渲染错误 + 重试；加入常驻 `role="status" aria-live="polite"` 的 sr-only 状态；保留 `ScoreRing`/维度/证据/改写布局。
15. `frontend/src/components/interview/AnswerComposer.vue`：props `streamState` 改为 `feedbackState`，删除 `streaming`；`:loading` 只绑 `submitting`；分析中显示独立文案/禁用“下一题”；状态行加 `aria-live="polite"`。
16. `frontend/src/pages/InterviewRoomPage.vue`：移除 `streamText`/`stopStream` 解构与 props/事件；滚动阈值改 `1399px`；可选：提交后把焦点移到反馈标题或在移动端 `scrollIntoView`。
17. `frontend/src/pages/InterviewRoomPage.vue` 的 `onBeforeRouteLeave`：分析中离开时明确提示“本题反馈仍在分析，离开后可能需回到本题查看”；不要宣称“稍后自动展示”。

**测试/文档/运维**
18. `frontend/src/composables/useInterviewRoom.test.ts`（新增）：fake timers 覆盖 404→200、超时、切题不取消、卸载取消、重复提交、无 answerId、刷新恢复。
19. `frontend/src/components/interview/StreamingFeedback.test.ts`（可选）：四态渲染与 live 文案。
20. `docs/dev/frontend.md` §6/§8、`docs/design/api.md` §6.4-7、`docs/design/architecture.md` §5.2：标注 SSE 废弃/轮询状态机、404-as-pending、异步生成监听器；`docs/CHANGELOG.md` 记录。
21. 后端运维：`AsyncConfig` 的 `sseExecutor` 同时承担出题/解析/反馈，容量 2/8/64；反馈轮询改造后应监控队列拒绝与 `FeedbackGenerationListener` 失败率；若移除 SSE，可重命名线程池并缩减长连接预算。

### 3.2 报告文件名（公司-岗位-YYMMDD）

**已完成部分（审查时）**
- `frontend/src/utils/reportExport.ts` 已有 `buildReportFileName` + 单测；`ReportDetailPage.vue:91-106` 已在点击打印时改标题；`types/index.ts` 与 `api/reports.ts` 已补 `company/role/title/questionCount`；后端 `ReportDetailResponse` 已返回这些字段。**结论：报告 detail 现在有 company/role（来自 JD 聚合），无需再查会话。**

**剩余改造**
1. `frontend/src/utils/reportExport.ts`
   - 签名加 `timeZone?: string`（默认 `Intl.DateTimeFormat().resolvedOptions().timeZone`）；用 `Intl.DateTimeFormat('en-CA', { timeZone, ... })` 取本地 `YYYY-MM-DD` 再转 `YYMMDD`；不要从 ISO 字符串截取 UTC 日期。
   - 清理段尾 `.`/空格；限制总长（例如 company 40 + role 50 + 日期 6）；Windows 保留名（CON/PRN/AUX/NUL/COM1…）加 `_` 前缀。
   - 增加可选 `suffix`（如 `.pdf`）由调用方决定；打印标题不要带扩展名。
2. `frontend/src/utils/reportExport.test.ts`：补 `Asia/Shanghai` 跨日、`UTC`、非法日期、保留名、超长裁剪用例。
3. `frontend/src/composables/useReportPrintTitle.ts`（新增，纯逻辑可测）
   - 输入 `report`、`timeZone`、`routeId`；`READY` 时设置 `document.title`，返回 `restore()`。
   - 用 `afterprint` + `visibilitychange` + 长兜底（30–60s）；恢复前校验路由仍是当前报告且标题未被其他逻辑改写；卸载时恢复。
4. `frontend/src/pages/ReportDetailPage.vue`
   - 用上面的 composable 在 `report READY` 时持续保持标题（覆盖 Ctrl+P、菜单打印、系统分享），而不仅是按钮点击。
   - 打印前展开所有 `details`（记录原 `open` 状态，`afterprint` 恢复）；打印后恢复滚动位置。
   - 页头/封面展示 `company · role · generatedAt`，Badge 用 `role` 而非固定“模拟面试”。
   - 删除 1s 恢复与重复监听；打印入口保留 `print:hidden`。
5. `frontend/src/styles/main.css`（或新 `print.css`）
   - `@media print`：强制浅色 Token/移除 `.dark`；`@page { size:A4; margin:12mm }`；`print-color-adjust: exact`；隐藏 `header[data-app-header]`、侧栏、底部导航、footer、Toast、额度；卡片去阴影/边框；`details` 内容展开；`.report-question { break-inside: avoid }`。
6. `frontend/src/components/common/AppShell.vue`：给 header、`SidebarContent`、bottom nav、footer 加 `print:hidden`（或在全局打印 CSS 中按语义选择器隐藏）；`QuotaBadge` 同样。
7. `frontend/src/components/report/ReportQuestionItem.vue`：打印时展开（CSS 或 JS）；`summary` 的 Chevron 打印隐藏；分页控制。
8. `frontend/src/router/index.ts:148-150`：`afterEach` 与报告标题协作文档化；若报告页自己管理标题，路由守卫不要在其后覆盖（或让报告页 watch route 重新应用）。
9. `frontend/src/pages/ReportDetailPage.vue` 的 `:id` 复用：`watch(reportId)` 重载并重置 `notFound`/轮询（见 R-07），否则文件名会对应错误报告。
10. 后端（确定性文件名，建议 V1.1）
    - `backend/oc-interview/.../ReportController.java` 新增 `POST /reports/{id}/export` + `GET` 状态，或同步返回 PDF；响应头 `Content-Disposition: attachment; filename*=UTF-8''<encoded>`；文件名由后端用同一规则（含用户时区）生成，避免浏览器差异。
    - `docs/design/api.md:616` 补响应头与文件名规则；未实现前文档标注“仅规划”。
11. 验收（人工，不启动浏览器也可写成清单）：Chrome/Edge/Firefox/Safari/iOS 各打印一次，确认默认名、暗色、页头隐藏、8 题全展开、分页不切断题目卡片；Ctrl+P 与按钮两条入口一致。

## 4. 已核对区域

- **API 客户端**：`src/api/client.ts`（内存 Token、401 单次刷新、并发去重、错误映射、204/空体、AbortSignal）、`client.test.ts`；`auth/users/quota/plans/resumes/jds/interviews/reports/admin/normalize/index`；`stream.ts`（已死代码）。
- **状态与路由**：`src/stores/app.ts`（登录/注册/静默恢复/登出/额度/资料）、`src/router/index.ts`（requiresAuth/requiresAdmin/redirect/title/scrollBehavior）、`src/main.ts`、`App.vue`。
- **Composable**：`useInterviewRoom`（计时/草稿/提交/跳过/暂停/结束/反馈轮询/卸载）、`usePolling`、`useTheme`、`useToast`。
- **页面**：Login/Register/Dashboard/Resume/Jd/InterviewSetup/InterviewRoom/ReportList/ReportDetail/Plans/Settings/Admin/NotFound 全部通读。
- **组件**：`common`（AppShell/Sidebar/PageHeader/AsyncState/ExportMenu/MetricStrip/QuotaBadge/navigation）、`ui`（Alert/Badge/Button/Card/Dialog/EmptyState/Input/Progress/Skeleton/Spinner/Textarea/ToastHost）、`charts`（ScoreRing/RadarChart）、`interview`（AnswerComposer/QuestionNav/QuestionPanel/RoomHeader/StreamingFeedback/TimerRing/UploadDropzone/setup 三件）、`report`（DimensionBars/ImprovementPlanList/ReportInsightCards/ReportQuestionItem）、`resume`（列表/详情/预览/编辑/版式/解析态/导出）、`jd`（表单/列表/Match/原文）、`admin`（七个面板 + 分页 + meta）。
- **样式**：`styles/tokens.css`、`styles/main.css`、`tailwind.config.ts`、`index.html`；暗色 Token、focus-visible、打印缺失、移动端断点。
- **工程**：`package.json` scripts、`tsconfig.app.json`（strict/noUncheckedIndexedAccess/exactOptionalPropertyTypes）、`eslint.config.js`（`vue/no-v-html` error）、`vitest.config.ts`、`vite.config.ts`、`dist/` 产物体量。
- **契约对照**：`docs/design/api.md`（认证/额度/简历/JD/面试/SSE/报告/管理端）、`docs/design/admin-api.md`、`docs/design/architecture.md`（报告轮询 2/3/5s、90s）、`docs/dev/frontend.md`（SSE 封装/缓存/轮询）、`docs/dev/ui-system.md`（断点/触控/打印浅色/表格卡片化）；后端 `SessionDetailResponse`、`QuestionItemResponse`、`ReportDetailResponse`、`ReportController`、`FeedbackService`、`FeedbackGenerationListener`、`AnswerService`、`AdminUserController`、`MeResponse`、`UpdateMeRequest`。
- **未做**：未运行 typecheck/lint/test/build，未执行 Playwright/视觉脚本，未启动浏览器；因此“构建是否当前源码通过”未验证，`dist/` 01:04 产物仅作包体参考。