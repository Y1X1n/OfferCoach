# API 接口契约

> 状态：生效 ｜ 版本：v1.8 ｜ 更新：2026-11-09
> Base URL：`https://api.offercoach.example.com/api/v1` ｜ 内部接口：`http://<service>/internal/v1`
> 关联：[数据模型](./data-model.md)、[前端规范](../dev/frontend.md)、[契约流程](../dev/workflow.md)

## 0. 接口总览

| 分组 | 方法与路径 | 鉴权 | 说明 |
|---|---|---|---|
| 认证 | `POST /auth/register` | 公开 | 注册 |
| 认证 | `POST /auth/login` | 公开 | 登录 |
| 认证 | `POST /auth/refresh` | Cookie | 刷新 Access Token |
| 认证 | `POST /auth/logout` | USER | 登出 |
| 用户 | `GET/PATCH/DELETE /users/me` | USER | 资料、隐私、注销 |
| 额度 | `GET /quota` | USER | 余额与每日用量 |
| 工作台 | `GET /dashboard` | USER | 首屏聚合：额度、简历/JD 就绪、进行中会话、最近报告 |
| 套餐 | `GET /plans`、`POST /orders` | USER | 套餐与下单（V1.1） |
| 简历 | `POST/GET /resumes` | USER | 上传、列表 |
| 简历 | `GET/PATCH/DELETE /resumes/{id}` | USER | 详情、修正、删除 |
| 简历 | `POST /resumes/{id}/reparse` | USER | 重新解析 |
| 简历 | `GET /resumes/{id}/download-url` | USER | 预签名下载 |
| JD | `POST/GET /jds` | USER | 创建、列表 |
| JD | `GET/DELETE /jds/{id}` | USER | 详情、删除 |
| JD | `POST /jds/{id}/match` | USER | 匹配分析 |
| 面试 | `POST/GET /interviews` | USER | 创建、列表 |
| 面试 | `GET /interviews/{id}` | USER | 会话详情 |
| 面试 | `POST /interviews/{id}/start` | USER | 异步出题 |
| 面试 | `GET /interviews/{id}/events` | USER | 出题 SSE |
| 面试 | `GET /interviews/{id}/questions` | USER | 题目列表 |
| 面试 | `POST /interviews/{id}/questions/{qid}/skip` | USER | 跳过 |
| 面试 | `POST /interviews/{id}/pause`、`/resume`、`/abandon` | USER | 暂停/恢复/放弃 |
| 回答 | `POST /interviews/{id}/answers` | USER | 提交文字回答 |
| 回答 | `POST /interviews/{id}/answers/audio` | USER | 提交语音（V1.1） |
| 反馈 | `GET /interviews/{id}/answers/{aid}/feedback` | USER | 权威评分 |
| 反馈 | `GET /ai/feedback/stream` | USER + streamToken | 评分 SSE |
| 面试 | `POST /interviews/{id}/finish` | USER | 结束并触发报告 |
| 报告 | `GET /reports`、`GET /reports/{id}` | USER | 列表、详情 |
| 报告 | `POST /reports/{id}/retry` | USER | 重试生成 |
| 报告 | `POST/GET /reports/{id}/export` | USER | PDF（V1.1） |
| 通知 | `GET /notifications`、`POST /notifications/{id}/read` | USER | 通知中心 |
| 管理 | `/admin/questions/**` | ADMIN/SUPPORT | 题库 |
| 管理 | `/admin/prompts/**` | ADMIN | Prompt 与实验 |
| 管理 | `/admin/users/**`、`/admin/ai-calls/**`、`/admin/metrics/**`、`/admin/evals/**` | ADMIN/SUPPORT | 用户、日志、看板、评测 |
| 内部 | `/internal/v1/**` | Service Token | 服务间调用，不暴露公网 |

## 1. 通用约定

### 1.1 协议与格式

- 仅 HTTPS；请求/响应 `application/json; charset=utf-8`；文件上传 `multipart/form-data`；流式 `text/event-stream`。
- 字段命名 `camelCase`；时间 ISO-8601 UTC；ID 以字符串传输（`"id": "9001"`）。
- 金额单位分（整数）；分数 `number`，保留 1 位小数。
- 列表分页：`page`（1 起）、`size`（1–100，默认 20）；响应 `items/total/page/size`。
- 未声明字段一律忽略；客户端不得依赖字段顺序；新增字段视为兼容变更。
- 可空字段显式返回 `null`（不使用“缺省即 null”的隐式约定），前端必须容忍显式 null。

### 1.2 请求头

| Header | 必填 | 说明 |
|---|---|---|
| `Authorization` | 除公开接口外 | `Bearer <accessToken>` |
| `X-Request-Id` | 否 | 客户端 UUID；未传则服务端生成，响应回传 |
| `X-Device-Id` | 注册/登录 | 设备指纹（前端生成，随机 UUID + 本地持久化） |
| `Idempotency-Key` | 计费/创建类 POST | 建议 UUID；24h 内相同 Key 返回首次结果 |
| `Accept-Language` | 否 | `zh-CN` / `en-US`，影响提示文案 |

### 1.3 响应头

| Header | 说明 |
|---|---|
| `X-Request-Id` | 全链路请求 ID |
| `X-Trace-Id` | W3C Trace ID，便于排查 |
| `X-RateLimit-Limit` / `X-RateLimit-Remaining` / `X-RateLimit-Reset` | 限流信息（当前仅返回 429 错误码，响应头待补） |
| `Retry-After` | 429/503 时建议重试秒数 |

### 1.4 鉴权与角色

