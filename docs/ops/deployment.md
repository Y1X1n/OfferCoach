# 部署与环境

> 状态：生效 ｜ 版本：v1.4 ｜ 更新：2026-11-08
> 开发：Docker Compose ｜ 生产：Kubernetes + Helm
> 关联：[架构](../design/architecture.md)、[后端规范](../dev/backend.md)、[CI/CD](ci-cd.md)、[可观测性](observability.md)

## 1. 环境矩阵

| 环境 | 用途 | 数据 | 域名 | 部署方式 |
|---|---|---|---|---|
| local | 单人开发 | 种子数据 | `localhost` | Compose |
| dev | 联调 | 可重置 | `dev-api.offercoach.internal` | Compose / 单节点 K8s |
| staging | 预发、E2E、压测 | 脱敏副本 | `staging-api.offercoach.com` | Helm |
| prod | 生产 | 真实 | `api.offercoach.com` | Helm + 灰度 |

- 各环境独立数据库、Redis、MQ、MinIO 桶；禁止跨环境连库。
- 生产变更必须先过 staging，数据库迁移在 staging 演练。
- 开发环境允许模拟支付与固定验证码；生产强制关闭。

## 2. 本地一键启动

### 2.1 服务清单

| 服务 | 端口 | 说明 |
|---|---|---|
| frontend | 5174 | Vite dev server（5173 常被其他本地进程占用） |
| gateway | 8080 | 统一入口 |
| identity / interview / admin | 8081–8083 | 业务服务（单体模式只起 app） |
| report-worker | — | 无 HTTP |
| ai-orchestrator | 8000 | FastAPI |
| postgres | 5432 | 主库 |
| redis | 6379 | 缓存/限流 |
| rabbitmq | 5672 / 15672 | 消息/管理台 |
| milvus | 19530 | 向量库 |
| etcd / minio-milvus | 2379 / 9001 | Milvus 依赖 |
| minio | 9000 / 9001 | 对象存储 |
| prometheus / grafana | 9090 / 3000 | 监控 |
| tempo / loki | 3200 / 3100 | 链路/日志 |

