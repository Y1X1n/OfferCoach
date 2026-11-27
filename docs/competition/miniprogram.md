# 支付宝小程序运行说明

> 状态：生效 ｜ 更新：2026-11-27
> 关联：[参赛改造说明](./alipay-agents-emerge.md)、[检测入口](./agent-entry.md)

## 1. 目录与页面

```text
miniprogram/
├── app.json / app.js / app.acss / mini.project.json
├── utils/
│   ├── config.js      唯一配置入口（后端地址、体验码）
│   ├── api.js         请求封装、游客会话、上传、录音支付、轮询
│   └── format.js      展示映射、Markdown 导出、示例素材
└── pages/
    ├── index/         工作台：额度、进行中会话、最近报告、快捷入口
    ├── resume/list    简历列表：文件上传 / 拍照 / 粘贴文本
    ├── resume/detail  简历详情：结构化内容、重命名、设为默认、重解析、导出、删除
    ├── jd/list        岗位列表
    ├── jd/create      新增岗位：粘贴文本 / 链接抓取 / 搜索岗位
    ├── jd/detail      岗位详情：必备技能、JD 原文、简历匹配分析
    ├── setup/         面试配置：选简历与岗位、题量/难度/风格
    ├── interview/     答题：语音作答、题目朗读、追问、暂停/恢复/跳过/放弃、草稿
    ├── reports/       报告列表：分数趋势 + 历史报告
    ├── report/        报告详情：五维、逐题证据、改进计划、复制 Markdown
    ├── me/            我的：昵称、额度、邀请码、数据导出、退出、注销
    └── plans/         套餐与额度：支付宝小程序内支付
```

覆盖范围与 Web 端一致（管理端除外）：简历上传/解析/修正/重解析/删除/导出、JD 文本与联网获取、匹配分析、会话全生命周期、语音作答与朗读、追问、报告与趋势、额度套餐与支付、账号与隐私。

页面只通过 `utils/api.js` 访问后端，不持有任何模型密钥；鉴权、额度、降级判断全部在服务端。

## 2. 部署前必改的两个配置

`miniprogram/utils/config.js`：

| 变量 | 说明 |
|---|---|
| `API_BASE` | `oc-app` 的公网地址，必须以 `/api/v1` 结尾，例如 `https://api.example.com/api/v1` |
| `ACCESS_CODE` | 与服务端注册码一致：服务端开 `DEV_VERIFY_CODE` 时填该固定验证码；开 `REGISTER_INVITE_CODE` 时填邀请码；两者都关时留空（自动走算术验证码） |

`miniprogram/mini.project.json` 之外，`APPID` 在支付宝开发者工具中关联，不写入仓库。

后端侧要求：

1. 域名已备案且为 HTTPS（小程序不允许 IP / 非 HTTPS）；
2. 支付宝开放平台 → 小程序 → 开发设置 → 服务器域名，把 `API_BASE` 的域名加入白名单；
3. 注册开关：`DEV_VERIFY_CODE_ENABLED=true` + `DEV_VERIFY_CODE=<与 ACCESS_CODE 相同>`，
   或 `REGISTER_INVITE_ENABLED=true` + `REGISTER_INVITE_CODE=<与 ACCESS_CODE 相同>`；
   生产环境用邀请码，不要开启固定验证码。

## 3. 本地预览（无支付宝开发者工具时）

预览运行时直接加载真实的 `axml / acss / js`，把 `my.*` 映射到浏览器，并把 `/api` 代理到本地 `oc-app`：

```bash
# 1) 后端（Docker 或本地 jar 均可），默认 127.0.0.1:8081
docker compose up -d

# 2) 预览服务
node scripts/competition/preview/server.js
# 打开 http://127.0.0.1:5180/preview/?page=pages/index/index&code=123456
```

- `code` 参数会覆盖 `ACCESS_CODE`，仅用于本地联调，不写进仓库配置；
- `OC_API_TARGET` 可指定后端地址，`OC_PREVIEW_PORT` 可改端口；
- 预览运行时会去掉 `Origin` 头，绕过后端 CORS 白名单（仅本地预览生效）。

这不是官方运行时：`my.getAuthCode`、支付、真实登录等原生能力不在预览范围内。

## 4. 上传、体验版与发布

1. 支付宝开发者工具 → 导入 `miniprogram/` 目录 → 填写 APPID；
2. 详情 → 服务器域名，确认第 2 节的域名已登记；
3. 上传版本 → 在开放平台把该版本设为**体验版** → 生成体验码；
4. 成员管理 → 体验成员，加入评审账号（体验版默认只有成员可访问）；
5. 正式发布需提交审核，满足平台对内容、类目与域名备案的要求。

## 5. 交互与接口约定

| 环节 | 接口 | 说明 |
|---|---|---|
| 游客会话 | `POST /auth/register` 或 `/auth/login` | 设备指纹 + 体验码自动注册；Token 过期后静默重登 |
| 简历 | `POST /resumes/manual` | 文本直传，轮询 `GET /resumes/{id}` 直到 `READY` |
| 岗位 | `POST /jds` | 文本直传，同步返回结构化结果 |
| 会话 | `POST /interviews` → `/start` | 轮询 `GET /interviews/{id}` 直到 `READY` |
| 题目 | `GET /interviews/{id}/questions` | **逐题解锁**：只返回已答/已跳过 + 当前题 |
| 作答 | `POST /interviews/{id}/answers` | 返回 `answerId`，再取 `/feedback` |
| 报告 | `POST /interviews/{id}/finish` → `GET /reports/{id}` | 报告异步生成，页面轮询直到 `READY`/`FAILED` |
| 语音作答 | `POST /interviews/{id}/answers/audio` | 录音 16kHz WAV，≤120s；失败回退文字输入 |
| 朗读 | `GET /interviews/{id}/questions/{qid}/speech` | 需鉴权，先 `my.downloadFile` 再播放缓存文件 |
| 追问 | `POST /interviews/{id}/questions/{qid}/follow-up` | 每题需已作答，整场最多 2 次 |
| 支付 | `POST /orders`（`payMode=MINI`）→ `POST /orders/{orderNo}/miniapp-pay` | 换 `tradeNo` 后 `my.tradePay`，状态以回调为准 |

## 6. 已知限制

- **语音与朗读**：代码已接入 `my.getRecorderManager` 与 `my.createInnerAudioContext`，但录音格式、麦克风权限与播放链路必须在支付宝客户端真机验证；预览运行时不伪造录音，会提示改用文字。
- **通知中心**：`docs/design/api.md` §10 设计过通知接口，但后端尚未实现，小程序侧未放入口，避免出现空页面。
- **体验版**需要加入体验成员；正式发布需通过平台审核。
- 后端未配置模型 Key 时，出题走题库/模板、评分走启发式规则，界面会显式标注降级；ASR/TTS/搜索/抓取同理不可用。
- 支付需要 oc-pay 配置 `ALIPAY_APP_ID` 与应用私钥，且该 appId 与小程序一致，否则只能看到「渠道未开通」。
