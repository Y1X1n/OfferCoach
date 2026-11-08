# 前端开发规范

> 状态：生效 ｜ 版本：v1.5 ｜ 更新：2026-11-07
> 技术栈：Vue 3.5、TypeScript 5.6、Vite 6、Pinia 2、Vue Router 4、Tailwind CSS、shadcn-vue
> 关联：[UI 设计系统](./ui-system.md)、[API](../design/api.md)
>
> **当前实现**：`frontend/` 已接入真实后端（端口 5174，API 基址 `VITE_API_BASE_URL`，默认 `/api/v1` 并代理到 8080）。认证、额度、简历、JD、面试、评分 SSE 与报告均走 `src/api/`，不依赖 `src/mocks/`；Access Token 只存内存，Refresh 使用 HttpOnly Cookie。M1–M4 后端接口按 [API 契约](../design/api.md) 联调。验证命令：`pnpm typecheck`、`pnpm lint`、`pnpm test:unit`、`pnpm build`。

## 1. 技术栈与依赖边界

| 能力 | 选型 | 约束 |
|---|---|---|
| 框架 | Vue 3.5（`<script setup lang="ts">`） | 禁止 Options API 新代码 |
| 构建 | Vite 6 | 环境变量仅 `VITE_` 前缀 |
| 语言 | TypeScript 5.6 | `strict: true`，禁止 `any` |
| 路由 | Vue Router 4 | 路由级懒加载 + 元信息守卫 |
| 客户端状态 | Pinia 2 | 只存 UI/会话状态，不存服务端实体 |
| 服务端状态 | `@tanstack/vue-query` 5 | 缓存、重试、失效统一管理 |
| 样式 | Tailwind CSS 3.4 + CSS Variables | 只使用 Design Token |
| 组件 | shadcn-vue（Reka UI） | 源码复制进仓库，可改 |
| 图标 | lucide-vue-next | 禁止混用多套图标 |
| 表单 | vee-validate + zod | Schema 前后端可共享字段约束 |
| 图表 | ECharts + vue-echarts | 雷达图/趋势图按需引入 |
| 日期 | dayjs（timezone 插件） | 统一 UTC → 本地时区 |
| Markdown | markdown-it + DOMPurify | 禁止直接 `v-html` |
| 音频 | MediaRecorder + wavesurfer.js | 仅 V1.1 启用 |
| 测试 | Vitest + Vue Test Utils + Playwright | 见 [testing.md](./testing.md) |
| 文档 | Storybook 8 | 核心组件必须有 stories |

**禁止引入**：jQuery、Moment、Element Plus/Ant Design Vue（与设计系统冲突）、未维护的 SSE 库。新增依赖需说明体积、维护状态与替代方案。

> **演示版实际依赖**：Vue 3.5、Vite 6、TypeScript、Pinia、Vue Router、Tailwind、Reka UI、lucide、Vitest、Playwright Core（截图冒烟）、ESLint + Prettier。表中 TanStack Query、vee-validate/zod、ECharts、dayjs、markdown-it/DOMPurify、vue-i18n、Storybook 为**接入后端目标态**，当前未安装；表单校验用手写规则，图表为纯 SVG，日期用原生 `Intl`。安装或移除依赖时同步更新本表与 `package.json`。

## 2. 目录结构

### 2.1 当前实现（已接后端）

```text
frontend/src/
├── main.ts、App.vue
├── router/index.ts          # 路由、登录守卫、静默恢复、页面标题
├── api/                     # client、auth、users、quota、plans、resumes、jds、interviews、reports、stream
├── pages/                   # 12 个路由页面（扁平，按路由命名）
├── components/
│   ├── ui/                  # shadcn 风格原子组件（Button/Input/Card/Dialog/Toast…）
│   ├── common/              # AppShell、Sidebar、PageHeader、AsyncState、ExportMenu、MetricStrip、QuotaBadge
│   ├── charts/              # ScoreRing、RadarChart（纯 SVG）
│   ├── interview/           # 房间/创建页组件；setup/ 为选择与配置子组件
│   ├── resume/              # 预览、编辑器（editor/ 六个分区）、版式选择、4 套 layout
│   ├── jd/ report/ dashboard/
├── composables/             # useTheme、useToast、useInterviewRoom、usePolling
├── constants/               # 简历版式元数据
├── stores/                  # app.ts：认证、当前用户、额度
├── types/index.ts           # API DTO 与 UI 领域类型
├── styles/                  # tokens.css、main.css
└── utils/                   # cn、format、resumeExport、resumeLayout
```

