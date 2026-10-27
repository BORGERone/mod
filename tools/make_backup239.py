#!/usr/bin/env python3
"""Create a deterministic, self-contained build 239 backup with all test inputs."""
import argparse
import pathlib
import zipfile

import make_backup238 as previous

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAR_NAME = 'settledlands-0.8.0-build239-empty-corpse-cleanup.jar'
JAR_SHA = 'b68fd79357da507fff35939964816c2a301101ac3afb31298bf4ea31ab7a4290'
BASE238_NAME = previous.JAR_NAME
BASE238_SHA = previous.JAR_SHA
BASE237_NAME = previous.BASE_NAME
BASE237_SHA = previous.BASE_SHA
DOCS = ['README-Build239-EmptyCorpseCleanup-RU.txt', 'CHECK-JAR-239-RU.txt',
        'README-Build238-CorpseRecovery-RU.txt', 'CHECK-JAR-238-RU.txt', 'SHA256SUMS-build238.txt',
        'README-Build237-CorpsePose-RU.txt', 'CHECK-JAR-237-RU.txt', 'SHA256SUMS-build237.txt',
        'START-HERE-RU.md', 'HANDOFF-NEXT-SESSION-RU.md', 'AI-GUIDE-SettledLands.md', 'SESSION-STATE.md']


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--inputs', type=pathlib.Path, required=True)
    ap.add_argument('--base237', type=pathlib.Path, required=True)
    ap.add_argument('--base238', type=pathlib.Path, required=True)
    ap.add_argument('--jar', type=pathlib.Path, required=True)
    ap.add_argument('--out', type=pathlib.Path, required=True)
    a = ap.parse_args()
    in_paths = [a.jar, a.base237, a.base238] + [a.inputs / name for name in previous.INPUTS]
    if a.out.resolve() in {x.resolve() for x in in_paths}:
        raise SystemExit('refusing to overwrite an input')
    entries = {name: (ROOT / name).read_bytes() for name in DOCS}
    entries[JAR_NAME] = previous.read_expected(a.jar, JAR_SHA)
    entries['test-inputs/' + BASE237_NAME] = previous.read_expected(a.base237, BASE237_SHA)
    entries['test-inputs/' + BASE238_NAME] = previous.read_expected(a.base238, BASE238_SHA)
    for name, sha in previous.INPUTS.items():
        entries['test-inputs/' + name] = previous.read_expected(a.inputs / name, sha)
    for directory in ('tools', 'tests'):
        for file in sorted((ROOT / directory).iterdir()):
            if file.is_file() and file.suffix in ('.py', '.sh'):
                entries[f'{directory}/{file.name}'] = file.read_bytes()
    assert {'tools/patch_corpse_cleanup239.py', 'tools/make_backup239.py',
            'tests/gate_build239.py', 'tests/jvm_cleanup239.py', 'tests/jvm_recovery238.py',
            'AI-GUIDE-SettledLands.md'} <= set(entries)
    manifest = ''.join(f'{previous.digest(data)}  {name}\n' for name, data in sorted(entries.items())).encode('utf-8')
    entries['BACKUP-MANIFEST.sha256'] = manifest
    # Stable file order, timestamps and permissions allow byte-identical rebuilds.
    with zipfile.ZipFile(a.out, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 27, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            z.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(a.out) as z:
        if z.testzip() is not None or len(z.namelist()) != len(set(z.namelist())):
            raise SystemExit('backup ZIP invalid')
        if set(z.namelist()) != set(entries):
            raise SystemExit('backup ZIP entry mismatch')
        for name, data in entries.items():
            if z.read(name) != data:
                raise SystemExit('backup ZIP differs: ' + name)
        assert z.read('AI-GUIDE-SettledLands.md') == (ROOT / 'AI-GUIDE-SettledLands.md').read_bytes()
    data = a.out.read_bytes()
    print(f'backup OK: {a.out}, {len(data)} bytes, sha256 {previous.digest(data)}, {len(entries)} entries')


if __name__ == '__main__':
    main()
