# AI 工具能力 PRD：语音作答（ASR）/ 题目朗读（TTS）/ 简历识别（OCR）

> 状态：评审中 ｜ 版本：v3.0 ｜ 更新：2026-11-14
> 关联：[requirements.md](../requirements.md)（R-11 / R-38 / R-39 / R-89）｜ [api.md](./api.md) §9 ｜ [architecture.md](./architecture.md) ADR-011 ｜ [backend.md](../dev/backend.md)
> 定位：本文是三项语音 / 图像能力的**产品口径、服务边界与验收标准**。对外接口字段在评审通过后**统一回写 [api.md](./api.md)**（单一来源，不在两处重复）。
> 变更记录：v2.0 改为三个独立可部署服务；**v3.0 收敛为一个 AI 聚合模块 `oc-ai`，其内 ASR / TTS / OCR 为三个独立服务**（避免顶层模块膨胀）。

## 1. 背景与目标

DeepSeek 于 2026-11-13 开放「全家桶应用 API」：在既有 OpenAI 兼容 `/chat/completions` 之外，新增可**独立调用**的工具接口 `POST https://api.deepseek.com/v1/tools/{search,fetch,ocr,asr,tts}`，与对话接口同源、同 Key。本项目已接入对话接口（`oc-ai-client`），但语音 / 图像能力一直停留在「契约预留、未实现」：

- `interview_answer` 已预留 `input_mode='VOICE'`、`audio_key`、`audio_duration_ms`、`asr_status`、`asr_confidence`（迁移 `V202611070004`）。
- [api.md](./api.md) §9 已冻结语音作答契约，但后端 `AnswerService` 仍拒绝非 `TEXT` 作答。
- R-11「扫描件 OCR 降级」目前只降级为「引导手动粘贴」，未真实识别。

本期把 R-38（语音作答）、R-11（扫描件 OCR）从「规划 / 降级」落地为**真实调用 DeepSeek 工具接口**，并新增 R-39（题目朗读），三项能力分别由**三个独立微服务**承载。

### 目标

1. 模拟面试中可用语音作答：录音 → ASR 转写 → 文本可编辑 → 提交，评分链路不变。
2. 模拟考官可朗读题目（TTS）：音色可选、音频可缓存、可停止。
3. 上传图片 / 扫描版简历时用 OCR 识别文本，进入既有「结构化 → 编辑器改内容 → 按版式模板导出」链路。
4. 三项能力统一鉴权、统一降级、统一计量与审计；**无 Key 或上游失败时不阻断主流程**（回退文本）。
5. 三个服务**独立构建、独立部署、独立扩容、独立故障隔离**：任一服务不可用时主流程不受影响。

### 非目标（本期不做）

- 不做实时语音对话（边说边识别）/ 双工通话。
- 不做视频、表情、口音评分；ASR 只产出文本，不做语气 / 流畅度评分维度。
- 不承诺 OCR 版面像素级还原（只做逐页文本 → 结构化）。
- 本次不接入 Search / Fetch（另立需求）。

## 2. 术语与关键澄清

| 术语 | 含义 |
|---|---|
| DeepSeek Tools API | `POST https://api.deepseek.com/v1/tools/{asr,tts,ocr}`；Base URL 与对话接口同源，复用同一 API Key |
| 三个微服务 | `oc-ai` 聚合模块内的三个独立服务：`asr`(8094) / `tts`(8095) / `ocr`(8096)，各自独立 Jar / Dockerfile / 部署单元；**仅内网可达，不暴露公网**。顶层模块只增加 `oc-ai` 一个 |
| AI 聚合模块 | Maven 父模块 `oc-ai`，含共享库 `oc-ai-core` 与三个服务子模块；对外是一个 AI 域，对内是三个独立进程 |
| 边缘业务服务 | 既有 `oc-app`（8081）。它是浏览器唯一入口，负责鉴权、会话校验、额度、审计，再调用工具微服务 |
| 简历「模板」 | 前端**版式预设**（`classic/modern/compact/minimal`，见 `frontend/src/constants/resumeLayouts.ts`），由 `resume.structured_json` 渲染；**不是**后端预填内容的模板 |
| 「在模板内部改动内容」 | OCR / 解析出的结构化字段在**简历编辑器**中修改，并按所选版式实时预览与导出（Markdown / JSON / 打印 PDF） |

