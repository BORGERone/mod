#!/usr/bin/env python3
"""Independent extracted-source Build 261 gate and byte-for-byte source ZIP reproduction."""
import argparse
import hashlib
import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
ZIP_NAME = 'SettledLands-Build261-IdleLook-Punchy-Animation-Source.zip'
JAR_NAME = 'settledlands-0.8.0-build261-idle-look-punchy-animation.jar'
BASE_NAME = 'settledlands-0.8.0-build260-npc-facing-sounds.jar'


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--zip', type=pathlib.Path, default=ROOT / ZIP_NAME)
    ap.add_argument('--java', required=True, type=pathlib.Path)
    ap.add_argument('--gson-source', required=True, type=pathlib.Path)
    a = ap.parse_args()
    archive, java, gson = a.zip.resolve(), a.java.resolve(), a.gson_source.resolve()
    assert archive.is_file() and java.is_file() and gson.is_file()
    with tempfile.TemporaryDirectory(prefix='sl261-backup-gate-') as td:
        root = pathlib.Path(td) / 'source'
        root.mkdir()
        with zipfile.ZipFile(archive) as z:
            assert z.testzip() is None
            z.extractall(root)
            names = set(z.namelist())
        manifest_path = root / 'BACKUP-MANIFEST.sha256'
        assert manifest_path.name in names
        expected = {}
        for line in manifest_path.read_text(encoding='utf-8').splitlines():
            sha, name = line.split('  ', 1)
            assert name not in expected
            expected[name] = sha
        assert set(expected) == names - {'BACKUP-MANIFEST.sha256'}
        for name, sha in expected.items():
            path = root / name
            assert path.is_file() and digest(path.read_bytes()) == sha, name
        print(f'OK extracted backup manifest: {len(expected)}/{len(expected)} files', flush=True)

        reproduced = pathlib.Path(td) / 'reproduced-source.zip'
        subprocess.run([sys.executable, str(root / 'tools/make_backup261.py'),
                        '--out', str(reproduced)], cwd=root, check=True)
        assert reproduced.read_bytes() == archive.read_bytes()
        print(f'OK byte-for-byte source ZIP reproduction sha256={digest(archive.read_bytes())}', flush=True)

        with tempfile.TemporaryDirectory(prefix='sl261-independent-primary-') as _:
            result = subprocess.run(
                [sys.executable, str(root / 'tests/gate_build261.py'),
                 '--base', str(root / BASE_NAME), '--jar', str(root / JAR_NAME),
                 '--java', str(java), '--gson-source', str(gson)],
                cwd=root, capture_output=True, text=True,
            )
            if result.returncode:
                raise AssertionError(f'extracted release gate failed\nstdout:\n{result.stdout}\nstderr:\n{result.stderr}')
            print(result.stdout, end='', flush=True)
        print('BUILD 261 INDEPENDENT SOURCE-BACKUP GATE: PASS', flush=True)

if __name__ == '__main__':
    main()
