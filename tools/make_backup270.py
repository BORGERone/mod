#!/usr/bin/env python3
"""Create the complete Build270 source archive by overlaying the Build269 bundle."""
from __future__ import annotations
import argparse
import hashlib
import pathlib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
BASE_ZIP = ROOT / "SettledLands-Build269-Pursuit-Source.zip"
OUT_DEFAULT = ROOT / "SettledLands-Build270-Pursuit-Hotfix-Source.zip"
OVERLAY_FILES = [
    "AI-GUIDE-SettledLands.md",
    "API-COMPAT-270-RU.md",
    "BACKUP-270-RU.txt",
    "CHECKLIST-TEST-BUILD270-RU.md",
    "GATE-RESULT-270.txt",
    "HANDOFF-NEXT-SESSION-RU.md",
    "README-Build269-Pursuit-RU.txt",
    "README-Build270-Pursuit-Hotfix-RU.md",
    "REVOKED-BUILD269-RU.txt",
    "SHA256SUMS-build270.txt",
    "SESSION-STATE.md",
    "START-HERE-RU.md",
    "settledlands-0.8.0-build270-pursuit-hotfix.jar",
    "src/build270/dev/settledlands/mixin/NpcPursuitGraphUiMixin269.java",
    "src/build270/dev/settledlands/mixin/ScreenAccessorPursuit269.java",
    "tests/gate_backup270.py",
    "tests/gate_pursuit270.py",
    "tests/java270/PursuitFormationProbe270.java",
    "tools/make_backup270.py",
    "tools/patch_pursuit270.py",
]
OVERLAY_DIRS = ["tools/api270"]


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
                name = path.relative_to(ROOT).as_posix()
                contents[name] = path.read_bytes()
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
    print(f"wrote {out} bytes={out.stat().st_size} sha256={hashlib.sha256(out.read_bytes()).hexdigest()} files={len(contents)}")


if __name__ == "__main__":
    main()