## 3. 能力定义（对齐 DeepSeek 官方接口）

### 3.1 ASR 语音作答（R-38）

- 接口：`POST /v1/tools/asr`，`multipart/form-data`，字段 `file`。
- 输入约束：**16-bit PCM WAV 或 Ogg Opus**（按文件内容判断）；WAV 采样率 8k–48k；单 / 双声道（**只识别左声道**）；**≤ 8 MiB**；**≤ 120 秒**；上传需 10 分钟内完成；**不支持流式**。
- 输出：`{ request_id, text, duration_ms }`；语种自动识别（含中文、粤语）。
- 计费：按音频时长，¥0.78/小时，限时 5 折 **¥0.39/小时**（精确到秒，不足 1 秒按 1 秒）。
- 错误：`400`(格式 / 缺字段) `408`(上传超时) `413`(>8 MiB) `429`(并发超限) `503`/`504`(繁忙 / 超时)。

**关键技术风险（必须解决）**：浏览器 `MediaRecorder` 默认产出 **WebM 容器封装 Opus**，而 DeepSeek ASR 只接受 **16-bit PCM WAV 或 Ogg Opus**，二者不通用。本方案**不使用 `MediaRecorder` 默认产物**，而是用 `AudioWorklet`（不可用则回退 `ScriptProcessor`）采集原始 PCM，在前端封装为 **16 kHz / 单声道 / 16-bit WAV** 后上传：浏览器内完成、零服务端转码依赖。录音时长客户端硬上限 120s，超限自动停止并提交。

### 3.2 TTS 题目朗读（R-39，新增）

- 接口：`POST /v1/tools/tts`，`application/json`。
- 入参：`text`(必填，≤10,000 计费字符，请求体 ≤64 KiB)、`voice_id`(`stella` 默认女声 / `tide` 男声)、`format`(`wav` 默认 / `pcm`)、`filter_markdown`(默认 `true`)。
- 输出：**流式** `audio/wav`（或 24 kHz / 16-bit / 单声道 PCM），单次 ≤30 分钟音频。
- 计费：¥3/万字符，限时 5 折 **¥1.5/万字符**（汉字算 2 字符）。
- 错误：`400`(无可朗读文本 / 内容审核不过 `Content Exists Risk`) `429`/`503`/`504`；**已开始返回后失败视为失败**（响应中途断开）。
- 交互：题目卡片旁「朗读 / 停止」按钮；切换题目或离开页面自动停止；管理端可配默认音色。

### 3.3 OCR 简历识别（R-11 落地）

- 接口：`POST /v1/tools/ocr`，`multipart/form-data`，字段 `file`（必填，带文件名）+ `purpose`（必填）。
  - `purpose=text_recognition`：无图 Word/PPT 直接抽取文字；其他**逐页识别**。
  - `purpose=rich_layout_recognition`：全部逐页识别。
- 输入约束：PDF / Word(DOC/DOCX) / PowerPoint(PPT/PPTX) / 图片(PNG/JPEG/WebP/GIF/BMP/TIFF)；**≤100 MiB**；**≤1000 页**；上传 ≤120s，总时长 ≤180s；每账号并发 15。
- 输出：`{ id, object:"ocr.result", page_count, pages:[{index,text,finish_reason}], usage }`；逐页文本**保留坐标标记** `<｜ref｜>…<｜/ref｜><｜box｜>[[x1,y1,x2,y2]]<｜/box｜>`（0–1000 归一化坐标，原点左上）。
- 计费：¥1.5/千页，按成功页数 `page_count` 计费；失败 / 超时不计费。
- **触发策略**：简历上传后先走既有本地抽取（PDFBox / POI）；当抽取文本**过短（<200 字符，疑似扫描件）**，或用户上传的是**图片 / PPT** 时，自动降级调用 OCR；OCR 文本再进入 `ResumeStructurer` → `structured_json`。既有 `PARSE_NEED_MANUAL` 保持为最终兜底。
- 后端只做「文本 → 结构化」，**坐标不入库、不回传前端**，`purpose` 默认 `text_recognition`。

