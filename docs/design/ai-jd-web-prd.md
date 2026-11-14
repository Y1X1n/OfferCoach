# 面试目标岗位联网获取 PRD：Search / Fetch

> 状态：生效（已实现并验证）｜ 版本：v3.2 ｜ 更新：2026-11-14
> 关联：[ai-tools-prd.md](./ai-tools-prd.md)（同属 `oc-ai` 工具域）｜ [requirements.md](../requirements.md)（R-22 / R-23）｜ [api.md](./api.md) §6/§7 ｜ [architecture.md](./architecture.md) ADR-011
> 定位：本文覆盖**在创建模拟面试时联网获取目标岗位 JD，用真实 JD 量身定制题目**（DeepSeek 的 Search / Fetch 两个只读工具接口）。语音/图像三项能力见 [ai-tools-prd.md](./ai-tools-prd.md)。
> 变更记录：
> v2.0 把目的从"往 JD 库导数据"改为"面试出题用真实 JD"，入口移到面试创建流程；
> **v3.0 按评审意见确定主次：用户粘贴岗位链接 → 先 Fetch → 抓不到再用 Search 兜底**（原方案以 Search 为主，实测后调整）；
> v3.1 记录实施与真机验证结果（§12）；v3.2 补充 JD 去重规则与迁移（§13）。

## 1. 背景与目标

现状：创建模拟面试必须**先有一条 JD**（`CreateSessionRequest.jdId` 必填），而用户拿到 JD 的方式只有**手工复制粘贴**。题目质量完全取决于粘贴是否完整——漏掉任职要求里的关键技术栈，题目就会跑偏。

本期：在面试创建流程里**粘贴目标岗位链接**，由系统优先抓取该页正文；**抓不到时自动用搜索兜底**，取回同一岗位的岗位职责与任职要求，落成 JD 用于出题，从而"量身定制题目"。

DeepSeek 提供两个只读联网接口：

| 接口 | 路径 | 用途 | 计费（成功请求） | 账号级 QPS |
|---|---|---|---|---|
| Fetch | `POST /v1/tools/fetch` | 抓取单个 URL，返回 Markdown 正文 | 5 元 / 1,000 次 | 25（另有目标域名级限制） |
| Search | `POST /v1/tools/search` | 关键词检索网页，返回结构化结果 | 5 元 / 1,000 次 | 100 |

对应既有需求 R-22（"JD URL 抓取"）与新增 R-23。

### 目标

1. **主路径**：用户粘贴目标岗位链接 → 系统 Fetch 抓取 → 抽取岗位职责/任职要求 → 预览 → 确认后用于出题。
2. **兜底路径**：Fetch 抓不到（SPA / 登录墙 / 被拒 / 正文过短）→ 自动改用 Search 检索同一岗位并取回正文（见 §4.3 兜底策略）。
3. **辅助入口**：没有链接时可直接按关键词搜索岗位候选。
4. 取回的 JD 与手工粘贴的 JD **走同一条出题链路**（岗位族推断 → 题库检索 → 维度配额 → AI/模板补齐），题目因此贴合真实要求。
5. 取回内容**可预览、可解释、可降级**；两条路径都失败时明确提示并保留手工粘贴。
6. 成本可控：按次计费，需缓存与每日限额。

### 非目标

- 不做批量爬取 / 岗位监控 / 岗位库。
- 不做登录态抓取、验证码绕过、反爬对抗。
- 不做投递、打招呼、内推。
- 不保证取回内容与页面完全一致（见 §2 实测限制）。

## 2. 实测结论（决定方案的关键证据）

用真实 Key 实测两个接口，结论直接决定主用路径：

### 2.1 Search 能拿到 JD 正文（因此可作兜底）

`need_content=true` 时结果项自带网页正文，**DeepSeek 索引里已有渲染后的内容**，绕开 SPA / 登录墙：