### 2.2 接入后端后的目标结构

```text
frontend/src/
├── app/                     # main.ts、App.vue、router/{index,routes,guards}、providers
├── pages/                   # 只做路由编排与布局
├── features/<域>/           # 页面组件、composable、schema、api
├── components/{ui,common,charts}
├── api/                     # client.ts、generated/、<controller>.ts
├── composables/             # useSseStream、usePagination、usePermission、useCountdown
├── stores/                  # auth、ui、interviewRoom（仅客户端状态）
├── types/                   # 按域拆分
└── styles/
```

**分层规则**：

- `pages` 只做路由级编排与布局，业务逻辑下沉到 `features`（目标态）。
- 当前 `components/<域>` 与扁平 `pages` 是演示版取舍；新增业务组件按域放入 `components/<域>`，接入后端后随 feature 迁移。
- `components/ui` 不允许 import `stores`/`features`/`pages`；只依赖 composable 与工具。
- `api` 层不允许 import 组件；只依赖类型与 `client.ts`（目标态）。
- 跨域复用先提升到 `composables` 或 `components/common`，禁止互相深层引用。

## 3. TypeScript 约束

### 3.1 编译配置

```json
{
  "compilerOptions": {
    "strict": true,
    "noUncheckedIndexedAccess": true,
    "noImplicitOverride": true,
    "exactOptionalPropertyTypes": true,
    "verbatimModuleSyntax": true,
    "types": ["vite/client"]
  }
}
```

### 3.2 类型规则

- 禁止 `any`；不确定用 `unknown`，再用 zod 解析。
- ID 一律 `string`（后端 int64 序列化为字符串）。
- 金额字段命名为 `...Cents`，类型 `number`；展示统一走 `formatCents`。
- 时间字段命名 `...At`，类型 `string`（ISO）；展示走 `formatDateTime`。
- 联合类型用可辨识联合，不用布尔组合：

```ts
type AsyncState<T> =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; error: AppError };
```

- API DTO 从 OpenAPI 生成，禁止手写同名类型；UI 模型单独定义并显式映射。
- 枚举用**字面量联合 + `Record` 映射**（等价于 `as const`，无运行时代码）；OpenAPI 生成的 DTO 除外。
- `tsconfig.app.json` 已开启本表全部严格项，包括 `exactOptionalPropertyTypes` 与 `verbatimModuleSyntax`；可选 prop 显式接受 `undefined`。

## 4. API 层

### 4.1 客户端封装

```ts
// api/client.ts
const BASE_URL = import.meta.env.VITE_API_BASE_URL;

let accessToken: string | null = null;
let refreshing: Promise<void> | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}

export function getAccessToken() {
  return accessToken;
}

export interface AppError {
  code: string;
  message: string;
  requestId?: string;
  details?: Record<string, unknown>;
}

export async function toAppError(res: Response): Promise<AppError> {
  try {
    const body = await res.json();
    return {
      code: body.code ?? 'INTERNAL_ERROR',
      message: body.message ?? '请求失败，请稍后重试',
      requestId: body.requestId,
      details: body.details,
    };
  } catch {
    return { code: 'INTERNAL_ERROR', message: `请求失败（${res.status}）` };
  }
}

export async function request<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const isFormData = init.body instanceof FormData;
  const res = await fetch(`${BASE_URL}${path}`, {
    ...init,
    credentials: 'include',
    headers: {
      ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...init.headers,
    },
  });

  if (res.status === 401 && retry && !path.startsWith('/auth/')) {
    await refreshOnce();
    return request<T>(path, init, false);
  }

  if (!res.ok) {
    throw await toAppError(res);
  }
  return res.status === 204 ? (undefined as T) : ((await res.json()) as T);
}

async function refreshOnce() {
  if (!refreshing) {
    refreshing = request<RefreshResponse>('/auth/refresh', { method: 'POST' })
      .then((r) => setAccessToken(r.accessToken))
      .finally(() => { refreshing = null; });
  }
  return refreshing;
}
```