- JWT RS256；`sub=userId`、`role`、`jti`、`exp`。
- Access Token 15 分钟；Refresh Token 通过 `HttpOnly; Secure; SameSite=Lax` Cookie 下发，路径 `/api/v1/auth`。
- 角色：`USER`、`SUPPORT`、`ADMIN`。`/admin/**` 要求 `ADMIN` 或 `SUPPORT`（写操作仅 `ADMIN`）。

### 1.5 错误响应

```json
{
  "code": "QUOTA_EXCEEDED",
  "message": "面试额度不足，请升级套餐或购买次数包",
  "requestId": "req_01J9Z3K8",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "details": {"required": 1, "balance": 0, "planCode": "FREE"},
  "timestamp": "2026-11-07T10:00:00Z"
}
```

### 1.6 错误码表

| HTTP | code | 含义 | 客户端处理 |
|---|---|---|---|
| 400 | `VALIDATION_FAILED` | 参数校验失败 | 展示 `details.fields` |
| 401 | `UNAUTHENTICATED` | 未登录 | 跳登录 |
| 401 | `TOKEN_EXPIRED` | Access Token 过期 | 静默刷新后重放 |
| 401 | `REFRESH_INVALID` | Refresh 失效/重放 | 清空会话，跳登录 |
| 402 | `QUOTA_EXCEEDED` | 额度不足 | 打开升级弹窗 |
| 403 | `FORBIDDEN` | 无权访问 | 提示无权限 |
| 404 | `RESOURCE_NOT_FOUND` | 资源不存在或无权查看 | 空状态页 |
| 409 | `STATE_CONFLICT` | 状态机不允许 | 刷新会话状态 |
| 409 | `DUPLICATE_ANSWER` | 该题已提交 | 携带 `details.answerId` 跳转 |
| 409 | `DUPLICATE_REQUEST` | 幂等键冲突且参数不同 | 提示勿重复提交 |
| 413 | `FILE_TOO_LARGE` | 文件超限 | 提示压缩/换文件 |
| 415 | `UNSUPPORTED_FILE` | 文件类型不支持 | 提示支持格式 |
| 422 | `FILE_SCAN_FAILED` | 病毒扫描失败 | 拒绝上传 |
| 422 | `PARSE_FAILED` | 解析失败 | 提供手动录入 |
| 422 | `PARSE_NEED_MANUAL` | 无法自动解析（扫描件/OCR 不可用） | 引导粘贴文本，不扣额度 |
| 422 | `CONTENT_REJECTED` | 内容违规 | 提示修改后重试，不扣额度 |
| 423 | `SESSION_LOCKED` | 会话被其他操作占用 | 稍后重试 |
| 429 | `RATE_LIMITED` | 触发限流 | 按 `Retry-After` 退避 |
| 429 | `STREAM_LIMIT_EXCEEDED` | 并发流超限 | 关闭旧流后重试 |
| 500 | `INTERNAL_ERROR` | 未预期错误 | 展示请求 ID |
| 502 | `AI_OUTPUT_INVALID` | 模型输出不合法且修复失败 | 提供“重试评分” |
| 502 | `QUESTION_GEN_FAILED` | 出题失败（会话事件返回） | 自动返还额度，可重新创建 |
| 502 | `ASR_FAILED` | 语音转写失败 | 改用文字输入，不重复扣额度 |
| 503 | `AI_UNAVAILABLE` | 供应商不可用 | 展示降级提示 |
| 504 | `AI_TIMEOUT` | AI 超时 | 重试或降级 |

> 规则：错误信息不得包含堆栈、上游原始响应、Prompt 内容；`details` 仅放可安全展示的字段。

### 1.7 资源、访问分级与版本（融合）

- **资源命名**：名词、复数（`/interviews`）；动作端点 `POST /{id}/<动作>`；嵌套最多 2 层（`/interviews/{id}/answers`）。保留复数写法，不采用单数资源。
- **访问分级**：作为**网关策略**，不写进 URL。
  - `pb` 公开：`/auth/register`、`/auth/login`、`/auth/refresh`、`GET /plans`、健康检查；
  - `pt` 登录：其余 `/api/v1/**`；
  - `pv` 内部：`/internal/v1/**`，仅服务间调用，网关禁止入站；
  - `df` 加密：当前不启用，以 HTTPS + JWT + HttpOnly Refresh Cookie 替代。
- **版本**：按服务整体升级；破坏性变更升 `/api/v2`，旧端点保留至少一个发布周期并在 OpenAPI 标 `deprecated`；新增可选字段视为兼容。
- **例外**：`402 QUOTA_EXCEEDED` 是产品语义明确的业务状态码，保留不改为 403/429。
- **无信封**：HTTP 成功直接返回 DTO，失败统一 `ApiError`；内部接口同样使用 `ApiError`，未来改 RPC 时才引入 `Result<T>`。

## 2. 认证与用户

### 2.1 注册

注册入口由运行时策略控制：管理端可关闭注册；开启时默认要求一次性算术验证码；邀请码始终可选。

`GET /auth/registration-policy`（公开）

```json
{
  "registrationOpen": true,
  "requireCaptcha": true,
  "requireVerifyCode": false,
  "inviteOptional": true,
  "inviteRewardInviterInterview": 1,
  "inviteRewardInviteeResume": 1,
  "dailyInterviewLimit": 3,
  "dailyResumeLimit": 3
}
```

- `requireVerifyCode=true` 表示部署配置了固定运维码（开发验证码或固定邀请码），此时前端展示“验证码 / 邀请码”输入框且不需要算术验证码。
- `inviteOptional` 恒为 `true`；个人邀请码只作为推荐奖励凭证，不决定能否注册。

`POST /auth/captcha`（公开，单 IP 每小时上限 120 次）→ `{captchaId, question, expiresIn}`；答案只存Redis，5 分钟有效且一次性消费。

