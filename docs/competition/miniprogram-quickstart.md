# OfferCoach 面试教练 · 支付宝小程序

AI 模拟面试小程序：粘贴简历与目标岗位 → 按经历出题 → 逐题评分给证据 → 复盘报告。

这是从主仓库 `miniprogram/` 拆出的**独立可编译工程**，可直接用支付宝小程序开发者工具导入。

## 一、导入与运行

1. 安装「支付宝小程序开发者工具」（Windows / macOS）。
2. 打开工具 → 导入项目 → 选择本目录。
3. AppID：`2021007126674072`（已写入 `mini.project.json`，通常自动带出）。
4. **配置已就绪**：`API_BASE` 已指向 `https://offercoach.0803.xin/api/v1`，`ACCESS_CODE` 留空（服务端走算术验证码）。
5. 首次运行前必须把域名加入白名单，见下方「二、上线前必做」。
   本地临时联调可勾选「详情 → 本地设置 → 不校验合法域名」。

## 二、上线前必做

| 项 | 位置 | 说明 |
|---|---|---|
| 服务器域名白名单 | open.alipay.com → 控制台 → 选中该小程序 → 开发设置 → 服务器域名 → `httpRequest 域名` 添加 `https://offercoach.0803.xin` | 不加白名单真机/IDE 会报 `request:fail`；必须 HTTPS + 域名已备案 |
| 体验成员 | 开放平台 → 成员管理 | 体验版默认只有成员能进 |
| 后端部署 | 主仓库 `compose.yaml` | 小程序不含任何 AI 逻辑，必须连后端 |
| 支付（可选） | 后端 `ALIPAY_*` 配置 | APPID 需与小程序一致，否则套餐页显示「渠道未开通」 |

## 三、目录结构

```text
.
├── app.json          12 个页面注册
├── app.js            启动与游客会话
├── app.acss          全局样式
├── mini.project.json 项目配置（含 APPID）
├── pages/
│   ├── index/        工作台 + 一键体验
│   ├── resume/       简历列表 / 详情
│   ├── jd/           岗位列表 / 新增（文本·链接·搜索）/ 详情
│   ├── setup/        面试配置
│   ├── interview/    答题（语音·朗读·追问·暂停·跳过·放弃·草稿）
│   ├── reports/      报告列表 + 分数趋势
│   ├── report/       报告详情
│   ├── me/           资料 / 额度 / 邀请 / 导出 / 注销
│   └── plans/        套餐与小程序内支付
└── utils/
    ├── config.js     唯一配置入口
    ├── api.js        请求 / 上传 / 录音 / 支付 / 轮询
    ├── session.js    创建会话编排（配置页与一键体验共用）
    └── format.js     展示映射与示例素材
```

## 四、零外部依赖

- 无 `node_modules`、无 npm 包、无构建步骤；
- 所有 `require` 均为相对路径；
- 不引用主仓库任何文件，可独立 `git init`。

## 五、已知限制

- 语音作答与题目朗读依赖 `my.getRecorderManager` / `my.createInnerAudioContext`，**必须在支付宝客户端真机验证**；
- 后端未配置模型 Key 时，出题走题库/模板、评分走启发式规则，界面会标注降级；
- 通知中心后端未实现，未放入口。

## 六、与主仓库同步

本目录是主仓库 `miniprogram/` 的副本。改完请同步回去：

```bash
# 在主仓库根目录
cp -r E:/zcodework/offercoach-miniprogram/. miniprogram/
```
