#!/usr/bin/env python3
"""Create a reproducible build 242 backup containing the guide and all inputs."""
import argparse
import pathlib
import zipfile

import make_backup238 as legacy
import make_backup239 as build239
import make_backup240 as build240
import make_backup241 as build241

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAR_NAME = 'settledlands-0.8.0-build242-body-required.jar'
JAR_SHA = '375e8faed7ea19e7bc1c343a043f833f82112a10b0ff17c1f6982bc18c2afe6d'
DOCS = ['README-Build242-BodyRequired-RU.txt', 'CHECK-JAR-242-RU.txt', 'SHA256SUMS-build241.txt'] + build241.DOCS


def main():
    ap = argparse.ArgumentParser()
    for flag in ('inputs', 'base237', 'base238', 'base239', 'base240', 'base241', 'jar', 'out'):
        ap.add_argument('--' + flag, type=pathlib.Path, required=True)
    a = ap.parse_args()
    sources = [a.jar, a.base237, a.base238, a.base239, a.base240, a.base241] + [a.inputs / n for n in legacy.INPUTS]
    if a.out.resolve() in {x.resolve() for x in sources + [ROOT / n for n in DOCS]}:
        raise SystemExit('refusing to overwrite an input or documentation')
    entries = {name: (ROOT / name).read_bytes() for name in DOCS}
    entries[JAR_NAME] = legacy.read_expected(a.jar, JAR_SHA)
    for path, name, digest in [
        (a.base237, legacy.BASE_NAME, legacy.BASE_SHA),
        (a.base238, legacy.JAR_NAME, legacy.JAR_SHA),
        (a.base239, build239.JAR_NAME, build239.JAR_SHA),
        (a.base240, build240.JAR_NAME, build240.JAR_SHA),
        (a.base241, build241.JAR_NAME, build241.JAR_SHA),
    ]:
        entries['test-inputs/' + name] = legacy.read_expected(path, digest)
    for name, sha in legacy.INPUTS.items():
        entries['test-inputs/' + name] = legacy.read_expected(a.inputs / name, sha)
    for directory in ('tools', 'tests'):
        for file in sorted((ROOT / directory).iterdir()):
            if file.is_file() and file.suffix in ('.py', '.sh'):
                entries[f'{directory}/{file.name}'] = file.read_bytes()
    assert {'tools/patch_altar_corpse242.py', 'tools/make_backup242.py',
            'tests/gate_build242.py', 'tests/jvm_altar242.py', 'tests/jvm_queue242.py',
            'tests/gate_build241.py', 'AI-GUIDE-SettledLands.md'} <= set(entries)
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


if __name__ == '__main__':
    main()