**规则**：

- 所有请求必须走 `request`，禁止组件内直接 `fetch`。
- 401 刷新只允许一次，失败清空登录态并跳转；避免并发刷新风暴。
- 错误统一转为 `AppError { code, message, requestId, details }`，UI 只认 `code` 做分支，不匹配文案。
- 查询参数统一 `URLSearchParams`；不手工拼字符串。
- 上传使用 `FormData`，不要设置 `Content-Type`（浏览器自动带 boundary）。
- 计费/创建类请求必须带 `Idempotency-Key`（`crypto.randomUUID()`）。

### 4.2 TanStack Query 约定

| 数据 | queryKey | 失效时机 |
|---|---|---|
| 当前用户 | `['me']` | 登录/登出/资料更新 |
| 额度 | `['quota']` | 创建会话、上传、支付后 |
| 简历列表 | `['resumes', page]` | 上传/删除/重命名 |
| 会话详情 | `['interview', id]` | 开始/提交/结束 |
| 题目列表 | `['interview', id, 'questions']` | 出题完成、跳题 |
| 报告 | `['report', id]` | 生成完成、重试 |
| 管理端列表 | `['admin', resource, filters]` | 增删改后 |

- `staleTime`：静态配置 5 分钟；额度 30 秒；会话/报告 0。
- 轮询仅在明确状态（`PREPARING`、`GENERATING`）开启，完成后立即停止。
- 禁止在 `onSuccess` 里做路由跳转等副作用，统一在组件中处理。

## 5. 状态管理

### 5.1 Pinia 只放客户端状态

```ts
export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null);
  const accessToken = ref<string | null>(null);
  const isAuthenticated = computed(() => !!accessToken.value && !!user.value);

  async function login(payload: LoginPayload) {
    const res = await authApi.login(payload);
    accessToken.value = res.accessToken;
    setAccessToken(res.accessToken);
    user.value = res.user;
  }

  async function restore() {
    try {
      const res = await authApi.refresh();
      accessToken.value = res.accessToken;
      setAccessToken(res.accessToken);
      user.value = res.user;
    } catch {
      clear();
    }
  }

  function clear() {
    accessToken.value = null;
    user.value = null;
    setAccessToken(null);
  }

  return { user, accessToken, isAuthenticated, login, restore, clear };
});
```

- 禁止把简历、题目、报告等服务端实体放进 Pinia。
- 面试房间的“当前题目、草稿回答、计时”属于客户端状态，放 `interviewRoom` store。
- Store 不做 HTTP 细节；调用 `api/` 并处理结果。

> **当前实现**：`stores/app.ts` 只保存认证态、当前用户与额度，服务端实体由页面通过 `src/api/` 拉取；已删除 mock 账号表与整包 `localStorage` 持久化。

### 5.2 面试房间状态

```ts
interface InterviewRoomState {
  sessionId: string;
  questions: Question[];
  currentIndex: number;
  draftAnswers: Record<string, string>;
  remainingSec: number;
  streamStatus: 'idle' | 'connecting' | 'streaming' | 'done' | 'error';
  feedbackByQuestion: Record<string, Partial<Feedback>>;
}
```

- 草稿停笔 3 秒写 `localStorage`（key `oc_draft_<sessionId>_<questionId>`），刷新后恢复；切题、离开、卸载与 `beforeunload` 必须立即 flush；提交或跳过时清除。
- 离开页面前若存在未提交草稿，弹确认框。

## 6. SSE 封装（关键）