`POST /auth/register`（公开，限流 3/min/IP + 单 IP/单设备日限；触发返回 429 `RATE_LIMITED`）

```json
{
  "email": "user@example.com",
  "password": "Example#12345",
  "nickname": "小王",
  "captchaId": "cap_xxx",
  "captchaAnswer": "56",
  "inviteCode": "ABCD2345XY",
  "verifyCode": ""
}
```

- `inviteCode` 可选；填写有效邀请码时，邀请人获得面试次数、新用户获得解析次数（都受每日/累计上限与同设备限制）。
- `captchaId/captchaAnswer` 在 `requireCaptcha=true` 时必填；`verifyCode` 在 `requireVerifyCode=true` 时必填。
- 客户端统一附带 `X-Device-Id` 请求头（首次访问生成并持久化 UUID），服务端据此计算设备指纹用于注册风控。

`201 Created`

```json
{
  "userId": "10001",
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "expiresIn": 900,
  "user": {"id": "10001", "nickname": "小王", "role": "USER", "planCode": "FREE"}
}
```

同时 `Set-Cookie: oc_rt=...; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth; Max-Age=604800`。

### 2.2 登录

`POST /auth/login`（限流 10/min/IP；同账号 5 次失败锁定 15 分钟）

```json
{"account": "user@example.com", "password": "Example#12345"}
```

`200 OK`，响应同注册。失败统一返回 `UNAUTHENTICATED`，不区分“账号不存在/密码错误”；触发限流/锁定返回 429 `RATE_LIMITED`。

### 2.3 刷新与登出

- `POST /auth/refresh`：无 Body，依赖 Refresh Cookie；同一家族内旋转 Refresh Token 并返回新 Access Token；并发/多标签页在 30 秒宽限期内重复提交旧 Token 视为并发刷新并同族补发，超过宽限期或家族已吊销（登出/改密/停用）按重放处理并吊销整族。
- `POST /auth/logout`：需要有效 Access；吊销当前 Access 与整个 Refresh 家族；`204 No Content`。Redis 吊销失败返回 500，不伪装成功。
- 已吊销 Access 返回 `UNAUTHENTICATED`；只有自然过期返回 `TOKEN_EXPIRED`。

### 2.4 当前用户

- `GET /users/me` → 用户资料 + 套餐 + 时区 + 隐私设置。
- `PATCH /users/me`：`nickname`、`avatarUrl`、`timezone`、`allowRawResumeToLlm`。
- `DELETE /users/me`：需要 `{"password": "..."}` 二次确认；返回 `202`，7 天后物理删除。

### 2.5 个人邀请

`GET /users/me/invite`（需登录）

```json
{
  "inviteCode": "ABCD2345XY",
  "invitedCount": 2,
  "rewardedInterviewCredits": 2,
  "rewardedResumeCredits": 2,
  "rewardInviterInterview": 1,
  "rewardInviteeResume": 1,
  "dailyCap": 3,
  "totalCap": 20,
  "remainingToday": 1,
  "remainingTotal": 18
}
```

邀请关系仅在注册时写入；同设备（同一设备指纹）注册只记录推荐关系、不发放奖励；超过每日/累计上限不再发放但仍记录关系。

## 3. 额度与订阅

### 3.1 查询额度

`GET /quota`

```json
{
  "planCode": "FREE",
  "planName": "免费版",
  "interviewCredits": 2,
  "resumeCredits": 1,
  "periodEnd": "2026-12-01",
  "daily": {"date": "2026-11-07", "sessionsUsed": 1, "sessionsLimit": 3},
  "features": {"rewrittenAnswer": false, "pdfExport": false}
}
```

### 3.2 套餐与下单（V1.1）

- `GET /plans` → 套餐列表（公开，可缓存 5 分钟）。
- `POST /orders`（`Idempotency-Key` 必填）

```json
{"planCode": "PRO_MONTHLY"}
```

`201` → `{"orderId":"7001","orderNo":"OC202611070001","amountCents":3900,"status":"CREATED","payUrl":"https://pay.example.com/..."}`

- `POST /payments/mock-callback`（仅开发环境）模拟支付成功；生产由支付网关回调，验签后发额度。

### 3.3 工作台聚合

`GET /dashboard`：工作台首屏一次请求返回额度、简历/JD 就绪状态、进行中会话与最近报告，替代简历/JD/面试/报告四个分页列表请求。

```json
{
  "quota": {"planCode": "FREE", "planName": "免费版", "interviewCredits": 2, "resumeCredits": 1,
    "periodEnd": "2026-12-01", "daily": {"date": "2026-11-09", "sessionsUsed": 1, "sessionsLimit": 3},
    "features": {"rewrittenAnswer": false, "pdfExport": false}},
  "resumeReady": true,
  "jdCount": 1,
  "activeSession": {"id": "97", "status": "READY", "resumeId": "41", "jdId": "18",
    "resumeTitle": "后端简历", "jdTitle": "高级后端工程师", "jdCompany": "示例公司",
    "questionCount": 2, "answeredCount": 0, "startedAt": "2026-11-09T09:55:00Z", "lastActiveAt": "2026-11-09T10:00:00Z"},
  "recentReports": [{"id": "12", "sessionId": "90", "status": "READY", "overallScore": 73.3,
    "summary": "完成 2 题，总分 73.3。", "title": "后端简历", "company": "示例公司",
    "role": "高级后端工程师", "questionCount": 2, "createdAt": "2026-11-09T09:30:00Z"}],
  "reportTotal": 1,
  "averageScore": 73.3,
  "scoredReportCount": 1
}
```

