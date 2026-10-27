#!/usr/bin/env python3
"""Create and verify a self-contained reproducible build 238 backup ZIP."""
import argparse
import hashlib
import pathlib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
INPUTS = {
    'settledlands-0.8.0-build230-modelfix.jar': 'b41dc40574e3c2d809534107e46f8a7037f9e13e052331390e2a2e5bf4f4b818',
    'settledlands-0.8.0-build232-earlytex2.jar': 'a75d8a8f724f5e7433be152ddcc672a17b885930444cf74e60128c52bffdf914',
    'settledlands-0.8.0-build233-probe.jar': '0592d61c4ffe5888f93ca17fdf139e5982a63d060ce606c13c1d7b742a661260',
    'settledlands-0.8.0-build234-probe.jar': 'f83b8c69178176ecfbd3a3136e2328f25cbf80af791a818f0ab200a7fd6039b7',
    'settledlands-0.8.0-build235-probe.jar': 'cfddca97baf6a98bb0decff64e3f44b60b2c11a0ae574ac5f9078ded0edf5b9c',
    'settledlands-0.8.0-build236-corpse-tex.jar': '4d640528b565e788450be82ec237ca950c3dd3a3e3a8646fccde12f8534ddecf',
}
DOCS = ['README-Build238-CorpseRecovery-RU.txt', 'CHECK-JAR-238-RU.txt',
        'README-Build237-CorpsePose-RU.txt', 'CHECK-JAR-237-RU.txt',
        'START-HERE-RU.md', 'HANDOFF-NEXT-SESSION-RU.md', 'AI-GUIDE-SettledLands.md', 'SESSION-STATE.md']
BASE_NAME = 'settledlands-0.8.0-build237-corpse-pose.jar'
BASE_SHA = '9abf34da8a566d882e4bb7a4561a93e019aa4c5a58461ec1e54fc51039e58c45'
JAR_NAME = 'settledlands-0.8.0-build238-corpse-recovery.jar'
JAR_SHA = '632a64b9a94c31165f351152e260ece774c62870570a4ac6101c06ca634a84c2'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def read_expected(path, sha):
    data = path.read_bytes()
    if digest(data) != sha:
        raise SystemExit(f'unexpected SHA-256: {path}: {digest(data)}')
    return data


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--inputs', type=pathlib.Path, required=True)
    ap.add_argument('--base237', type=pathlib.Path, required=True)
    ap.add_argument('--jar', type=pathlib.Path, required=True)
    ap.add_argument('--out', type=pathlib.Path, required=True)
    a = ap.parse_args()
    entries = {name: (ROOT / name).read_bytes() for name in DOCS}
    entries[JAR_NAME] = read_expected(a.jar, JAR_SHA)
    entries['test-inputs/' + BASE_NAME] = read_expected(a.base237, BASE_SHA)
    for name, sha in INPUTS.items():
        entries['test-inputs/' + name] = read_expected(a.inputs / name, sha)
    for directory in ('tools', 'tests'):
        for file in sorted((ROOT / directory).iterdir()):
            if file.is_file() and file.suffix in ('.py', '.sh'):
                entries[f'{directory}/{file.name}'] = file.read_bytes()
    assert {'tools/patch_corpse_reclaim238.py', 'tests/gate_build238.py',
            'tests/jvm_recovery238.py', 'AI-GUIDE-SettledLands.md'} <= set(entries)
    manifest = ''.join(f'{digest(data)}  {name}\n' for name, data in sorted(entries.items())).encode('utf-8')
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
    print(f'backup OK: {a.out}, {len(data)} bytes, sha256 {digest(data)}, {len(entries)} entries')


if __name__ == '__main__':
    main()
