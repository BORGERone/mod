#!/usr/bin/env python3
"""Create deterministic Build 262 source/retest ZIP; Java and Gson sources stay external."""
import argparse
import hashlib
import pathlib
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import patch_auto_ai262 as patch

ZIP_NAME = 'SettledLands-Build262-Smooth-Npc-Turning-Source.zip'
BASE_NAME = 'settledlands-0.8.0-build261-idle-look-punchy-animation.jar'
JAR_NAME = 'settledlands-0.8.0-build262-smooth-npc-turning.jar'
EXCLUDED = {
    'GATE-RESULT-262.txt', 'GATE-BACKUP-RESULT-262.txt',
    'SHA256SUMS-build262.txt', 'BACKUP-MANIFEST.sha256', ZIP_NAME,
}


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument('--out', type=pathlib.Path, default=ROOT / ZIP_NAME)
    a = ap.parse_args()
    files: dict[str, bytes] = {}

    def add(path: pathlib.Path, name: str) -> None:
        assert path.is_file() and name not in files, (path, name)
        files[name] = path.read_bytes()

    for glob in ('*.md', '*.txt'):
        for path in sorted(ROOT.glob(glob)):
            if path.name not in EXCLUDED and not path.name.lower().endswith('.log'):
                add(path, path.name)
    for folder in ('tools', 'tests', 'src/build260', 'src/build261', 'src/build262'):
        for path in sorted((ROOT / folder).rglob('*')):
            if path.is_file() and not any(part.startswith('.') or part == '__pycache__'
                                          for part in path.relative_to(ROOT).parts) \
                    and path.suffix not in ('.pyc', '.pyo'):
                add(path, path.relative_to(ROOT).as_posix())
    add(ROOT / BASE_NAME, BASE_NAME)
    add(ROOT / JAR_NAME, JAR_NAME)

    assert digest(files[BASE_NAME]) == patch.BASE_SHA
    assert digest(files[JAR_NAME]) == '21fc8fa7c861c3652f020ce9bb10bac133ac253675084982e65f16cc734f2531'
    required = {
        'AI-GUIDE-SettledLands.md', 'HANDOFF-NEXT-SESSION-RU.md', 'START-HERE-RU.md', 'SESSION-STATE.md',
        'API-COMPAT-262-RU.md', 'README-Build262-Smooth-Npc-Turning-RU.txt',
        'CHECKLIST-TEST-BUILD262-RU.md', 'BACKUP-262-RU.txt',
        'tests/gate_build262.py', 'tests/gate_backup262.py', 'tests/jvm_auto_ai262.py',
        'tests/mutants262.py', 'tools/patch_auto_ai262.py', 'tools/make_backup262.py',
        'src/build262/dev/settledlands/npc/NpcAutoAiEngine255.java',
    }
    assert required <= files.keys(), required - files.keys()
    assert not any(name.lower().endswith('.log') for name in files)
    files['BACKUP-MANIFEST.sha256'] = ''.join(
        digest(files[name]) + '  ' + name + '\n' for name in sorted(files)
    ).encode('utf-8')
    out = a.out.resolve()
    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, 'w') as z:
        for name in sorted(files):
            info = zipfile.ZipInfo(name, (2026, 10, 30, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            z.writestr(info, files[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(out) as z:
        assert z.testzip() is None and len(z.namelist()) == len(files)
    print(f'{out}: {out.stat().st_size} bytes sha256={digest(out.read_bytes())}; files={len(files)}')

if __name__ == '__main__':
    main()