## 4. 服务边界与架构决策

### 4.1 决策（评审确认，不同意请在评审中指出）

- **D1「三个微服务」= AI 聚合模块 `oc-ai` 内的三个独立服务。**
  顶层只新增一个模块 `oc-ai`，其下为共享库 `oc-ai-core` 与三个可独立启动的服务 `oc-ai-asr` / `oc-ai-tts` / `oc-ai-ocr`（独立 Jar、Dockerfile、端口 **8094 / 8095 / 8096**、健康检查、资源限额）。三个服务**仅内网可达**，不挂 Nginx 公网路由。
  端口说明：原计划 8091–8093，实施时检测到本机 8091 已被其他项目的 Vite 开发服务器占用，故整体后移到 8094–8096 并在配置中可覆盖（`SERVER_PORT` / `AI_ASR_BASE_URL` 等）。
  理由：AI 能力归属一个域，模块列表不因新增能力而膨胀（`oc-ai` 一个入口即可看全 AI 侧）；同时三个服务仍可独立构建、部署、扩容与故障隔离。
- **D2 业务边界留在 `oc-app`，工具服务只做「文件 ↔ 文本 / 音频」转换。**
  鉴权、会话状态、题目归属、额度、幂等、审计、降级决策全部在 `oc-app`；工具服务不做业务判断。理由：避免把面试状态机拆散到三个服务，避免跨服务事务与分布式一致性问题。
- **D3 密钥与 Base URL 复用现有 AI 运行时配置**（`ai_runtime_config` 单行 + AES-GCM），不为工具接口引入第二套密钥。三个服务**只读**该行（30s 缓存），写入仍只在管理端（`oc-app`）。
- **D4 TTS 音频由 `oc-app` 代理返回**（`GET /api/v1/interviews/{id}/questions/{qid}/speech` → `audio/wav`），浏览器不直连微服务、不接触 Key；同题同音色**按内容哈希在 `oc-ai-tts` 侧缓存**，命中不回源、不计费。
- **D5 ASR 转写先行、再进异步反馈**：`oc-app` 同步等待转写（≤10s），成功后再走既有异步反馈链路；失败回退文本输入且**不重复扣额度**（对齐 [api.md](./api.md) §9）。

### 4.2 服务拓扑

```text
Browser（Vue 3 SPA）
   │ HTTPS / JSON / 音频二进制
   ▼
Nginx ──▶ oc-app (8081)  ← 唯一公网入口：JWT、会话校验、额度、审计、降级
             │  内网 HTTP（X-OC-Internal-Token）
             │
             ├──▶ oc-ai ─┬─▶ asr (8094)  ──▶ DeepSeek POST /v1/tools/asr
             │           ├─▶ tts (8095)  ──▶ DeepSeek POST /v1/tools/tts
             │           └─▶ ocr (8096)  ──▶ DeepSeek POST /v1/tools/ocr
             │
              PostgreSQL 16（只读 ai_runtime_config；追加 ai_call_log）· Redis
```

- 工具服务**不对外暴露**：本地绑 `127.0.0.1`；容器网络内只允许 `oc-app` 访问，Compose 中不 `ports:` 映射到宿主机。
- 工具服务**不持有业务表**：只读 `ai_runtime_config`（单行），只追加 `ai_call_log`（append-only 日志，无跨表 JOIN）。这是对「不跨服务 JOIN」约束的显式例外，仅限日志与配置读取。
- 故障隔离：任一工具服务宕机 → 对应能力降级（回退文本 / 不朗读 / 手动粘贴），面试主流程与评分不受影响。

