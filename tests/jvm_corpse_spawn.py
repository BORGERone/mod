#!/usr/bin/env python3
"""Execute the complete CorpseLifecycle class on JVM with executable stubs.

Checks that the new corpse already has its owner and die animation *before*
addFreshEntity, and that non-player / client-world / null-create paths still
return normally. The build 236 jar is a negative control.
"""
import argparse
import pathlib
import subprocess
import tempfile
import zipfile

import jvm_corpse_load as f
from patch_probe233 import Asm

ENTITY = 'net/minecraft/world/entity/Entity'
LIVING = 'net/minecraft/world/entity/LivingEntity'
PLAYER = 'net/minecraft/server/level/ServerPlayer'
SERVER = 'net/minecraft/server/level/ServerLevel'
EVENT = 'net/neoforged/neoforge/event/entity/living/LivingDeathEvent'
CONTENT = 'dev/settledlands/npc/NpcContent'
LIFECYCLE = 'dev/settledlands/npc/CorpseLifecycle'
CORPSE, TYPE, LEVEL = f.CORPSE, f.TYPE, f.LEVEL


def subclass(name, super_name):
    return f.fake_class(name, [], [(1, '<init>', '()V', 1, 1, f.empty_init(super_name))], super_name=super_name)


def player_class():
    def ctor(cp):
        return (b'\x2a' + f.op(0xb7, cp.method(LIVING, '<init>', '()V')) +
                b'\x2a\x2b' + f.op(0xb5, cp.field(PLAYER, 'world', 'L' + LEVEL + ';')) + b'\xb1')
    def world(cp):
        return b'\x2a' + f.op(0xb4, cp.field(PLAYER, 'world', 'L' + LEVEL + ';')) + b'\xb0'
    methods = [(1, '<init>', '(L' + LEVEL + ';)V', 2, 2, ctor),
               (1, 'level', '()L' + LEVEL + ';', 1, 1, world)]
    for name in ('getX', 'getY', 'getZ'):
        methods.append((1, name, '()D', 2, 1, lambda cp: b'\x0e\xaf'))
    methods.append((1, 'getYRot', '()F', 1, 1, lambda cp: b'\x0b\xae'))
    return f.fake_class(PLAYER, [(1, 'world', 'L' + LEVEL + ';')], methods, super_name=LIVING)


def event_class():
    def ctor(cp):
        return (b'\x2a' + f.op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
                b'\x2a\x2b' + f.op(0xb5, cp.field(EVENT, 'entity', 'L' + LIVING + ';')) + b'\xb1')
    def entity(cp):
        return b'\x2a' + f.op(0xb4, cp.field(EVENT, 'entity', 'L' + LIVING + ';')) + b'\xb0'
    return f.fake_class(EVENT, [(1, 'entity', 'L' + LIVING + ';')], [
        (1, '<init>', '(L' + LIVING + ';)V', 2, 2, ctor),
        (1, 'getEntity', '()L' + LIVING + ';', 1, 1, entity)])


def corpse_class():
    fields = [(1, 'anim', 'Ljava/lang/String;'), (1, 'ownerSet', 'Z'),
              (1, 'playCount', 'I'), (1, 'moves', 'I')]
    def ctor(cp):
        return (b'\x2a' + f.op(0xb7, cp.method(ENTITY, '<init>', '()V')) +
                b'\x2a' + f.ldc(cp.string('')) + f.op(0xb5, cp.field(CORPSE, 'anim', 'Ljava/lang/String;')) + b'\xb1')
    def inc(cp, field):
        ref = cp.field(CORPSE, field, 'I')
        return b'\x2a\x59' + f.op(0xb4, ref) + b'\x04\x60' + f.op(0xb5, ref)
    def move(cp):
        return inc(cp, 'moves') + b'\xb1'
    def owner(cp):
        return b'\x2a\x04' + f.op(0xb5, cp.field(CORPSE, 'ownerSet', 'Z')) + b'\xb1'
    def play(cp):
        return (b'\x2a\x2b' + f.op(0xb5, cp.field(CORPSE, 'anim', 'Ljava/lang/String;')) +
                inc(cp, 'playCount') + b'\xb1')
    return f.fake_class(CORPSE, fields, [
        (1, '<init>', '()V', 2, 1, ctor),
        (1, 'moveTo', '(DDDFF)V', 3, 9, move),
        (1, 'setOwner', '(L' + PLAYER + ';)V', 2, 2, owner),
        (1, 'playCustomAnimation', '(Ljava/lang/String;Z)V', 3, 3, play),
    ], super_name=ENTITY)


