#!/usr/bin/env python3
"""Independent Build 262 backup gate: manifest, byte-reproducible ZIP and extracted full gate."""
import argparse
import hashlib
import pathlib
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
ZIP_NAME = 'SettledLands-Build262-Smooth-Npc-Turning-Source.zip'


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--zip', type=pathlib.Path, default=ROOT / ZIP_NAME)
    ap.add_argument('--java', required=True, type=pathlib.Path)
    ap.add_argument('--gson-source', required=True, type=pathlib.Path)
    a = ap.parse_args()
    archive, java, gson = a.zip.resolve(), a.java.resolve(), a.gson_source.resolve()
    assert archive.is_file() and java.is_file() and gson.is_file()
    with tempfile.TemporaryDirectory(prefix='sl262-backup-gate-') as td:
        tmp = pathlib.Path(td)
        extract = tmp / 'source'
        extract.mkdir()
        with zipfile.ZipFile(archive) as z:
            assert z.testzip() is None
            names = z.namelist()
            assert len(names) == len(set(names))
            z.extractall(extract)
            files = {name: (extract / name).read_bytes() for name in names}
        assert 'BACKUP-MANIFEST.sha256' in files
        expected = ''.join(sha(files[name]) + '  ' + name + '\n'
                           for name in sorted(files) if name != 'BACKUP-MANIFEST.sha256')
        assert files['BACKUP-MANIFEST.sha256'].decode('utf-8') == expected
        print(f'OK backup manifest ({len(files)}/{len(files)} files)', flush=True)

        regenerated = tmp / 'reproduced.zip'
        subprocess.run([sys.executable, str(extract / 'tools/make_backup262.py'),
                        '--out', str(regenerated)], cwd=extract, check=True)
        assert regenerated.read_bytes() == archive.read_bytes()
        print(f'OK byte-identical source ZIP reproduction sha256={sha(archive.read_bytes())}', flush=True)

        result = subprocess.run(
            [sys.executable, str(extract / 'tests/gate_build262.py'),
             '--base', str(extract / 'settledlands-0.8.0-build261-idle-look-punchy-animation.jar'),
             '--jar', str(extract / 'settledlands-0.8.0-build262-smooth-npc-turning.jar'),
             '--java', str(java), '--gson-source', str(gson)],
            cwd=extract, capture_output=True, text=True,
        )
        if result.returncode:
            raise AssertionError('independent extracted Build 262 gate failed\n'
                                 + result.stdout + '\n' + result.stderr)
        print(result.stdout, end='')
        print('OK independent extracted full Build 262 release gate', flush=True)
    print('BUILD 262 BACKUP GATE: PASS', flush=True)

if __name__ == '__main__':
    main()