```ts
// composables/useSseStream.ts
import { onBeforeUnmount, ref } from 'vue';
import { BASE_URL, getAccessToken, toAppError, type AppError } from '@/api/client';

export interface SseHandlers {
  onMeta?: (data: MetaEvent) => void;
  onToken?: (data: TokenEvent) => void;
  onSection?: (data: SectionEvent) => void;
  onResult?: (data: ResultEvent) => void;
  onError?: (err: AppError) => void;
}

export function useSseStream() {
  const controller = ref<AbortController | null>(null);
  const status = ref<'idle' | 'connecting' | 'open' | 'closed'>('idle');

  async function start(url: string, answerId: string, token: string, handlers: SseHandlers) {
    stop();
    controller.value = new AbortController();
    status.value = 'connecting';

    const res = await fetch(
      `${BASE_URL}${url}?answerId=${encodeURIComponent(answerId)}&token=${encodeURIComponent(token)}`,
      {
        headers: { Authorization: `Bearer ${getAccessToken()}`, Accept: 'text/event-stream' },
        signal: controller.value.signal,
      },
    );

    if (!res.ok || !res.body) {
      status.value = 'closed';
      handlers.onError?.(await toAppError(res));
      return;
    }

    status.value = 'open';
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let event = 'message';
    let dataLines: string[] = [];

    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n');

      let idx: number;
      while ((idx = buffer.indexOf('\n')) >= 0) {
        const line = buffer.slice(0, idx);
        buffer = buffer.slice(idx + 1);

        if (line === '') {
          dispatch(event, dataLines.join('\n'), handlers);
          event = 'message';
          dataLines = [];
        } else if (line.startsWith('event:')) {
          event = line.slice(6).trim();
        } else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trimStart());
        }
      }
    }
    status.value = 'closed';
  }

  function dispatch(event: string, raw: string, handlers: SseHandlers) {
    if (!raw) return;
    let data: MetaEvent & TokenEvent & SectionEvent & ResultEvent & AppError;
    try {
      data = JSON.parse(raw);
    } catch {
      return;
    }
    switch (event) {
      case 'meta': handlers.onMeta?.(data); break;
      case 'token': handlers.onToken?.(data); break;
      case 'section': handlers.onSection?.(data); break;
      case 'result': handlers.onResult?.(data); break;
      case 'error': handlers.onError?.(data); break;
      case 'ping': break;
      default: break;
    }
  }

  function stop() {
    controller.value?.abort();
    controller.value = null;
    status.value = 'closed';
  }

  onBeforeUnmount(stop);
  return { start, stop, status };
}
```

**使用规则**：

- 组件卸载、路由离开、用户点击“停止”都必须 `stop()`。
- 收到 `result` 后仍要请求一次反馈详情，以服务端落库为准。
- 不自动重试扣费类请求；SSE 重连前重新申请 `streamToken`。
- 文本渲染节流 50ms；不要每个 Token 都触发一次响应式更新。
- `JSON.parse` 外包 try/catch，坏事件跳过并计数，不中断整条流。

> 评分 SSE 由 `src/api/stream.ts` 实现（fetch + ReadableStream），事件解析规则（`meta/token/section/result/error/ping`）按契约执行；断线最多重连 2 次，重连前重新申请 streamToken。

## 7. 路由与权限

### 7.1 路由表

| 路径 | 页面 | 权限 | 说明 |
|---|---|---|---|
| `/login` | 登录 | 公开 | 已登录跳首页 |
| `/register` | 注册 | 公开 | — |
| `/` | 工作台 | USER | 最近会话、额度、快捷入口 |
| `/resumes` | 简历列表 | USER | — |
| `/resumes/:id` | 简历详情 | USER | 结构化修正 |
| `/jds/:id` | JD 详情 | USER | 匹配分析 |
| `/interview/new` | 面试配置 | USER | 选择简历/JD |
| `/interview/:id` | 面试房间 | USER | 全屏布局、离开确认 |
| `/reports` | 报告列表 | USER | — |
| `/reports/:id` | 报告详情 | USER | 雷达图、逐题拆解 |
| `/plans` | 套餐 | USER | — |
| `/settings` | 设置 | USER | 隐私、账号 |
| `/admin/*` | 管理端 | ADMIN/SUPPORT | 独立布局 |