### 4.3 模块与依赖

```text
oc-ai（聚合父模块，仅 pom，无代码）
  ├─ oc-ai-core（共享库，无 main）
  │    ├─ tools/DeepSeekToolsClient   multipart 上传 / 二进制流接收 / 错误码映射 / 超时
  │    ├─ internal/InternalAuth       X-OC-Internal-Token 常量时间校验（服务端）+ 注入（客户端）
  │    ├─ config/ToolsRuntimeConfig   复用 oc-ai-client 的 AiRuntimeConfigService
  │    ├─ log/AiCallLogWriter         追加 ai_call_log（失败只告警）
  │    └─ api/InternalDtos            三个服务的请求 / 响应 DTO 与错误体
  ├─ oc-ai-asr  (Boot app, 8094) ─┐
  ├─ oc-ai-tts  (Boot app, 8095) ─┼── 依赖 oc-ai-core
  └─ oc-ai-ocr  (Boot app, 8096) ─┘

oc-app 依赖 oc-ai-core（作为客户端调用三个服务）
```

依赖方向：`oc-ai-core → {oc-common, oc-ai-client}`；三个服务 → `oc-ai-core`；`oc-app → oc-ai-core`。**工具服务之间零依赖**，可单独发布。ArchUnit 新增规则：`oc-ai-asr|tts|ocr` 不得互相依赖，不得依赖 `oc-interview` / `oc-admin` / `oc-identity`。

### 4.4 内部接口契约（服务间，非公网）

| 服务 | 内部端点 | 请求 | 响应 |
|---|---|---|---|
| ASR | `POST /internal/v1/asr` | multipart `file` | `{requestId, text, durationMs}` |
| TTS | `POST /internal/v1/tts` | JSON `{text, voiceId, format}` | `audio/wav` 流（或 `audio/pcm`） |
| OCR | `POST /internal/v1/ocr` | multipart `file`, `purpose` | `{id, pageCount, pages:[{index,text,finishReason}]}` |

统一请求头：`X-OC-Internal-Token`（共享密钥）、`X-OC-User-Id`（用于日志归属与限流）、`X-OC-Request-Id`（Trace 串联）。统一错误体 `{code, message, retryable}`；上游 `429/503/504` 映射为 `TOOL_UPSTREAM_BUSY`，`400` 映射为 `TOOL_INVALID_INPUT`。

浏览器可见接口（**评审通过后回写 [api.md](./api.md)**）：

- `POST /api/v1/interviews/{id}/answers/audio`（对齐 api.md §9：`questionId` / `file` / `durationMs`）
- `GET /api/v1/interviews/{id}/questions/{qid}/speech`
- 简历上传接受类型扩展（`+png/jpg/jpeg/webp/tiff/bmp/ppt/pptx`），端点不变

## 5. 数据与契约变更

- `ai_call_log.scene` 为 `VARCHAR(32)`（无 CHECK），可直接新增 `ASR`/`TTS`/`OCR`；`AiScene` 枚举新增同名值；前端 `AiCallPanel` 场景筛选项补 `TTS`/`OCR`（`ASR` 已有）。
- `interview_answer` 补 DO 映射：`audio_key`、`audio_duration_ms`、`asr_status`、`asr_confidence`（列已存在，`InterviewAnswerDO` 当前未映射）。
- TTS 缓存：`oc-ai-tts` 本地卷 `tts/{sha256(text+voiceId+format)}.wav`，7 天 TTL 清理；不新增表。
- 新增运行时配置（管理端「AI 配置」页扩展）：`asrEnabled`、`ttsEnabled`、`ocrEnabled`、`ttsDefaultVoice`（`stella|tide`）。
- 无新增业务表；三个服务不建表、不迁移。Flyway 仍只在 `oc-app` 执行。

## 6. 计量、额度与限流

