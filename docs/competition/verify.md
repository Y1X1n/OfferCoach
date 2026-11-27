# 实测手册：联网搜索出题 + 行业通用

> 状态：生效 ｜ 更新：2026-11-28
> 关联：[参赛改造说明](./alipay-agents-emerge.md)、[小程序运行说明](./miniprogram.md)、[检测入口](./agent-entry.md)

目标：验证三件事——**题库来自联网搜索**、**模型出题优先**、**非 IT 行业同样可用**。
按 0 → 6 的顺序做，每步都有「通过标准」，不用一次配齐。

## 0. 一页速查

| 步骤 | 命令 / 动作 | 通过标准 |
|---|---|---|
| 冒烟（无 Key） | `node backend/scripts/verify-search-first.mjs` | 会话 `READY`，至少有题；来源是 `BANK`/模板 |
| 只开模型 | `.env` 配 `AI_ENABLED=true` + Key，重建 oc-app | 来源变 `GENERATED`，`retrieval_json.source=AI` |
| 开搜索 | 启动 `oc-ai-search`(8097) | `retrieval_json.source=SEARCH_AI`，`searchRefs` 有链接 |
| 行业切换 | `INDUSTRY=nursing node backend/scripts/verify-search-first.mjs` | 护理题出现「院感/交接班/输液」，不再是 Java 题 |
| 沉淀 | 查 `question_bank where source_repo='ai-search'` | 有 `DRAFT` 行，`job_family` 为行业名 |
| 召回 | 审核发布后重跑同行业 | 出现 `BANK` 命中，`whyThisQuestion` 说明命中岗位题 |
| 小程序 | 预览或真机跑一遍准备→出题 | 题目与所选行业一致 |
| 检测入口 | `python agent/agent.py --prompt "为护理岗位..."` | 日志 `使用行业样例：nursing`，8 次模型调用，退出码 0 |

## 1. 准备环境

### 1.1 重建并启动后端

```bash
git switch feat/alipay-mini
docker compose up -d --build oc-app
curl -s http://127.0.0.1:8081/actuator/health
```

根级 `compose.yaml` 在容器内用多阶段构建，宿主机不需要 JDK / Maven。

`.env` 里至少要有（本地默认值即可）：

```bash
DEV_VERIFY_CODE=123456          # 注册用
OC_AI_INTERNAL_TOKEN=dev-internal-token   # oc-app 与工具服务必须一致
AI_SEARCH_BASE_URL=http://host.docker.internal:8097
QUESTION_SEARCH_ENABLED=true
QUESTION_SEARCH_COUNT=6
```

### 1.2 启动模型（先只验证「模型出题」）

```bash
AI_ENABLED=true
AI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
AI_API_KEY=sk-xxx
AI_MODEL=qwen-plus
```

改完 `.env` 后 `docker compose up -d oc-app` 让容器重建生效。

### 1.3 启动搜索工具服务（验证「搜索优先」）

`oc-ai-search` 不在根 compose 里，需要单独起（宿主机有 JDK 21 时最简单）：

```bash
cd backend
mvn -pl oc-ai/oc-ai-search -am -DskipTests package
SERVER_ADDRESS=127.0.0.1 SERVER_PORT=8097 \
AI_ENABLED=true AI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1 AI_API_KEY=sk-xxx \
OC_AI_INTERNAL_TOKEN=dev-internal-token \
DB_URL=jdbc:postgresql://127.0.0.1:5432/offercoach DB_USER=oc DB_PASSWORD=oc_dev_password \
java -jar oc-ai/oc-ai-search/target/oc-ai-search-*.jar
```

没有 JDK 也可以跑容器，关键是 `SERVER_ADDRESS=0.0.0.0` 并把端口映射到宿主机：

```bash
docker run -d --name oc-ai-search -p 8097:8097 \
  -e SERVER_ADDRESS=0.0.0.0 -e AI_ENABLED=true -e AI_BASE_URL=... -e AI_API_KEY=... \
  -e OC_AI_INTERNAL_TOKEN=dev-internal-token \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/offercoach -e DB_USER=oc -e DB_PASSWORD=oc_dev_password \
  eclipse-temurin:21-jre java -jar /app/oc-ai-search.jar   # 先把 jar 挂进容器
```

自检：

```bash
curl -s http://127.0.0.1:8097/actuator/health
docker logs oc-app --tail 50 | grep -i search
```

## 2. 冒烟：不配 Key 也要能出题

```bash
node backend/scripts/verify-search-first.mjs
```

预期：会话 `READY`，题目来自题库/模板，脚本最后打印
`行业关键词命中：0/7` 和 `[warn] 题目里没有行业关键词`。

**这一条很重要**：它证明「模型/搜索都不可用时系统不会挂」，是降级基线，不是失败。

## 3. 只开模型：来源应从 BANK 变成 GENERATED

配好 1.2 后重跑脚本，输出里应看到：

```
来源分布：GENERATED 3 · BANK 0
```

再看检索来源（此时应没有搜索证据）：

```bash
docker exec offercoach-postgres-1 psql -U oc -d offercoach -c \
 "select seq, source, retrieval_json->>'source' as retrieval from interview_question where session_id=<会话ID> order by seq;"
```

