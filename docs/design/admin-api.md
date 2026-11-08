# 管理端 API 与页面契约

> 状态：生效 ｜ 版本：v1.0 ｜ 更新：2026-11-07
> 前缀：`/api/v1/admin` ｜ 鉴权：`ADMIN`/`SUPPORT` 可读，写操作仅 `ADMIN`
> 关联：[api.md](./api.md)、[data-model.md](./data-model.md)、[ui-system.md](../dev/ui-system.md)

## 0. 约定

- 所有响应沿用 [api.md](./api.md) §1 的 `camelCase`、字符串 ID、ISO-8601 UTC、显式 null、`ApiError`。
- 列表统一 `items/total/page/size`。
- 写操作写 `admin_audit_log`（actor、action、target、before/after 脱敏 JSON）。

## 1. 指标

`GET /metrics/overview?range=7d`

```json
{
  "users": 120,
  "activeSessions": 3,
  "completedSessions": 45,
  "reports": 40,
  "avgScore": 74.2,
  "aiCalls": 860,
  "aiFailures": 12,
  "avgAiLatencyMs": 840,
  "interviewCredits": 980,
  "resumeCredits": 2400
}
```

`range` 支持 `24h|7d|30d`，只影响 AI 调用与审计统计窗口。

## 2. 题库

`GET /questions?page=1&size=20&status=&dimension=&difficulty=&jobFamily=&keyword=`

```json
{
  "items": [{
    "id": "30001", "title": "缓存一致性", "jobFamily": "后端",
    "dimension": "DEPTH", "difficulty": "MEDIUM",
    "tags": ["Redis", "缓存"], "status": "PUBLISHED",
    "source": "IMPORT", "sourceRepo": "didilili/mianstack",
    "company": null, "version": 1, "createdAt": "...", "updatedAt": "..."
  }],
  "total": 26385, "page": 1, "size": 20
}
```

- `GET /questions/{id}` 增加 `stem/referenceAnswer/scoringPoints`。
- `POST /questions` 新建，`PATCH /questions/{id}` 更新（发布后修改自动 `version+1`）。
- `POST /questions/{id}/publish`、`POST /questions/{id}/archive`。
- `POST /questions/import`（multipart `file`，JSONL/CSV）同步返回：
  `{"batchId":"1","total":100,"success":98,"failed":1,"skipped":1}`
- `GET /questions/import/{batchId}` 返回同结构 + `errorReport`。

新建请求：

```json
{
  "title": "缓存与数据库一致性",
  "jobFamily": "后端",
  "dimension": "DEPTH",
  "difficulty": "MEDIUM",
  "tags": ["Redis", "缓存"],
  "stem": "在高并发场景下如何保证缓存与数据库一致性？",
  "referenceAnswer": "……",
  "referencePoints": ["延迟双删", "版本号", "降级预案"]
}
```

## 3. 用户与额度

`GET /users?keyword=&status=&page=1&size=20`

```json
{
  "items": [{
    "id": "1", "email": "user@example.com", "nickname": "示例用户",
    "role": "USER", "status": "ACTIVE", "planCode": "PRO",
    "interviewCredits": 29, "resumeCredits": 9998,
    "createdAt": "...", "lastLoginAt": "..."
  }],
  "total": 2, "page": 1, "size": 20
}
```

- `PATCH /users/{id}/status`：`{"status":"ACTIVE|DISABLED"}`。
- `POST /users/{id}/quota`：`{"type":"INTERVIEW|RESUME","delta":3,"reason":"客服补偿","idempotencyKey":"admin_..."}`
  - `delta>0` 增加，`delta<0` 扣减；写 `quota_ledger`，幂等键 `admin-quota:{key}`。

## 4. Prompt 模板

- `GET /prompts`：`{code,name,scene,description,activeVersion,publishedAt,versionCount}`
- `POST /prompts`：`{code,name,scene,description}`
- `GET /prompts/{code}/versions`：版本列表（含 status/contentHash/evalReport/createdAt）
- `POST /prompts/{code}/versions`：`{version,content,variablesSchema,modelConfig}` → DRAFT
- `POST /prompts/{code}/versions/{version}/publish`：归档旧 PUBLISHED，发布新版本
- `POST /prompts/{code}/rollback`：`{version}`，将目标版本重新发布

运行时 `PromptRegistry` 按 `code` 读取 PUBLISHED 内容；缺失时回退内置 Prompt：
`question_gen`（出题）、`feedback`（评分）、`report`（报告总评）。

## 5. AI 调用日志

`GET /ai-calls?page=1&size=20&scene=&status=&userId=`

```json
{
  "items": [{
    "id": "1", "userId": "1", "scene": "FEEDBACK",
    "provider": "openai-compatible", "model": "deepseek-chat",
    "status": "SUCCESS", "tokensIn": 820, "tokensOut": 240,
    "firstTokenMs": 320, "latencyMs": 2100, "errorCode": null, "createdAt": "..."
  }],
  "total": 1, "page": 1, "size": 20
}
```

## 6. 审计日志

`GET /audit-logs?page=1&size=20&action=&actorId=`

```json
{
  "items": [{
    "id": "1", "actorId": "3", "actorEmail": "ops@example.com",
    "action": "QUESTION_PUBLISH", "targetType": "QUESTION", "targetId": "30001",
    "before": {"status": "DRAFT"}, "after": {"status": "PUBLISHED"},
    "requestId": "req_...", "createdAt": "..."
  }],
  "total": 1, "page": 1, "size": 20
}
```

## 7. 前端页面

- 路由 `/admin`，`meta.requiresAdmin=true`；`USER` 访问跳工作台并提示无权限。
- 侧边栏与用户菜单仅在 `role=ADMIN|SUPPORT` 时显示“管理后台”。
- 页签：概览、题库、Prompt、用户、AI 日志、审计日志；沿用现有 `PageHeader/Card/Button/Badge/Dialog/EmptyState`。
- 写操作按角色禁用：`SUPPORT` 只读，`ADMIN` 可编辑。