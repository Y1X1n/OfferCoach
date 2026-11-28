# 线上部署记录（小程序后端）

> 更新：2026-11-28 ｜ 环境：ECS `101.201.225.199`（2 核 / 1.7GB）｜ 域名：`offercoach.0803.xin`

## 1. 当前拓扑

```text
Nginx (443, Let's Encrypt)  offercoach.0803.xin
  ├── /            → 纯文本提示（Web 前端已下线）
  └── /api/        → 127.0.0.1:8081  offercoach-app:1.0.0
                        │
                     compose 网络
                        ├── postgres:5432        offercoach_offercoach_pgdata
                        ├── redis:6379           offercoach_offercoach_redisdata
                        ├── ai-search:8097       offercoach-ai-search:1.0.0（内部）
                        ├── ai-asr:8094          offercoach-ai-asr:1.0.0（内部）
                        └── ai-tts:8095          offercoach-ai-tts:1.0.0（内部）
```

部署目录 `/opt/offercoach`：`compose.yaml`、`offercoach.env`（600 权限）、`keys/jwt-*.pem`、`data/`、`backups/`。

## 2. 部署方式

服务器仅 1.7GB 内存，**不在服务器上构建**，统一「本机构建镜像 → `docker save` → `scp` → `docker load`」。

```bash
# 本机（Docker 内 Maven，无需宿主机 JDK）
docker run --rm -v <repo>/backend:/ws -v maven-repo-cache:/root/.m2/repository \
  -w /ws maven:3.9-eclipse-temurin-21 \
  mvn -B -ntp -pl oc-app -am -Dmaven.test.skip=true \
      -Dspotless.check.skip=true -Dcheckstyle.skip=true package

# 运行时镜像：eclipse-temurin:21-jre + jar，entrypoint 与仓库 Dockerfile 一致
docker build -t offercoach-app:1.0.0 .
docker save offercoach-app:1.0.0 | gzip -1 > app.tar.gz
scp app.tar.gz root@101.201.225.199:/root/
ssh root@101.201.225.199 'gunzip -f /root/app.tar.gz && docker load -i /root/app.tar'
```

工具服务（`oc-ai-*`）同样处理，`Xmx` 分别 256m，compose `mem_limit` 300m。

## 3. 关键配置项

`/opt/offercoach/offercoach.env`：

| 键 | 值 / 说明 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `CORS_ORIGINS` | `https://offercoach.0803.xin` |
| `DEV_VERIFY_CODE_ENABLED` / `REGISTER_INVITE_ENABLED` | 均为 `false` → 注册走**算术验证码**（小程序自动解） |
| `OC_AI_INTERNAL_TOKEN` | 随机 32 字节，app 与 ai-* 必须一致 |
| `AI_SEARCH_BASE_URL` | `http://ai-search:8097` |
| `AI_ASR_BASE_URL` | `http://ai-asr:8094` |
| `AI_TTS_BASE_URL` | `http://ai-tts:8095` |
| `AI_ENABLED` | `false`（实际以库内 `ai_runtime_config.enabled=true` 为准） |
| `OC_AI_SECRET` | 管理端保存 Key 的 AES-GCM 根密钥 |

模型配置在库内 `ai_runtime_config`（Base URL / 模型 / 加密 Key），**优先于 env**。

## 4. 实测结果（2026-11-28）

| 项 | 结果 |
|---|---|
| 健康 | `/actuator/health` 200；4 个容器 `RestartCount=0` |
| 注册 → 工作台 | 201 / 200，额度 3，周期至 2026-12-28 |
| 出题（技术岗） | 3 题全部 `GENERATED`，`retrieval_json.source=SEARCH_AI`，检索 6 条线索 |
| 出题（护理岗） | 命中「患者/输液/病房」3/8，不再出 Java 题 |
| 题库沉淀 | `question_bank` 新增 `DRAFT`（`source_repo=ai-search`，按岗位族分组） |
| TTS | `GET .../speech` → 200 `audio/wav`，952KB，7.9s |
| ASR | 上传 WAV → 201，转写 `DONE`，5.6s，文本与朗读一致 |
| 内存 | 6 容器合计约 950MB；系统可用 ~90MB + 1GB swap（未使用） |

## 5. 已知风险

- **内存余量小**：可用不足 100MB，靠 swap 兜底；建议把 `ofm` 遗留卷/目录清掉，或缩容 `ai-*` 的 `Xmx`。
- **旧 Prompt 覆盖**：线上 `question_gen` 曾有一条 50 字已发布版，导致模型输出无法解析。已置 `DRAFT`（`id=1`），**不要重新发布短 Prompt**。
- **ocr / fetch 未部署**：扫描件识别与 JD 链接抓取不可用，界面会提示改为粘贴文本。