期望 `retrieval` 为 `AI`；题目开始贴 JD（但仍可能比较泛）。

## 4. 开搜索：这是本次改造的核心

启动 1.3 后重跑脚本，期望：

- 输出题目直接引用 JD 的行业场景；
- SQL 里 `retrieval` 变成 **`SEARCH_AI`**，且 `searchRefs` 有 1–3 条 `{title,site,url}`：

```bash
docker exec offercoach-postgres-1 psql -U oc -d offercoach -c \
 "select retrieval_json->>'searchQuery' as q, jsonb_pretty(retrieval_json->'searchRefs') from interview_question where session_id=<会话ID> limit 1;"
```

- 出题耗时增加约 1–3 秒属于正常（多了一次检索）。

**失败但没报错** = 搜索被静默跳过，按 §8 排查。

## 5. 行业切换：护理 vs 后端

```bash
INDUSTRY=nursing node backend/scripts/verify-search-first.mjs
```

通过标准：

| 检查 | 技术岗 | 护理岗 |
|---|---|---|
| 题目关键词 | 接口 / 缓存 / 性能 | 院感 / 交接班 / 输液 |
| 不该出现 | —— | 「Java int 与 long」这类纯技术题 |
| `job_family` | 后端 / 通用 | 医疗健康 |

如果护理岗仍出技术题，按顺序查：`JobFamilyClassifier` 是否归到「医疗健康」→ 搜索是否可用 → 模型是否可用。
（都不可用时会回落到固定题库，而固定题库以 IT 为主，这是已知限制。）

## 6. 沉淀与召回

每次「搜索 + 模型」出题后，生成题会按岗位族落草稿：

```bash
docker exec offercoach-postgres-1 psql -U oc -d offercoach -c \
 "select id, job_family, dimension, status, left(stem, 40) from question_bank where source_repo='ai-search' order by id desc limit 10;"
```

期望：`status=DRAFT`、`job_family=医疗健康` 等。

发布后才能被召回（本地验证可以临时改状态，正式环境走管理端）：

```sql
UPDATE question_bank SET status='PUBLISHED' WHERE id = <id>;
```

再跑一次同行业脚本，应出现 `source=BANK` 且 `whyThisQuestion` 显示「题库命中该岗位题目」。

## 7. 小程序端

本地预览：

```bash
node scripts/competition/preview/server.js
# http://127.0.0.1:5180/preview/?page=pages/index/index&code=123456
```

重点看：简历列表上传/粘贴 → 岗位（文本或链接）→ 配置 → 出题是否贴合行业 → 答题/评分 → 报告。

真机（需要你的支付宝账号）：

1. 支付宝开发者工具导入 `miniprogram/`，填 APPID；
2. 改 `miniprogram/utils/config.js` 的 `API_BASE`（`https://<域名>/api/v1`）与 `ACCESS_CODE`；
3. 小程序后台把域名加进服务器域名白名单；
4. 上传 → 体验版 → 添加体验成员。

语音作答与朗读必须在真机验证（预览运行时不伪造录音）。

## 8. 检测入口（千问）

```bash
DASHSCOPE_API_KEY=sk-xxx \
OPENAI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1 \
AGENT_LOG_DIR=./agent-logs \
python agent/agent.py --prompt "请为护理岗位执行一次完整业务流程"
```

期望：日志 `使用行业样例：nursing`、`calls=8`、退出码 0，`report.md` 里是护理场景。
把 prompt 换成「后端开发」再跑一次，应切回技术样例。

## 9. 故障排查

| 现象 | 可能原因 | 处理 |
|---|---|---|
| 一直是 `BANK`，关键词 0 命中 | `AI_ENABLED=false` 或 Key 无效 | 看 oc-app 日志 `AI 未启用` / 401 |
| `retrieval=AI` 不是 `SEARCH_AI` | 搜索服务没起 / 地址不对 / 内部令牌不一致 | `curl :8097/actuator/health`；比对 `OC_AI_INTERNAL_TOKEN` |
| 出题变慢但没搜索证据 | `QUESTION_SEARCH_ENABLED=false` | 改 `.env` 后重建 oc-app |
| 注册 429 | 同 IP 注册太频繁 | 用 `VERIFY_EMAIL` + `VERIFY_PASSWORD` 复用账号 |
| `question_bank` 没有 `ai-search` 行 | 本次是兜底路径（无搜索）或迁移未跑 | 先确认 `retrieval=SEARCH_AI`；再看 Flyway 是否到最新 |
| 护理岗仍出技术题 | 岗位族归类失败或模型/搜索不可用 | 查 JD 标题是否含「护士/护理」，或先按 §4 打通搜索 |

## 10. 最小通过集（提交前）

- [ ] §2 冒烟通过（降级可用）
- [ ] §4 `retrieval=SEARCH_AI` + `searchRefs` 非空
- [ ] §5 护理岗与技术岗题目明显不同
- [ ] §6 有 `DRAFT` 沉淀，发布后能 `BANK` 命中
- [ ] §7 小程序端完成一次完整面试
- [ ] §8 检测入口两种行业各跑通一次
