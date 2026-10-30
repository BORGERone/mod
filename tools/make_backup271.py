#!/usr/bin/env python3
"""Create the complete Build271 source archive by overlaying the Build270 bundle."""
from __future__ import annotations
import argparse
import hashlib
import pathlib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
BASE_ZIP = ROOT / "SettledLands-Build270-Pursuit-Hotfix-Source.zip"
OUT_DEFAULT = ROOT / "SettledLands-Build271-Pursuit-Nodes-Source.zip"
OVERLAY_FILES = [
    "AI-GUIDE-SettledLands.md",
    "API-COMPAT-271-RU.md",
    "BACKUP-271-RU.txt",
    "CHECKLIST-TEST-BUILD271-RU.md",
    "GATE-RESULT-271.txt",
    "HANDOFF-NEXT-SESSION-RU.md",
    "README-Build269-Pursuit-RU.txt",
    "README-Build270-Pursuit-Hotfix-RU.md",
    "README-Build271-Pursuit-Nodes-RU.md",
    "REVOKED-BUILD269-RU.txt",
    "REVOKED-BUILD270-RU.txt",
    "SESSION-STATE.md",
    "SHA256SUMS-build271.txt",
    "START-HERE-RU.md",
    "settledlands-0.8.0-build271-pursuit-node-hotfix.jar",
    "src/build271/dev/settledlands/npc/NpcPursuit269.java",
    "src/build271/dev/settledlands/npc/client/PursuitGraphNodes271.java",
    "src/build271/dev/settledlands/mixin/NpcPursuitGraphUiMixin271.java",
    "src/build271/dev/settledlands/mixin/NpcPursuitContextMenuMixin271.java",
    "tests/gate_backup271.py",
    "tests/gate_pursuit271.py",
    "tests/java271/PursuitNodeProbe271.java",
    "tools/make_backup271.py",
    "tools/patch_pursuit271.py",
]
OVERLAY_DIRS = ["tools/api271"]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", type=pathlib.Path, default=BASE_ZIP)
    parser.add_argument("--out", type=pathlib.Path, default=OUT_DEFAULT)
    args = parser.parse_args()
    base, out = args.base.resolve(), args.out.resolve()
    assert base.is_file()
    contents: dict[str, bytes] = {}
    with zipfile.ZipFile(base) as archive:
        assert archive.testzip() is None
        contents = {name: archive.read(name) for name in archive.namelist()}
    for rel in OVERLAY_FILES:
        path = ROOT / rel
        assert path.is_file(), f"missing overlay file: {path}"
        contents[rel] = path.read_bytes()
    for rel in OVERLAY_DIRS:
        folder = ROOT / rel
        assert folder.is_dir()
        for path in sorted(folder.rglob("*")):
            if path.is_file():
                contents[path.relative_to(ROOT).as_posix()] = path.read_bytes()
    contents.pop("BACKUP-MANIFEST.sha256", None)
    manifest = "".join(f"{hashlib.sha256(data).hexdigest()}  {name}\n"
                       for name, data in sorted(contents.items()))
    contents["BACKUP-MANIFEST.sha256"] = manifest.encode("utf-8")
    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, "w") as archive:
        for name, data in sorted(contents.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 30, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    digest = hashlib.sha256(out.read_bytes()).hexdigest()
    print(f"wrote {out} bytes={out.stat().st_size} sha256={digest} files={len(contents)}")


if __name__ == "__main__":
    main()
