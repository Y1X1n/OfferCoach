# 千问检测入口说明（agent.py）

> 状态：生效 ｜ 更新：2026-11-27
> 关联：[参赛改造说明](./alipay-agents-emerge.md)、[小程序运行说明](./miniprogram.md)

## 1. 它是什么

`agent/agent.py` 是提交给千问 AI 平台的**独立功能检测入口**。它把作品的核心链路
（简历结构化 → JD 解析与匹配 → 按缺口出题 → 逐题评分 → 复盘报告）在单进程内重跑一遍，
模型调用走 OpenAI 兼容的 `chat/completions`，不依赖 HTTP 后端、数据库或小程序页面。

之所以不是「调用 oc-app」：检测环境只允许访问模型服务，且不保证能启动 Java 服务；
赛事也明确允许「复用业务模块 + 增加适配入口」。因此这里复用同一套业务规则与 Prompt 结构，
用 Python 标准库重写编排。

## 2. 运行

```bash
python agent.py --version
# 1.0.0

python agent.py --prompt "请执行一次完整业务流程，完成简历解析、出题、评分与报告。"
```

环境变量（全部只从环境读取，不落盘）：

| 变量 | 用途 |
|---|---|
| `DASHSCOPE_API_KEY` | 模型密钥（必需） |
| `OPENAI_BASE_URL` / `DASHSCOPE_BASE_URL` | 模型地址，以 `/v1` 或 `/api/v1` 结尾（必需，优先用前者） |
| `AGENT_LOG_DIR` | 日志目录，写入 `agent.log`（缺省时写入口同目录 `logs/`） |

模型默认 `qwen-plus`，在赛事允许列表内。

## 3. 输出与退出码

- stdout：Markdown 复盘报告（可直接粘贴阅读）；
- 文件：`$AGENT_LOG_DIR/report.md`、`$AGENT_LOG_DIR/result.json`，若工作目录可写也各写一份；
- 日志：每次模型调用的 attempt、HTTP 状态、tokens；
- 退出码：`0` 正常完成；`1` 模型不可用 / 执行失败；`2` 缺少 `--prompt`。

## 4. 业务样例

| 文件 | 说明 |
|---|---|
| `samples/resume.md` | 虚构候选人简历（三年后端，含量化结果） |
| `samples/jd.md` | 高级后端工程师 JD（订单/交易方向） |
| `samples/answers.json` | 按维度（DEPTH / PROJECT / BEHAVIOR / FIT / DEFAULT）准备的示例回答 |
| `samples/nursing/` | 非 IT 行业样例（护理岗简历 / JD / 回答），用于证明「各行各业」不是口号 |

出题后按维度为每题匹配示例回答，因此不依赖人工输入即可完成评分与报告。
`--prompt` 命中「护理 / 护士 / 医疗 / 临床 / nursing」等关键词时自动切换到护理样例，其余走技术岗样例。

## 5. 稳定性设计

- **限流**：429 / 5xx 按 `Retry-After` 退避重试，最多 5 次；单次调用 90s 超时；
- **总预算**：整轮 20 分钟上限，接近上限时停止继续调用并报错，避免检测超时；
- **JSON 兜底**：模型输出带 ``` 围栏或前后缀文字时，按括号配对截取 JSON；
- **零依赖**：只用 `urllib / json / logging` 等标准库，`requirements.txt` 仅作声明；
- **无外网**：不访问检索、知识库、Embedding 或任何第三方服务。

## 6. 本地验证

有模型 Key 时：

```bash
cd agent
DASHSCOPE_API_KEY=sk-xxx OPENAI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1 \
AGENT_LOG_DIR=./logs python agent.py --prompt "完整流程"
```

没有 Key 时，可把 `OPENAI_BASE_URL` 指向任意 OpenAI 兼容的 mock：
需要按当前 prompt 返回对应 JSON（简历、JD、匹配、题目、评分、报告），并建议让首个请求返回 429，
以验证重试路径。跑通后检查 `$AGENT_LOG_DIR/agent.log` 中 `model call ok` 的次数应为 8 次
（解析 2 + 匹配 1 + 出题 1 + 评分 3 + 报告 1）。

## 7. 提交前自检

```bash
python scripts/competition/build-product.py     # 组装 product.zip
python scripts/competition/check-package.py     # 校验结构、大小、命名与 --version
```
