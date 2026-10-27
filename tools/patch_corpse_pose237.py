#!/usr/bin/env python3
"""Build 237: restore the frozen corpse animation, without a Java compiler.

The input jars are the exact, previously distributed builds 235 and 236. The
236 PlayerCorpsePossession insertion is deliberately replaced: its successful
path jumps over the inserted bytes, and the exceptional path directly accesses
private/protected GuideNpc fields. The replacement uses public profile APIs.

    python3 tools/patch_corpse_pose237.py --base <build236.jar> \
        --base235 <build235.jar> --out <build237.jar>
"""
import argparse
import hashlib
import json
import struct
import sys
import tomllib
import zipfile

sys.path.insert(0, __file__.rsplit('/', 1)[0])
import classedit
import decode
import smt
from patch_probe233 import Asm

BASE236_SHA = '4d640528b565e788450be82ec237ca950c3dd3a3e3a8646fccde12f8534ddecf'
BASE235_SHA = 'cfddca97baf6a98bb0decff64e3f44b60b2c11a0ae574ac5f9078ded0edf5b9c'
OLD_VERSION = '0.8.0-build236-corpse-tex'
NEW_VERSION = '0.8.0-build237-corpse-pose'
CORPSE = 'dev/settledlands/npc/GuideCorpseNpc'
GUIDE = 'dev/settledlands/npc/GuideNpc'
PROFILE = 'dev/settledlands/npc/NpcProfile'
LIFECYCLE = 'dev/settledlands/npc/CorpseLifecycle'
POSS = 'dev/settledlands/npc/PlayerCorpsePossession'
PENDING = POSS + '$PendingBody'
MIXIN_NAME = 'CorpseRenderProbeMixin'
EXPECTED_DIFF = {
    CORPSE + '.class', LIFECYCLE + '.class', POSS + '.class',
    'settledlands.mixins.json', 'settledlands-patch-info.txt',
    'META-INF/neoforge.mods.toml',
}
CHANGELOG = (
    'Build 237: restore a missing die animation when a non-coffin corpse is loaded, '
    'and initialise the animation for newly spawned player corpses. Repair the '
    'build 236 corpse-texture insertion using public profile APIs; disable the '
    'per-frame diagnostic probe that mixed different entities and flooded logs.'
)
PATCHINFO = (
    'сборка 237: у загруженных не-гробов восстанавливается отсутствующая анимация die; '
    'у нового стандартного трупа она задаётся до спавна. Исправлена недостижимая '
    'и незаконно обращавшаяся к приватным полям вставка build 236: текстура '
    'подготавливается через публичный профиль, журнал пишется на успешном пути. '
    'Отключён клиентский зонд, смешивавший разные сущности и спамивший на каждом кадре. '
    'Модели с пустым именем отдельно требуют диагностики; имя не угадывается.'
)


def u2(value):
    return struct.pack('>H', value)


def ldc(index):
    return b'\x12' + bytes([index]) if index <= 255 else b'\x13' + u2(index)


def constant_ref(k, index):
    entry = k.cp[index]
    assert entry and entry['tag'] in (9, 10, 11), (index, entry)
    owner = k.utf8(k.cp[entry['a']]['a'])
    nt = k.cp[entry['b']]
    return owner, k.utf8(nt['a']), k.utf8(nt['b'])


def find_invoke(k, code, owner, name, desc):
    matches = []
    for pc, raw in decode.walk(code):
        if raw[0] in (0xb6, 0xb7, 0xb8, 0xb9):
            idx = struct.unpack('>H', raw[1:3])[0]
            if constant_ref(k, idx) == (owner, name, desc):
                matches.append((pc, len(raw)))
    assert len(matches) == 1, (owner, name, desc, matches)
    return matches[0]


def assert_pool_roundtrip(k, raw):
    old = k._dirty
    k._dirty = True
    pool = k._pool_bytes()
    k._dirty = old
    assert pool == raw[10:k.cp_end], 'constant-pool roundtrip mismatch'


def add_same_frame(parts, offset):
    """The added target has the unchanged initial locals and an empty stack."""
    nested = []
    found = 0
    for ni, name, body in parts['nested']:
        if name == 'StackMapTable':
            frames = smt.parse(body)
            assert all(f['offset'] != offset for f in frames)
            frames.append(dict(kind=0, offset=offset, payload=b''))
            frames.sort(key=lambda f: f['offset'])
            body = smt.build(frames)
            found += 1
        nested.append([ni, name, body])
    assert found == 1, 'expected exactly one StackMapTable'
    parts['nested'] = nested


