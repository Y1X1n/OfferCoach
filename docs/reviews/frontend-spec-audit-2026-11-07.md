# 前端规范对照与融合评审（2026-11-07）

> 状态：留档 ｜ 范围：`frontend/src` 全量（10,252 行）+ [frontend.md](../dev/frontend.md)、[conventions.md](../dev/conventions.md)、[ui-system.md](../dev/ui-system.md)
> 外部规范：阿里云开发者社区《前端开发规范》（2023-02-22，https://developer.aliyun.com/article/1168574）
> 结论：P0/P1 已修复并通过门禁；P2 列入前端待办。规范取舍已回写正文，本文只作追溯记录。

## 1. 方法与口径

- 三路并行只读审查：命名/SFC、TS/CSS/可访问性/性能、路由/状态/质量门禁/文档漂移。
- 判定标准是「可执行、可验证、与 Vue 3 / TS 严格模式不冲突」，不以规则条数论优劣。
- 三类处置：**采纳**（直接落地）、**适配**（保留更优实践并写明理由）、**不采纳**（与框架或阶段目标冲突）。
- 既有 [frontend-review-2026-11-07.md](frontend-review-2026-11-07.md) 的结论仍有效；本次在其基础上补齐工具链与规范融合。

## 2. 已修复项

| 问题 | 证据 | 处置 | 验证 |
|---|---|---|---|
| 质量工具链缺失 | `package.json` 只有 dev/build/typecheck/visual；文档要求的 lint/test 不存在 | ESLint flat config + Prettier（含 Tailwind 排序）+ Vitest；新增 lint/format/test/check 脚本 | `pnpm lint`、`pnpm typecheck`、`pnpm test:unit`、`pnpm build` 全通过 |
| TS 严格项未开 | `exactOptionalPropertyTypes: false`、缺 `verbatimModuleSyntax` | 两项开启，修正 20+ 可选 prop 的显式 `undefined` 类型 | typecheck 0 error |
| 注册后无法再次登录 | `login` 只接受 demo 账号 | 新增 SHA-256 哈希的 mock 账号表；注册清空简历/JD/会话/报告 | 单测 2 例 + 浏览器注册流程 |
| 主题双 key、跟随系统失效 | `oc_theme` 与 `oc_theme_pref` 并存 | 合并为单 key，`system` 通过 `matchMedia` 实时跟随，旧 key 自动迁移 | 浏览器验证暗色默认、浅色持久化 |
| 移动抽屉无焦点管理 | 自绘遮罩，Tab 可逃逸、ESC 无效 | 改用 Reka `DialogRoot`，焦点陷阱/ESC/焦点归还由底层保证 | 视觉脚本 0 错误 |
| 流式反馈无过程播报 | 仅结束态 `sr-only` 摘要 | 流式期间 2 秒节流播报已输出字数 | 代码审查 |
| 题目状态仅颜色/图标 | 已完成/已跳过对读屏不可见 | 增加 `sr-only` 状态文本 | 代码审查 |
| 浅色语义色作正文 | `text-success`/`text-warning` 对比度约 2.1–2.7:1 | 文本统一 `*-strong`；`accent-strong` 色相修正为靛蓝 | 代码审查 |
| 触控目标过小 | 24–32px 的关闭/复制按钮 | `sm` 按钮移动端 44px；Toast 关闭 44px；底部导航 44px | 代码审查 |
| 任意字号 209 处 | `text-[10/11/12/13px]` | 新增 `text-3xs/2xs`、`tracking-label/badge/caption/hero`、`scale-98` Token 并批量替换；剩余仅布局轨道与容器尺寸 | 构建通过 |
| 三栏断点散落 | 18 处 `min-[1400px]:` 与 JS `1279` 互相矛盾 | Tailwind 注册 `room: 1400px`，统一 `room:`；1280 单列、1400 三栏 | 1280 单列 960px；1440 三栏 240/448/400 |
| 移动端横向溢出 | `documentElement.scrollWidth` 1184 @390 | 面试网格补 `grid-cols-1`；题号导航 `contain-layout`；`body overflow-x: clip` | 375/390 溢出 0 |
| 草稿刷新丢失 | 文档要求 3 秒落盘，代码无 | 停笔 3 秒写 `sessionStorage`，提交/跳过清除 | 代码审查 |
| 死代码 | `StatCard.vue`、`todayIso`、`ChevronLeft`、`ApiError`、空 `api/` | 删除 | 全仓 0 引用 |
| Alert 全量 `role=alert` | 静态说明首屏即播报 | 仅 `danger` 用 `alert`，其余 `status` | 代码审查 |
| Toast 无上限、错误消失快 | 3.6s 统一消失 | 最多 3 条；错误 6s | 代码审查 |
| `accent-strong` 色相错误 | 189° 青色与 243° 靛蓝混用 | 修正为 `243 75% 40%` | 视觉检查 |