- `activeSession` 取最近活跃的未结束会话（`CREATED/READY/IN_PROGRESS/PAUSED`），无则 `null`。
- `recentReports` 最多 4 条；`averageScore` 与 `scoredReportCount` 取最近 20 份报告中已出分的记录统计。

## 4. 简历

### 4.1 上传

`POST /resumes`（`multipart/form-data`，限流 10/hour/user）

| 字段 | 类型 | 说明 |
|---|---|---|
| `file` | binary | PDF/DOCX，≤10MB |
| `title` | string | 可选，默认文件名 |
| `isDefault` | boolean | 可选 |

`201`

```json
{
  "id": "1024",
  "title": "后端开发-张同学.pdf",
  "parseStatus": "PENDING",
  "estimatedReadyInMs": 5000,
  "quota": {"charged": 1, "resumeCredits": 2}
}
```

### 4.1.1 手动建档

`POST /resumes/manual`（JSON，同用户同内容去重）

两种用法二选一：

```json
{"title": "后端开发-张同学", "rawText": "姓名、经历等纯文本，20-50000 字", "isDefault": true}
```

```json
{
  "title": "后端开发-张同学",
  "summary": "3 年后端经验……",
  "structured": {"basic": {}, "education": [], "experiences": [], "projects": [], "skills": [], "certificates": []},
  "isDefault": true
}
```

- `rawText` 路径返回 `PENDING` 并异步解析；`structured` 路径直接落库为 `READY`，不触发解析。
- 两条路径都扣一次 RESUME 额度；返回结构与 4.1 一致。

### 4.2 列表与详情

- `GET /resumes?page=1&size=20` → 分页摘要（不含 `structuredJson`）。
- `GET /resumes/{id}` → 完整详情：

```json
{
  "id": "1024",
  "title": "后端开发-张同学.pdf",
  "parseStatus": "READY",
  "pageCount": 2,
  "structured": {
    "basic": {"name": "张三", "city": "上海", "yearsOfExperience": 3},
    "education": [],
    "experiences": [],
    "projects": [],
    "skills": [],
    "confidence": {"basic": 0.96, "projects": 0.71}
  },
  "summary": "3 年后端经验，主栈 Java/Spring，具备高并发与消息队列实践……",
  "lowConfidenceFields": ["projects[0].metrics"],
  "createdAt": "2026-11-07T09:30:00Z"
}
```

### 4.3 修正、重解析、删除

- `PATCH /resumes/{id}`：`{"title":"...","structured":{...},"isDefault":true}`；修改后异步重建摘要与匹配向量。
- `POST /resumes/{id}/reparse`：`202`；每份简历每小时最多 2 次，不重复扣额度。
- `DELETE /resumes/{id}`：软删除，`204`；被进行中会话引用时返回 `409 STATE_CONFLICT`。
- `GET /resumes/{id}/download-url` → `{"url":"/api/v1/resumes/{id}/file","expiresIn":900}`。
- `GET /resumes/{id}/file`：鉴权后流式返回原文件；手录结构化简历返回 404。

## 5. JD 与匹配

### 5.1 创建 JD

`POST /jds`（限流 30/hour/user）

```json
{"title": "高级后端工程师", "company": "某互联网公司", "rawText": "岗位职责：……任职要求：……"}
```

`201`

```json
{
  "id": "2048",
  "title": "高级后端工程师",
  "structured": {
    "seniority": "SENIOR",
    "mustHave": ["Java", "Spring Boot", "高并发", "MySQL"],
    "niceToHave": ["Kafka", "Kubernetes"],
    "techStack": ["Java", "Spring Cloud", "Redis"],
    "keywords": ["微服务", "稳定性"]
  },
  "createdAt": "2026-11-07T09:35:00Z"
}
```

### 5.2 匹配分析

`POST /jds/{id}/match`（同步，P95 < 3s；超时 8s）

```json
{
  "resumeId": "1024",
  "matchScore": 76.5,
  "matchedSkills": [{"skill": "Java", "evidence": "3 年 Java 后端经验"}],
  "missingSkills": [{"skill": "Kubernetes", "suggestion": "补充容器编排实践或课程项目"}],
  "risks": ["项目描述缺少可量化指标"],
  "evidence": [
    {"jdQuote": "熟悉高并发系统设计", "resumeQuote": "支撑日均 200 万请求"}
  ]
}
```

- `GET /jds`、`GET /jds/{id}`、`DELETE /jds/{id}` 同简历语义。

## 6. 面试会话

### 6.1 创建会话

`POST /interviews`（`Idempotency-Key` 必填，限流 5/min/user）

```json
{
  "resumeId": "1024",
  "jdId": "2048",
  "config": {
    "questionCount": 8,
    "difficulty": "MIXED",
    "interviewerStyle": "STANDARD",
    "answerMode": "TEXT",
    "timeLimitSec": 180,
    "language": "zh-CN"
  }
}
```

`201`

```json
{
  "id": "9001",
  "status": "CREATED",
  "questionCount": 8,
  "quota": {"charged": 1, "balance": 2},
  "createdAt": "2026-11-07T09:40:00Z"
}
```

错误：`QUOTA_EXCEEDED`、`STATE_CONFLICT`（已有未完成会话）、`RESOURCE_NOT_FOUND`（简历/JD 不属于当前用户）。

### 6.2 开始（异步出题）

`POST /interviews/{id}/start` → `202 Accepted`

```json
{"id": "9001", "status": "PREPARING", "estimatedReadyInMs": 9000}
```

客户端随后连接 `GET /interviews/{id}/events`（SSE）或轮询 `GET /interviews/{id}`。该 SSE 端点与评分流一样，使用 `fetch` + `ReadableStream` 携带 `Authorization`，不使用原生 `EventSource`。