def patch_corpse_load(raw):
    k = classedit.Klass(raw)
    assert_pool_roundtrip(k, raw)
    m = k.find_method('readAdditionalSaveData', '(Lnet/minecraft/nbt/CompoundTag;)V')
    assert m is not None
    parts = classedit.code_parts(k, m)
    # The known method has only its two arguments as locals. Its previous full
    # frame contains exactly those locals; both new branches leave an empty stack.
    assert parts['max_locals'] == 2
    frames = [smt.parse(b) for _, n, b in parts['nested'] if n == 'StackMapTable']
    assert len(frames) == 1 and [f['offset'] for f in frames[0]] == [24, 25, 85]
    pc, size = find_invoke(k, parts['code'], CORPSE, 'setCoffin', '(Z)V')
    at = pc + size
    assert at == 48
    assert at not in decode.targets(parts['code'])

    is_coffin = k.add_methodref(CORPSE, 'isCoffin', '()Z')
    get_anim = k.add_methodref(CORPSE, 'getCustomAnimName', '()Ljava/lang/String;')
    to_string = k.add_methodref('java/util/Objects', 'toString',
                                '(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;')
    is_empty = k.add_methodref('java/lang/String', 'isEmpty', '()Z')
    play = k.add_methodref(CORPSE, 'playCustomAnimation', '(Ljava/lang/String;Z)V')
    empty = k.add_string('')
    die = k.add_string('die')
    a = Asm()
    a.raw(0x2a); a.op(0xb6, is_coffin); a.jump(0x9a, 'done')  # coffin: unchanged
    a.raw(0x2a); a.op(0xb6, get_anim); a.raw(ldc(empty)); a.op(0xb8, to_string)
    a.op(0xb6, is_empty); a.jump(0x99, 'done')                  # existing animation: unchanged
    a.raw(0x2a); a.raw(ldc(die)); a.raw(0x03); a.op(0xb6, play)
    while len(a.buf) % 4:
        a.raw(0x00)  # preserve switch alignment and put the frame after padding
    a.label('done')
    ins = a.finish()
    assert len(ins) % 4 == 0
    parts = classedit.insert(parts, at, ins)
    add_same_frame(parts, at + len(ins))
    parts['max_stack'] = max(parts['max_stack'], 3)
    return k.dump(m, classedit.build_code_body(k, parts))


def patch_stock_spawn(raw):
    k = classedit.Klass(raw)
    assert_pool_roundtrip(k, raw)
    m = k.find_method('onPlayerDeath', '(Lnet/neoforged/neoforge/event/entity/living/LivingDeathEvent;)V')
    assert m is not None
    parts = classedit.code_parts(k, m)
    pc, size = find_invoke(k, parts['code'], CORPSE, 'setOwner',
                           '(Lnet/minecraft/server/level/ServerPlayer;)V')
    at = pc + size
    assert parts['code'][at] == 0x2c  # aload_2: the following addFreshEntity call
    assert at not in decode.targets(parts['code'])
    play = k.add_methodref(CORPSE, 'playCustomAnimation', '(Ljava/lang/String;Z)V')
    die = k.add_string('die')
    ins = b'\x2d' + ldc(die) + b'\x03\xb6' + u2(play)  # corpse local 3
    parts = classedit.insert(parts, at, ins)
    parts['max_stack'] = max(parts['max_stack'], 3)
    return k.dump(m, classedit.build_code_body(k, parts))


