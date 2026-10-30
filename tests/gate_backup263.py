#!/usr/bin/env python3
"""Independent Build 263 backup gate: manifest, byte-reproducible ZIP and extracted full gate."""
import argparse
import hashlib
import pathlib
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
ZIP_NAME = 'SettledLands-Build263-Cutscene-Fade-Source.zip'


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--zip', type=pathlib.Path, default=ROOT / ZIP_NAME)
    parser.add_argument('--java', required=True, type=pathlib.Path)
    args = parser.parse_args()
    archive, java = args.zip.resolve(), args.java.resolve()
    assert archive.is_file() and java.is_file()

    with tempfile.TemporaryDirectory(prefix='sl263-backup-gate-') as td:
        temp = pathlib.Path(td)
        extract = temp / 'source'
        extract.mkdir()
        with zipfile.ZipFile(archive) as source:
            assert source.testzip() is None
            names = source.namelist()
            assert len(names) == len(set(names))
            assert all(not pathlib.PurePosixPath(name).is_absolute()
                       and '..' not in pathlib.PurePosixPath(name).parts for name in names)
            source.extractall(extract)
            files = {name: (extract / name).read_bytes() for name in names}
        assert 'BACKUP-MANIFEST.sha256' in files
        expected = ''.join(sha(files[name]) + '  ' + name + '\n'
                           for name in sorted(files) if name != 'BACKUP-MANIFEST.sha256')
        assert files['BACKUP-MANIFEST.sha256'].decode('utf-8') == expected
        print(f'OK backup manifest ({len(files)}/{len(files)} files)', flush=True)

        regenerated = temp / 'reproduced.zip'
        result = subprocess.run(
            [sys.executable, str(extract / 'tools/make_backup263.py'), '--out', str(regenerated)],
            cwd=extract, capture_output=True, text=True,
        )
        if result.returncode:
            raise AssertionError('backup reproduction failed\n' + result.stdout + '\n' + result.stderr)
        assert regenerated.read_bytes() == archive.read_bytes()
        print(f'OK byte-identical source ZIP reproduction sha256={sha(archive.read_bytes())}', flush=True)

        result = subprocess.run(
            [sys.executable, str(extract / 'tests/gate_build263.py'),
             '--base', str(extract / 'settledlands-0.8.0-build262-smooth-npc-turning.jar'),
             '--jar', str(extract / 'settledlands-0.8.0-build263-cutscene-fade.jar'),
             '--java', str(java)],
            cwd=extract, capture_output=True, text=True,
        )
        if result.returncode:
            raise AssertionError('independent extracted Build 263 gate failed\n'
                                 + result.stdout + '\n' + result.stderr)
        print(result.stdout, end='')
        print('OK independent extracted full Build 263 release gate', flush=True)
    print('BUILD 263 BACKUP GATE: PASS', flush=True)


if __name__ == '__main__':
    main()
