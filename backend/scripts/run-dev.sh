#!/usr/bin/env bash
# 本地一键启动 oc-app（8081）、oc-gateway（8080）与三个 AI 工具服务（8094/8095/8096）。
# 日志写入 backend/.local/；Ctrl+C 或退出时由 trap 停止本次启动的进程。
# 极端情况下（Windows 下 Maven 派生的 java 子进程未被回收）需手动结束残留进程。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT/.local"
PG_PORT="${PG_PORT:-5432}"
REDIS_PORT="${REDIS_PORT:-6379}"
APP_PID=""
GW_PID=""
ASR_PID=""
TTS_PID=""
OCR_PID=""
SEARCH_PID=""
FETCH_PID=""

die() {
  echo "[错误] $*" >&2
  exit 1
}

# 可选：读取 backend/.env（与 Compose 共用）
if [ -f "$ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$ROOT/.env"
  set +a
fi

JAVA_HOME="${JAVA_HOME:-E:/Develop/JDK21}"
MVN_BIN="${MVN_BIN:-E:/tools/apache-maven-3.9.9/bin/mvn}"

# Git Bash 下统一为 /x/... 路径，兼容 Windows 风格 JAVA_HOME
if command -v cygpath >/dev/null 2>&1; then
  JAVA_HOME="$(cygpath -u "$JAVA_HOME")"
  MVN_BIN="$(cygpath -u "$MVN_BIN")"
fi
export JAVA_HOME

# ---- 1. JDK 21 / Maven ----
JAVA_BIN="$JAVA_HOME/bin/java"
[ -x "$JAVA_BIN" ] || die "未找到 java：$JAVA_BIN（请设置 JAVA_HOME）"
JAVA_VERSION="$("$JAVA_BIN" -version 2>&1 | awk -F'"' '/version/ {print $2; exit}')"
case "$JAVA_VERSION" in
  21.*) ;;
  *) die "需要 JDK 21，当前为 ${JAVA_VERSION:-未知}" ;;
esac

if [ ! -x "$MVN_BIN" ]; then
  if command -v mvn >/dev/null 2>&1; then
    MVN_BIN="$(command -v mvn)"
  else
    die "未找到 Maven：$MVN_BIN（可用 MVN_BIN 指定）"
  fi
fi

# ---- 2. 端口检查（只检测，不启动容器）----
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

port_open 127.0.0.1 "$PG_PORT" \
  || die "PostgreSQL 127.0.0.1:$PG_PORT 不可连接，先运行 bash scripts/check-infra.sh"
port_open 127.0.0.1 "$REDIS_PORT" \
  || die "Redis 127.0.0.1:$REDIS_PORT 不可连接，先运行 bash scripts/check-infra.sh"

for port in 8080 8081 8094 8095 8096 8097 8098; do
  if port_open 127.0.0.1 "$port"; then
    die "端口 $port 已被占用，请先停止已有进程"
  fi
done

mkdir -p "$LOG_DIR"

# ---- 3. 清理 ----
cleanup() {
  local status=$?
  trap - EXIT INT TERM
  if [ -n "$APP_PID" ] || [ -n "$GW_PID" ] || [ -n "$ASR_PID" ] || [ -n "$TTS_PID" ] || [ -n "$OCR_PID" ] \
     || [ -n "$SEARCH_PID" ] || [ -n "$FETCH_PID" ]; then
    echo
    echo "停止 dev 服务..."
    for pid in ${APP_PID} ${GW_PID} ${ASR_PID} ${TTS_PID} ${OCR_PID} ${SEARCH_PID} ${FETCH_PID}; do
      [ -n "$pid" ] || continue
      kill "$pid" 2>/dev/null || true
    done
    wait 2>/dev/null || true
    echo "已停止，日志：$LOG_DIR"
  fi
  exit "$status"
}
trap cleanup EXIT INT TERM

wait_ready() {
  local name="$1" url="$2" port="$3" pid="$4" i=0
  while [ "$i" -lt 90 ]; do
    if command -v curl >/dev/null 2>&1; then
      if curl -fsS --max-time 2 "$url" >/dev/null 2>&1; then
        echo "  $name 就绪：$url"
        return 0
      fi
    elif port_open 127.0.0.1 "$port"; then
      echo "  $name 端口已监听：127.0.0.1:$port"
      return 0
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "  $name 启动失败，见日志 $LOG_DIR/$name.log" >&2
      return 1
    fi
    sleep 2
    i=$((i + 1))
  done
  echo "  $name 启动超时（180s），见日志 $LOG_DIR/$name.log" >&2
  return 1
}