## 3. 阿里规范的采纳 / 适配 / 不采纳

| 规则 | 决策 | 理由 |
|---|---|---|
| 2 空格缩进、JS 单引号、HTML 属性双引号 | 采纳 | 由 Prettier 固化，当前代码已符合 |
| JS 花括号 | 适配 | 多行分支强制；单行 guard（`if (!x) return`）保留，避免无意义行数膨胀 |
| `typeof x === 'undefined'` | 不采纳 | TS 严格模式下用 `x === undefined` / 可选链更安全，原规则针对 ES5 全局 `undefined` 可被改写 |
| HTML 块级元素首尾注释 | 不采纳 | Vue 组件结构由模板与组件边界表达；逐块注释成为噪声 |
| 删除无用代码 / 禁止 `console` | 采纳 | `src` 禁止 `console` 与 `debugger`，校验脚本除外 |
| 禁止手动操作 DOM | 适配 | 文件选择、聚焦使用模板 ref；确有必要的浏览器 API 单点保留 |
| 组件文件名 PascalCase | 采纳 | 与 Vue 官方风格指南一致；阿里原文举例自相矛盾 |
| 组件 name 至少两个单词 / Base 前缀 | 不采纳改名 | shadcn-vue 原子组件保留 `Button`/`Input` 等名称；显式 import 下与原生标签无冲突。新增通用组件使用域前缀 |
| SFC 顺序 template → script → style | 不采纳 | 项目统一 `script → template → style`（Vue 3 惯例），ESLint `vue/block-order` 强制一致，避免 58 文件纯格式改动 |
| props 必须类型、required/default | 采纳 | 全部 `defineProps` 已有 TS 类型 + `withDefaults`；对外契约补注释 |
| 组件样式必须 scoped | 采纳 | 仅 2 个 `<style>` 块，均 scoped；全局样式收敛在 `main.css` |
| 模板只写简单表达式 | 部分采纳 | 长链式 class/嵌套三元抽 computed；短表达式保留可读性 |
| 方法/状态必须逐条注释 | 适配 | 只对「对外契约、副作用、非显然 why、额度/评分/安全规则」写注释 |
| 路由 children path 以 `/` 开头 | 不采纳 | Vue Router 官方推荐相对路径，绝对路径会破坏父级组合 |
| 路由 name 与组件名一致 | 部分采纳 | name 用页面语义 kebab-case；不强制与文件名逐字一致 |
| API 一个 Controller 一个文件 | 接入后端时采纳 | 当前 mock-first 无 `api/` 层，目标结构已写入 frontend.md |
| 目录/文件 kebab-case | 采纳 | 目录与工具文件已合规；`.vue` 按 PascalCase |

## 4. 文档与代码漂移（已回写）

| 文档位置 | 原声明 | 处理 |
|---|---|---|
| frontend.md §1 | 列出 TanStack Query、vee-validate/zod、ECharts、dayjs、vue-i18n、Storybook | 标注为「接入后端目标态」，当前实际依赖单列 |
| frontend.md §2 | `app/features/api` 目标结构 | 改为「当前演示版结构 + 目标结构」双栏 |
| frontend.md §3.2 | `formatMoney` | 改为实际 `formatCents`；枚举接受「字面量联合 + Record」等价写法 |
| frontend.md §5.2 | 草稿每 3 秒写 sessionStorage | 已实现，更新为现状 |
| frontend.md §7 | `/reports`、`/admin/*`、403、角色守卫 | 标注演示版未实现，列入待办 |
| frontend.md §12 | 文案必须走 vue-i18n | 演示版中文单语例外，接入真实账号前迁移 |
| frontend.md §13 | 禁止简历/回答写入 localStorage | 增加 mock 演示例外与失效条件 |
| frontend.md §15 | Playwright 已覆盖全流程 | 更正为「截图冒烟」；单元测试已补 |
| frontend.md §16 | `npm run lint/test` | 改为 pnpm 脚本与真实门槛 |
| ui-system.md §2.2 | 字号表与 Tailwind 实际不一致 | 对齐为 12/13/14 与 2xs/3xs 例外 |
| ui-system.md §8 | xl=1280 三栏 | 面试间三栏改为 `room` 1400；xl 仍为通用桌面断点 |

