# OfferCoach 后端

Spring Boot 3.3.5 / Java 21 的 Maven 多模块工程。

**当前状态**：M1–M5 已实现并上线（`0.8.2`）；账号、额度、简历、JD、题库、会话、评分、报告与管理端均可用。完整说明见根目录 [README.md](../README.md) 与 [docs/CHANGELOG.md](../docs/CHANGELOG.md)。

## 模块

| 模块 | 职责 | 状态 |
|---|---|---|
| `oc-common` | 错误码、异常、Trace、Redis Key | M0 |
| `oc-identity` | 账号、JWT、额度、套餐 | M0 |
| `oc-ai-client` | AI 服务客户端 | M3+ |
| `oc-interview` | 简历 / JD / 会话 / 回答 / 报告 | M1+ |
| `oc-admin` | 题库、Prompt、运营查询 | M5+ |
| `oc-report-worker` | RabbitMQ 消费者、报告编排 | M4+ |
| `oc-gateway` | Spring Cloud Gateway，统一入口 | M0（路由） |
| `oc-app` | 聚合启动（MVP 单体部署） | M0 |

模块依赖：`oc-app → identity / interview / admin / report-worker / ai-client / common`；禁止循环，由 ArchUnit 强制。

## 端口

| 服务 | 端口 |
|---|---|
| gateway（统一入口） | 8080 |
| app | 8081 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| RabbitMQ / 管理台 | 5672 / 15672 |
| MinIO / 控制台 | 9000 / 9001 |
| Milvus / 健康 | 19530 / 9091（vector profile） |
| etcd | 2379（vector profile） |

前端 API 基址：`http://127.0.0.1:8080/api/v1`；前端 dev server 5174。

## 启动

前置：JDK 21、Maven 3.9+、PostgreSQL 与 Redis 可连接。

1. 准备基础设施（不启动 app / gateway）：

```bash
cp .env.example .env          # 按需修改密码等
bash scripts/check-infra.sh   # 检查 5432 / 6379，缺失时打印 docker run 命令

# 使用 Compose 启动（本机已有 offercoach-postgres / offercoach-redis 时只起缺失服务）
docker compose up -d postgres redis
docker compose up -d rabbitmq minio          # 按需
docker compose --profile vector up -d milvus # 按需
```

2. 启动应用（二选一）：

```bash
# 一键：后台启动 app + gateway，日志在 backend/.local/，Ctrl+C 停止
bash scripts/run-dev.sh

# 或手动两个终端（首次先 mvn -q -DskipTests install，保证同级模块可用）
mvn -pl oc-app -am spring-boot:run       # app，8081
mvn -pl oc-gateway spring-boot:run       # 另开终端，gateway，8080
```

> 若 `-am` 在上游库模块报 `Unable to find a suitable main class`，先执行 `mvn -q -DskipTests install`，再改用 `mvn -pl oc-app spring-boot:run`。
> `.env` 只被 Compose 与 `run-dev.sh` 自动读取；手动 Maven 启动前请自行 export 所需变量。

健康检查：`http://127.0.0.1:8081/actuator/health`、`http://127.0.0.1:8080/actuator/health`。
接口文档：`http://127.0.0.1:8081/swagger-ui.html`。

## 生产配置

生产使用 `prod` profile 启动，`oc-app/src/main/resources/application-prod.yml` 覆盖安全相关项：

```bash
SPRING_PROFILES_ACTIVE=prod \
CORS_ORIGINS=https://app.example.com \
java -jar oc-app/target/oc-app-*.jar
```

- Refresh Cookie 强制 `Secure`（`oc.auth.cookie.secure=true`），生产必须走 HTTPS。
- 固定验证码关闭（`oc.auth.dev-verify-code.enabled=false`）。
- 演示账号关闭（`oc.demo.enabled=false`）。
- `CORS_ORIGINS` 无默认值，必须显式配置，缺失时启动失败，避免生产误放开跨域。
- 健康端点仅返回状态（`management.endpoint.health.show-details=never`）。
- 日志由 `logback-spring.xml` 在非 local profile 下输出 JSON，无需额外覆盖。
- 数据库、Redis、JWT 等真实凭据通过环境变量 / K8s Secret 注入，仓库不保存密钥。