#### 会话事件流

`GET /interviews/{id}/events`（`text/event-stream`）

```text
event: status
data: {"status":"PREPARING","progress":10}

event: question_ready
data: {"seq":1,"questionId":"30001","dimension":"DEPTH","difficulty":"EASY"}

event: status
data: {"status":"READY","progress":100,"readyCount":8}

event: ping
data: {"ts":"2026-11-07T09:40:10Z"}
```

失败时：`event: failed` + `data: {"code":"QUESTION_GEN_FAILED","refunded":true}`。

### 6.3 查询会话与题目

`GET /interviews/{id}`

```json
{
  "id": "9001",
  "status": "IN_PROGRESS",
  "config": {"questionCount": 8, "difficulty": "MIXED", "answerMode": "TEXT"},
  "progress": {"answered": 3, "skipped": 0, "total": 8},
  "currentSeq": 4,
  "totalScore": null,
  "startedAt": "2026-11-07T09:41:00Z",
  "lastActiveAt": "2026-11-07T09:52:00Z"
}
```

`GET /interviews/{id}/questions`（仅返回当前及已答题目，未到题不打乱顺序）

```json
{
  "items": [
    {
      "id": "30001",
      "seq": 1,
      "dimension": "DEPTH",
      "difficulty": "EASY",
      "text": "请介绍你最近一个项目中负责的核心模块……",
      "source": "BANK",
      "referencePoints": ["背景与目标", "个人职责", "技术取舍", "量化结果"],
      "status": "ANSWERED",
      "whyThisQuestion": "你的简历提到订单系统重构，JD 要求高并发经验"
    }
  ]
}
```

- `POST /interviews/{id}/questions/{qid}/skip` → `204`；计 0 分，状态 `SKIPPED`。
- `POST /interviews/{id}/pause`、`POST /interviews/{id}/resume` 切换 `PAUSED/IN_PROGRESS`。
- `POST /interviews/{id}/abandon` → `204`：主动放弃会话。未提交任何回答时返还额度；已作答不返还。允许从 `CREATED/PREPARING/READY/IN_PROGRESS/PAUSED` 发起。

### 6.4 提交回答

`POST /interviews/{id}/answers`（`Idempotency-Key` 必填）

```json
{
  "questionId": "30001",
  "answerText": "我当时负责订单履约链路的重构……",
  "durationMs": 96000,
  "inputMode": "TEXT"
}
```

`201`

```json
{
  "answerId": "40001",
  "status": "SCORING"
}
```

- 服务端在事务提交后异步生成反馈；前端应轮询 `GET /interviews/{id}/answers/{answerId}/feedback`（404 表示尚未生成），完成后一次性展示，不依赖 SSE token 流。
- 兼容字段 `stream` 仅在显式开启 `oc.ai.sse-enabled` 时可用，默认关闭。

错误：

- `409 DUPLICATE_ANSWER`，`details.answerId` 指向已有回答，客户端应直接打开反馈。
- `422 VALIDATION_FAILED`：长度不足 10 或超过 4000。
- `409 STATE_CONFLICT`：会话非 `IN_PROGRESS`。

### 6.5 查询回答与反馈

- `GET /interviews/{id}/answers/{answerId}` → 回答原文、时长、状态。
- `GET /interviews/{id}/answers/{answerId}/feedback` → 权威评分结果（FREE 套餐 `rewrittenAnswer` 为 `null`）；异步生成期间返回 `404 RESOURCE_NOT_FOUND`，客户端可每 500–1000ms 轮询。
- `POST /interviews/{id}/answers/{answerId}/feedback` → 主动触发并同步返回评分；用于轮询超时后的重试。幂等，不重复扣额度。

```json

```json
{
  "feedbackId": "50001",
  "version": 1,
  "answerId": "40001",
  "overallScore": 8.1,
  "dimensions": {
    "content": 8.5,
    "structure": 8.0,
    "depth": 7.5,
    "fit": 8.5,
    "language": 8.0
  },
  "strengths": ["背景交代清楚，先给结论再展开", "引用了可量化的性能指标"],
  "improvements": ["技术取舍部分缺少替代方案对比", "结尾可补充复盘与后续优化"],
  "evidence": [
    {"quote": "QPS 从 800 提升到 2400", "reason": "量化结果增强说服力"}
  ],
  "rewrittenAnswer": "建议按 STAR 结构重写：……",
  "model": "large",
  "degraded": false,
  "createdAt": "2026-11-07T09:53:10Z"
}
```

### 6.6 结束会话

`POST /interviews/{id}/finish` → `202`

```json
{"reportId": "60001", "status": "GENERATING", "estimatedReadyInMs": 5000}
```

要求至少完成 1 题；否则返回 `409 STATE_CONFLICT`。

## 7. SSE 评分流协议（兼容，默认关闭）

> 2026-11-08 起前端改为轮询反馈，`oc.ai.sse-enabled` 默认 `false`；以下协议仅保留给显式开启的兼容客户端。

### 7.1 端点

`GET /ai/feedback/stream?answerId=40001&token=st_...`

- 该路径由 Gateway 直接路由到 `ai-orchestrator`。
- `token` 为 `interview-service` 签发的 5 分钟短期 JWT，`aud=ai`，`sub=userId`，`answerId` 绑定。
- 客户端同时携带 `Authorization`；服务端双校验。**不把长期 Access Token 放 URL**。

### 7.2 事件类型

| event | data | 说明 |
|---|---|---|
| `meta` | `{"answerId","model","promptVersion","degraded"}` | 连接成功后的首事件 |
| `token` | `{"seq":1,"delta":"这道题"}` | 增量文本，按序拼接 |
| `section` | `{"name":"strengths"}` | 可选：提示前端切换到某一区块 |
| `result` | `{"feedbackId","overallScore","dimensions":{...}}` | 结构化结果（最终） |
| `error` | `{"code","message","retryable"}` | 错误，流随即关闭 |
| `ping` | `{"ts":"..."}` | 每 15s 心跳，客户端 30s 未收到则重连 |

### 7.3 示例

```text
event: meta
data: {"answerId":"40001","model":"large","promptVersion":"score_answer@v3","degraded":false}

