#!/usr/bin/env bash
# 发布版本检查：从根 pom.xml 提取项目自身 <version>（跳过 parent），
# RELEASE=true 时版本含 SNAPSHOT 则退出 1，阻断发布。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
POM="${ROOT_DIR}/pom.xml"

if [[ ! -f "${POM}" ]]; then
    echo "未找到 ${POM}" >&2
    exit 2
fi

# 跳过 <parent> 段，只取项目自身的 <version>，避免误读父 POM 版本。
PROJECT_SECTION="$(sed -n '/<\/parent>/,$p' "${POM}")"
VERSION="$(printf '%s\n' "${PROJECT_SECTION}" \
    | grep -m1 -o '<version>[^<]*</version>' \
    | sed -E 's#</?version>##g' || true)"

if [[ -z "${VERSION}" ]]; then
    echo "无法从 ${POM} 解析项目版本" >&2
    exit 2
fi

if [[ "${RELEASE:-false}" == "true" && "${VERSION}" == *SNAPSHOT* ]]; then
    echo "版本 ${VERSION} 仍为 SNAPSHOT，禁止 RELEASE 发布；请先将根 pom.xml 改为 release 版本。" >&2
    exit 1
fi

echo "版本检查通过：${VERSION}（RELEASE=${RELEASE:-false}）"