def server_class():
    fields = [(1, 'adds', 'I'), (1, 'animAtAdd', 'Ljava/lang/String;'),
              (1, 'ownerAtAdd', 'Z'), (1, 'playsAtAdd', 'I'), (1, 'movesAtAdd', 'I')]
    def add(cp):
        a = Asm()
        ref = cp.field(SERVER, 'adds', 'I')
        a.raw(0x2a, 0x59); a.op(0xb4, ref); a.raw(0x04, 0x60); a.op(0xb5, ref)
        for target, source, desc in [('animAtAdd', 'anim', 'Ljava/lang/String;'),
                                     ('ownerAtAdd', 'ownerSet', 'Z'),
                                     ('playsAtAdd', 'playCount', 'I'), ('movesAtAdd', 'moves', 'I')]:
            a.raw(0x2a, 0x2b); a.op(0xc0, cp.cls(CORPSE)); a.op(0xb4, cp.field(CORPSE, source, desc))
            a.op(0xb5, cp.field(SERVER, target, desc))
        a.raw(0x04, 0xac)
        return a.finish()
    return f.fake_class(SERVER, fields, [
        (1, '<init>', '()V', 1, 1, f.empty_init(LEVEL)),
        (1, 'addFreshEntity', '(L' + ENTITY + ';)Z', 3, 2, add)], super_name=LEVEL)


def type_class():
    def create(cp):
        a = Asm()
        a.op(0xb2, cp.field(CONTENT, 'returnNull', 'Z')); a.jump(0x99, 'create')
        a.raw(0x01, 0xb0)
        a.label('create'); a.op(0xbb, cp.cls(CORPSE)); a.raw(0x59)
        a.op(0xb7, cp.method(CORPSE, '<init>', '()V')); a.raw(0xb0)
        return a.finish()
    return f.fake_class(TYPE, [], [
        (1, '<init>', '()V', 1, 1, f.empty_init()),
        (1, 'create', '(L' + LEVEL + ';)L' + ENTITY + ';', 2, 2, create)])


def content_class():
    def guide(cp):
        return f.op(0xbb, cp.cls(TYPE)) + b'\x59' + f.op(0xb7, cp.method(TYPE, '<init>', '()V')) + b'\xb0'
    return f.fake_class(CONTENT, [(9, 'returnNull', 'Z')], [
        (9, 'guideCorpse', '()L' + TYPE + ';', 2, 0, guide)])