## 5. 剩余待办（P2，不阻塞演示版）

1. ~~拆分 `stores/app.ts`：评分纯函数下沉 `domain/`~~（已完成，见 §7）。
2. 接入后端时新增 `api/`（一 Controller 一文件）+ TanStack Query，服务端实体迁出 Pinia。
3. 全量 i18n 迁移；先公共组件与错误码映射（演示版中文单语例外仍有效）。
4. ~~页面拆分：InterviewRoom / InterviewSetup / ResumeEditor~~（已完成，见 §7）；Settings 379 行仍可继续拆分。
5. 正式 E2E 断言与 Storybook/视觉回归；当前 visual 脚本只是冒烟。
6. 额度预扣与页面文案一致性；演示版当前不扣额度。
7. 管理端、403、角色守卫与详情路由。
8. ~~`Resume.confidence` 键类型~~（已完成）；`class` prop 清理与微型图标按钮 44px 仍有个别残留。
9. 合并旧 [frontend-review-2026-11-07.md](frontend-review-2026-11-07.md) 中仍有效的 P2 条目，避免重复。

## 6. 验证记录

| 命令 | 结果 |
|---|---|
| `pnpm typecheck` | 0 error（strict + `exactOptionalPropertyTypes` + `verbatimModuleSyntax`） |
| `pnpm lint` | 0 error / 0 warning（ESLint 10 flat） |
| `pnpm test:unit` | 2 文件 10 用例通过 |
| `pnpm build` | 通过；入口 78KB gzip，单路由最大 17.8KB |
| `pnpm visual` | 8 题全流程；700ms 流式 36 字；Dialog 焦点陷阱/还原通过；0 控制台错误 |
| `pnpm visual:extra` | 注册/上传/JD/12 题/暗色；移动端溢出 0；0 错误 |
| `pnpm visual:resume` | 4 套版式 + 编辑；移动端溢出 0 |
| `pnpm visual:dashboard` | 几何断言通过；移动端溢出 0 |
| 补充检查 | 1280 单列 960px；1400 三栏；375/390 无横向溢出；system 暗色默认、浅色持久化 |

未验证：真实后端、SSE 首字、真实上传解析（均未接入）。

## 7. 第二批：规范落地重构（同日）

| 项 | 前 | 后 | 说明 |
|---|---|---|---|
| InterviewRoomPage | 831 行 | 298 行 | 新增 `useInterviewRoom` 与 RoomHeader/QuestionPanel/AnswerComposer；计时、流式、草稿、提交编排下沉 |
| InterviewSetupPage | 718 行 | 158 行 | 新增 setup/ResumePicker、JdPicker、InterviewConfigPanel、SetupSummary；radiogroup 语义保留 |
| ResumeEditor | 529 行 | 223 行 | 六分区拆到 `resume/editor/`；校验与保存仍在父组件 |
| stores/app.ts | 698 行 | 413 行 | 评分/报告聚合抽到 `domain/scoring.ts`；持久化与 mock 账号拆到 stores 子模块 |
| `Resume.confidence` | `Record<string, number>` | `Partial<Record<ResumeSectionKey, number>>` | 读写点补 `?? 0` |
| 设置开关热区 | 44×24 | 64×44 | 轨道视觉不变，伪元素式绝对热区 |
| 报告筛选/清除 | 28–32px | 移动端 44px | 桌面尺寸不变；“查看详情”移动端常显 |
| 路由平滑滚动 | 始终 smooth | reduced-motion 时 auto | — |

验证：`pnpm typecheck`、`pnpm lint`（0/0）、22 个单测、`pnpm build`、四组 visual 脚本全部通过；补充浏览器断言：草稿 3 秒落盘并在刷新后恢复 34 字、开关热区 64×44、筛选 chip 44px、移动端“查看详情”可见、390px 溢出 0。

未变：mock localStorage、文案、评分公式、演示数据；`window.confirm` 离开面试间仍保留，后续可换 Dialog。新增 `useInterviewRoom` 450 行，属于逻辑模块而非展示组件。