### 7.2 守卫

```ts
router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (to.meta.public) {
    return auth.isAuthenticated && to.name === 'login' ? { name: 'dashboard' } : true;
  }
  if (!auth.isAuthenticated) await auth.restore();
  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } };
  }
  const roles = to.meta.roles as string[] | undefined;
  if (roles && !roles.includes(auth.user!.role)) return { name: 'forbidden' };
  return true;
});
```

- 权限只用于隐藏入口，后端必须再次校验。
- 路由级 `defineAsyncComponent` 或动态 import；管理端单独 chunk。
- 404/403 页面独立，不暴露资源是否存在。

## 8. 表单规范

- 统一 `vee-validate` + `zod`；Schema 放在 `features/x/schema.ts`。
- 错误展示在字段下方，提交失败聚焦第一个错误字段。
- 提交按钮：`loading` 时禁用并显示进度；防重复点击。
- 文本域显示字数与上限；接近上限变色，超出禁止提交。
- 密码框支持显示/隐藏；注册实时提示强度但不阻塞输入。
- 危险操作（删除简历、注销账号）必须二次确认，输入关键词或密码。
- 自动保存草稿仅限面试回答；其他表单不做隐式提交。

## 9. 样式约束

- 只允许使用 Design Token（见 [ui-system.md](./ui-system.md)）：`bg-surface`、`text-muted`、`rounded-lg`、`shadow-card`。
- 禁止任意值：`text-[#123456]`、`mt-[13px]`。字号只允许 `text-3xs`(10/14) 与 `text-2xs`(11/16) 用于密集元数据和简历版式预览；产品正文 ≥ `text-xs`(12px)。
- 布局轨道与容器尺寸（`grid-cols-[...]`、`h-[calc(...)]`、`w-[min(...)]`）属于页面结构，可作为例外；不得用于间距、字号或颜色。
- 禁止内联 `style`，除非动态计算（如进度条宽度、环形尺寸、雷达容器高度）。
- 类名顺序由 Prettier + `prettier-plugin-tailwindcss` 自动排序；不要手工对抗。
- 响应式移动优先：`sm/md/lg/xl` 断点统一。面试间三栏使用 `room`（1400px）断点，其余页面不得新增自定义断点。
- 暗色模式通过 `.dark` 类切换；组件不得写死浅色背景。
- 动画只改变 `transform/opacity`；进度条宽度与 SVG 描边属绘制属性例外；尊重 `prefers-reduced-motion`。
- 页面根节点使用 `overflow-x: clip`；内部横向滚动容器（题号导航）使用 `contain-layout`，避免滚动宽度污染文档。

## 10. 性能预算

| 指标 | 预算 | 校验方式 |
|---|---|---|
| 首屏 JS（gzip） | ≤ 250 KB | `rollup-plugin-visualizer` |
| 单路由 chunk（gzip） | ≤ 120 KB | CI 体积门禁 |
| LCP（4G） | ≤ 2.5s | Lighthouse CI |
| INP | ≤ 200ms | 实验室 + RUM |
| CLS | ≤ 0.1 | Lighthouse CI |
| 字体 | 仅 2 个字重，`font-display: swap` | — |

**手段**：

- 路由懒加载；ECharts 按需引入（只注册 Radar/Line/Bar + 必要组件）。
- 图片 `loading="lazy"`、明确宽高；头像用固定尺寸。
- 列表超过 100 条使用虚拟滚动（`vue-virtual-scroller`）。
- 长文本 Markdown 渲染做缓存；流式期间用纯文本，结束后再渲染 Markdown。
- 预取：进入面试房间前预取题目与反馈；空闲时预取报告路由 chunk。

## 11. 可访问性