def harness_class():
    def main(cp):
        a = Asm()
        out = cp.field('java/lang/System', 'out', 'Ljava/io/PrintStream;')
        print_s = cp.method('java/io/PrintStream', 'print', '(Ljava/lang/String;)V')
        println_s = cp.method('java/io/PrintStream', 'println', '(Ljava/lang/String;)V')
        println_i = cp.method('java/io/PrintStream', 'println', '(I)V')
        println_z = cp.method('java/io/PrintStream', 'println', '(Z)V')
        on_death = cp.method(LIFECYCLE, 'onPlayerDeath', '(L' + EVENT + ';)V')
        event_init = cp.method(EVENT, '<init>', '(L' + LIVING + ';)V')

        def new_server():
            a.op(0xbb, cp.cls(SERVER)); a.raw(0x59); a.op(0xb7, cp.method(SERVER, '<init>', '()V')); a.raw(0x4c)
        def player(level_local=True):
            a.op(0xbb, cp.cls(PLAYER)); a.raw(0x59)
            if level_local:
                a.raw(0x2b)
            else:
                a.op(0xbb, cp.cls(LEVEL)); a.raw(0x59); a.op(0xb7, cp.method(LEVEL, '<init>', '()V'))
            a.op(0xb7, cp.method(PLAYER, '<init>', '(L' + LEVEL + ';)V'))
        def death_event_with_top_entity():
            # Entity is stored to local 2 before constructing event.
            a.raw(0x4d); a.op(0xbb, cp.cls(EVENT)); a.raw(0x59, 0x2c); a.op(0xb7, event_init); a.op(0xb8, on_death)
        def print_field(label, field, desc, printer):
            a.op(0xb2, out); a.raw(f.ldc(cp.string(label + '='))); a.op(0xb6, print_s)
            a.op(0xb2, out); a.raw(0x2b); a.op(0xb4, cp.field(SERVER, field, desc)); a.op(0xb6, printer)

        new_server(); player(); death_event_with_top_entity()
        print_field('normal.adds', 'adds', 'I', println_i)
        print_field('normal.anim', 'animAtAdd', 'Ljava/lang/String;', println_s)
        print_field('normal.owner', 'ownerAtAdd', 'Z', println_z)
        print_field('normal.plays', 'playsAtAdd', 'I', println_i)
        print_field('normal.moves', 'movesAtAdd', 'I', println_i)

        new_server(); a.op(0xbb, cp.cls(LIVING)); a.raw(0x59)
        a.op(0xb7, cp.method(LIVING, '<init>', '()V')); death_event_with_top_entity()
        print_field('nonplayer.adds', 'adds', 'I', println_i)

        new_server(); player(level_local=False); death_event_with_top_entity()
        print_field('client.adds', 'adds', 'I', println_i)

        new_server(); a.raw(0x04); a.op(0xb3, cp.field(CONTENT, 'returnNull', 'Z'))
        player(); death_event_with_top_entity(); print_field('null.adds', 'adds', 'I', println_i)
        a.raw(0x03); a.op(0xb3, cp.field(CONTENT, 'returnNull', 'Z'))
        a.raw(0xb1)
        return a.finish()
    return f.fake_class('CorpseSpawnHarness', [], [(9, 'main', '([Ljava/lang/String;)V', 8, 3, main)])


def run_fixture(java, jar_path):
    with tempfile.TemporaryDirectory(prefix='corpse-spawn-jvm-') as temp:
        folder = pathlib.Path(temp)
        with zipfile.ZipFile(jar_path) as jar:
            actual = jar.read(LIFECYCLE + '.class')
        classes = {LIFECYCLE: actual, ENTITY: f.blank_class(ENTITY), LIVING: subclass(LIVING, ENTITY),
                   PLAYER: player_class(), LEVEL: f.blank_class(LEVEL), SERVER: server_class(),
                   EVENT: event_class(), TYPE: type_class(), CONTENT: content_class(),
                   CORPSE: corpse_class(), 'CorpseSpawnHarness': harness_class()}
        for name, data in classes.items():
            path = folder / (name + '.class'); path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
        r = subprocess.run([java, '-Xshare:off', '-Xverify:all', '-cp', str(folder), 'CorpseSpawnHarness'],
                           text=True, capture_output=True)
        if r.returncode:
            raise RuntimeError(f'JVM spawn fixture failed:\n{r.stdout}\n{r.stderr}')
        return dict(line.split('=', 1) for line in r.stdout.splitlines() if '=' in line)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--java', required=True)
    ap.add_argument('--base236', required=True)
    ap.add_argument('--jar', required=True)
    args = ap.parse_args()
    old = run_fixture(args.java, args.base236)
    assert old['normal.anim'] == '' and old['normal.plays'] == '0' and old['normal.adds'] == '1', old
    print('236 negative control: stock spawn omits the die animation')
    got = run_fixture(args.java, args.jar)
    expected = {'normal.adds': '1', 'normal.anim': 'die', 'normal.owner': 'true',
                'normal.plays': '1', 'normal.moves': '1', 'nonplayer.adds': '0',
                'client.adds': '0', 'null.adds': '0'}
    assert got == expected, (got, expected)
    print('237 JVM execution: animation and owner before spawn; return branches OK')


if __name__ == '__main__':
    main()