event: token
data: {"seq":1,"delta":"整体回答结构清晰，"}

event: token
data: {"seq":2,"delta":"先交代了背景与目标。"}

event: section
data: {"name":"improvements"}

event: token
data: {"seq":3,"delta":"技术取舍部分建议补充……"}

event: result
data: {"feedbackId":"50001","overallScore":8.1,"dimensions":{"content":8.5,"structure":8.0,"depth":7.5,"fit":8.5,"language":8.0}}
```

### 7.4 客户端约束

1. 使用 `fetch` + `ReadableStream`（EventSource 无法携带 Authorization）。
2. 断线重连最多 2 次，重连需重新申请 `streamToken`（避免令牌过期）；不得自动重复扣额度。
3. 收到 `result` 后必须再调用一次反馈详情接口，以服务端落库结果为准。
4. 用户主动取消：调用 `AbortController.abort()`；服务端检测断连后停止上游生成，节省 Token。
5. 渲染时按 `seq` 排序去重；文本块用纯文本/Markdown 安全渲染。

## 8. 报告

### 8.1 列表与详情

- `GET /reports?page=1&size=20` → 摘要列表。
- `GET /reports/{id}`：

```json
{
  "id": "60001",
  "sessionId": "9001",
  "title": "后端开发-张同学",
  "company": "云帆科技",
  "role": "高级后端工程师",
  "questionCount": 8,
  "status": "READY",
  "overallScore": 78.4,
  "radar": [
    {"dimension": "content", "label": "内容相关性", "score": 8.2},
    {"dimension": "structure", "label": "结构化表达", "score": 7.8},
    {"dimension": "depth", "label": "专业深度", "score": 7.1},
    {"dimension": "fit", "label": "岗位匹配", "score": 8.4},
    {"dimension": "language", "label": "语言表达", "score": 8.0}
  ],
  "summary": "表达清晰、岗位匹配度较好，专业深度与量化结果仍有提升空间。",
  "highlights": ["项目背景交代完整", "能主动给出性能数据", "对 JD 技术栈理解到位"],
  "risks": ["技术方案缺少取舍对比", "行为题回答偏流水账", "未体现失败复盘"],
  "improvementPlan": [
    {"priority": 1, "action": "用 STAR 重写两段项目经历", "reason": "行为题得分最低", "resource": "STAR 模板"},
    {"priority": 2, "action": "补充缓存一致性方案的对比", "reason": "深度题证据不足", "resource": "专项练习"}
  ],
  "match": {"before": 76.5, "after": 82.0, "delta": 5.5},
  "questionBreakdown": [
    {
      "questionId": "30001",
      "answerId": "40001",
      "feedbackId": "50001",
      "seq": 1,
      "score": 8.5,
      "dimension": "DEPTH",
      "summary": "...",
      "question": "谈谈你对缓存一致性的理解。",
      "answer": "我采用延迟双删……",
      "feedback": {
        "overallScore": 8.5,
        "dimensions": {"content": 8.4, "structure": 8.0, "depth": 8.6, "fit": 8.2, "language": 8.3},
        "strengths": ["方案对比完整"],
        "improvements": ["补充失败场景"],
        "evidence": [{"quote": "延迟双删", "reason": "命中关键取舍"}],
        "rewrittenAnswer": "结论先行：……"
      }
    }
  ],
  "pdf": {"available": true, "expiresAt": "2026-11-14T10:00:00Z"},
  "generatedAt": "2026-11-07T10:01:20Z"
}
```

### 8.2 重试与导出

- `POST /reports/{id}/retry`：仅 `FAILED` 可重试，`202`；报告置回 `GENERATING`，会话置回 `REPORT_GENERATING`，重置 `attempts`，不扣额度；手动重试最多 3 次/报告。生成前会等待所有可评分回答完成，最多自动重试 6 次（每次间隔 5 秒）。
- 逐题 `strengths/improvements/evidence` 按题目场景生成：项目题看职责与结果，技术题看原理与取舍，行为题看冲突与复盘，匹配题看动机与计划；同时参考 `referencePoints` 覆盖情况给出题目专属改进项。
- 当前前端使用浏览器「打印 / 存为 PDF」，默认文件名由 `公司-岗位-YYMMDD` 构成（如 `云帆科技-高级后端工程师-261107`）；打印时会展开逐题拆解并临时切到浅色主题。
- `POST /reports/{id}/export`（V1.1，未实现）：请求服务端生成 PDF，`202`；`GET /reports/{id}/export` 查询进度。

## 9. 语音作答（V1.1）

### 9.1 上传音频

`POST /interviews/{id}/answers/audio`（multipart）

| 字段 | 说明 |
|---|---|
| `questionId` | 题目 ID |
| `file` | webm/wav/mp3，10–300s，≤25MB |
| `durationMs` | 客户端录制时长 |

`201`

```json
{
  "answerId": "40002",
  "asrStatus": "PENDING",
  "stream": {"url": "/ai/feedback/stream", "answerId": "40002", "token": "st_...", "expiresIn": 300}
}
```

- ASR 完成前流式接口会先返回 `meta`，随后等待转写完成（最长 10s）再出评分 Token。
- ASR 失败：`event: error`，`code=ASR_FAILED`，前端切换文本输入，不重复扣额度。

## 10. 通知

- `GET /notifications?unreadOnly=true` → 列表。
- `POST /notifications/{id}/read` → `204`。
- `POST /notifications/read-all` → `204`。

## 11. 管理端

> 统一前缀 `/admin`；写操作记录 `admin_audit_log`。分页参数同上。
> **实现说明（2026-11-08）**：题库 CRUD/发布/归档/JSON 导入/批次、用户检索/状态/额度、指标概览、AI 调用日志、Prompt 模板/版本/发布、实验 CRUD、审计日志已实现；题库详情与 `reindex` 未实现（当前检索为 PostgreSQL trigram，无向量索引）；Prompt 评测/回滚与实验分组指标未实现；A/B 仅 CRUD，未接运行时。

### 11.1 题库

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/questions` | 筛选：`status/dimension/difficulty/jobFamily/keyword` |
| POST | `/admin/questions` | 新建题目 |
| GET | `/admin/questions/{id}` | 详情（含向量化状态） |
| PATCH | `/admin/questions/{id}` | 更新；发布后修改需升版本 |
| POST | `/admin/questions/{id}/publish` | 发布并触发增量向量化 |
| POST | `/admin/questions/{id}/archive` | 归档（Milvus 标 status=0） |
| POST | `/admin/questions/import` | 上传 JSONL/CSV，`202` 返回 `batchId` |
| GET | `/admin/questions/import/{batchId}` | 导入进度与错误报告 |
| POST | `/admin/questions/reindex` | 全量重建索引，`202` |