def patch_dress(raw235):
    k = classedit.Klass(raw235)
    assert_pool_roundtrip(k, raw235)
    m = k.find_method('dressBodyCorpse', '(L' + CORPSE + ';L' + PENDING + ';)V')
    assert m is not None
    parts = classedit.code_parts(k, m)
    assert len(parts['code']) == 154 and parts['code'][38] == 0xbb
    assert parts['exc'][1][:3] == [38, 93, 96]
    assert 38 not in decode.targets(parts['code'])

    profile = k.add_methodref(CORPSE, 'profile', '()L' + PROFILE + ';')
    model = k.add_methodref(PENDING, 'model', '()Ljava/lang/String;')
    texture = k.add_fieldref(PROFILE, 'texture', 'Ljava/lang/String;')
    apply = k.add_methodref(CORPSE, 'applyProfile', '()V')
    # Runs only in the existing non-empty-model branch. Existing profile is
    # always initialised; the public APIs avoid illegal access to DATA_TEXTURE.
    prepare = (b'\x2a\xb6' + u2(profile) + b'\x2b\xb6' + u2(model) +
               b'\xb5' + u2(texture) + b'\x2a\xb6' + u2(apply))
    old_start = parts['exc'][1][0]
    parts = classedit.insert(parts, old_start, prepare)
    # classedit deliberately keeps old branch targets AFTER insertion. The
    # original try began here, so extend it back over our preconditioning.
    parts['exc'][1][0] = old_start

    pc, size = find_invoke(k, parts['code'], CORPSE, 'setInvulnerable', '(Z)V')
    at = pc + size
    assert parts['code'][at] == 0xa7  # success-path goto; not a branch target
    assert at not in decode.targets(parts['code'])
    logger = k.add_fieldref(POSS, 'LOGGER', 'Lorg/slf4j/Logger;')
    info = k.add_interfaceref('org/slf4j/Logger', 'info',
                              '(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V')
    msg = k.add_string('[SettledLands][corpse] model={} tex={}')
    get_tex = k.add_methodref(CORPSE, 'texturePath', '()Ljava/lang/String;')
    log = (b'\xb2' + u2(logger) + ldc(msg) + b'\x2b\xb6' + u2(model) +
           b'\x2a\xb6' + u2(get_tex) + b'\xb9' + u2(info) + b'\x04\x00')
    parts = classedit.insert(parts, at, log)
    parts['max_stack'] = max(parts['max_stack'], 4)
    return k.dump(m, classedit.build_code_body(k, parts))


def checked_zip(path, expected_sha):
    data = open(path, 'rb').read()
    digest = hashlib.sha256(data).hexdigest()
    if digest != expected_sha:
        raise ValueError(f'{path}: unexpected sha256 {digest}')
    z = zipfile.ZipFile(path)
    assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist()))
    return z


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', required=True)
    ap.add_argument('--base235', required=True)
    ap.add_argument('--out', required=True)
    a = ap.parse_args()
    with checked_zip(a.base, BASE236_SHA) as base, checked_zip(a.base235, BASE235_SHA) as old:
        entries = {i.filename: base.read(i.filename) for i in base.infolist()}
        changed = dict(entries)
        changed[CORPSE + '.class'] = patch_corpse_load(entries[CORPSE + '.class'])
        changed[LIFECYCLE + '.class'] = patch_stock_spawn(entries[LIFECYCLE + '.class'])
        changed[POSS + '.class'] = patch_dress(old.read(POSS + '.class'))
        mixin = entries['settledlands.mixins.json'].decode('utf-8')
        line = '    "' + MIXIN_NAME + '",\n'
        assert mixin.count(line) == 1
        mixin = mixin.replace(line, '', 1)
        assert MIXIN_NAME not in json.loads(mixin)['client']
        changed['settledlands.mixins.json'] = mixin.encode('utf-8')
        toml = entries['META-INF/neoforge.mods.toml'].decode('utf-8')
        needle = 'version="' + OLD_VERSION + '"'
        assert toml.count(needle) == 1
        toml = toml.replace(needle, 'version="' + NEW_VERSION + '"', 1)
        assert toml.count("description='''\n") == 1
        toml = toml.replace("description='''\n", "description='''\n" + CHANGELOG + '\n', 1)
        assert tomllib.loads(toml)['mods'][0]['version'] == NEW_VERSION
        changed['META-INF/neoforge.mods.toml'] = toml.encode('utf-8')
        changed['settledlands-patch-info.txt'] = (
            PATCHINFO + '\n' + entries['settledlands-patch-info.txt'].decode('utf-8')
        ).encode('utf-8')
        actual_diff = {n for n in entries if entries[n] != changed[n]}
        assert actual_diff == EXPECTED_DIFF, (actual_diff, EXPECTED_DIFF)
        with zipfile.ZipFile(a.out, 'w') as out:
            for info in base.infolist():
                out.writestr(info, changed[info.filename])
    with zipfile.ZipFile(a.out) as out:
        assert out.testzip() is None
        print('entries:', len(out.namelist()), '| changed:', ', '.join(sorted(actual_diff)))
    data = open(a.out, 'rb').read()
    print(f'{a.out}: {len(data)} bytes sha256 {hashlib.sha256(data).hexdigest()}')


if __name__ == '__main__':
    main()
