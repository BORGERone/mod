#!/usr/bin/env python3
"""Create deterministic Build 263 source/retest ZIP with exact baseline and candidate."""
import argparse
import hashlib
import pathlib
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import patch_cutscene_fade263 as patch

ZIP_NAME = 'SettledLands-Build263-Cutscene-Fade-Source.zip'
BASE_NAME = 'settledlands-0.8.0-build262-smooth-npc-turning.jar'
JAR_NAME = 'settledlands-0.8.0-build263-cutscene-fade.jar'
EXCLUDED = {
    'GATE-RESULT-263.txt', 'GATE-BACKUP-RESULT-263.txt',
    'SHA256SUMS-build263.txt', 'BACKUP-MANIFEST.sha256', ZIP_NAME,
}
EXPECTED_JAR_SHA = '293a22505849b92cec191d66fafe189cd401dc66b7a7f17904d79d057af03bc0'


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', type=pathlib.Path, default=ROOT / ZIP_NAME)
    args = parser.parse_args()
    files: dict[str, bytes] = {}

    def add(path: pathlib.Path, name: str) -> None:
        assert path.is_file() and name not in files, (path, name)
        files[name] = path.read_bytes()

    for glob in ('*.md', '*.txt'):
        for path in sorted(ROOT.glob(glob)):
            if path.name not in EXCLUDED and not path.name.lower().endswith('.log'):
                add(path, path.name)
    for folder in ('tools', 'tests', 'src/build263'):
        for path in sorted((ROOT / folder).rglob('*')):
            if path.is_file() and not any(part.startswith('.') or part == '__pycache__'
                                          for part in path.relative_to(ROOT).parts) \
                    and path.suffix not in ('.pyc', '.pyo'):
                add(path, path.relative_to(ROOT).as_posix())
    add(ROOT / BASE_NAME, BASE_NAME)
    add(ROOT / JAR_NAME, JAR_NAME)

    assert digest(files[BASE_NAME]) == patch.BASE_SHA
    assert digest(files[JAR_NAME]) == EXPECTED_JAR_SHA
    required = {
        'START-HERE-RU.md', 'HANDOFF-NEXT-SESSION-RU.md', 'AI-GUIDE-SettledLands.md',
        'SESSION-STATE.md', 'README-Build263-Cutscene-Fade-RU.txt', 'API-COMPAT-263-RU.md',
        'CHECKLIST-TEST-BUILD263-RU.md', 'BACKUP-263-RU.txt',
        'tests/gate_build263.py', 'tests/gate_backup263.py', 'tests/jvm_cutscene_fade263.py',
        'tests/CutsceneFadeControlProbe263.java', 'tests/CutsceneFadeGraphDataProbe263.java',
        'tests/CutsceneFadeOverlayProbe263.java',
        'tests/fixture263/dev/settledlands/npc/client/CutsceneCameraClient.java',
        'tools/patch_cutscene_fade263.py',
        'tools/make_backup263.py', 'src/build263/dev/settledlands/npc/CutsceneFadeControl263.java',
    }
    assert required <= files.keys(), required - files.keys()
    assert not any(name.lower().endswith('.log') for name in files)
    files['BACKUP-MANIFEST.sha256'] = ''.join(
        digest(files[name]) + '  ' + name + '\n' for name in sorted(files)
    ).encode('utf-8')

    out = args.out.resolve()
    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, 'w') as archive:
        for name in sorted(files):
            info = zipfile.ZipInfo(name, (2026, 10, 30, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, files[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(out) as archive:
        assert archive.testzip() is None and len(archive.namelist()) == len(files)
    print(f'{out}: {out.stat().st_size} bytes sha256={digest(out.read_bytes())}; files={len(files)}')


if __name__ == '__main__':
    main()
