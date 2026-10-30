#!/usr/bin/env python3
"""Deterministic Build 258 source/retest backup; excludes user logs and external dependencies."""
import argparse
import hashlib
import pathlib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
EXCLUDED = {'GATE-RESULT-258.txt', 'GATE-BACKUP-RESULT-258.txt', 'SHA256SUMS-build258.txt'}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', required=True, type=pathlib.Path)
    a = ap.parse_args()
    files: dict[str, bytes] = {}

    def add(path: pathlib.Path, name: str) -> None:
        assert path.is_file() and name not in files, (path, name)
        files[name] = path.read_bytes()

    for glob in ('*.md', '*.txt'):
        for path in sorted(ROOT.glob(glob)):
            if path.name not in EXCLUDED:
                add(path, path.name)
    for folder in ('tools', 'tests', 'src/build257', 'src/build258'):
        for path in sorted((ROOT / folder).rglob('*')):
            if path.is_file() and not any(part.startswith('.') or part == '__pycache__'
                                          for part in path.relative_to(ROOT).parts) \
                    and path.suffix not in ('.pyc', '.pyo'):
                add(path, path.relative_to(ROOT).as_posix())
    for name in ('settledlands-0.8.0-build257-calm-ai-stats.jar',
                 'settledlands-0.8.0-build258-stats-abi-fix.jar'):
        add(ROOT / name, name)
    assert digest(files['settledlands-0.8.0-build257-calm-ai-stats.jar']) == \
           '93013d8380263c3fdc4f5245a6ce4178f30bcda61478081bd9d10c93e42c45b1'
    assert b'BUILD 257 GATE: PASS' in files['GATE-RESULT-257.txt']
    assert b'BUILD 257 GATE: PASS' in files['GATE-BACKUP-RESULT-257.txt']
    assert {'AI-GUIDE-SettledLands.md', 'BACKUP-258-RU.txt', 'tests/gate_build258.py',
            'tests/jvm_auto_ai258.py', 'tests/mutants258.py', 'tools/patch_auto_ai258.py',
            'tools/gen_api258.py'} <= files.keys()
    assert not any(name.lower().endswith('.log') for name in files)
    files['BACKUP-MANIFEST.sha256'] = ''.join(
        digest(files[name]) + '  ' + name + '\n' for name in sorted(files)
    ).encode('utf-8')
    a.out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(a.out, 'w') as z:
        for name in sorted(files):
            info = zipfile.ZipInfo(name, (2026, 10, 30, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            z.writestr(info, files[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(a.out) as z:
        assert z.testzip() is None and len(z.namelist()) == len(files)
    print(f'{a.out}: {a.out.stat().st_size} bytes sha256 {digest(a.out.read_bytes())}; files={len(files)}')

if __name__ == '__main__':
    main()
