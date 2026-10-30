#!/usr/bin/env python3
"""Verify Build271 archive manifest and run the gate from a clean extraction."""
from __future__ import annotations
import argparse
import hashlib
import pathlib
import subprocess
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
DEFAULT_ZIP = ROOT / "SettledLands-Build271-Pursuit-Nodes-Source.zip"


def run(args: list[str], cwd: pathlib.Path) -> str:
    result = subprocess.run(args, cwd=cwd, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(f"failed: {' '.join(map(str,args))}\n{result.stdout}\n{result.stderr}")
    return result.stdout


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--zip", type=pathlib.Path, default=DEFAULT_ZIP)
    parser.add_argument("--java", type=pathlib.Path, required=True)
    args = parser.parse_args()
    archive_path, java = args.zip.resolve(), args.java.resolve()
    assert archive_path.is_file() and java.is_file()
    with tempfile.TemporaryDirectory(prefix="build271-source-gate-") as td:
        root = pathlib.Path(td) / "source"
        root.mkdir()
        with zipfile.ZipFile(archive_path) as archive:
            assert archive.testzip() is None
            archive.extractall(root)
        count = 0
        for line in (root / "BACKUP-MANIFEST.sha256").read_text(encoding="utf-8").splitlines():
            digest, rel = line.split("  ", 1)
            path = root / rel
            assert path.is_file(), rel
            assert hashlib.sha256(path.read_bytes()).hexdigest() == digest, rel
            count += 1
        with zipfile.ZipFile(archive_path) as archive:
            assert count == len(archive.namelist()) - 1
        output = run(["python3", "tests/gate_pursuit271.py", "--java", str(java)], root)
        print(f"BUILD 271 SOURCE ZIP GATE: PASS (manifest {count}/{count}; clean extracted source gate passed)")
        print(output, end="")


if __name__ == "__main__":
    main()
