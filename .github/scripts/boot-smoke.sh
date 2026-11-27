#!/usr/bin/env bash
# 启动冒烟：逐个拉起可部署服务，确认 Spring 上下文能初始化。
#
# 存在的理由：oc-pay-service 曾因 @Component 构造器歧义（两个构造函数都没标
# @Autowired）无法启动，从 PR #45 起坏了 9 天，而模块内没有任何加载 Spring 上下文
# 的测试——mvn verify 与 CI 全程绿灯。**编译通过 ≠ 能启动**，任何 bean 装配错误、
# 配置绑定错误（如 Jackson 3 把 write-dates-as-timestamps 挪了命名空间）都只在
# 真实启动时才暴露。
#
# 用法：在 backend/ 目录下执行本脚本。需要可用的 PostgreSQL 与 Redis。
set -uo pipefail

: "${DB_URL:=jdbc:postgresql://127.0.0.1:5432/offercoach}"
: "${DB_USER:=oc}"
: "${DB_PASSWORD:=oc_dev_password}"
: "${REDIS_HOST:=127.0.0.1}"
: "${REDIS_PORT:=6379}"
: "${BOOT_TIMEOUT:=120}"

# 模块目录:端口（顺序即启动顺序；oc-app 放最后，它要跑 Flyway 迁移）
SERVICES=(
  "oc-gateway:8080"
  "oc-ai/oc-ai-asr:8094"
  "oc-ai/oc-ai-tts:8095"
  "oc-ai/oc-ai-ocr:8096"
  "oc-ai/oc-ai-search:8097"
  "oc-ai/oc-ai-fetch:8098"
  "oc-pay/oc-pay-service:8099"
  "oc-app:8081"
)

failed=()
for entry in "${SERVICES[@]}"; do
  dir="${entry%:*}"
  port="${entry##*:}"
  artifact="$(basename "$dir")"
  jar="$(ls "$dir"/target/"$artifact"-*.jar 2>/dev/null | grep -v '\.original$' | head -1)"

  if [ -z "$jar" ]; then
    echo "::error::找不到 $artifact 的可执行 jar（$dir/target/）"
    failed+=("$artifact: no jar")
    continue
  fi

  log="$(mktemp)"
  SPRING_PROFILES_ACTIVE=local \
  SERVER_ADDRESS=127.0.0.1 \
  SERVER_PORT="$port" \
  DB_URL="$DB_URL" \
  DB_USER="$DB_USER" \
  DB_PASSWORD="$DB_PASSWORD" \
  REDIS_HOST="$REDIS_HOST" \
  REDIS_PORT="$REDIS_PORT" \
  AI_ENABLED=false \
    java -jar "$jar" >"$log" 2>&1 &
  pid=$!

  status="timeout"
  for _ in $(seq 1 "$BOOT_TIMEOUT"); do
    if grep -q "Started .*Application in" "$log" 2>/dev/null; then
      status="ok"
      break
    fi
    # 进程提前退出 = 启动失败，不必等满超时
    if ! kill -0 "$pid" 2>/dev/null; then
      status="exited"
      break
    fi
    sleep 1
  done

  kill "$pid" 2>/dev/null
  wait "$pid" 2>/dev/null

  started_line="$(grep -o "Started .*Application in [0-9.]* seconds" "$log" 2>/dev/null | tail -1)"
  case "$status" in
    ok)
      echo "✅ $artifact — $started_line"
      ;;
    *)
      echo "::error::$artifact 启动失败（$status）"
      # 只打印关键部分，避免 CI 日志被刷屏
      grep -E "APPLICATION FAILED TO START|Error starting ApplicationContext|Caused by:|No default constructor|Could not bind|Description:|Action:" "$log" 2>/dev/null | head -20
      failed+=("$artifact")
      ;;
  esac
  rm -f "$log"
done

echo
if [ ${#failed[@]} -gt 0 ]; then
  echo "❌ 启动冒烟失败：${failed[*]}"
  exit 1
fi
echo "✅ 全部 ${#SERVICES[@]} 个服务启动成功"
