#!/usr/bin/env python3
"""校验参赛总包 product.zip 是否符合赛事《任务说明》。

检查项：
- 外层/内层命名、结构、压缩包大小上限（200MB / 100MB）
- 五项材料齐全，封面为 16:9
- agent.zip 根目录有 agent.json、唯一检测入口、依赖声明与小程序工程
- 压缩包无加密、无绝对路径、无越界路径、无重复条目
- 解压后执行 `agent.py --version`，退出码 0 且版本号与 agent.json 一致

用法：
    python scripts/competition/check-package.py [dist/competition/product.zip]
"""

from __future__ import annotations

import json
import os
import re
import struct
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ZIP = ROOT / "dist" / "competition" / "product.zip"

PRODUCT_FILES = {"agent.zip", "link.md", "description.pdf", "demo.mp4"}
COVER_ALLOWED = {"cover.png", "cover.jpg", "cover.jpeg"}
MAX_PRODUCT_MB = 200
MAX_AGENT_MB = 100

errors: list[str] = []
warnings: list[str] = []


def error(message: str) -> None:
    errors.append(message)


def warn(message: str) -> None:
    warnings.append(message)


def check_zip_safety(archive: zipfile.ZipFile, label: str) -> list[str]:
    names = archive.namelist()
    if len(names) != len(set(names)):
        error(f"{label} 存在重复条目")
    for info in archive.infolist():
        name = info.filename
        if info.flag_bits & 0x1:
            error(f"{label} 含加密条目：{name}")
        if name.startswith("/") or re.match(r"^[A-Za-z]:", name):
            error(f"{label} 含绝对路径：{name}")
        if ".." in Path(name).parts:
            error(f"{label} 含越界路径：{name}")
        if info.external_attr >> 16 & 0o170000 == 0o120000:
            error(f"{label} 含符号链接：{name}")
    return names


def png_size(path: Path) -> tuple[int, int] | None:
    data = path.read_bytes()[:32]
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    width, height = struct.unpack(">II", data[16:24])
    return width, height


def check_product(product: Path) -> Path:
    if not product.exists():
        raise SystemExit(f"找不到 {product}")
    size_mb = product.stat().st_size / 1024 / 1024
    print(f"product.zip  {size_mb:.2f} MB")
    if size_mb > MAX_PRODUCT_MB:
        error(f"product.zip 超过 {MAX_PRODUCT_MB}MB")

    with zipfile.ZipFile(product) as archive:
        names = check_zip_safety(archive, "product.zip")
        roots = {name.split("/")[0] for name in names}
        nested_dirs = {name.split("/")[0] for name in names if "/" in name}
        expected = PRODUCT_FILES | COVER_ALLOWED
        missing = {name for name in PRODUCT_FILES if name not in roots}
        if missing:
            error(f"product.zip 根目录缺少：{', '.join(sorted(missing))}")
        if not roots & COVER_ALLOWED:
            error("product.zip 根目录缺少 cover.png / cover.jpg / cover.jpeg")
        if nested_dirs - (roots & COVER_ALLOWED):
            warn(f"根目录出现额外目录：{', '.join(sorted(nested_dirs - (roots & COVER_ALLOWED)))}")
        extra = roots - expected
        if extra:
            warn(f"根目录存在未声明的文件：{', '.join(sorted(extra))}")

        agent_bytes = archive.read("agent.zip")
        with tempfile.TemporaryDirectory() as temp_dir:
            agent_path = Path(temp_dir) / "agent.zip"
            agent_path.write_bytes(agent_bytes)
            check_agent(agent_path)
    return product


