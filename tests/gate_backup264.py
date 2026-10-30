#!/usr/bin/env python3
"""Verify Build 264 source ZIP manifest and rerun the full gate from a clean extraction."""
import argparse
import hashlib
import pathlib
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--zip", type=pathlib.Path, default=ROOT / "SettledLands-Build264-Profession-Skins-Source.zip")
    parser.add_argument("--java", type=pathlib.Path, required=True)
    args = parser.parse_args()
    archive_path = args.zip.resolve()
    java = args.java.resolve()
    assert archive_path.is_file() and java.is_file()
    with zipfile.ZipFile(archive_path) as archive:
        assert archive.testzip() is None
        names = archive.namelist()
        assert len(names) == len(set(names))
        assert "BACKUP-MANIFEST.sha256" in names
        assert not any(name.lower().endswith(".log") for name in names)
        manifest = archive.read("BACKUP-MANIFEST.sha256").decode("utf-8").splitlines()
        expected = {name: digest for digest, name in (line.split("  ", 1) for line in manifest)}
        assert set(expected) == set(names) - {"BACKUP-MANIFEST.sha256"}
        for name, digest in expected.items():
            assert hashlib.sha256(archive.read(name)).hexdigest() == digest, name

    with tempfile.TemporaryDirectory(prefix="sl264-backup-gate-") as td:
        extracted = pathlib.Path(td)
        with zipfile.ZipFile(archive_path) as archive:
            archive.extractall(extracted)
        result = subprocess.run(
            [sys.executable, "tests/gate_build264.py", "--java", str(java),
             "--base", "settledlands-0.8.0-build262-smooth-npc-turning.jar",
             "--jar", "settledlands-0.8.0-build264-profession-skins.jar"],
            cwd=extracted, capture_output=True, text=True,
        )
        if result.returncode:
            raise RuntimeError(result.stdout + "\n" + result.stderr)
    print("BUILD 264 BACKUP GATE: PASS (manifest, extraction, deterministic rebuild, full Build 264 gate)")


if __name__ == "__main__":
    main()