| 实测查询 | 命中站点 | 取回正文 |
|---|---|---|
| `site:zhipin.com 后端工程师` | BOSS直聘 | **2,617 / 4,177 字符**（含岗位职责） |
| `上海 高级后端工程师 招聘 …` | 腾讯招聘 | summary 1,176、content 1,809 字符 |
| 同上 | 国家大学生就业服务平台 | summary 874、content 1,089 字符 |
| 同上 | 国企招聘系统（zhiye.com） | summary 541、content 549 字符 |

### 2.2 Fetch 只对服务端渲染页面有效 —— 所以必须有 Search 兜底

| 实测 URL | 结果 |
|---|---|
| `szai.ncss.cn/student/jobs/…/detail.html` | 200，正文 1,089 字符，但**多为导航与免责声明**，JD 正文缺失（SPA） |
| `ctyun.zhiye.com/zpdetail/621064790` | 200，正文仅 **341 字符**（导航菜单，SPA） |
| `www.zhipin.com/web/geek/job?…` | 200，正文仅 **75 字符**（登录墙） |

**结论**：招聘站多为 SPA / 登录墙，Fetch 直接抓取**成功率不高**；但用户给的就是链接，所以流程设计为 **URL 优先（Fetch）+ Search 兜底**——Fetch 拿不到时用 Search 从索引里取同一岗位的正文（§2.1 已证明索引里有内容）。

### 2.3 接口契约（实施依据）

**Search** 请求：`query`(必填,1–128 字符,支持 `site:`/`-`/`"..."`/`lang:`/`after:`/`before:`)、`count`(1–50,默认 10)、`ori_question`(可选,辅助意图理解)、`localization`(可选,**传 IP 以优先返回本地化结果**)、`need_content`(默认 false)。
响应 `results[]`：`rerank_score`(0–3,≥1.5 优质)、`title`、`url`、`snippet`(100–200 字符)、`summary`(600–800 字符)、`content`(正文)、`site_name`、`site_icon`、`date`、`authority`(0–4,≥3 权威)。

**Fetch** 请求：`url`(必填,≤8192,须含 host 的 http/https,**不得带用户名密码**)。响应：`title`、`content`(Markdown)。

**错误**：`400` 入参、`401` Key、`402` 余额、`403` Fetch 被安全策略拒绝、`404` Fetch 无可提取正文、`408` 读体超时、`413` 体过大、`429` QPS、`5xx` 服务端。**仅 200 且有结果的请求计费**。

## 3. 服务边界与架构决策

沿用 [ai-tools-prd.md](./ai-tools-prd.md) §4：**工具服务只做"取回内容"，业务判断留在 `oc-app`**。

- **D1 新增两个服务，挂在 `oc-ai` 模块内**：`oc-ai-search`(8097)、`oc-ai-fetch`(8098)。独立 Jar / Dockerfile / 健康检查，仅内网可达。
  拆两个而非合并，理由与 ADR-011 一致：**外部资源特征不同**——Search QPS 100、返回结构化 JSON；Fetch QPS 25、下载整页、**有目标域名级限流**（各自需要独立的在途与域名限流状态）。顶层模块仍只增加 `oc-ai` 一个。
