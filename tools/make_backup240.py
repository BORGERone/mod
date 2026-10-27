#!/usr/bin/env python3
"""Create a deterministic, self-contained build 240 backup with all test inputs."""
import argparse
import pathlib
import zipfile

import make_backup239 as previous

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAR_NAME = 'settledlands-0.8.0-build240-npc-local-cult.jar'
JAR_SHA = '2e4a29f6f147d6d081234ea5b7d3839b9a5ba297e984b3962b6f3113e47d76b5'
DOCS = ['README-Build240-NpcLocalCult-RU.txt', 'CHECK-JAR-240-RU.txt', 'SHA256SUMS-build239.txt'] + previous.DOCS


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--inputs', type=pathlib.Path, required=True)
    ap.add_argument('--base237', type=pathlib.Path, required=True)
    ap.add_argument('--base238', type=pathlib.Path, required=True)
    ap.add_argument('--base239', type=pathlib.Path, required=True)
    ap.add_argument('--jar', type=pathlib.Path, required=True)
    ap.add_argument('--out', type=pathlib.Path, required=True)
    a = ap.parse_args()
    in_paths = [a.jar, a.base237, a.base238, a.base239] + [a.inputs / n for n in previous.previous.INPUTS]
    if a.out.resolve() in {x.resolve() for x in in_paths}:
        raise SystemExit('refusing to overwrite an input')
    old = previous.previous
    entries = {name: (ROOT / name).read_bytes() for name in DOCS}
    entries[JAR_NAME] = old.read_expected(a.jar, JAR_SHA)
    entries['test-inputs/' + previous.BASE237_NAME] = old.read_expected(a.base237, previous.BASE237_SHA)
    entries['test-inputs/' + previous.BASE238_NAME] = old.read_expected(a.base238, previous.BASE238_SHA)
    entries['test-inputs/' + previous.JAR_NAME] = old.read_expected(a.base239, previous.JAR_SHA)
    for name, sha in old.INPUTS.items():
        entries['test-inputs/' + name] = old.read_expected(a.inputs / name, sha)
    for directory in ('tools', 'tests'):
        for file in sorted((ROOT / directory).iterdir()):
            if file.is_file() and file.suffix in ('.py', '.sh'):
                entries[f'{directory}/{file.name}'] = file.read_bytes()
    assert {'tools/patch_npc_local_cult240.py', 'tools/make_backup240.py',
            'tests/gate_build240.py', 'tests/jvm_npc_cult240.py', 'tests/gate_build239.py',
            'tests/jvm_cleanup239.py', 'tests/jvm_recovery238.py',
            'AI-GUIDE-SettledLands.md'} <= set(entries)
    manifest = ''.join(f'{old.digest(data)}  {name}\n' for name, data in sorted(entries.items())).encode('utf-8')
    entries['BACKUP-MANIFEST.sha256'] = manifest
    # Stable file order, timestamps and permissions allow byte-identical rebuilds.
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
    print(f'backup OK: {a.out}, {len(data)} bytes, sha256 {old.digest(data)}, {len(entries)} entries')


if __name__ == '__main__':
    main()
