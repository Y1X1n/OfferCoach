#!/usr/bin/env bash
# 判断本次变更是否涉及指定区域；stdout 只输出 true / false，诊断信息走 stderr。
# 用法：changed-paths.sh <backend|frontend>
# 环境变量：EVENT_NAME、BASE_SHA（PR）、BEFORE_SHA（push）、HEAD_SHA
# 保守策略：基线缺失、diff 失败或变更列表为空时一律视为「已变更」，宁多跑不漏跑。
set -uo pipefail

area="${1:?usage: changed-paths.sh <backend|frontend|scripts>}"
case "$area" in
  backend) pattern='^(backend/|\.github/workflows/ci\.yml$)' ;;
  frontend) pattern='^(frontend/|\.github/workflows/ci\.yml$)' ;;
  # 运维脚本与编排文件不属于前后端：此前只改 scripts/ 或 compose.yaml 时两个 job 全跳过，
  # 等于零检查（备份脚本、启动冒烟脚本都属于这一类）。
  scripts) pattern='^(scripts/|backend/scripts/|\.github/scripts/|compose\.yaml$|\.github/workflows/ci\.yml$)' ;;
  *) echo "unknown area: $area" >&2; exit 2 ;;
esac

base=""
case "${EVENT_NAME:-}" in
  pull_request) base="${BASE_SHA:-}" ;;
  push) base="${BEFORE_SHA:-}" ;;
esac
head="${HEAD_SHA:-HEAD}"

changed="true"
if [ -n "$base" ] && git cat-file -e "${base}^{commit}" 2>/dev/null; then
  files="$(git diff --name-only "$base" "$head" 2>/dev/null)" || files=""
  if [ -n "$files" ]; then
    total="$(printf '%s\n' "$files" | wc -l | tr -d ' ')"
    printf '%s\n' "$files" | head -n 50 >&2
    if [ "$total" -gt 50 ]; then
      echo "... (${total} files changed, listing first 50)" >&2
    fi
    if ! printf '%s\n' "$files" | grep -Eq "$pattern"; then
      changed="false"
    fi
  fi
else
  echo "base commit unavailable; assuming ${area} changed" >&2
fi

echo "$changed"
