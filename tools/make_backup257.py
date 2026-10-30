#!/usr/bin/env python3
"""Create deterministic, self-contained Build 257 source/retest backup (external Gson/Java excluded)."""
import argparse
import hashlib
import pathlib
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import patch_auto_ai257 as p

EXCLUDED = {
    'GATE-RESULT-257.txt', 'GATE-BACKUP-RESULT-257.txt', 'SHA256SUMS-build257.txt',
}

def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--base256-zip', required=True, type=pathlib.Path)
    ap.add_argument('--out', required=True, type=pathlib.Path)
    a = ap.parse_args()
    previous = a.base256_zip.resolve()
    assert previous.is_file()
    files: dict[str, bytes] = {}

    def add(path: pathlib.Path, name: str) -> None:
        assert path.is_file() and name not in files, (path, name)
        files[name] = path.read_bytes()

    # Current documentation and the signed-off historical gate evidence.
    for glob in ('*.md', '*.txt'):
        for path in sorted(ROOT.glob(glob)):
            if path.name not in EXCLUDED:
                add(path, path.name)

    for folder in ('tools', 'tests', 'src/build256', 'src/build257'):
        for path in sorted((ROOT / folder).rglob('*')):
            if path.is_file() and not any(part.startswith('.') or part == '__pycache__'
                                          for part in path.relative_to(ROOT).parts) \
                    and path.suffix not in ('.pyc', '.pyo'):
                add(path, path.relative_to(ROOT).as_posix())

    for name in (
        'settledlands-0.8.0-build255-auto-ai.jar',
        'settledlands-0.8.0-build256-npc-fp-corpse.jar',
        'settledlands-0.8.0-build257-calm-ai-stats.jar',
        'SettledLands-Build256-NpcFpCorpse-Source.zip',
    ):
        add(ROOT / name, name)
    assert sha(files['settledlands-0.8.0-build256-npc-fp-corpse.jar']) == p.BASE_SHA
    assert files['GATE-RESULT-256.txt'] and files['GATE-BACKUP-RESULT-256.txt']
    assert b'BUILD 256 GATE: PASS' in files['GATE-RESULT-256.txt']
    assert b'BUILD 256 GATE: PASS' in files['GATE-BACKUP-RESULT-256.txt']
    assert {'AI-GUIDE-SettledLands.md', 'BACKUP-257-RU.txt', 'tests/gate_build257.py',
            'tests/mutants257.py', 'tools/patch_auto_ai257.py'} <= files.keys()
    forbidden = ('hackersandslashers', 'playeranimlib', 'touch_n_motion',
                 'entity_model_features', 'entity_texture_features', 'emf_compat')
    assert not any(n.lower().endswith('.jar') and any(term in n.lower() for term in forbidden)
                   for n in files)
    assert not any(n.lower().endswith('.log') for n in files)

    files['BACKUP-MANIFEST.sha256'] = ''.join(
        sha(files[name]) + '  ' + name + '\n' for name in sorted(files)
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
    print(f'{a.out}: {a.out.stat().st_size} bytes sha256 {sha(a.out.read_bytes())}; files={len(files)}')

if __name__ == '__main__':
    main()
