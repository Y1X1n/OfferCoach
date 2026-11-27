#!/usr/bin/env bash
# 备份 PostgreSQL 与上传文件（简历原件、语音音频）。
#
# 存在的理由：数据库有每日 dump，但上传文件只落在 `appdata` 卷里，此前没有任何备份手段。
# 卷或主机丢失时数据库行还在、文件全没了，而且没有任何恢复路径。
#
# 用法：
#   ./scripts/backup.sh                 # 同时备份数据库与文件
#   ./scripts/backup.sh --db-only
#   ./scripts/backup.sh --files-only
#
# 环境变量（与 compose.yaml 一致，缺省值仅供本机）：
#   BACKUP_DIR         备份输出根目录，默认 ./backups
#   BACKUP_RETENTION   保留份数，默认 7（按目录名倒序删除更旧的）
#   DB_CONTAINER       容器名，默认自动探测 offercoach-postgres-1
#   DB_NAME / DB_USER  默认 offercoach / oc
#   STORAGE_ROOT       上传文件目录，默认 docker volume `offercoach_appdata` 的挂载点；
#                      非容器部署时指向 oc.storage.root（如 /data/storage）
#
# 恢复：
#   pg_restore -h <host> -U oc -d offercoach --clean --if-exists <dump>
#   tar xzf storage-<ts>.tar.gz -C <storage_root 的父目录>
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$ROOT/backups}"
BACKUP_RETENTION="${BACKUP_RETENTION:-7}"
DB_NAME="${DB_NAME:-offercoach}"
DB_USER="${DB_USER:-oc}"
DB_CONTAINER="${DB_CONTAINER:-}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
TARGET="$BACKUP_DIR/$STAMP"

mode="all"
case "${1:-}" in
  --db-only) mode="db" ;;
  --files-only) mode="files" ;;
  "") ;;
  *) echo "用法：$0 [--db-only|--files-only]" >&2; exit 2 ;;
esac

mkdir -p "$TARGET"

# ---- 数据库 ----
# 宿主可能没装 pg_dump/pg_restore：能用容器就用容器，两边工具版本也与服务端一致。
db_container() {
  if [ -n "$DB_CONTAINER" ]; then
    printf '%s' "$DB_CONTAINER"
    return 0
  fi
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx 'offercoach-postgres-1'; then
    printf '%s' 'offercoach-postgres-1'
    return 0
  fi
  return 1
}

dump_db() {
  local out="$TARGET/db-$STAMP.dump"
  local container
  if container="$(db_container)"; then
    echo "== pg_dump（容器 $container）"
    # -Fc 自定义格式：支持 pg_restore 选择性恢复与并行恢复
    docker exec -e PGPASSWORD="${PGPASSWORD:-}" "$container" \
      pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc > "$out"
    # 验证归档可读：损坏的备份比没有备份更危险（会让人以为能恢复）
    docker exec -i "$container" pg_restore --list < "$out" > /dev/null
  else
    echo "== pg_dump（本机客户端）"
    pg_dump -h "${DB_HOST:-127.0.0.1}" -p "${DB_PORT:-5432}" -U "$DB_USER" -d "$DB_NAME" -Fc > "$out"
    pg_restore --list "$out" > /dev/null
  fi
  echo "   $(du -h "$out" | cut -f1)  $out"
}

# ---- 上传文件 ----
# 不依赖额外镜像：优先从运行中的 oc-app 容器直接复制（docker cp），
# 拉不了新镜像的离线/受限环境也能用。STORAGE_ROOT 存在时直接 tar 本地目录。
backup_files() {
  local src="${STORAGE_ROOT:-}"
  if [ -n "$src" ]; then
    if [ ! -d "$src" ]; then
      echo "!! STORAGE_ROOT 不存在：$src" >&2
      return 1
    fi
    echo "== 上传文件（$src）"
    tar czf "$TARGET/storage-$STAMP.tar.gz" -C "$src" .
    echo "   $(du -h "$TARGET/storage-$STAMP.tar.gz" | cut -f1)  $TARGET/storage-$STAMP.tar.gz"
    return 0
  fi

  local app
  app="$(docker ps --format '{{.Names}}' 2>/dev/null |
    grep -E '^(offercoach-app-1|oc-app)$' | head -1 || true)"
  if [ -n "$app" ]; then
    local stage="$TARGET/.storage-stage"
    echo "== 上传文件（从容器 $app 复制 /data/storage）"
    mkdir -p "$stage"
    docker cp "$app:/data/storage/." "$stage/"
    tar czf "$TARGET/storage-$STAMP.tar.gz" -C "$stage" .
    rm -rf "$stage"
    echo "   $(du -h "$TARGET/storage-$STAMP.tar.gz" | cut -f1)  $TARGET/storage-$STAMP.tar.gz"
    return 0
  fi

  if docker volume inspect "${STORAGE_VOLUME:-offercoach_appdata}" > /dev/null 2>&1; then
    echo "!! 找不到运行中的 oc-app 容器，但卷 ${STORAGE_VOLUME:-offercoach_appdata} 存在；" >&2
    echo "   请设置 STORAGE_ROOT 指向该卷的宿主路径后重跑（或启动容器再用 docker cp 模式）" >&2
    return 1
  fi
  echo "!! 既没有 STORAGE_ROOT 也没有可复制的容器，跳过文件备份" >&2
  return 1
}

[ "$mode" = "files" ] || dump_db
[ "$mode" = "db" ] || backup_files

# ---- 校验和与保留策略 ----
(cd "$TARGET" && sha256sum ./* > SHA256SUMS)
echo "== 已写入 $TARGET"

prune() {
  local count
  count="$(find "$BACKUP_DIR" -maxdepth 1 -mindepth 1 -type d | wc -l | tr -d ' ')"
  if [ "$count" -le "$BACKUP_RETENTION" ]; then
    return
  fi
  find "$BACKUP_DIR" -maxdepth 1 -mindepth 1 -type d | sort | head -n "$((count - BACKUP_RETENTION))" |
    while read -r old; do
      echo "== 清理过期备份 $old"
      rm -rf "$old"
    done
}
prune

echo
echo "恢复提示："
echo "  pg_restore -h <host> -U $DB_USER -d $DB_NAME --clean --if-exists $TARGET/db-$STAMP.dump"
echo "  tar xzf $TARGET/storage-$STAMP.tar.gz -C <storage_root>"
echo "异地复制本目录（对象存储 / 另一台机器）：本脚本只负责本地产出，异地与保留期由运维策略决定。"