- 所有交互元素可键盘到达；焦点样式不可移除。
- 图标按钮必须有 `aria-label`；装饰图标 `aria-hidden="true"`。
- 对话框使用 Reka UI 的 focus trap；关闭后焦点回到触发元素。
- 颜色对比度 ≥ 4.5:1（正文）；分数颜色不能是唯一信息载体，需同时显示数字。
- 表单字段使用 `<label for>`；错误用 `aria-describedby` 关联。
- 流式反馈：可视区域正常渲染，另设 `aria-live="polite"` 的摘要区，流式期间每 2 秒更新已输出字数，结束态播报最终分数。
- 支持 200% 缩放不横向滚动；移动端点击区域 ≥ 44×44px（`sm` 按钮在 `max-sm` 抬到 `h-11`，Toast 关闭与底部导航同理）。
- 语义色作正文时必须用 `*-strong`；浅色语义色只用于图标、圆点与填充，不能单独承载信息。

## 12. 国际化

- 所有用户可见文案走 `vue-i18n`，禁止硬编码中文。
- Key 命名：`interview.question.progress`；按页面分组。

> **演示态例外**：当前为中文单语 mock 版，文案硬编码；接入真实账号前完成 `vue-i18n` 迁移，错误码映射与 key 规范仍按本节执行。
- 日期、数字、金额用 `Intl` / dayjs locale，不手工拼接。
- 后端错误码映射到前端文案，不直接展示后端 `message`（可作为兜底）。
- 文案长度预留英文 1.5 倍空间，避免按钮溢出。

## 13. 安全约束

- Access Token 只存内存；Refresh Token 由后端 `HttpOnly` Cookie 管理，前端不可读。
- 禁止把 Token、回答正文、简历内容写入 `localStorage`、URL、埋点。
- Markdown 渲染必须经 DOMPurify，白名单标签；禁止 `v-html` 直接渲染模型输出。
- 外链统一 `rel="noopener noreferrer"`，并校验协议为 `https`。
- 文件上传前端校验类型与大小，但以后端为准。
- 管理端不缓存敏感数据到 Service Worker；退出时清空 query cache。
- CSP 由网关下发；前端不引入 `unsafe-eval` 依赖。

> **本机偏好**：仅主题偏好（`oc_theme_pref`）、设备指纹（`oc_device_id`，随机 UUID）、简历版式（`oc_resume_layout_v1:{resumeId}`）与回答草稿（`oc_draft_<sessionId>_<questionId>`）保存在浏览器；不包含 Token、简历正文或回答正文以外的服务端实体。

## 14. 埋点与监控

| 事件 | 关键属性 |
|---|---|
| `interview_created` | questionCount, difficulty, planCode |
| `interview_started` | sessionId, readyLatencyMs |
| `answer_submitted` | questionId, inputMode, answerLength, durationMs |
| `feedback_first_token` | sessionId, answerId, ttftMs |
| `feedback_completed` | answerId, overallScore, latencyMs, degraded |
| `report_viewed` | reportId, readyLatencyMs |
| `quota_exceeded` | scene, planCode |
| `sse_reconnect` | answerId, attempt, reason |

- 埋点不包含简历/JD/回答原文与任何 PII。
- 采样：性能类 100%，业务类 100%，调试类 10%。
- 前端异常上报 Sentry：附 `requestId`、路由、浏览器版本；不附请求体。
- RUM 上报首字时间，与服务端 TTFT 对齐分析。

## 15. 测试要求

- 单元测试：`utils/format`、`utils/resumeExport`（Markdown/JSON/HTML 转义）、`api/client`（请求头、401 刷新去重、错误映射）共 19 例，`pnpm test:unit`。
- 组件测试：核心业务组件（AnswerEditor、StreamingFeedback、ScoreRing）覆盖交互与边界（目标态）。
- Composable 测试：`useSseStream` 用 mock ReadableStream 覆盖正常流、坏事件、中断、重连（接入后端后）。
- E2E 目标：登录 → 上传简历 → 粘贴 JD → 开始面试 → 答 1 题 → 看流式反馈 → 结束 → 报告（见 [testing.md](./testing.md)）。
- 当前 `frontend/scripts/visual-*.mjs` 是截图冒烟：跑真实 Chrome、记录控制台错误与几何断言，但不替代行为断言。
- 视觉回归：Storybook + Chromatic（目标态），至少覆盖明暗主题与移动端。
- 禁止用 snapshot 代替行为断言。

