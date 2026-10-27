#!/usr/bin/env python3
"""Create and validate a self-contained, reproducible build 237 backup ZIP."""
import argparse
import hashlib
import pathlib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
INPUTS = [
    'settledlands-0.8.0-build230-modelfix.jar',
    'settledlands-0.8.0-build232-earlytex2.jar',
    'settledlands-0.8.0-build233-probe.jar',
    'settledlands-0.8.0-build234-probe.jar',
    'settledlands-0.8.0-build235-probe.jar',
    'settledlands-0.8.0-build236-corpse-tex.jar',
]
DOCS = ['README-Build237-CorpsePose-RU.txt', 'CHECK-JAR-237-RU.txt',
        'START-HERE-RU.md', 'HANDOFF-NEXT-SESSION-RU.md', 'AI-GUIDE-SettledLands.md', 'SESSION-STATE.md']
EXPECTED_JAR_SHA = '9abf34da8a566d882e4bb7a4561a93e019aa4c5a58461ec1e54fc51039e58c45'


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--inputs', type=pathlib.Path, required=True)
    p.add_argument('--jar', type=pathlib.Path, required=True)
    p.add_argument('--out', type=pathlib.Path, required=True)
    a = p.parse_args()
    entries = {name: (ROOT / name).read_bytes() for name in DOCS}
    entries[a.jar.name] = a.jar.read_bytes()
    if hashlib.sha256(entries[a.jar.name]).hexdigest() != EXPECTED_JAR_SHA:
        raise SystemExit('unexpected build 237 SHA-256')
    for directory in ('tools', 'tests'):
        for file in sorted((ROOT / directory).iterdir()):
            if file.is_file() and file.suffix in ('.py', '.sh'):
                entries[f'{directory}/{file.name}'] = file.read_bytes()
    for name in INPUTS:
        entries['test-inputs/' + name] = (a.inputs / name).read_bytes()
    manifest = ''.join(f'{hashlib.sha256(data).hexdigest()}  {name}\n'
                       for name, data in sorted(entries.items())).encode('utf-8')
    entries['BACKUP-MANIFEST.sha256'] = manifest

    # Stable timestamps and permissions make repeated backups byte-identical.
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
            raise SystemExit('backup ZIP content mismatch')
        for name, data in entries.items():
            if z.read(name) != data:
                raise SystemExit('backup ZIP entry differs: ' + name)
        if z.read('AI-GUIDE-SettledLands.md') != (ROOT / 'AI-GUIDE-SettledLands.md').read_bytes():
            raise SystemExit('AI-GUIDE missing or incorrect')
    data = a.out.read_bytes()
    print(f'backup OK: {a.out}, {len(data)} bytes, sha256 {hashlib.sha256(data).hexdigest()}, {len(entries)} entries')


if __name__ == '__main__':
    main()
