# 前端独立演示版复查（2026-11-07）

> 状态：留档 ｜ 类型：一次性复查 ｜ 结论已回写 `frontend/` 与相关文档
> 范围：`frontend/` 全部页面、共享组件、Mock 状态与评分逻辑；两名只读审查子代理（逻辑 / UI）独立检查。
> 说明：本文件只记录本次发现与处置，**不作为长期执行依据**。

## 1. 复查方法

1. 逻辑审查：逐文件检查 `stores/app.ts`、`mocks/*`、`router`、面试间/报告/简历/JD 页面，重点验证评分、状态机、持久化与边界。
2. UI 审查：对照 [docs/dev/ui-system.md](../dev/ui-system.md) 与 `artifacts/*.png`，检查对比度、焦点、响应式、文案与触控。
3. 真实浏览器：Playwright + 本机 Chrome，覆盖登录、8 题完整面试、报告、注册、上传、JD 分析、12 题配置、移动端、暗色、Dialog 焦点。
4. 修复后重跑 `pnpm typecheck`、`pnpm build`、`pnpm visual`、`pnpm visual:extra`，控制台 0 错误。

## 2. P1 修复

| # | 问题 | 处置 | 位置 |
|---|---|---|---|
| 1 | 状态色文字对比度不足（success/warning/accent/danger） | 新增 `*-strong` 文本 token 与 `danger-solid` 按钮底色；Badge/Alert/Toast/ScoreRing/错误文案统一使用 | `styles/tokens.css`、`tailwind.config.ts`、`components/ui/*` |
| 2 | Dialog 无焦点陷阱、关闭不还原焦点、description 未关联 | Tab 在弹层内循环；记录并还原触发元素；补 `aria-describedby`；ESC 关闭 | `components/ui/Dialog.vue` |
| 3 | 流式反馈环形图显示 85，正文写 8.5/10 | `ScoreRing` 增加 `max`，按 10 分制显示并复用 `scoreTone`；阈值统一为 8.5/7.0 | `components/charts/ScoreRing.vue`、`components/interview/StreamingFeedback.vue` |
| 4 | 用户界面暴露 M1/V1.1/开发模式/产品文档/接入后端 | 全部替换为用户能力说明或“即将开放”；PDF 改为可用的“打印 / 存为 PDF” | 登录/注册/套餐/设置/报告/面试配置 |
| 5 | 不可用的 PDF 按钮常驻且键盘不可达 | 删除 disabled 按钮，改为真实 `window.print()` | `pages/ReportDetailPage.vue` |
| 6 | 跳过/未答未按 0 分计入报告，与文案不符 | 所有题目等权计入；未答生成 0 分反馈；报告拆解包含全部题目；风险项提示未答数量 | `stores/app.ts` `finishInterview` |
| 7 | 种子报告均分/雷达与拆解不一致 | `report_1` 改为 77.0、`report_2` 改为 82.7，雷达按拆解维度均值重算 | `mocks/data.ts` |

## 3. P2 修复

- Toast 在移动端上移，避免压住固定底栏。
- 面试间三栏断点从 1280px 调到 1400px，避免 1280–1399 中栏过窄；低于断点单列堆叠。
- 移除 UploadDropzone 内嵌 Button，避免嵌套交互。
- 题目截断增加 `title`；JD 原文展开按钮补 `aria-expanded`；Input/Textarea 的 hint 加入 `aria-describedby`。
- 删除简历/JD 时检查**所有**进行中会话，不只最新一场。
- 新注册用户清空会话/报告；演示账号登录时若无数据则恢复种子，避免跨账号历史串数据。
- 套餐页 PRO 用户看到 FREE 卡时显示“降级暂未开放”并禁用。
- `localStorage` 写入增加 try/catch，读取增加结构校验，避免隐私模式/坏数据崩溃。

## 4. 剩余 P2（未关闭）

1. 移动端抽屉无 `role="dialog"`、ESC 与焦点管理；375px 顶栏控件较多，标题会被压缩。
2. 列表行内图标按钮 32px、Toast 关闭按钮 14px，低于 44px 触控目标。
3. 全站仍有 10px/11px caption 字号，低于规范的 12px 下限。
4. 面试回答草稿未做 3 秒自动保存；刷新会丢失未提交草稿。
5. Mock 将回答/会话持久化到 `localStorage`，仅适用于演示；接入真实后端时必须移除，避免 PII 落本地。
6. 出题未真正使用难度/面试官风格/JD 缺口，仅从固定 12 题题库切片；接后端后由 RAG 出题替代。
7. 尚无 Store/评分逻辑单元测试；当前依靠 Playwright 全流程与类型检查。

## 5. 结论

- 前端已具备**独立演示**能力，核心链路完整、响应式与暗色可用、关键可访问性问题已修。
- 后端/AI 暂缓期间，前端可继续用 mock 迭代视觉与交互；接入后端时优先替换 `stores/app.ts` 的 action 与 `mocks/api.ts` 的流式实现。
- 剩余 P2 在恢复后端前逐项清理，不阻塞当前演示。