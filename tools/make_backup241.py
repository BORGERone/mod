#!/usr/bin/env python3
"""Create a reproducible build 241 backup containing the guide and all inputs."""
import argparse
import pathlib
import zipfile

import make_backup240 as previous

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAR_NAME = 'settledlands-0.8.0-build241-altar-body-respawn.jar'
JAR_SHA = 'ded3b5b10db1edfeeea6f90ee753d420d1f7f17da92cf136d8db3012346f5b27'
DOCS = ['README-Build241-AltarBodyRespawn-RU.txt', 'CHECK-JAR-241-RU.txt', 'SHA256SUMS-build240.txt'] + previous.DOCS


def main():
    ap = argparse.ArgumentParser()
    for flag in ('inputs', 'base237', 'base238', 'base239', 'base240', 'jar', 'out'):
        ap.add_argument('--' + flag, type=pathlib.Path, required=True)
    a = ap.parse_args()
    legacy = previous.previous.previous  # make_backup238: hashes and test-inputs
    sources = [a.jar, a.base237, a.base238, a.base239, a.base240] + [a.inputs / n for n in legacy.INPUTS]
    if a.out.resolve() in {x.resolve() for x in sources}:
        raise SystemExit('refusing to overwrite an input')
    entries = {name: (ROOT / name).read_bytes() for name in DOCS}
    entries[JAR_NAME] = legacy.read_expected(a.jar, JAR_SHA)
    entries['test-inputs/' + previous.previous.BASE237_NAME] = legacy.read_expected(a.base237, previous.previous.BASE237_SHA)
    entries['test-inputs/' + previous.previous.BASE238_NAME] = legacy.read_expected(a.base238, previous.previous.BASE238_SHA)
    entries['test-inputs/' + previous.previous.JAR_NAME] = legacy.read_expected(a.base239, previous.previous.JAR_SHA)
    entries['test-inputs/' + previous.JAR_NAME] = legacy.read_expected(a.base240, previous.JAR_SHA)
    for name, sha in legacy.INPUTS.items():
        entries['test-inputs/' + name] = legacy.read_expected(a.inputs / name, sha)
    for directory in ('tools', 'tests'):
        for file in sorted((ROOT / directory).iterdir()):
            if file.is_file() and file.suffix in ('.py', '.sh'):
                entries[f'{directory}/{file.name}'] = file.read_bytes()
    assert {'tools/patch_altar_corpse241.py', 'tools/make_backup241.py', 'tests/gate_build241.py',
            'tests/jvm_altar241.py', 'tests/gate_build240.py', 'tests/jvm_recovery238.py',
            'AI-GUIDE-SettledLands.md'} <= set(entries)
    manifest = ''.join(f'{legacy.digest(data)}  {name}\n' for name, data in sorted(entries.items())).encode('utf-8')
    entries['BACKUP-MANIFEST.sha256'] = manifest
    with zipfile.ZipFile(a.out, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 28, 0, 0, 0))
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
    print(f'backup OK: {a.out}, {len(data)} bytes, sha256 {legacy.digest(data)}, {len(entries)} entries')


if __name__ == '__main__': main()
