#!/usr/bin/env python3
"""组装参赛总包 product.zip。

结构（与赛事《任务说明》一致，两层都不额外套目录）：

    product.zip
    ├── agent.zip
    ├── link.md
    ├── description.pdf
    ├── demo.mp4
    └── cover.png

    agent.zip
    ├── agent.json / agent.py / requirements.txt
    ├── samples/
    ├── miniprogram/      支付宝小程序工程
    └── server/           作品所需的后端源码（Spring Boot 多模块）

用法：
    python scripts/competition/build-product.py
    python scripts/competition/build-product.py --skip-server   # 只验证小程序与检测入口
"""

from __future__ import annotations

import argparse
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
AGENT_SRC = ROOT / "agent"
MINIPROGRAM_SRC = ROOT / "miniprogram"
BACKEND_SRC = ROOT / "backend"
MATERIALS = ROOT / "dist" / "competition"
BUILD = MATERIALS / "build"

REQUIRED_MATERIALS = ["link.md", "description.pdf", "demo.mp4"]
COVER_CANDIDATES = ["cover.png", "cover.jpg", "cover.jpeg"]

SERVER_IGNORE_DIRS = {
    ".git",
    "target",
    "node_modules",
    ".idea",
    ".vscode",
    "__pycache__",
    ".venv",
    ".local",
    ".drafts",
}
SERVER_IGNORE_SUFFIXES = {".class", ".jar", ".log", ".dump", ".bak", ".tsbuildinfo"}


def fail(message: str) -> None:
    print(f"[build-product] 失败：{message}", file=sys.stderr)
    raise SystemExit(1)


def log(message: str) -> None:
    print(f"[build-product] {message}")


def ensure_materials() -> Path:
    for name in REQUIRED_MATERIALS:
        if not (MATERIALS / name).exists():
            fail(f"缺少作品材料 {MATERIALS / name}（先运行 build-assets 与录屏脚本）")
    for name in COVER_CANDIDATES:
        if (MATERIALS / name).exists():
            return MATERIALS / name
    fail("缺少封面图 cover.png / cover.jpg / cover.jpeg")
    raise AssertionError("unreachable")


def copy_server(target: Path) -> None:
    """复制后端源码，排除构建产物与本地运行时文件。"""

    def ignore(directory: str, names: list[str]) -> set[str]:
        ignored = set()
        for name in names:
            path = Path(directory) / name
            if name in SERVER_IGNORE_DIRS or path.suffix in SERVER_IGNORE_SUFFIXES:
                ignored.add(name)
                continue
            if name.startswith(".env"):
                ignored.add(name)
        return ignored

    shutil.copytree(BACKEND_SRC, target, ignore=ignore)
    api_doc = ROOT / "docs" / "design" / "api.md"
    if api_doc.exists():
        shutil.copy2(api_doc, target / "API.md")


def stage_agent(include_server: bool) -> Path:
    stage = BUILD / "agent"
    if stage.exists():
        shutil.rmtree(stage)
    stage.mkdir(parents=True)

    for name in ["agent.json", "agent.py", "requirements.txt"]:
        source = AGENT_SRC / name
        if not source.exists():
            fail(f"检测入口缺少 {source}")
        shutil.copy2(source, stage / name)

    samples = AGENT_SRC / "samples"
    if not samples.is_dir():
        fail(f"检测入口缺少样例目录 {samples}")
    shutil.copytree(samples, stage / "samples")

    if not (MINIPROGRAM_SRC / "app.json").exists():
        fail("miniprogram/app.json 不存在")
    shutil.copytree(
        MINIPROGRAM_SRC,
        stage / "miniprogram",
        ignore=shutil.ignore_patterns("node_modules", "__pycache__", "*.log"),
    )

    if include_server:
        if not BACKEND_SRC.is_dir():
            fail("缺少 backend/ 源码目录")
        copy_server(stage / "server")
    return stage


def zip_tree(source_dir: Path, zip_path: Path) -> None:
    if zip_path.exists():
        zip_path.unlink()
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(source_dir.rglob("*")):
            if path.is_dir() or path.is_symlink():
                continue
            archive.write(path, path.relative_to(source_dir).as_posix())


def size_mb(path: Path) -> float:
    return path.stat().st_size / 1024 / 1024


def main() -> int:
    parser = argparse.ArgumentParser(description="组装 product.zip")
    parser.add_argument("--skip-server", action="store_true", help="不打包后端源码，仅用于快速自检")
    args = parser.parse_args()

    cover = ensure_materials()
    BUILD.mkdir(parents=True, exist_ok=True)

    log("组装 agent.zip 目录")
    stage = stage_agent(include_server=not args.skip_server)

    agent_zip = BUILD / "agent.zip"
    log("压缩 agent.zip")
    zip_tree(stage, agent_zip)
    if size_mb(agent_zip) > 100:
        fail(f"agent.zip 超过 100MB：{size_mb(agent_zip):.1f}MB")

    product_dir = BUILD / "product"
    if product_dir.exists():
        shutil.rmtree(product_dir)
    product_dir.mkdir(parents=True)
    shutil.copy2(agent_zip, product_dir / "agent.zip")
    for name in ["link.md", "description.pdf", "demo.mp4"]:
        shutil.copy2(MATERIALS / name, product_dir / name)
    shutil.copy2(cover, product_dir / cover.name)

    product_zip = MATERIALS / "product.zip"
    log("压缩 product.zip")
    zip_tree(product_dir, product_zip)
    if size_mb(product_zip) > 200:
        fail(f"product.zip 超过 200MB：{size_mb(product_zip):.1f}MB")

    log(f"完成：{product_zip}")
    log(f"  product.zip {size_mb(product_zip):.1f}MB ｜ agent.zip {size_mb(agent_zip):.1f}MB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