新建题目：

```json
{
  "title": "缓存与数据库一致性",
  "jobFamily": "BACKEND",
  "dimension": "DEPTH",
  "difficulty": "MEDIUM",
  "tags": ["Redis", "缓存", "一致性"],
  "stem": "在高并发场景下，你如何保证缓存与数据库的一致性？",
  "referenceAnswer": "……",
  "scoringPoints": [{"point": "延迟双删", "weight": 0.3}],
  "source": "MANUAL"
}
```

### 11.2 Prompt 与实验

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/prompts` | 模板列表（含当前发布版本） |
| POST | `/admin/prompts` | 新建模板 |
| GET | `/admin/prompts/{code}/versions` | 版本列表 |
| POST | `/admin/prompts/{code}/versions` | 新建草稿版本 |
| POST | `/admin/prompts/{code}/versions/{version}/evaluate` | 触发离线评测 |
| POST | `/admin/prompts/{code}/versions/{version}/publish` | 发布（需评测通过） |
| POST | `/admin/prompts/{code}/rollback` | 回滚到指定版本 |
| GET | `/admin/experiments` | 实验列表 |
| POST | `/admin/experiments` | 创建实验 |
| POST | `/admin/experiments/{id}/status` | 切换实验状态 |

### 11.3 AI 运行时配置

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/ai-config` | 当前启用状态、Base URL、主/小模型、`hasApiKey`、`apiKeyMasked`、更新时间与操作人 |
| PUT | `/admin/ai-config` | `{enabled,baseUrl,model,smallModel,apiKey?,clearApiKey?}`；Key 留空保留旧值 |
| POST | `/admin/ai-config/test` | 用当前配置发极小请求，返回 `{ok,model,latencyMs,message}`；不可用 503 |

- Key 使用 AES-GCM 加密存储，接口与审计永不返回明文；保存后运行时缓存立即失效。
- 未配置 `OC_AI_SECRET` 时拒绝保存 Key；URL/模型仍可保存。

### 11.4 用户权限

| 方法 | 路径 | 说明 |
|---|---|---|
| PATCH | `/admin/users/{id}/role` | `{role:"USER|SUPPORT|ADMIN"}`；禁止改自己、禁止撤销最后一个管理员 |
| POST | `/admin/users/{id}/unlock` | 清空失败计数与锁定；已停用/注销账号 409 |
| POST | `/admin/users/{id}/reset-password` | `{password}`；8–64 位含字母和数字；204 |

### 11.5 平台策略

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/settings/quota` | 注册开关、每日面试/解析上限、月度免费额度、邀请奖励与注册风控阈值 |
| PUT | `/admin/settings/quota` | 全量更新同上字段；写 `SETTINGS_QUOTA_UPDATE` 审计 |

```json
{
  "registrationOpen": true,
  "dailyInterviewLimit": 3,
  "dailyResumeLimit": 3,
  "freeMonthlyInterviews": 3,
  "freeMonthlyResumes": 3,
  "inviteRewardInviterInterview": 1,
  "inviteRewardInviteeResume": 1,
  "inviteDailyCap": 3,
  "inviteTotalCap": 20,
  "registrationIpDailyCap": 20,
  "registrationDeviceDailyCap": 3
}
```

- 每日上限与邀请奖励 0 表示“不限制 / 暂停发放”；保存后 30 秒内生效。
- 月度免费额度在周期到期时自动续发；到期套餐降级为 FREE 后按该值续发。

创建实验：

```json
{
  "templateCode": "score_answer",
  "name": "v4 结构化评分灰度",
  "trafficPercent": 10,
  "variants": [
    {"key": "control", "version": "v3", "weight": 50},
    {"key": "test", "version": "v4", "weight": 50}
  ],
  "guardrails": {
    "maxErrorRate": 0.02,
    "maxTtftRegression": 0.15,
    "minSamples": 200
  }
}
```

### 11.3 用户、日志、看板

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/users?keyword=&status=` | 用户检索 |
| PATCH | `/admin/users/{id}/status` | 禁用/启用 |
| POST | `/admin/users/{id}/quota` | 补/扣额度，必填 `reason` 与 `idempotencyKey` |
| GET | `/admin/ai-calls` | 按场景/模型/状态/耗时/用户筛选 |
| GET | `/admin/ai-calls/{id}` | 详情（Prompt 与输出脱敏，仅 ADMIN） |
| GET | `/admin/metrics/overview?range=7d` | 核心指标 |
| POST | `/admin/evals/rag` | 触发召回评测，`202` |
| GET | `/admin/evals/{jobId}` | 评测结果 |
| GET | `/admin/audit-logs` | 审计日志 |

