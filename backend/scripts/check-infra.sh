#!/usr/bin/env bash
# 检查本地 PostgreSQL / Redis 是否可连接。
# 只做检测，不启动、不停止任何容器；缺失时仅打印手工启动命令。
set -euo pipefail

HOST="${INFRA_HOST:-127.0.0.1}"
PG_PORT="${PG_PORT:-5432}"
REDIS_PORT="${REDIS_PORT:-6379}"

port_open() {
  local host="$1" port="$2"
  if command -v nc >/dev/null 2>&1; then
    nc -z -w 2 "$host" "$port" >/dev/null 2>&1 && return 0
  fi
  if command -v timeout >/dev/null 2>&1; then
    timeout 2 bash -c "exec 3<>/dev/tcp/$host/$port" >/dev/null 2>&1 && return 0
  fi
  (exec 3<>"/dev/tcp/$host/$port") >/dev/null 2>&1
}

container_healthy() {
  local name="$1"
  shift
  command -v docker >/dev/null 2>&1 || return 1
  docker exec "$name" "$@" >/dev/null 2>&1
}

PG_MISSING=0
REDIS_MISSING=0

if port_open "$HOST" "$PG_PORT"; then
  echo "[ok] PostgreSQL：$HOST:$PG_PORT 可连接"
elif container_healthy offercoach-postgres pg_isready -U oc -d offercoach; then
  echo "[ok] PostgreSQL：容器 offercoach-postgres 内部健康（端口未映射到本机）"
else
  echo "[missing] PostgreSQL：$HOST:$PG_PORT 不可连接"
  PG_MISSING=1
fi

if port_open "$HOST" "$REDIS_PORT"; then
  echo "[ok] Redis：$HOST:$REDIS_PORT 可连接"
elif container_healthy offercoach-redis redis-cli ping; then
  echo "[ok] Redis：容器 offercoach-redis 内部健康（端口未映射到本机）"
else
  echo "[missing] Redis：$HOST:$REDIS_PORT 不可连接"
  REDIS_MISSING=1
fi

if [ "$PG_MISSING" -eq 0 ] && [ "$REDIS_MISSING" -eq 0 ]; then
  echo "基础设施就绪：可以启动 backend/oc-app。"
  exit 0
fi

echo
echo "缺失服务的启动命令（Docker Hub 直连不通，先走 DaoCloud 镜像再 retag）："

if [ "$PG_MISSING" -eq 1 ]; then
  cat <<EOF
# PostgreSQL
docker pull docker.m.daocloud.io/library/postgres:16-alpine
docker tag docker.m.daocloud.io/library/postgres:16-alpine postgres:16-alpine
docker run -d --name offercoach-postgres \\
  -e POSTGRES_DB=offercoach -e POSTGRES_USER=oc -e POSTGRES_PASSWORD=oc_dev_password \\
  -p $PG_PORT:5432 -v offercoach-pgdata:/var/lib/postgresql/data postgres:16-alpine
EOF
fi

if [ "$REDIS_MISSING" -eq 1 ]; then
  cat <<EOF
# Redis
docker pull docker.m.daocloud.io/library/redis:7.2-alpine
docker tag docker.m.daocloud.io/library/redis:7.2-alpine redis:7.2-alpine
docker run -d --name offercoach-redis \\
  -p $REDIS_PORT:6379 -v offercoach-redisdata:/data redis:7.2-alpine redis-server --appendonly yes
EOF
fi

cat <<'EOF'

也可以使用 Compose（本机已有容器时只启动缺失服务）：
  cd backend && docker compose up -d postgres redis

本脚本不会自动启动容器；启动后重新运行本脚本确认。
EOF

exit 1