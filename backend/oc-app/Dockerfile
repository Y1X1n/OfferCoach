# 多阶段构建：镜像内完成 Maven 打包，无需宿主机 JDK / Maven。
# 不写 `# syntax=docker/dockerfile:1`、也不用 RUN --mount=cache：前者需要额外拉取
# dockerfile 前端镜像（Docker Hub 不可达时构建直接失败），后者要求 BuildKit。
# 代价是每次重建都会重新解析 Maven 依赖；本地一键启动可接受，CI 不走镜像构建。
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
# 样式与覆盖率门禁由 CI 负责；镜像构建只做打包，跳过它们以免
# Windows 工作区的 CRLF 让 `docker compose build` 直接失败，也省掉一轮全量扫描。
RUN mvn -B -q -Dmaven.test.skip=true \
        -Dspotless.check.skip=true -Dcheckstyle.skip=true -Djacoco.skip=true \
        package

FROM eclipse-temurin:21-jre

# curl 供 compose healthcheck 使用；运行进程仍以非 root 用户启动。
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app

WORKDIR /app

# 预建持久化目录并交给 app 用户；首次挂载命名卷时 Docker 会继承该属主。
RUN mkdir -p /data/storage && chown -R app:app /data

COPY --from=build --chown=app:app /src/oc-app/target/oc-app-*.jar /app/app.jar

USER app

EXPOSE 8081

ENTRYPOINT ["java", \
  "-Xms128m", "-Xmx320m", \
  "-XX:MaxMetaspaceSize=128m", \
  "-XX:+UseSerialGC", \
  "-Duser.timezone=UTC", \
  "-jar", "/app/app.jar"]