def check_agent(agent_zip: Path) -> None:
    size_mb = agent_zip.stat().st_size / 1024 / 1024
    print(f"agent.zip    {size_mb:.2f} MB")
    if size_mb > MAX_AGENT_MB:
        error(f"agent.zip 超过 {MAX_AGENT_MB}MB")

    with zipfile.ZipFile(agent_zip) as archive:
        names = check_zip_safety(archive, "agent.zip")
        roots = {name.split("/")[0] for name in names}

        if "agent.json" not in roots:
            error("agent.zip 根目录缺少 agent.json")
            return
        manifest = json.loads(archive.read("agent.json").decode("utf-8"))
        runtime = manifest.get("runtime")
        version = manifest.get("version")
        if runtime not in ("python", "node"):
            error(f"agent.json runtime 非法：{runtime}")
        if not isinstance(version, str) or not re.fullmatch(r"\d+\.\d+\.\d+", version):
            error(f"agent.json version 需为语义化版本：{version}")

        entry_by_runtime = {"python": "agent.py", "node": "agent.js"}
        entry = entry_by_runtime.get(runtime)
        others = set(entry_by_runtime.values()) - {entry}
        if entry not in roots:
            error(f"agent.zip 根目录缺少检测主入口 {entry}")
        else:
            if roots & others:
                error(f"agent.zip 根目录同时存在多个入口：{entry} 与 {sorted(roots & others)}")
            if entry not in {name for name in names if name.count('/') == 0}:
                error(f"{entry} 不在根目录")
        dependency = "requirements.txt" if runtime == "python" else "package.json"
        if dependency not in roots:
            error(f"agent.zip 根目录缺少依赖声明 {dependency}")

        for required_dir in ("miniprogram", "samples"):
            if required_dir not in roots:
                error(f"agent.zip 缺少必要目录 {required_dir}/")
        if "miniprogram/app.json" not in names:
            error("agent.zip 内 miniprogram/ 不是完整的支付宝小程序工程（缺 app.json）")

        # 解压后跑 --version
        with tempfile.TemporaryDirectory() as temp_dir:
            archive.extractall(temp_dir)
            run_version_check(Path(temp_dir), runtime, version)


def run_version_check(root: Path, runtime: str, version: str) -> None:
    entry = root / ("agent.py" if runtime == "python" else "agent.js")
    if not entry.exists():
        return
    command = [sys.executable, str(entry)] if runtime == "python" else ["node", str(entry)]
    env = dict(os.environ)
    env["AGENT_LOG_DIR"] = str(root / "logs")
    try:
        result = subprocess.run(
            command + ["--version"],
            cwd=str(root),
            capture_output=True,
            text=True,
            timeout=60,
            env=env,
        )
    except (OSError, subprocess.SubprocessError) as exc:
        error(f"--version 执行失败：{exc}")
        return
    output = (result.stdout or "").strip()
    if result.returncode != 0:
        error(f"--version 退出码为 {result.returncode}，期望 0")
    if output != version:
        error(f"--version 输出 {output!r}，与 agent.json 的 {version!r} 不一致")
    else:
        print(f"--version    ok（{output}）")


def check_cover(cover: Path) -> None:
    sizes = png_size(cover)
    if not sizes:
        warn("封面不是 PNG，跳过比例校验（请人工确认 16:9）")
        return
    width, height = sizes
    ratio = width / height
    if abs(ratio - 16 / 9) > 0.02:
        error(f"封面比例不是 16:9：{width}x{height}")
    else:
        print(f"cover       {width}x{height}（16:9）")


def main() -> int:
    product = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_ZIP
    product = product.resolve()
    check_product(product)

    materials = product.parent
    cover = next((materials / name for name in COVER_ALLOWED if (materials / name).exists()), None)
    if cover:
        check_cover(cover)
    for name in ("description.pdf", "demo.mp4", "link.md"):
        path = materials / name
        if not path.exists():
            error(f"缺少 {name}")
        elif path.stat().st_size < 1024:
            warn(f"{name} 体积异常（{path.stat().st_size}B）")

    print()
    for message in warnings:
        print(f"[warn] {message}")
    for message in errors:
        print(f"[error] {message}")
    if errors:
        print(f"\n未通过：{len(errors)} 项错误")
        return 1
    print("校验通过：结构、命名、大小、入口与 --version 均符合要求")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
