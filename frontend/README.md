# OfferCoach Web

Vue 3 + TypeScript + Vite + Pinia + Tailwind 的前端实现，UI 基座为 **shadcn-vue 风格（Reka UI + Tailwind）**，采用中性面 + 单一 accent。前端已接入真实后端：认证、额度、简历、JD、面试、评分轮询与报告均通过 `src/api/` 调用 `docs/design/api.md` 定义的接口，不再使用 mock 数据。

## 启动

```bash
cd frontend
pnpm install
pnpm dev        # http://localhost:5174
```

默认 API 基址为 `/api/v1`，Vite dev server 将 `/api` 代理到 `http://localhost:8080`（gateway / app）。需要直连其他环境时设置：

```bash
VITE_API_BASE_URL=https://api.example.com/api/v1 pnpm dev
```

Refresh Token 由后端 `HttpOnly` Cookie 管理，因此前后端需同站或按后端 CORS 配置携带凭据（客户端固定 `credentials: 'include'`）。

本地演示账号由后端环境变量 `oc.demo.email` / `oc.demo.password` 初始化（`DemoDataInitializer`），前端不硬编码账号与密码，仅在 `import.meta.env.DEV` 下展示提示。注册需要后端下发的验证码；未开启验证码能力时页面直接展示后端错误信息。

## 构建与验证

```bash
pnpm typecheck     # vue-tsc --noEmit（strict + exactOptionalPropertyTypes）
pnpm lint          # ESLint 10 flat config，0 error / 0 warning
pnpm test:unit     # Vitest：API client、简历导出、格式化
pnpm build         # vue-tsc + vite build
pnpm check         # typecheck + lint + test + build
```

## 页面与接口

| 页面        | 路径                | 主要接口                                                                 |
| ----------- | ------------------- | ------------------------------------------------------------------------ |
| 登录 / 注册 | `/login`、`/register` | `POST /auth/login`、`POST /auth/register`、`POST /auth/refresh`           |
| 工作台      | `/`                 | `GET /quota`、`GET /resumes`、`GET /interviews`、`GET /reports`、`GET /jds` |
| 简历管理    | `/resumes`          | `POST/GET /resumes`、`GET/PATCH/DELETE /resumes/{id}`、`POST .../reparse` |
| 目标岗位    | `/jds`              | `POST/GET /jds`、`GET/DELETE /jds/{id}`、`POST /jds/{id}/match`           |
| 创建面试    | `/interview/new`    | `POST /interviews`（Idempotency-Key）、`POST /interviews/{id}/start`      |
| 面试间      | `/interview/:id`    | `GET /interviews/{id}`、题目/跳过/暂停/恢复/放弃/提交回答/结束             |
| 报告列表    | `/reports`          | `GET /reports`、`POST /reports/{id}/retry`                                |
| 报告详情    | `/reports/:id`      | `GET /reports/{id}`（生成中轮询）、失败重试                                |
| 套餐        | `/plans`            | `GET /plans`、`GET /quota`                                                |
| 设置        | `/settings`         | `GET/PATCH /users/me`、`POST /auth/logout`                                |

## 实现要点

- **认证**：Access Token 只存内存；`401` 时并发去重地 `POST /auth/refresh` 并重放原请求，失败清空会话回登录。刷新 Cookie 静默恢复在首个路由守卫中执行。
- **SSE**：`src/api/stream.ts` 使用 `fetch` + `ReadableStream`（携带 `Authorization`），解析 `meta/token/section/result/error/ping`；token 按 `seq` 排序去重；30 秒无事件视为断线，最多重连 2 次，重连前用同一幂等键重新申请 `streamToken`。收到 `result` 后再请求一次权威反馈详情。
- **幂等**：创建会话与提交回答使用 `crypto.randomUUID()` 作为 `Idempotency-Key`；网络失败重试复用同一 key。
- **轮询**：简历 `PARSING` 每 2 秒、面试 `PREPARING` 每 2 秒、报告 `GENERATING` 每 2–3 秒轮询，状态结束后停止。
- **简历导出**：`src/utils/resumeExport.ts` 提供 Markdown、JSON 与 PDF（新窗口打印，可另存 PDF）。打印 HTML 逐字段转义；版式沿用当前 `classic/modern/compact/minimal` 预览风格。编辑弹窗可导出未保存的表单内容。
- **版式偏好**：版式属于本机展示偏好，保存在 `localStorage`（`oc_resume_layout_v1:{resumeId}`），不写入服务端简历实体。
- **草稿**：回答停笔 3 秒写入 `localStorage`（`oc_draft_<sessionId>_<questionId>`）；切题、卸载、提交、跳过与 `beforeunload` 立即 flush。

## 结构

```text
src/
├── api/             # client、auth、users、quota、plans、resumes、jds、interviews、reports、stream
├── components/
│   ├── ui/          # Button/Input/Card/Badge/Dialog/Toast 等
│   ├── common/      # AppShell、Sidebar、PageHeader、AsyncState、ExportMenu、MetricStrip
│   ├── charts/      # ScoreRing、RadarChart（纯 SVG）
│   ├── interview/   # 房间/创建页组件
│   ├── jd/ report/ dashboard/
│   └── resume/      # 预览、4 套版式、编辑器
├── composables/     # useTheme、useToast、useInterviewRoom、usePolling
├── constants/       # 简历版式元数据
├── pages/           # 路由页面
├── router/          # 路由、静默恢复与登录守卫
├── stores/          # app（认证、当前用户、额度）
├── styles/          # Design Token 与全局样式
├── types/           # API DTO 与 UI 领域类型
└── utils/           # cn、格式化、简历导出、版式偏好
```

## 约束

- 只用 Design Token（`bg-surface`、`text-muted`、`brand` 等），不写任意 hex。
- 禁止 `any`、`v-html`、`src` 内 `console`；图标用 lucide-vue-next。
- 所有请求走 `src/api/`，UI 只按 `ApiError.code` 分支，不匹配文案。
- 面试房间 375px 可完成作答；≥1400px 三栏，以下单列。
- 所有异步视图具备 loading / empty / error 三态（`AsyncState`）。