- 上游按量计费（见 §3）；`oc-app` 侧新增**每用户每日次数上限**（平台策略可配，默认：朗读 = 每题 ≤3 次；OCR = 每日 ≤5 次；语音作答 = 面试题目量内不额外计数）。
- 复用 `QuotaService` 与 `RedisKeys` 增加日计数器；是否新增 `QuotaType` 见 §11 待决（默认**不新增**，用日限流控制成本）。
- 上游并发：ASR / TTS 每账号 ≤50、OCR ≤15。工具服务各自用**信号量**限制在途请求，超限快速失败（`TOOL_BUSY`），不排队堆积；`oc-app` 对 `429/503` 退避重试 1 次。
- 所有调用写 `ai_call_log`（`scene` / `provider=deepseek` / `model=tools-asr|tools-tts|tools-ocr` / 耗时 / 状态 / `error_code`），由工具服务自行追加，`user_id` 取自 `X-OC-User-Id`；成本按官方单价换算后展示在管理端。

## 7. 前端设计

- **录音作答**：`AnswerComposer` 增加麦克风按钮（最小化图标按钮，遵循 Design Token）；录制中显示计时与波形（对齐 [ui-system.md](../dev/ui-system.md) 的 VoiceRecorder 规格）；停止 → 上传 → 显示「转写中」→ 转写文本**回填可编辑**（沿用 10–4000 字符校验）→ 用户确认提交（`inputMode='VOICE'`）。权限拒绝 / 浏览器不支持 / ASR 失败 → Toast 提示并保留文本输入。
- **题目朗读**：`QuestionPanel` / `RoomHeader` 增加「朗读 / 停止」按钮；播放 `oc-app` 代理返回的 `audio/wav`；切换题目 / 离开页面自动停止；状态用 `aria-live`。
- **OCR 上传**：`UploadDropzone` 扩展接受类型（`+png/jpg/jpeg/webp/tiff/bmp/ppt/pptx`）；上传后提示「识别中」；解析状态沿用 `PARSING → READY / FAILED`。
- **模板内改内容**：解析出结构化字段后进入既有 `ResumeEditor`，用户在所选版式（`ResumeLayoutPicker`）下逐字段修改、实时预览、导出（Markdown / JSON / 打印 PDF）。**本条复用既有能力，不新增页面**。
- **设置**：`SettingsPage` 增加「自动朗读题目」开关与音色选择（本地偏好）。
- 前端**不直连**三个工具服务，全部走 `oc-app`；不新增跨域配置。

## 8. 安全与合规

- Key 仅存密文（AES-GCM），不下发前端；三个服务从 DB 读取解密后只在内存中使用，**不写日志**。
- 服务间鉴权：`X-OC-Internal-Token`（≥32 字节随机，常量时间比较），本地/容器内网绑定，公网无路由；Token 轮换通过环境变量滚动重启。
- 上传校验：`multipart` 大小上限（音频 ≤8 MiB；OCR 单独端点放宽至 ≤20 MiB）、扩展名 + 魔数 + MIME 校验（复用 `ResumeService` 既有模式）。
- 隐私：音频 / 简历文本仅在调用时传给 DeepSeek，不用于训练（对齐 R-86）；音频落盘保留期可配（默认随简历一并软删）。
- 内容审核：TTS `400 Content Exists Risk`、ASR 失败均按「能力不可用」处理，不影响面试主流程。
- 服务不暴露 `/actuator` 公网路径；Prometheus 抓取走内网。

## 9. 验收标准