### 2.2 Compose 关键片段

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: offercoach
      POSTGRES_USER: oc
      POSTGRES_PASSWORD: ${PG_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./sql/init:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U oc -d offercoach"]
      interval: 5s
      timeout: 3s
      retries: 20

  redis:
    image: redis:7.2-alpine
    command: ["redis-server", "--appendonly", "yes", "--maxmemory", "512mb", "--maxmemory-policy", "allkeys-lru"]
    volumes: [redisdata:/data]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      retries: 20

  rabbitmq:
    image: rabbitmq:3.13-management-alpine
    environment:
      RABBITMQ_DEFAULT_USER: oc
      RABBITMQ_DEFAULT_PASS: ${MQ_PASSWORD}
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 10s
      retries: 12

  milvus:
    image: milvusdb/milvus:v2.5.4
    command: ["milvus", "run", "standalone"]
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
    ports: ["19530:19530"]
    depends_on: [etcd, minio]
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9091/healthz"]
      interval: 10s
      retries: 20

  ai-orchestrator:
    build: ./ai-orchestrator
    env_file: .env
    depends_on:
      postgres: {condition: service_healthy}
      redis: {condition: service_healthy}
      milvus: {condition: service_started}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/healthz"]
      interval: 10s
      retries: 12
    deploy:
      resources:
        limits: {cpus: "2.0", memory: 2g}

  gateway:
    build: ./backend
    command: ["java", "-jar", "oc-gateway.jar"]
    ports: ["8080:8080"]
    depends_on:
      ai-orchestrator: {condition: service_healthy}

volumes:
  pgdata:
  redisdata:
  miniodata:
```

- 所有服务使用 `.env`，仓库只提交 `.env.example`。
- Milvus 依赖 etcd 与 MinIO；本地 MinIO 同时承担对象存储与 Milvus 存储，生产分开。
- 首次启动执行 `make seed` 导入题库样例与演示账号。

## 3. 镜像规范

### 3.1 Java

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN groupadd -r app && useradd -r -g app app
WORKDIR /app
COPY --from=build /src/oc-app/target/oc-app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseG1GC", \
            "-XX:+ExitOnOutOfMemoryError", "-Duser.timezone=UTC", "-jar", "app.jar"]
```

### 3.2 Python

```dockerfile
FROM python:3.12-slim AS build
WORKDIR /src
COPY pyproject.toml uv.lock ./
RUN pip install uv && uv sync --frozen --no-dev

FROM python:3.12-slim
RUN useradd -r -u 1001 app
WORKDIR /app
COPY --from=build /src/.venv /app/.venv
COPY app ./app
COPY prompts ./prompts
ENV PATH="/app/.venv/bin:$PATH"
USER 1001
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "2", "--timeout-keep-alive", "75"]
```

- 统一非 root、UTC 时区、只读根文件系统（K8s `readOnlyRootFilesystem: true` + 临时目录挂载）。
- 镜像标签：`git sha` + 语义版本；禁用 `latest` 部署。
- 构建启用缓存挂载；镜像扫描 High/Critical 阻断发布。

## 4. Kubernetes 部署

### 4.1 工作负载

| Deployment | 副本（prod） | CPU 请求/限制 | 内存请求/限制 | HPA |
|---|---|---|---|---|
| gateway | 3 | 500m / 1 | 512Mi / 1Gi | 3–10，CPU 65% |
| identity | 3 | 500m / 1 | 512Mi / 1Gi | 3–8 |
| interview | 3 | 750m / 1.5 | 768Mi / 1.5Gi | 3–12，CPU 70% |
| admin | 2 | 300m / 500m | 384Mi / 768Mi | 2–4 |
| ai-orchestrator | 4 | 1 / 2 | 2Gi / 4Gi | 4–20，CPU 65% + 活跃 SSE |
| report-worker | 2 | 500m / 1 | 512Mi / 1Gi | 2–6，队列深度 |
| frontend | 2 | 100m / 300m | 128Mi / 256Mi | 2–4 |

- `ai-orchestrator` 的 HPA 同时参考 `oc_sse_active_connections`（Prometheus Adapter）；仅 CPU 会低估长连接负载。
- 所有服务设置 `PodDisruptionBudget minAvailable: 1`。
- 反亲和：同一 Deployment 的 Pod 尽量分散到不同节点。

### 4.2 探针与优雅退出

```yaml
startupProbe:
  httpGet: {path: /healthz, port: 8000}
  failureThreshold: 30
  periodSeconds: 2
livenessProbe:
  httpGet: {path: /healthz, port: 8000}
  periodSeconds: 10
  timeoutSeconds: 3
readinessProbe:
  httpGet: {path: /readyz, port: 8000}
  periodSeconds: 5
  timeoutSeconds: 3
lifecycle:
  preStop:
    exec: {command: ["sh", "-c", "sleep 10"]}
terminationGracePeriodSeconds: 60
```

- `readyz` 检查 PG/Redis/Milvus 依赖；依赖不可用时摘流，但健康进程不重启（避免雪崩）。
- SSE 服务退出前：停止接受新连接 → 等待进行中的流完成或超时 → 关闭。`preStop` 10s 让 Ingress 先摘除。
- Java 服务设置 `server.shutdown=graceful`、`spring.lifecycle.timeout-per-shutdown-phase=30s`。

### 4.3 Ingress（SSE 关键）

```yaml
metadata:
  annotations:
    nginx.ingress.kubernetes.io/proxy-buffering: "off"
    nginx.ingress.kubernetes.io/proxy-request-buffering: "off"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "300"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "300"
    nginx.ingress.kubernetes.io/proxy-http-version: "1.1"
    nginx.ingress.kubernetes.io/enable-cors: "true"
    nginx.ingress.kubernetes.io/cors-allow-origin: "https://app.offercoach.com"
spec:
  rules:
    - host: api.offercoach.com
      http:
        paths:
          - path: /api/v1/ai
            pathType: Prefix
            backend: {service: {name: ai-orchestrator, port: {number: 8000}}}
          - path: /api
            pathType: Prefix
            backend: {service: {name: gateway, port: {number: 8080}}}
```

- `/api/v1/ai/**` 直达 AI 服务，减少一跳；该路由只接受带 `streamToken` 的请求，AI 服务自行校验。
- `/internal/**` 在 Ingress 层直接拒绝（返回 404），内部调用走 ClusterIP。
- 若使用云 LB，需关闭其响应缓冲/压缩，或改用 NLB + 直通。

### 4.4 资源配置注意

- Python 容器设置 `MALLOC_ARENA_MAX=2`，降低内存碎片。
- 模型供应商调用为 I/O 密集，`uvicorn workers` 不宜过多；按 2–4 起步，用 HPA 扩副本。
- 向量检索与重排若本地部署，单独节点池（GPU/高内存），不与 API 混部。

## 5. 备份与容灾

| 组件 | 策略 | RPO | RTO |
|---|---|---|---|
| PostgreSQL | 每日全量 + WAL 归档，PITR 7 天 | 5min | 1h |
| MinIO | 版本化 + 跨区复制（生产） | 15min | 2h |
| Redis | AOF everysec + 每日快照 | 1s（缓存可丢） | 30min |
| RabbitMQ | quorum queue + 持久化 | 0（已确认消息） | 30min |
| Milvus | 由 PG 题库重建，不单独备份 | 重建时长 | 4h |
| 配置/密钥 | GitOps + Secret 备份 | 0 | 30min |

- 每季度做一次恢复演练：从备份恢复 PG 到临时环境，跑通登录 → 面试 → 报告。
- Milvus 重建脚本必须幂等，可在新 Collection 上完成后再切别名。
- Redis 丢失会导致登录态失效与限流重置，需在事件中说明并强制重新登录。

## 6. 安全基线

- 网络：默认拒绝；仅 Ingress 入站；服务间 NetworkPolicy 白名单；`/internal/**` 不对公网开放。
- 密钥：K8s Secret/外部 KMS；禁止写入镜像、Git、日志；每 90 天轮换。
- 镜像：非 root、只读根文件系统、禁用特权、最小基础镜像。
- 依赖：每周扫描；High/Critical 7 天内修复。
- 数据库：最小权限账号（应用无 DDL 权限）；审计登录；生产禁公网。
- 备份加密；恢复演练使用脱敏数据。
- 管理端：IP 白名单 + MFA（V1.1）+ 操作审计。

## 7. 成本控制

- 资源：requests 按 P50 设置，limits 按 P95；HPA 避免长期高位。
- 存储：简历 180 天、音频 30 天、报告 90 天生命周期；日志分层（热 7 天，冷 30 天）。
- AI：三级预算熔断（单场/用户日/全站小时）；夜间评测使用小模型；embedding 与摘要缓存。
- 闲置：开发环境夜间缩容到 0；staging 非工作时间 1 副本。
- 每月成本复盘：单位经济模型（单场成本、单用户成本、毛利率）。

## 8. 当前 ECS 实例（2026-11-08）

| 项 | 值 |
|---|---|
| 实例 | `<ECS_INSTANCE_ID>`（cn-beijing，Ubuntu 22.04） |
| 公网 IP | `<ECS_PUBLIC_IP>` |
| 域名 | `https://<PUBLIC_DOMAIN>`（阿里云 DNS A 记录） |
| TLS | Let's Encrypt，`<PUBLIC_DOMAIN>`，有效期至 2027-02-05，Certbot 自动续期 |
| 入口 | 宿主 Nginx 80/443 → `/` `127.0.0.1:8082`（web 容器）、`/api/` `127.0.0.1:8081`（app 容器） |
| 容器 | `offercoach-app:0.8.2`、`offercoach-web:0.7.1`、`offercoach-postgres:16-alpine`、`offercoach-redis:7.2-alpine`；均为 `restart=unless-stopped` |
| 数据 | 命名卷 `offercoach_pgdata`、`offercoach_redisdata`；宿主目录 `/opt/offercoach/data` 挂载为 `/data`（UID 10001）；仅绑定 `127.0.0.1` 的 5432/6379 |
| 配置 | `/opt/offercoach`（Compose、`offercoach.env`、JWT 密钥，目录 700 / 文件 600）；`DEVICE_SALT` 用于设备指纹散列，首次部署时随机生成 |
| 题库 | `/opt/offercoach/data/qbank/questions.jsonl`（22,409 条，仅 MIT 来源：mianstack / agent-interview / ai-agent-interview-240）；非宽松许可来源已在 V9 迁移与导入器中排除 |
| 演示账号 | 生产演示账号由种子脚本/服务器环境维护，凭据不写入文档；本地开发默认演示账号见 `oc.demo.*` |
| 注册 | `REGISTER_INVITE_ENABLED=false`；注册开关、算术验证码、单 IP/单设备日限与邀请奖励由 `platform_settings` 控制，管理端「平台策略」在线调节 |
| AI | 当前 `AI_ENABLED=false`，简历/JD/出题/评分/报告使用确定性启发式降级；管理端「AI 配置」可在线切换 URL/Key/模型（保存即时失效缓存、约 30s 生效）；Key 以 AES-GCM 密文存库，根密钥 `OC_AI_SECRET` 存服务器 600 文件；管理端已发布 Prompt 版本会被运行时读取（60s 缓存）；SSE 默认关闭，前端轮询反馈 |
| 管理端 | `https://<PUBLIC_DOMAIN>/admin`；管理员账号由 `OC_ADMIN_*` 引导创建，凭据保存在服务器 600 权限文件；支持角色调整、解锁、重置密码、额度调整 |
| 备份 | `backup-before-invite-20261108.dump` 等；`/opt/offercoach/backups` 每日 03:15 全库 dump，保留 7 天；Qbank 与简历原文件在 `/opt/offercoach/data` |
| 证书巡检 | `/opt/offercoach/cert-check.sh` 每周一 09:00 检查 30 天到期，写 `/var/log/offercoach-cert.log` 与 syslog |
| 管理员 | `OC_ADMIN_ENABLED=true` 时按 `OC_ADMIN_EMAIL/OC_ADMIN_PASSWORD` 创建或提升 ADMIN；凭据保存在服务器 600 权限文件，不入仓 |
| 已清理 | `9router`、`newapi`（2026-11-08 删除容器、卷、镜像与 `/root/9r-backup` 备份） |
| 保留 | `<OTHER_SERVICES>` |

- 前端已接真实后端：认证/额度/简历/JD/面试/SSE 反馈/报告/套餐均走 `/api/v1`，不再依赖 mock；简历编辑支持 Markdown/JSON/打印 PDF 导出。
- 生产验证（0.8.0）：邀请注册冒烟 21 项通过——策略下发、算术验证码一次性、邀请双方奖励、无效邀请码拒绝、注册开关 30 秒内生效、管理端策略保存/恢复、报告逐题回答与证据；浏览器验证注册页验证码与邀请码可选、设置页邀请卡、管理端平台策略面板。
- 生产验证（0.8.2）：演示/管理员登录、刷新宽限补发、登出吊销旧/新 Token、额度与公共端点全部通过；Flyway 无新迁移；发版前已生成数据库 dump `/opt/offercoach/backups/offercoach-20261108-223558.dump`。
- 生产验证（0.7.1）：工作台 1366px 视口页头与概览卡间距恢复 24px，右上「上传简历 / 开始模拟面试」不再压住卡片；`index-BD-OHtiX.js` 已上线。
- 已知：单实例、无异地备份/监控告警；每日 dump 只在本机；AI 未配置密钥时走启发式；题库已剔除 NC/ND/GPL/UNLICENSED 来源。
- 事故记录：20:59 因资源/IO 卡顿导致 Cloud Assistant 掉线，经实例重启恢复；随后按用户要求停掉 9router/newapi，释放内存。