- **D2 编排在 `oc-app`**：新增 `JdWebImportService`（`oc-interview`），负责 URL 校验、缓存、正文抽取、截断，再交给既有 `JdStructurer` 落库；工具服务不做 JD 语义判断。
- **D3 `localization` 默认不下发用户 IP**：该参数会把用户 IP 交给第三方。默认**把城市写进 query 关键词**（如 `上海 高级后端工程师 招聘`）实现地域化；IP 透传作为可选项，默认关闭（见 §7 隐私）。
- **D4 取回正文必须"抽取 + 截断"再落库**：网页正文含导航、页脚、推荐位噪声，且会超出 JD 8000 字符上限。新增 `WebContentCleaner`（Markdown → 纯文本，复用并扩展 [OcrTextCleaner](file:///E:/My_project/backend/oc-interview/src/main/java/com/offercoach/interview/document/OcrTextCleaner.java) 规则）+ `JdBodyExtractor`（按"岗位职责/任职要求/任职资格/职责/要求"等标题切段，截断到 8000 字符）。
- **D5 缓存按来源区分**：Search 结果按 `sha256(query+count+needContent)` 缓存 10 分钟；Fetch 结果按 `sha256(url)` 缓存 1 小时；**正文抽取结果按 `searchToken#resultId` 或 URL 缓存 15 分钟**供预览/确认复用。命中不重复计费。只缓存抽取后的正文，不落整页 HTML。
- **D6 取回的 JD 落成正常 `job_description` 行**（不在会话里内联 JD 文本）。理由：出题链路（岗位族推断、题库检索、报告、匹配）全部以 `jd_id` 为锚，落库即零改动复用；用户也能在 JD 列表里复用/编辑/删除。新增来源字段以区分（见 §6）。
- **D7 主次：URL 优先（Fetch）→ Search 兜底**。用户给的是链接，先按链接抓；只有抓不到才用搜索。判定与兜底见 §4.3。理由：Fetch 命中时结果最忠实于用户所指的那个岗位；Search 只是"同一岗位的替代来源"，可能与用户看到的页面不完全一致，仅在必要时使用，并在预览页明确标注来源为"搜索兜底"。

### 3.1 服务拓扑（增量）

```text
Browser ──▶ oc-app (8081)
              │  内网 HTTP（X-OC-Internal-Token）
              ├──▶ oc-ai-fetch  (8098) ──▶ DeepSeek POST /v1/tools/fetch   ← 先试
              └──▶ oc-ai-search (8097) ──▶ DeepSeek POST /v1/tools/search  ← 抓不到时兜底
                          PostgreSQL（只读 ai_runtime_config；追加 ai_call_log）· Redis（正文缓存）
```

### 3.2 内部接口契约

| 服务 | 内部端点 | 请求 | 响应 |
|---|---|---|---|
| Search | `POST /internal/v1/search` | `{query, count, oriQuestion, needContent, localization}` | `{results:[{rerankScore,title,url,snippet,summary,content,siteName,siteIcon,date,authority}]}` |
| Fetch | `POST /internal/v1/fetch` | `{url}` | `{title, content}` |

统一请求头 `X-OC-Internal-Token` / `X-OC-User-Id` / `X-OC-Request-Id`；统一错误体 `{code,message,retryable}`（扩展 `oc-ai-core` 的 `ToolErrorKind`：新增 `BLOCKED`→403、`NO_CONTENT`→404）。

## 4. 对外接口与交互

### 4.1 新增公开接口（评审通过后回写 [api.md](./api.md)）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/jds/preview` | **主入口**：由「URL」或「搜索候选」生成**未落库**的 JD 预览；URL 模式内建 Fetch → Search 兜底 |
| POST | `/api/v1/jds/search` | 辅助入口：无链接时按关键词检索岗位候选 |
| POST | `/api/v1/interviews` | **扩展现有接口**：`jdId` 之外可传 `previewToken`，在创建会话的同一事务内落 JD |

- `POST /jds/preview` 请求（二选一）：
  - URL 模式：`{url(必填), keywords(可选,如"高级后端工程师 上海",用于搜索兜底), company(可选)}`
  - 候选模式：`{searchToken, resultId}`
  响应 `200`：
  ```json
  {
    "previewToken": "pv_...",
    "resolvedBy": "URL | SEARCH_FALLBACK",
    "fallbackReason": "FETCH_BLOCKED | FETCH_NO_CONTENT | FETCH_TOO_SHORT | null",
    "title": "...", "company": "...", "siteName": "...", "sourceUrl": "...",
    "rawText": "（截断到 8000）",
    "structured": { "seniority": "MID", "responsibilities": [], "mustHave": [], "niceToHave": [], "techStack": [], "keywords": [] }
  }
  ```
  若 URL 模式兜底检索到多个候选、且**无法确定哪条对应当前链接**，则返回 `200 {needsChoice:true, searchToken, results:[…]}`，由前端让用户选一个后再调 `{searchToken, resultId}`。
- `POST /jds/search` 请求：`{query(必填,2–128 字符), location(可选,如"上海"), count(可选,1–10,默认 8)}`。
  响应：`{searchToken, results:[{resultId, title, url, siteName, snippet, summary, rerankScore, authority}]}`。
- `POST /interviews` 请求新增可选 `previewToken`；`jdId` 与 `previewToken` **至少提供一个**，同时提供以 `jdId` 优先。
  服务端在 `doCreate` 事务内：按 `previewToken` 取缓存预览 → 插入 `job_description`（记录来源）→ 用新 `jdId` 建会话。取不到缓存 → `409 STATE_CONFLICT`（提示重新获取）。

> 为什么用 `previewToken` 而不是"先建 JD 再建会话"：让用户在**确认题目之前**看到 JD 原文与结构化结果，且不想要的岗位不会污染 JD 列表；同时避免"建了 JD 却没面试成"的脏数据。

### 4.2 前端交互（[InterviewSetupPage.vue](file:///E:/My_project/frontend/src/pages/InterviewSetupPage.vue)）

在"选择 JD"这一步，现有下拉之上并列两个入口（保持现有极简风格，不新增页面）：

1. **粘贴岗位链接（主入口）**：URL 输入框 + 可选的"岗位/城市关键词"（提示"抓不到时用于搜索兜底"）→ "获取" → 展示 JD 预览（原文 + 解析出的必备技能/技术栈）→ 确认。
   - 若走了兜底，预览区顶部标注**「链接无法抓取，已改用搜索获取同一岗位」**，并同时给出原始链接与搜索结果链接，让用户知道来源差异。
   - 若兜底返回多个候选 → 展示候选列表让用户选。
2. **搜索岗位（辅助入口）**：输入关键词（默认带入简历期望岗位）+ 城市 → 候选列表（标题、站点、摘要、`rerank_score` 徽标）→ 选中 → 预览 → 确认。
3. 保留"手工粘贴"与"选择已有 JD"两条原路径。
4. 兜底：两条路径都失败时，Toast 给**具体原因**（如"该链接与搜索均未取到正文，请手动粘贴"）。

预览确认后，`previewToken` 随创建请求提交，服务端落 JD 并开始出题。会话详情页展示 JD 标题 + 来源站点链接。

### 4.3 兜底策略（URL → Fetch → Search）

URL 模式下按顺序执行，命中即止：

1. **Fetch 该 URL**（`need_content` 无关；Fetch 直接返回 Markdown）。
2. **判定"抓不到"**（满足任一即触发兜底）：
   - Fetch 返回 `403`（被安全策略拒绝）/ `404`（无可提取正文）/ 其它 4xx；
   - Fetch 成功但 `WebContentCleaner` + `JdBodyExtractor` 抽取后正文 **< 300 字符**；
   - 抽取后正文**不含任何 JD 段落标题**（岗位职责/任职要求/任职资格/职责/要求/任职条件）**且** `rawText` 中招聘关键词（招聘/职位/薪资/工作地点）命中数 < 2 —— 典型"只抓到导航壳"。
3. **Search 兜底**：构造 query，优先级如下：
   1. 用户填写的 `keywords`（+ `company`）；
   2. 否则从 URL 派生：`site:<host>` + 路径 slug 去连字符后作为词（如 `/job/高级后端工程师-123` → `高级后端工程师`）+ `招聘`；
   3. 都没有则仅 `site:<host> 招聘`。
   检索 `count=8, need_content=true`，然后**择优**：
   - 若结果中存在 `url` 与原始链接**完全相同**的项 → 直接用它；
   - 否则若 `rerank_score ≥ 1.5` 且 `authority ≥ 3` 的最高分项**唯一领先**（与第二名差 ≥ 0.3）→ 自动采用；
   - 否则返回候选列表交用户选择（`needsChoice`）。
4. **两条都失败** → `422`，提示手动粘贴，`fallbackReason` 一并回传用于提示文案。

> 兜底只在"链接抓不到"时发生，用户明确知道发生了兜底（预览页标注 + 来源链接），避免"以为是自己给的链接，其实是搜索来的"误导。

### 4.4 出题侧是否需要改动

**不需要**。会话创建后仍按 `jd_id` 走既有 `QuestionContextBuilder` / `QuestionGenerationWorker` / `QuestionBankRetriever`（岗位族推断 + 关键词检索 + 维度配额）。本期收益来自"JD 是真实且当前的"，而不是改出题算法。
（可选增强，本期不做：把 JD 的 `mustHave` 直接作为检索关键词权重因子。）

## 5. 计量、额度与限流

- 上游按成功请求计费（5 元/千次）。每日限额（平台策略可配，默认）：`search ≤ 20 次/用户/日`、`fetch ≤ 20 次/用户/日`；单次 `count ≤ 10`。
- Redis 日计数器复用 `RedisKeys`（新增 `dailySearch` / `dailyFetch`）。
- 缓存命中不计次、`ai_call_log` 写 `DEGRADED` + `CACHE_HIT`（与 TTS 一致）。
- 调用写 `ai_call_log`：`scene=SEARCH|FETCH`、`provider=deepseek-tools`、`model=tools-search|tools-fetch`、耗时、状态、错误码；Search 记 `count`，Fetch 记目标域名（便于排查域名级 429）。

## 6. 数据与配置

- `job_description` 新增两列：`source_type VARCHAR(16) CHECK IN ('MANUAL','SEARCH','URL')`（默认 `MANUAL`）、`source_url VARCHAR(1024)`。迁移 `V202611140002__jd_source.sql`；既有行回填 `MANUAL`。
- 正文缓存走 Redis（不落库）；`previewToken` → 缓存预览对象（TTL 15 分钟）。
- `ai_runtime_config` 新增两列：`search_enabled`、`fetch_enabled`（默认 true）；管理端"AI 配置"页在现有 ASR/TTS/OCR 开关下方增加两个开关。
- 平台策略（`/admin/settings/quota`）新增 search/fetch 每日上限。
- JD 详情/列表响应增加 `sourceType` / `sourceUrl`（评审通过后回写 [api.md](./api.md)）。

## 7. 安全与合规

- **URL 校验**（Fetch）：仅 `http`/`https`；必须含 host；拒绝 URL 内嵌用户名/密码；拒绝 `localhost`/回环/私网 IP 字面量/非 http(s) 协议（纵深防御，虽然抓取由 DeepSeek 执行）。
- **不得变成开放代理**：仅用于 JD 导入，配合登录校验与每日限额；不接受前端自定义 header / 方法 / body。
- **提示注入**：取回的网页正文是**不可信外部内容**，可能含针对模型的指令。它只进入 `JdStructurer`（输出限定为结构化 JSON 字段）并作为 JD 原文展示；**禁止**把网页正文拼进任何"执行型"提示词，不在提示词中赋予其指令地位。
- **隐私**：默认不下发用户 IP（D3）；抓取内容与查询词不落库（除最终 JD 正文与来源链接），不做用户画像。
- **版权/来源**：预览与 JD 详情标注来源站点与原始链接（`site_name` + `source_url`）。

## 8. 降级与错误映射

URL 模式下，Fetch 的失败**不直接报错给用户**，而是先走 Search 兜底（§4.3）；只有兜底也失败才报错。

| 上游 / 场景 | 处理 |
|---|---|
| Fetch `403` / `404` / 4xx | 记 `fallbackReason` → **自动 Search 兜底** |
| Fetch 成功但抽取正文 < 300 字符 / 无 JD 段落标题 | 同上，**自动 Search 兜底** |
| Search 兜底结果为空 | `422 NO_CONTENT`，提示"该链接与搜索均未取到正文，请手动粘贴" |
| Search 兜底命中多个候选且无法确定 | `200 {needsChoice:true, searchToken, results}`，前端让用户选 |
| 直接关键词搜索（辅助入口）结果为空 | `200` 空列表 + 提示"未找到匹配岗位，换个关键词或手动粘贴" |
| 上游 `429` / `5xx` | `503 AI_UNAVAILABLE`（`retryable=true`），前端可重试 |
| `402` 余额不足 | `402`，提示联系管理员 |
| 未配置 Key / 能力被关闭 | `503`，前端隐藏入口或提示"管理员已关闭" |
| `previewToken` 过期 | `409 STATE_CONFLICT`，提示重新获取 |

## 9. 验收标准

| # | 场景 | 通过标准 |
|---|---|---|
| W-1 | 金路径：链接抓取（Fetch 命中） | 粘贴服务端渲染招聘页 URL → Fetch 成功 → 预览正确（`resolvedBy=URL`）→ 创建会话 → 题目贴合 |
| W-2 | **金路径：链接兜底（Fetch 失败 → Search）** | 粘贴 BOSS直聘详情页 URL → Fetch 拿不到正文 → **自动 Search 兜底成功** → 预览标注"已改用搜索获取"并给出两个链接 → 创建会话 → 题目贴合 |
| W-3 | 金路径：关键词搜索 | 直接搜索"上海 高级后端工程师 招聘" → 选中真实岗位 → 预览含岗位职责/必备技能 → 出题命中该 JD 技术栈 |
| W-4 | 兜底择优 | 兜底检索命中同一 URL → 自动采用；命中多条且不唯一 → 返回 `needsChoice` 让用户选 |
| W-5 | 双失败 | 无效/受保护链接且搜索无结果 → `422` 明确提示手动粘贴，不建脏 JD |
| W-6 | 缓存 | 同 URL 二次抓取命中缓存；同 query 二次搜索命中缓存；预览到创建不重复计费（`ai_call_log` 记 `CACHE_HIT`） |
| W-7 | 不污染数据 | 预览后放弃创建 → `job_description` 无新增行 |
| W-8 | 限额 | 超每日上限 → `429 RATE_LIMITED`，无上游调用 |
| W-9 | 安全 | `file:///etc/passwd`、`http://127.0.0.1:8080/…`、含用户名密码的 URL 一律 `400` |
| W-10 | 鉴权 | 内部端点无 `X-OC-Internal-Token` → `401`；公网无法直连 8097/8098 |
| W-11 | 关闭开关 | 管理端关闭 search/fetch → 对应接口 `503`，前端入口提示不可用 |
| W-12 | 来源可追溯 | 新建 JD 的 `source_type`（`URL` / `SEARCH`）与 `source_url` 正确；详情页展示来源链接 |
| W-13 | 规范 | 后端 Checkstyle/Spotless/ArchUnit/JaCoCo 通过；前端 `pnpm check` 通过 |
| W-14 | JD 去重 | 手工重复粘贴同一 JD → `409` 并返回 `existingJdId`；联网重复获取同一岗位 → **复用同一 `jd_id`**，不新增记录；并发插入由数据库唯一索引兜底 |
| W-10 | 来源可追溯 | 新建 JD 的 `source_type`/`source_url` 正确；详情页展示来源链接 |
| W-11 | 规范 | 后端 Checkstyle/Spotless/ArchUnit/JaCoCo 通过；前端 `pnpm check` 通过 |

## 10. 里程碑与工作量（估）

| 阶段 | 内容 | 预估 |
|---|---|---|
| S1 | `oc-ai-search` / `oc-ai-fetch` 服务骨架 + `DeepSeekToolsClient` 扩展 + `ToolErrorKind` 扩展 + 迁移与配置 | 1.5d |
| S2 | `WebContentCleaner` + `JdBodyExtractor`（单测：导航噪声剥离、截断、"抓不到"判定） | 1d |
| S3 | `JdWebImportService`：URL→Fetch→Search 兜底编排（含兜底 query 推导与择优）+ `preview`/`search` 端点 + Redis 缓存 + `previewToken` + `POST /interviews` 扩展 + 日限额 | 2.5d |
| S4 | 面试创建页两个入口 + JD 预览区（含兜底来源标注、候选选择）+ 错误提示 | 1.5d |
| S5 | 管理端开关与平台策略、`ai_call_log` 场景、联调与验收（W-1…W-13） | 1d |

合计约 **7.5 人日**。

## 11. 风险与待决

- **W-R1 索引时效与准确性**：Search 取回的是 DeepSeek 索引快照，可能滞后或含无关片段。默认：预览页展示来源与链接，用户确认后才使用；兜底场景**显式标注**"已改用搜索获取"。
- **W-R2 站点 QPS / 拒绝**：Fetch 有目标域名级 QPS 限制，部分站点直接拒绝。默认：域名级退避 + 自动兜底，不做反爬对抗。
- **W-R3 成本**：5 元/千次虽低，"搜索一次 8 条、每条含正文"会放大响应体。默认 `count ≤ 10`，只缓存抽取后正文。
- **W-R4 `localization`（IP 透传）**：默认关闭且**不提供前端开关**，城市走关键词。若评审要求更强的地域相关性，再单独评估（涉及向第三方传用户 IP）。
- **W-R5 网站白名单**：R-22 原写"白名单 + SSRF 防护"。默认**不做白名单**（招聘站过于分散，白名单会大幅降低可用性），改为"协议/主机合法性校验 + 每日限额 + 明确降级"。请评审确认。
- **W-R6 是否允许"临时 JD"**（不进入 JD 列表）：当前默认落成正常 JD 行（D6）。若希望不污染列表，需要改成会话内联 JD 文本，改动面更大。
- **W-R7 兜底可能取到"相似但不同"的岗位**：搜索只能按关键词与站点匹配，无法保证与用户所看链接是同一个 posting。默认：只在 Fetch 失败时兜底，且预览页明确标注来源与链接，用户可拒绝。
- **W-R8 出题权重**：是否把 `mustHave` 作为检索关键词权重因子，本期不做。

## 12. 实施记录（2026-11-14）

### 12.1 已交付

| 层 | 内容 |
|---|---|
| 服务 | `oc-ai-search`(8097) / `oc-ai-fetch`(8098) 独立 Spring Boot 服务，仅内网、`X-OC-Internal-Token` 鉴权、信号量限流、独立 Dockerfile 与 Compose profile |
| 共享库 | `oc-ai-core` 新增 `search` / `fetch` 客户端方法；`ToolErrorKind` 新增 `BLOCKED`/`NO_CONTENT`；`FetchUrlRules` 统一 URL 校验 |
| 编排 | `JdWebImportService`：URL→Fetch→Search 兜底、兜底检索词推导、择优、Redis 三层缓存（查询 10min / 抓取 1h / 预览 15min）、日限额、`previewToken` |
| 接口 | `POST /api/v1/jds/search`、`POST /api/v1/jds/preview`；`POST /api/v1/interviews` 支持 `previewToken` |
| 数据 | `job_description.source_type` / `source_url`；`ai_runtime_config.search_enabled` / `fetch_enabled` |
| 前端 | 面试创建页"从招聘网站获取目标岗位"对话框（粘贴链接 / 搜索岗位 + JD 预览 + 兜底来源标注 + 候选选择 + 移除） |
| 管理端 | AI 配置页新增"岗位搜索 / 链接抓取"开关；AI 调用日志场景新增 `SEARCH` / `FETCH` |

### 12.2 真机验证（真实 Key）

| 项 | 结果 |
|---|---|
| 搜索 | `高级后端工程师 / 上海` 返回 5 条真实岗位；`site:zhipin.com` 可取回含岗位职责的正文 |
| URL 抓取 | `m.zhipin.com/zhaopin/…` **Fetch 直接成功**（2,544 字符，7 项必备技能）；标题取结构化岗位名而非页面标题 |
| 兜底 | `www.zhipin.com/web/geek/job?…`（登录墙）→ `resolvedBy=SEARCH_FALLBACK`、`fallbackReason=FETCH_TOO_SHORT`、返回 10 个候选 |
| 建会话 | `previewToken` 创建会话 201；DB 中 JD `source_type=SEARCH`、`source_url` 有值；出题 `whyThisQuestion` 引用该 JD |
| 缓存 | 同 query 二次搜索记 `CACHE_HIT`，不再回源 |
| 安全 | `file://`、`127.0.0.1`、`ftp://` 均 `400`；内部端点无令牌 `401` |
| 开关 | 关闭 search/fetch 后对应接口 `503`，重新开启恢复 |

### 12.3 与 v3.0 的差异（需知悉）

1. **每日上限走环境变量而非平台策略页**：`AI_SEARCH_DAILY_LIMIT` / `AI_FETCH_DAILY_LIMIT`（默认 20）。管理端 `/admin/settings/quota` 未新增这两项（避免扩展现有平台策略契约），如需运营在线调整再补。
2. **`siteName` 取值**：URL 模式用 host（如 `m.zhipin.com`）；候选模式用搜索结果的 `site_name`。
3. **Fetch 成功率好于 §2.2 预期**：部分 BOSS 直聘列表页是服务端渲染，Fetch 可直接拿到正文；登录墙 / SPA 页仍会兜底。
4. **JD 去重（v3.2 已实现）**：同用户相同内容只保留一条未删除记录，见 §13。

## 13. JD 去重（v3.2，2026-11-14）

### 13.1 规则

- **维度**：同一 `user_id` + 相同 `content_hash`（未删除记录）。
- **哈希**：`sha256(normalize(rawText))`。归一化统一换行、压缩制表符/全角空格与连续空行、去行尾空白 —— 排版差异不算新 JD。
- **行为分场景**：
  - **手工粘贴**（`POST /api/v1/jds`）：返回 `409 STATE_CONFLICT`，`details.existingJdId` 指向已有记录；前端自动打开该 JD 并提示"该 JD 已存在"。
  - **联网获取**（`previewToken` 建会话）：**静默复用**已有 JD，`interview_session.jd_id` 指向它，不产生新行。
- **并发兜底**：数据库唯一索引 `uq_jd_user_content_hash`（部分索引 `WHERE deleted_at IS NULL`）。应用层捕获 `DuplicateKeyException` 后回查并复用/报冲突。

### 13.2 迁移（`V202611140003__jd_dedupe.sql`）

1. 把指向重复 JD 的 `interview_session.jd_id` 改指该组**最早一条**；
2. 重复行软删除（保留数据，不影响部分唯一索引）；
3. 删除原普通索引 `idx_jd_content_hash`，建立唯一部分索引 `uq_jd_user_content_hash`。

### 13.3 验证

| 场景 | 结果 |
|---|---|
| 手工重复创建 | 第 1 次 `201 id=33`；第 2 次 `409` + `existingJdId=33` |
| 联网重复获取同一链接 | 两次会话均指向 `jd_id=34`，该用户 JD 总数未增加 |
| 数据库兜底 | 直接插入相同 `(user_id, content_hash)` → `duplicate key violates unique constraint uq_jd_user_content_hash` |
| 单测 | `JdDedupeHashTest` 4 项（换行/空白归一化、差异内容区分、null） |