| # | 场景 | 通过标准 |
|---|---|---|
| A-1 | 金路径：录音作答 | 录 30s 中文回答 → 转写文本准确回填 → 编辑 → 提交 → 评分正常；`input_mode='VOICE'`、`asr_status='DONE'` |
| A-2 | 边界：120s / 8 MiB | 超 120s 自动停止；超 8 MiB 在客户端拦截并提示，不产生扣费 |
| A-3 | 降级：无 Key / 上游 503 | 明确提示并保留文本输入，接口不 5xx，不扣额度 |
| A-4 | 金路径：题目朗读 | 点朗读 → 播放 `audio/wav` → 再点停止；换题自动停止 |
| A-5 | 缓存：同题重复朗读 | 第二次命中原哈希缓存，无上游调用（`ai_call_log` 无新增） |
| A-6 | 金路径：OCR 简历 | 上传扫描版 PDF / 图片 → OCR 文本 → 结构化 → 编辑器改字段 → 按版式导出 PDF / Markdown 正确 |
| A-7 | 降级：OCR 失败 | 回退 `PARSE_NEED_MANUAL` 引导手动粘贴，不扣费 |
| A-8 | 审计与计量 | 每次调用在 `ai_call_log` 可见（scene / provider / model / 耗时 / 状态），管理端可按场景筛选 |
| A-9 | 独立部署 | 三个服务可各自单独构建、单独重启；停掉任一服务，其余能力与面试主流程仍可用 |
| A-10 | 内网隔离 | 从公网无法直接访问 8094/8095/8096；缺失 / 错误 `X-OC-Internal-Token` 返回 401 |
| A-11 | 规范 | 前端 `pnpm check`（typecheck + lint + build + 单测）通过；后端 Checkstyle / Spotless / ArchUnit / JaCoCo 通过 |

## 10. 里程碑与工作量（估）

| 阶段 | 内容 | 预估 |
|---|---|---|
| T1 | `oc-ai` 聚合模块 + `oc-ai-core` 共享库：`DeepSeekToolsClient`（multipart / 二进制流 / 错误映射）、内部鉴权、配置读取、`ai_call_log` 写入、DTO | 2d |
| T2 | 三个服务骨架：子模块 pom / 主类 / Dockerfile / health / 信号量限流 / Compose 与部署脚本 | 1.5d |
| T3 | ASR：音频契约、内部端点、`oc-app` 编排（会话校验 → 调用 → DO 映射 → 异步反馈衔接）、降级 | 1.5d |
| T4 | TTS：内部端点、流式代理、内容哈希缓存、前端播放与状态 | 1.5d |
| T5 | OCR：上传扩展、触发策略、结构化衔接 | 1d |
| T6 | 前端录音组件（PCM → WAV）、朗读按钮、设置项 | 2d |
| T7 | 管理端配置扩展、日限流、单测 / 联调 / 验收 | 1.5d |

合计约 **11 人日**（相比单体内模块方案多约 2.5d，主要是三套独立部署单元与内部鉴权）。

## 11. 风险与待决

- **R1 浏览器音频格式**（§3.1）：已选定前端 PCM → WAV 方案，需在 Safari / iOS 实测 `AudioWorklet` 可用性，不可用则回退 `ScriptProcessor`。
- **R2 iOS 自动播放策略**：TTS 播放需用户手势触发，首次播放前不预载。
- **R3 上游限速与成本**：语音 / 朗读放大调用量与成本，依赖日限流与 TTS 缓存；单价以官方为准（限时折扣可能结束）。
- **R4 是否新增 `QuotaType`**：默认**不新增**，用日限流控制成本——请评审确认。
- **R5 `multipart` 全局上限**（当前 10 MB / 12 MB）：OCR 需更大文件时须单独放宽端点；默认 OCR 单端点 ≤20 MiB，其余不变。
- **R6 是否接入 Search / Fetch**：本次不做，另立需求。
- **R7 三服务运维成本**：新增 3 个进程（约 +3×320MB JVM 上限）、3 个健康检查、3 套日志与告警；单机 Compose 内存需复核（当前生产 2C4G 级别需评估）。默认每个服务 `-Xmx256m`。
- **R8 TTS 缓存多副本**：`oc-ai-tts` 多副本时本地缓存不共享，命中率下降但不影响正确性；如需共享改用对象存储或共享卷，本期不实现。
- **R9 内部 Token 管理**：单一共享密钥轮换需三服务与 `oc-app` 同步滚动；本期不引入 mTLS / 服务网格。