# ---- 4. 构建并启动 ----
echo "[1/3] 安装模块（跳过测试）..."
if ! (cd "$ROOT" && "$MVN_BIN" -q -DskipTests install) >"$LOG_DIR/install.log" 2>&1; then
  die "Maven install 失败，见日志 $LOG_DIR/install.log"
fi

# 服务间内部令牌：未显式配置时生成一次性随机值，供本次启动的进程共享。
if [ -z "${OC_AI_INTERNAL_TOKEN:-}" ]; then
  OC_AI_INTERNAL_TOKEN="$(head -c 32 /dev/urandom | od -An -tx1 | tr -d ' \n')"
  export OC_AI_INTERNAL_TOKEN
  echo "  已生成本次会话的 OC_AI_INTERNAL_TOKEN（仅进程内共享）"
fi

echo "[2/4] 启动 AI 工具服务（8094-8098）..."
(cd "$ROOT" && exec "$MVN_BIN" -pl oc-ai/oc-ai-asr spring-boot:run) >"$LOG_DIR/ai-asr.log" 2>&1 &
ASR_PID=$!
(cd "$ROOT" && exec "$MVN_BIN" -pl oc-ai/oc-ai-tts spring-boot:run) >"$LOG_DIR/ai-tts.log" 2>&1 &
TTS_PID=$!
(cd "$ROOT" && exec "$MVN_BIN" -pl oc-ai/oc-ai-ocr spring-boot:run) >"$LOG_DIR/ai-ocr.log" 2>&1 &
OCR_PID=$!
(cd "$ROOT" && exec "$MVN_BIN" -pl oc-ai/oc-ai-search spring-boot:run) >"$LOG_DIR/ai-search.log" 2>&1 &
SEARCH_PID=$!
(cd "$ROOT" && exec "$MVN_BIN" -pl oc-ai/oc-ai-fetch spring-boot:run) >"$LOG_DIR/ai-fetch.log" 2>&1 &
FETCH_PID=$!
wait_ready "ai-asr" "http://127.0.0.1:8094/actuator/health" 8094 "$ASR_PID" || die "oc-ai-asr 未就绪"
wait_ready "ai-tts" "http://127.0.0.1:8095/actuator/health" 8095 "$TTS_PID" || die "oc-ai-tts 未就绪"
wait_ready "ai-ocr" "http://127.0.0.1:8096/actuator/health" 8096 "$OCR_PID" || die "oc-ai-ocr 未就绪"
wait_ready "ai-search" "http://127.0.0.1:8097/actuator/health" 8097 "$SEARCH_PID" || die "oc-ai-search 未就绪"
wait_ready "ai-fetch" "http://127.0.0.1:8098/actuator/health" 8098 "$FETCH_PID" || die "oc-ai-fetch 未就绪"

echo "[3/4] 启动 oc-app（8081）..."
(cd "$ROOT" && exec "$MVN_BIN" -pl oc-app spring-boot:run) >"$LOG_DIR/app.log" 2>&1 &
APP_PID=$!
wait_ready "app" "http://127.0.0.1:8081/actuator/health" 8081 "$APP_PID" || die "oc-app 未就绪"

echo "[4/4] 启动 oc-gateway（8080）..."
(cd "$ROOT" && exec "$MVN_BIN" -pl oc-gateway spring-boot:run) >"$LOG_DIR/gateway.log" 2>&1 &
GW_PID=$!
wait_ready "gateway" "http://127.0.0.1:8080/actuator/health" 8080 "$GW_PID" || die "oc-gateway 未就绪"

echo
echo "dev 服务已就绪（Ctrl+C 停止）："
echo "  app      pid=$APP_PID  http://127.0.0.1:8081/actuator/health"
echo "  gateway  pid=$GW_PID  http://127.0.0.1:8080/actuator/health"
echo "  ai-asr   pid=$ASR_PID  http://127.0.0.1:8094/actuator/health"
echo "  ai-tts   pid=$TTS_PID  http://127.0.0.1:8095/actuator/health"
echo "  ai-ocr   pid=$OCR_PID  http://127.0.0.1:8096/actuator/health"
echo "  ai-search pid=$SEARCH_PID  http://127.0.0.1:8097/actuator/health"
echo "  ai-fetch pid=$FETCH_PID  http://127.0.0.1:8098/actuator/health"
echo "  API 基址              http://127.0.0.1:8080/api/v1"
echo "  日志                  $LOG_DIR/{app,gateway,ai-*}.log"
echo

wait