## 演示数据

- 账号：由 `oc.demo.email` 与 `oc.demo.password` 配置（仅 local 种子；不要在文档或仓库记录真实口令）。
- 注册验证码：`123456`（`DEV_VERIFY_CODE_ENABLED=true` 时固定，仅 local）。

## 接口（M0）

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/api/v1/auth/register` | 公开 | 注册，返回 accessToken 并下发 Refresh Cookie |
| POST | `/api/v1/auth/login` | 公开 | 登录，响应同注册 |
| POST | `/api/v1/auth/refresh` | Cookie | 轮换 Refresh，返回新 accessToken |
| POST | `/api/v1/auth/logout` | USER | 吊销当前 Access 与 Refresh，204 |
| GET | `/api/v1/users/me` | USER | 当前用户资料 |
| PATCH | `/api/v1/users/me` | USER | 修改昵称 / 头像 / 时区 |
| GET | `/api/v1/quota` | USER | 额度余额与每日用量 |
| GET | `/api/v1/plans` | 公开 | 套餐列表（可缓存 5 分钟） |
| GET | `/actuator/health` | 公开 | 健康检查（app / gateway 均有） |

Refresh Token 走 HttpOnly Cookie（`oc_rt`，Path `/api/v1/auth`）；业务接口用 `Authorization: Bearer <accessToken>`。

## 测试

```bash
mvn -q test                 # 单元测试 + ArchUnit 架构测试
mvn -pl oc-gateway test     # 网关 /internal/** 403 拦截单元测试
node scripts/smoke.mjs      # 冒烟：注册 → me → quota → refresh → logout → 失效校验
BASE_URL=http://127.0.0.1:8080 node scripts/smoke.mjs  # 默认值即 gateway
```

冒烟需 app 与 gateway 已启动、数据库迁移完成，使用固定验证码 `123456`。

## 发布版本

- 开发迭代保持 `SNAPSHOT`（根 `pom.xml`），生产发布前改为 release 版本（如 `0.1.0`）。
- 发布流水线执行 `RELEASE=true bash scripts/check-release.sh`；版本含 `SNAPSHOT` 时脚本退出 1 并阻断发布。

## 已知边界

- `/internal/**` 为服务间接口，网关不配置其路由；`InternalPathBlockFilter`（WebFilter）在路由前直接返回
  `403 FORBIDDEN`，不转发到 app。
- JWT 未配置密钥文件时使用开发临时密钥，重启即失效，仅限 local；生产走 K8s Secret。
- 验证码固定在 `123456`，无邮件 / 短信通道；仅 local 开启。
- 无支付、邮件、限流；`GET /plans` 只读，`POST /orders` 未实现。
- Redis 不可用时 Refresh 黑名单校验放行（fail-open），登出可能暂时不生效。
- Docker Hub 直连不通时，先走 DaoCloud 镜像再 retag（Compose 中仍用官方镜像名）：

```bash
for img in library/postgres:16-alpine library/redis:7.2-alpine \
           library/rabbitmq:3.13-management-alpine minio/minio:latest \
           milvusdb/milvus:v2.5.4 quay.io/coreos/etcd:v3.5.5; do
  docker pull "docker.m.daocloud.io/$img" && docker tag "docker.m.daocloud.io/$img" "$img"
done
```

## 目录

```text
backend/
├── compose.yaml        # 本地中间件（postgres/redis/rabbitmq/minio + vector profile）
├── .env.example        # 环境变量示例，复制为 .env
├── scripts/
│   ├── run-dev.sh        # 一键启动 app + gateway
│   ├── check-infra.sh    # 检查 5432/6379
│   ├── check-release.sh  # RELEASE=true 时阻断 SNAPSHOT 版本
│   └── smoke.mjs         # HTTP 冒烟脚本
└── oc-*/               # Maven 模块
```