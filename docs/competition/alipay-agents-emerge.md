# 支付宝「智能体涌现奖」参赛改造说明

> 状态：进行中 ｜ 赛季：第一赛季（提交截止 2026-12-06 23:59:59，北京时间）
> 活动页：https://www.qianwenai.com/arena/competitions/alipay-agents-emerge
> 关联：[交付清单](#4-交付清单对照)、[小程序运行说明](./miniprogram.md)、[检测入口说明](./agent-entry.md)

## 1. 活动硬性要求（摘自官方「详情」「任务说明」）

| 维度 | 要求 |
|---|---|
| 作品载体 | **支付宝小程序**，使用千问 AI 平台的模型开发；提供作品访问方式、完整源码、独立功能检测入口 |
| 创作方向 | 「生活好帮手」「消费新体验」为参考方向，不限制题材；须围绕真实生活需求或经营问题 |
| 交付总包 | `product.zip`（≤ 200 MB），根目录固定含 `agent.zip`、`link.md`、`description.pdf`、`demo.mp4`、`cover.jpeg/jpg/png`（16:9），两层都不额外套目录 |
| 代码包 | `agent.zip`（≤ 100 MB）：完整支付宝小程序工程 + 根目录 `agent.json` + 唯一检测主入口 `agent.py` 或 `agent.js` + 依赖声明 + 随包依赖 |
| 检测入口 | Linux Debian 12 / Python 3.12 或 Node.js 22；`--prompt "..."` 接收自然语言指令，`--version` 输出版本号退出码 0；正常完成退出码 0；单次 ≤ 30 分钟、内存 ≤ 4 GB |
| 模型调用 | 只能用 `DASHSCOPE_API_KEY` / `DASHSCOPE_BASE_URL`（以 `/api/v1` 结尾）/ `OPENAI_BASE_URL`（以 `/v1` 结尾）环境变量；仅 Chat Completions 兼容接口，不支持 Responses API、内置工具、MCP、工作流、知识库、Embedding、本地文件上传 |
| 网络 | 仅可访问千问模型服务，其它外网不可达；样例数据与素材必须随包提供 |
| 日志 | 写入 `$AGENT_LOG_DIR/agent.log` |
| 评审维度 | 用户价值、产品体验、AI 必要性、执行能力、生态适配性、责任可信 |
| 附加前提 | 获奖作品须在**支付宝开放平台**完成提交 |

## 2. 本项目的参赛形态

把现有「AI 模拟面试与简历诊断平台」收束成支付宝小程序 **OfferCoach 面试教练**：

```text
支付宝小程序（miniprogram/）
   │  my.request  →  https://<你的域名>/api/v1
   ▼
oc-app（现有 Spring Boot 模块化单体，零改动）
   ├─ 简历文本 → 结构化解析（AI）
   ├─ 目标岗位 JD → 技能抽取与匹配（AI）
   ├─ 按岗位 + 简历出题、逐题评分、复盘报告（AI）
   └─ 额度 / 会话状态机 / 报告 Worker
```

为什么满足「AI 必要性」：出题依据是简历与 JD 的**语义匹配**，评分要给**证据引用与改写建议**，模板无法覆盖；无 AI 时系统按既有设计降级为模板题与启发式评分，并在界面上显式标注。

为什么满足「责任可信」：不涉及资金与真实交易；简历只做结构化后展示，支持删除；评分明确标注为「练习参考，不代表真实录用结果」；不给出医疗、法律、投资建议。

### 2.1 与活动要求的对应关系

| 活动要求 | 本项目落点 |
|---|---|
| 支付宝小程序 | `miniprogram/`（原生 axml/acss/js，12 个页面，覆盖除管理端外的全部用户功能） |
| 千问模型 | 小程序不直连模型，由 `oc-app` 通过 OpenAI 兼容接口调用；检测入口 `agent.py` 直连 `OPENAI_BASE_URL` |
| 题库来源 | 出题前按「岗位标题 + 技能」联网检索公开面经线索 → 模型生成 → 题库/模板兜底；生成题按岗位族沉淀为草稿，行业通用 |
| 完整源码 | `agent.zip/miniprogram/` 含小程序工程、`oc-*` 后端源码与构建配置 |
| 独立检测入口 | `agent/agent.py`（纯标准库，无第三方依赖） |
| 可访问链接 | `link.md` 写明小程序体验版路径与体验账号 |
| 演示视频 | `demo.mp4`，覆盖「粘贴简历 → 选岗位 → 答题 → 评分 → 报告」 |

## 3. 改造清单（代码）

| 变更 | 位置 | 说明 |
|---|---|---|
| 新增小程序工程 | `miniprogram/` | 12 个页面：工作台、简历列表/详情、岗位列表/新增/详情、面试配置、答题、报告列表/详情、我的、套餐支付；`utils/api.js` 统一封装请求、上传、语音与支付 |
| 新增检测入口 | `agent/` | `agent.py` + `agent.json` + `requirements.txt` + `samples/` |
| 新增打包与自检 | `scripts/competition/build-product.py`、`scripts/competition/check-package.py` | 产出并校验 `product.zip` |
| 文档 | `docs/competition/**` | 本文件、小程序运行说明、检测入口说明 |
| 后端支付 | `oc-pay` + `oc-identity` | 新增 `payMode=MINI` 与 `POST /orders/{orderNo}/miniapp-pay`：授权码换 `user_id`、`alipay.trade.create` 建单，返回 `tradeNo` 给 `my.tradePay`；其余接口零改动 |

## 4. 交付清单对照

| 交付项 | 固定名 | 状态 | 生成方式 |
|---|---|---|---|
| 代码包 | `agent.zip` | 已生成 | `scripts/competition/build-product.py` 组装 |
| 作品链接 | `link.md` | 已生成 | `dist/competition/link.md`，含体验版路径与操作步骤 |
| 作品说明 | `description.pdf` | 已生成 | `scripts/competition/build-assets.js` 由 HTML 渲染 |
| 演示视频 | `demo.mp4` | 已生成 | 预览运行时 + Playwright 录制，见 [miniprogram.md](./miniprogram.md) |
| 封面 | `cover.png` | 已生成 | `scripts/competition/assets/cover.html` 渲染 1440×810 |
| 总包 | `product.zip` | 已生成 | `scripts/competition/build-product.py`，`check-package.py` 校验通过 |

## 5. 需要人工完成的事项

1. 千问 AI 平台**实名认证 + 报名**（活动页「立即报名」）。
2. 支付宝开放平台创建小程序，拿到 `APPID` 并填入 `miniprogram/mini.project.json`；同时把该 `APPID` 与私钥配置到 `oc-pay`（`ALIPAY_APP_ID` / `ALIPAY_PRIVATE_KEY_*`），否则套餐页只显示「渠道未开通」。
3. 在小程序后台把后端域名加入**服务器域名白名单**（HTTPS + 备案）。
4. 用支付宝开发者工具上传 → 生成体验版 → 按 `link.md` 描述添加体验成员。
5. 获奖后须在**支付宝开放平台**完成作品提交。
