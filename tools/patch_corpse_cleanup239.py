#!/usr/bin/env python3
"""Build 239: remove an owned body/coffin after every saved item is recovered.

Bytecode patch only, based on the exact build 238. Existing authorization,
partial-stack, malformed-NBT, ordinary-death, altar and health behavior remains
unchanged. The body disappears only after a nonempty saved list is fully emptied;
empty/missing lists and any remaining item are left alone.
"""
import argparse
import hashlib
import pathlib
import struct
import sys
import tomllib
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classedit
import decode
from patch_corpse_pose237 import assert_pool_roundtrip, find_invoke
from patch_corpse_reclaim238 import CORPSE, ENTITY, HELPER, PLAYER, COMPOUND

BASE_SHA = '632a64b9a94c31165f351152e260ece774c62870570a4ac6101c06ca634a84c2'
OLD_VERSION = '0.8.0-build238-corpse-recovery'
NEW_VERSION = '0.8.0-build239-empty-corpse-cleanup'
EXPECTED_DIFF = {HELPER + '.class', 'META-INF/neoforge.mods.toml', 'settledlands-patch-info.txt'}
CHANGELOG = ('Build 239: after the owner recovers every saved item from their possessed-body corpse '
             'or coffin, it disappears; a body with remaining items stays in the world.')
PATCHINFO = ('сборка 239: после получения всех сохранённых вещей владелец убирает свой труп '
             'или гроб; если остались вещи, он сохраняется. Обычная смерть и алтарь не меняются.')


def patch_recovery(raw):
    k = classedit.Klass(raw)
    assert_pool_roundtrip(k, raw)
    method = k.find_method('tryClaim', '(L' + PLAYER + ';L' + ENTITY + ';)V')
    assert method is not None
    parts = classedit.code_parts(k, method)
    # The only remove(String) in this method clears the corpse-items key after
    # the list became empty. Release any attached lead with the same vanilla
    # operation used by GuideCorpseNpc.mobInteract, then remove this entity.
    # Early returns, partial/invalid stacks, authorization and both messages
    # remain untouched. Calling dropLeash when not leashed is a vanilla no-op.
    pc, size = find_invoke(k, parts['code'], COMPOUND, 'remove', '(Ljava/lang/String;)V')
    at = pc + size
    assert parts['code'][pc] == 0xb6 and parts['code'][at] == 0x2c  # next is aload_2 (notification)
    assert at not in decode.targets(parts['code'])
    drop = k.add_methodref(CORPSE, 'dropLeash', '(ZZ)V')
    discard = k.add_methodref(ENTITY, 'discard', '()V')
    # aload_3, true, true, dropLeash; aload_3, discard; two alignment nops.
    ins = (b'\x2d\x04\x04\xb6' + struct.pack('>H', drop) +
           b'\x2d\xb6' + struct.pack('>H', discard) + b'\x00\x00')
    assert len(ins) == 12
    parts = classedit.insert(parts, at, ins)
    parts['max_stack'] = max(parts['max_stack'], 3)
    return k.dump(method, classedit.build_code_body(k, parts))


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--base', required=True, type=pathlib.Path)
    p.add_argument('--out', required=True, type=pathlib.Path)
    args = p.parse_args()
    digest = hashlib.sha256(args.base.read_bytes()).hexdigest()
    if digest != BASE_SHA:
        raise SystemExit('unexpected base SHA-256: ' + digest)
    if args.base.resolve() == args.out.resolve():
        raise SystemExit('refusing to overwrite the input jar')
    with zipfile.ZipFile(args.base) as base:
        assert base.testzip() is None and len(base.namelist()) == len(set(base.namelist()))
        entries = {info.filename: base.read(info.filename) for info in base.infolist()}
        updated = dict(entries)
        updated[HELPER + '.class'] = patch_recovery(entries[HELPER + '.class'])
        toml = entries['META-INF/neoforge.mods.toml'].decode('utf-8')
        needle = 'version="' + OLD_VERSION + '"'
        assert toml.count(needle) == 1 and toml.count("description='''\n") == 1
        toml = toml.replace(needle, 'version="' + NEW_VERSION + '"', 1)
        toml = toml.replace("description='''\n", "description='''\n" + CHANGELOG + '\n', 1)
        assert tomllib.loads(toml)['mods'][0]['version'] == NEW_VERSION
        updated['META-INF/neoforge.mods.toml'] = toml.encode('utf-8')
        updated['settledlands-patch-info.txt'] = (PATCHINFO + '\n' +
            entries['settledlands-patch-info.txt'].decode('utf-8')).encode('utf-8')
        changed = {n for n in entries if updated[n] != entries[n]}
        assert changed == EXPECTED_DIFF and set(updated) == set(entries), changed
        with zipfile.ZipFile(args.out, 'w') as out:
            for info in base.infolist():
                out.writestr(info, updated[info.filename])
    with zipfile.ZipFile(args.out) as z:
        assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist()))
    data = args.out.read_bytes()
    print('changed:', sorted(changed))
    print(f'{args.out}: {len(data)} bytes sha256 {hashlib.sha256(data).hexdigest()}')


if __name__ == '__main__':
    main()