补额度请求：

```json
{
  "type": "INTERVIEW",
  "delta": 3,
  "reason": "客服补偿：报告生成失败",
  "idempotencyKey": "comp_20261107_001"
}
```

## 12. 内部接口（不暴露公网）

| 方法 | 路径 | 调用方 | 说明 |
|---|---|---|---|
| POST | `/internal/v1/resumes:parse` | interview | 解析简历，返回结构化 + 摘要 |
| POST | `/internal/v1/jds:analyze` | interview | JD 结构化 + 匹配分析 |
| POST | `/internal/v1/questions:generate` | interview | RAG 出题 |
| POST | `/internal/v1/feedbacks` | ai-orchestrator → interview | 回写评分结果（幂等键 `answerId`） |
| POST | `/internal/v1/reports:generate` | report-worker | 生成报告 JSON |
| POST | `/internal/v1/asr` | interview | 语音转写 |
| GET | `/internal/v1/prompts/{code}/active` | ai-orchestrator | 拉取当前发布版本（缓存 60s） |
| POST | `/internal/v1/ai-calls:batch` | ai-orchestrator | 批量写调用日志 |

**内部鉴权**：`Authorization: Bearer <service-token>` + `X-Service-Name`；服务间 mTLS；生产网关禁止 `/internal/**` 入站。

出题请求示例：

```json
{
  "sessionId": "9001",
  "userId": "10001",
  "resumeSummary": "3 年后端，Java/Spring，订单系统重构……",
  "jdSummary": "高级后端，要求高并发、Kafka、K8s……",
  "gapSkills": ["Kubernetes", "Kafka"],
  "config": {"questionCount": 8, "difficulty": "MIXED", "interviewerStyle": "STANDARD", "language": "zh-CN"},
  "excludeQuestionIds": ["1201", "1302"]
}
```

出题响应：

```json
{
  "questions": [
    {
      "seq": 1,
      "text": "请介绍你负责的订单系统重构……",
      "dimension": "DEPTH",
      "difficulty": "EASY",
      "source": "BANK",
      "bankQuestionId": "1201",
      "referencePoints": ["背景", "职责", "取舍", "结果"],
      "retrieval": {"chunkIds": ["1201-stem-1"], "scores": [0.83], "rerankScores": [0.91]},
      "whyThisQuestion": "简历与 JD 均涉及订单/高并发"
    }
  ],
  "meta": {"retrievalLatencyMs": 420, "reuseRate": 0.75, "degraded": false}
}
```

评分回写请求：

```json
{
  "requestId": "fb_01J9Z4M2",
  "answerId": "40001",
  "sessionId": "9001",
  "userId": "10001",
  "overallScore": 8.1,
  "dimensions": {"content": 8.5, "structure": 8.0, "depth": 7.5, "fit": 8.5, "language": 8.0},
  "strengths": ["", ""],
  "improvements": ["", ""],
  "evidence": [{"quote": "", "reason": ""}],
  "rewrittenAnswer": "",
  "model": "large",
  "provider": "primary",
  "promptVersionId": "302",
  "tokensIn": 1820,
  "tokensOut": 640,
  "firstTokenMs": 720,
  "latencyMs": 6400
}
```

## 13. 幂等、并发与重试约定

| 场景 | 幂等键 | 重复请求行为 |
|---|---|---|
| 创建会话 | `Idempotency-Key` | 返回首次创建的会话 |
| 提交回答 | `Idempotency-Key` + `questionId` 唯一 | 返回已有 `answerId` |
| 下单 | `Idempotency-Key` | 返回首次订单 |
| 评分回写 | `answerId` | 覆盖/忽略，不重复计费 |
| 报告生成 | `reportId + attempt` | 已 READY 直接返回 |
| 补额度 | 管理端 `idempotencyKey` | 拒绝重复入账 |

- 相同 Key、不同请求体 → `409 DUPLICATE_REQUEST`。
- 幂等记录 24h 过期，DB + Redis 双写；以 DB 为准。
- 并发流限制：每用户 2 条；超出 `429 STREAM_LIMIT_EXCEEDED`。
- 客户端重试策略：仅对 `429/502/503/504` 与网络错误重试，指数退避 0.5s/1s/2s，最多 3 次；`4xx` 不重试（401 刷新后除外）。

## 14. OpenAPI 与契约管理

- 后端用 springdoc 生成 OpenAPI 3.1；Python 用 FastAPI 原生 OpenAPI；两者在 CI 中合并为 `openapi.bundle.json`。
- 前端通过 `openapi-typescript` 生成类型，禁止手写重复 DTO。
- CI 执行 `oasdiff` 检查破坏性变更；破坏性变更需升级 `/api/v2` 或双版本过渡，旧端点标 `deprecated` 并保留至少一个发布周期。
- 接口文档先行：骨架层冻结 URL/方法/错误码/鉴权后再并行开发；每个接口必须有请求/响应示例与边界/错误用例。
- 内部接口同样纳入契约测试，字段增删必须双端同步。
- 每个接口必须声明：鉴权要求、限流、错误码、幂等性、示例。