## 16. 质量门禁

```bash
pnpm typecheck      # vue-tsc --noEmit
pnpm lint           # eslint .（0 error / 0 warning）
pnpm format:check   # prettier --check
pnpm test:unit      # vitest run
pnpm build          # vue-tsc + vite build（体积预算）
pnpm visual         # 8 题全流程截图冒烟
pnpm visual:extra   # 注册/上传/JD/12 题/移动端/暗色
pnpm check          # typecheck + lint + test + build
```

- ESLint 规则：`@typescript-eslint/no-explicit-any: error`、`vue/no-v-html: error`、`vue/component-api-style: script-setup`、`vue/block-order: script→template→style`、`no-console: error`（`scripts/` 除外）。
- Prettier：单引号、无分号、2 空格、100 列；Tailwind 类名自动排序（`cn()` 视为类名函数）。
- 覆盖率目标：`features/` ≥ 60%，`composables/` ≥ 80%，`components/ui` 不强制；演示版当前只统计已建单测，未设覆盖率门槛。
- PR 必须通过 `pnpm check`；视觉脚本在主要 UI 变更时跑。

## 17. Do / Don't 清单

| Do | Don't |
|---|---|
| 页面拆分为容器 + 展示组件 | 500 行单文件组件 |
| 所有请求走 `api/` | 组件里直接 `fetch` |
| 错误按 `code` 分支 | 用 `message.includes('额度')` 判断 |
| SSE 卸载即停止 | 路由离开后流还在跑 |
| 金额用分、ID 用字符串 | 用 `number` 存 int64 ID |
| 每个异步视图有 loading/empty/error | 只做成功态 |
| 组件用 Token 样式 | 满屏任意值 hex |
| 表单 schema 校验 | 只靠后端报错 |
| 关键路径有 E2E | 只测工具函数 |
| 埋点脱敏 | 上报回答原文 |

## 18. 提交规范

- 分支：`feat/xxx`、`fix/xxx`、`chore/xxx`；禁止直接推 `main`。
- Commit：`feat(interview): 支持流式评分中断重连`。
- PR 描述包含：变更点、截图/录屏、测试方式、风险与回滚。
- UI 变更必须附明暗主题与移动端截图；涉及 SSE 必须附首字耗时录屏或日志。

## 19. 外部规范融合（阿里前端规范）

> 来源：阿里云开发者社区《前端开发规范》（2023-02-22）。只吸收与 Vue 3 + TS 严格模式兼容、能由工具守护的部分；冲突项与理由见 [评审记录](../reviews/frontend-spec-audit-2026-11-07.md)。

| 采纳 | 适配 | 不采纳 |
|---|---|---|
| 2 空格、JS 单引号、HTML 属性双引号（Prettier 固化） | 花括号：多行强制，单行 guard 允许 | SFC `template→script→style`（项目统一 `script` 优先） |
| 删除死代码；`src` 禁止 `console`/`debugger` | 注释：仅对外契约、副作用、非显然 why | 单单词原子组件改 `Base` 前缀 |
| props 类型与默认值、`v-for` key、组件样式 scoped | 手动 DOM 仅保留文件选择与聚焦 | `typeof x === 'undefined'`（改用 `=== undefined`/可选链） |
| 目录 kebab-case、组件按域组织 | 模板简单表达式：长链/嵌套三元抽 computed | HTML 块级首尾注释 |
| 路由懒加载；API 按 Controller 拆分（接入后端后） | 路由 `name` 用页面语义 kebab-case | `children` path 强制以 `/` 开头 |
| 复用现有错误码、额度、安全约束 | 逐方法注释改为契约级注释 |  |

新增规则必须先进入本表或正文，再由 ESLint/Prettier/测试守护；只写在评审记录里的规则不生效。
