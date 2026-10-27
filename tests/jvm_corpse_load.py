#!/usr/bin/env python3
"""Run the *actual* readAdditionalSaveData bytecode on real JVM + minimal stubs.

No javac and no Minecraft are required. The fixture keeps the candidate class's
constant pool, fields, target method, constructor, coffin accessors and <clinit>
byte-for-byte; unrelated methods are omitted and its external dependencies are
minimal, executable stand-ins. jvm_verify.py separately verifies the complete jar.
"""
import argparse
import pathlib
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import classedit
from patch_earlytex_mixin import CP, u2, u4, attr
from patch_probe233 import Asm

CORPSE = 'dev/settledlands/npc/GuideCorpseNpc'
GUIDE = 'dev/settledlands/npc/GuideNpc'
TAG = 'net/minecraft/nbt/CompoundTag'
SED = 'net/minecraft/network/syncher/SynchedEntityData'
ACCESSOR = 'net/minecraft/network/syncher/EntityDataAccessor'
SERIALIZER = 'net/minecraft/network/syncher/EntityDataSerializer'
SERIALIZERS = 'net/minecraft/network/syncher/EntityDataSerializers'
TYPE = 'net/minecraft/world/entity/EntityType'
LEVEL = 'net/minecraft/world/level/Level'
COMPONENT = 'net/minecraft/network/chat/Component'
MUTABLE = 'net/minecraft/network/chat/MutableComponent'


def op(code, index):
    return bytes([code]) + u2(index)


def ldc(index):
    return b'\x12' + bytes([index]) if index <= 255 else b'\x13' + u2(index)


def fake_class(name, fields, methods, *, super_name='java/lang/Object', interfaces=(),
               access=0x0021, major=49):
    cp = CP()
    this_i, super_i = cp.cls(name), cp.cls(super_name)
    ifaces = [cp.cls(i) for i in interfaces]
    code_name = cp.utf8('Code')
    field_bytes = []
    for flags, fname, desc in fields:
        field_bytes.append(u2(flags) + u2(cp.utf8(fname)) + u2(cp.utf8(desc)) + u2(0))
    method_bytes = []
    for flags, mname, desc, stack, locals_, generate in methods:
        if generate is None:  # abstract interface method
            method_bytes.append(u2(flags) + u2(cp.utf8(mname)) + u2(cp.utf8(desc)) + u2(0))
        else:
            body = generate(cp)
            code_attr = u2(stack) + u2(locals_) + u4(len(body)) + body + u2(0) + u2(0)
            method_bytes.append(u2(flags) + u2(cp.utf8(mname)) + u2(cp.utf8(desc)) +
                                u2(1) + attr(code_name, code_attr))
    return (b'\xca\xfe\xba\xbe' + u2(0) + u2(major) + cp.dump() +
            u2(access) + u2(this_i) + u2(super_i) +
            u2(len(ifaces)) + b''.join(u2(i) for i in ifaces) +
            u2(len(field_bytes)) + b''.join(field_bytes) +
            u2(len(method_bytes)) + b''.join(method_bytes) + u2(0))


def empty_init(super_name='java/lang/Object'):
    return lambda cp: b'\x2a' + op(0xb7, cp.method(super_name, '<init>', '()V')) + b'\xb1'


def blank_class(name):
    return fake_class(name, [], [(1, '<init>', '()V', 1, 1, empty_init())])


def guide_class():
    fields = [(1, 'entityData', 'L' + SED + ';'), (1, 'anim', 'Ljava/lang/String;'),
              (1, 'playCount', 'I'), (1, 'readCount', 'I'), (1, 'hasName', 'Z'),
              (1, 'nameCount', 'I')]

    def ctor(cp):
        return (b'\x2a' + op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
                b'\x2a' + op(0xbb, cp.cls(SED)) + b'\x59' +
                op(0xb7, cp.method(SED, '<init>', '()V')) +
                op(0xb5, cp.field(GUIDE, 'entityData', 'L' + SED + ';')) +
                b'\x2a' + ldc(cp.string('')) +
                op(0xb5, cp.field(GUIDE, 'anim', 'Ljava/lang/String;')) + b'\xb1')

    def inc(cp, field):
        f = cp.field(GUIDE, field, 'I')
        return b'\x2a\x59' + op(0xb4, f) + b'\x04\x60' + op(0xb5, f)

    def read(cp):
        return inc(cp, 'readCount') + b'\xb1'

    def play(cp):
        return (b'\x2a\x2b' + op(0xb5, cp.field(GUIDE, 'anim', 'Ljava/lang/String;')) +
                inc(cp, 'playCount') + b'\xb1')

    def get_anim(cp):
        return b'\x2a' + op(0xb4, cp.field(GUIDE, 'anim', 'Ljava/lang/String;')) + b'\xb0'

    def has_name(cp):
        return b'\x2a' + op(0xb4, cp.field(GUIDE, 'hasName', 'Z')) + b'\xac'

    def set_name(cp):
        return (b'\x2a\x04' + op(0xb5, cp.field(GUIDE, 'hasName', 'Z')) +
                inc(cp, 'nameCount') + b'\xb1')

    methods = [
        (1, '<init>', '(L' + TYPE + ';L' + LEVEL + ';)V', 3, 3, ctor),
        (1, 'readAdditionalSaveData', '(L' + TAG + ';)V', 3, 2, read),
        (1, 'playCustomAnimation', '(Ljava/lang/String;Z)V', 3, 3, play),
        (1, 'getCustomAnimName', '()Ljava/lang/String;', 1, 1, get_anim),
        (1, 'hasCustomName', '()Z', 1, 1, has_name),
        (1, 'setCustomName', '(L' + COMPONENT + ';)V', 3, 2, set_name),
    ]
    return fake_class(GUIDE, fields, methods)


def tag_class():
    fields = [(1, 'coffin', 'Z'), (1, 'uuidPresent', 'Z'), (1, 'owner', 'Ljava/lang/String;')]

    def ctor(cp):
        return (b'\x2a' + op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
                b'\x2a' + ldc(cp.string('')) + op(0xb5, cp.field(TAG, 'owner', 'Ljava/lang/String;')) + b'\xb1')

    def get(field, desc, ret):
        return lambda cp: b'\x2a' + op(0xb4, cp.field(TAG, field, desc)) + bytes([ret])

    methods = [
        (1, '<init>', '()V', 2, 1, ctor),
        (1, 'hasUUID', '(Ljava/lang/String;)Z', 1, 2, get('uuidPresent', 'Z', 0xac)),
        (1, 'getUUID', '(Ljava/lang/String;)Ljava/util/UUID;', 1, 2, lambda cp: b'\x01\xb0'),
        (1, 'getString', '(Ljava/lang/String;)Ljava/lang/String;', 1, 2,
         get('owner', 'Ljava/lang/String;', 0xb0)),
        (1, 'getBoolean', '(Ljava/lang/String;)Z', 1, 2, get('coffin', 'Z', 0xac)),
    ]
    return fake_class(TAG, fields, methods)


def synched_data_class():
    fields = [(1, 'value', 'Ljava/lang/Object;')]

    def define(cp):
        return (op(0xbb, cp.cls(ACCESSOR)) + b'\x59' +
                op(0xb7, cp.method(ACCESSOR, '<init>', '()V')) + b'\xb0')

    def setter(cp):
        return b'\x2a\x2c' + op(0xb5, cp.field(SED, 'value', 'Ljava/lang/Object;')) + b'\xb1'

    def getter(cp):
        return b'\x2a' + op(0xb4, cp.field(SED, 'value', 'Ljava/lang/Object;')) + b'\xb0'

    methods = [
        (1, '<init>', '()V', 1, 1, empty_init()),
        (9, 'defineId', '(Ljava/lang/Class;L' + SERIALIZER + ';)L' + ACCESSOR + ';', 2, 2, define),
        (1, 'set', '(L' + ACCESSOR + ';Ljava/lang/Object;)V', 2, 3, setter),
        (1, 'get', '(L' + ACCESSOR + ';)Ljava/lang/Object;', 1, 2, getter),
    ]
    return fake_class(SED, fields, methods)


def component_interface():
    def translatable(cp):
        return (op(0xbb, cp.cls(MUTABLE)) + b'\x59' +
                op(0xb7, cp.method(MUTABLE, '<init>', '()V')) + b'\xb0')
    return fake_class(COMPONENT, [], [
        (9, 'translatable', '(Ljava/lang/String;[Ljava/lang/Object;)L' + MUTABLE + ';', 2, 2, translatable)
    ], access=0x0601, major=52)


def mutable_component():
    return fake_class(MUTABLE, [], [(1, '<init>', '()V', 1, 1, empty_init())], interfaces=[COMPONENT])


def encode_member(member):
    attrs = b''.join(u2(a['name_idx']) + u4(len(a['body'])) + a['body'] for a in member['attrs'])
    return (u2(member['acc']) + u2(member['name_idx']) + u2(member['desc_idx']) +
            u2(len(member['attrs'])) + attrs)


def actual_corpse_subset(jar_path):
    with zipfile.ZipFile(jar_path) as z:
        raw = z.read(CORPSE + '.class')
    k = classedit.Klass(raw)
    selected_names = {'<init>', '<clinit>', 'readAdditionalSaveData', 'isCoffin', 'setCoffin'}
    selected = [m for m in k.methods if m['name'] in selected_names]
    assert {m['name'] for m in selected} == selected_names and len(selected) == len(selected_names)
    # Copy the original pool, fields and the selected method_info structures
    # exactly, including Code, StackMapTable and class attributes. Unrelated
    # methods are omitted; BootstrapMethods must still accompany their CP entries.
    attrs = b''.join(u2(a['name_idx']) + u4(len(a['body'])) + a['body'] for a in k.attrs)
    return (raw[:k.cp_end] + u2(k.access) + u2(k.this) + u2(k.super) +
            u2(len(k.ifaces)) + b''.join(u2(i) for i in k.ifaces) +
            u2(len(k.fields)) + b''.join(encode_member(f) for f in k.fields) +
            u2(len(selected)) + b''.join(encode_member(m) for m in selected) +
            u2(len(k.attrs)) + attrs)


def harness_class():
    def main(cp):
        a = Asm()
        corpse_init = cp.method(CORPSE, '<init>', '(L' + TYPE + ';L' + LEVEL + ';)V')
        tag_init = cp.method(TAG, '<init>', '()V')
        read = cp.method(CORPSE, 'readAdditionalSaveData', '(L' + TAG + ';)V')
        out = cp.field('java/lang/System', 'out', 'Ljava/io/PrintStream;')
        print_s = cp.method('java/io/PrintStream', 'print', '(Ljava/lang/String;)V')
        println_s = cp.method('java/io/PrintStream', 'println', '(Ljava/lang/String;)V')
        println_i = cp.method('java/io/PrintStream', 'println', '(I)V')

        def print_value(label, field, desc, println):
            a.op(0xb2, out); a.raw(ldc(cp.string(label + '='))); a.op(0xb6, print_s)
            a.op(0xb2, out); a.raw(0x2b); a.op(0xb4, cp.field(GUIDE, field, desc)); a.op(0xb6, println)

        def scenario(label, *, coffin=False, anim='default', owner='', uuid=False, has_name=False):
            a.op(0xbb, cp.cls(CORPSE)); a.raw(0x59, 0x01, 0x01); a.op(0xb7, corpse_init); a.raw(0x4c)
            a.op(0xbb, cp.cls(TAG)); a.raw(0x59); a.op(0xb7, tag_init); a.raw(0x4d)
            if coffin:
                a.raw(0x2c, 0x04); a.op(0xb5, cp.field(TAG, 'coffin', 'Z'))
            if uuid:
                a.raw(0x2c, 0x04); a.op(0xb5, cp.field(TAG, 'uuidPresent', 'Z'))
            if owner:
                a.raw(0x2c); a.raw(ldc(cp.string(owner))); a.op(0xb5, cp.field(TAG, 'owner', 'Ljava/lang/String;'))
            if anim != 'default':
                a.raw(0x2b)
                a.raw(0x01 if anim is None else ldc(cp.string(anim)))
                a.op(0xb5, cp.field(GUIDE, 'anim', 'Ljava/lang/String;'))
            if has_name:
                a.raw(0x2b, 0x04); a.op(0xb5, cp.field(GUIDE, 'hasName', 'Z'))
            a.raw(0x2b, 0x2c); a.op(0xb6, read)
            print_value(label + '.anim', 'anim', 'Ljava/lang/String;', println_s)
            print_value(label + '.plays', 'playCount', 'I', println_i)
            print_value(label + '.reads', 'readCount', 'I', println_i)
            print_value(label + '.names', 'nameCount', 'I', println_i)

        scenario('body')
        scenario('coffin', coffin=True)
        scenario('existing', anim='different_animation')
        scenario('null', anim=None)
        scenario('owner', owner='Owner', uuid=True)
        scenario('named', owner='Owner', has_name=True)
        scenario('coffin_existing', coffin=True, anim='coffin_animation')
        a.raw(0xb1)
        return a.finish()
    return fake_class('CorpseLoadHarness', [], [
        (9, 'main', '([Ljava/lang/String;)V', 6, 3, main)
    ])


def create_fixtures(folder, jar_path):
    classes = {CORPSE: actual_corpse_subset(jar_path), GUIDE: guide_class(), TAG: tag_class(),
               SED: synched_data_class(), ACCESSOR: blank_class(ACCESSOR),
               SERIALIZER: blank_class(SERIALIZER), TYPE: blank_class(TYPE), LEVEL: blank_class(LEVEL),
               SERIALIZERS: fake_class(SERIALIZERS, [(9, 'BOOLEAN', 'L' + SERIALIZER + ';')], []),
               COMPONENT: component_interface(), MUTABLE: mutable_component(),
               'CorpseLoadHarness': harness_class()}
    for name, data in classes.items():
        path = folder / (name + '.class')
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)


def run_fixture(java, jar_path):
    with tempfile.TemporaryDirectory(prefix='corpse-load-jvm-') as temp:
        folder = pathlib.Path(temp)
        create_fixtures(folder, jar_path)
        r = subprocess.run([java, '-Xshare:off', '-Xverify:all', '-cp', str(folder), 'CorpseLoadHarness'],
                           text=True, capture_output=True)
        if r.returncode:
            raise RuntimeError(f'JVM fixture failed for {jar_path}:\n{r.stdout}\n{r.stderr}')
        values = dict(line.split('=', 1) for line in r.stdout.splitlines() if '=' in line)
        return values


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--java', required=True)
    ap.add_argument('--base236', required=True)
    ap.add_argument('--jar', required=True)
    a = ap.parse_args()
    old = run_fixture(a.java, a.base236)
    assert old['body.anim'] == '' and old['body.plays'] == '0', old
    print('236 negative control: missing body animation reproduced')
    got = run_fixture(a.java, a.jar)
    expected = {
        'body.anim': 'die', 'body.plays': '1', 'body.reads': '1', 'body.names': '0',
        'coffin.anim': '', 'coffin.plays': '0', 'coffin.reads': '1', 'coffin.names': '0',
        'existing.anim': 'different_animation', 'existing.plays': '0', 'existing.reads': '1', 'existing.names': '0',
        'null.anim': 'die', 'null.plays': '1', 'null.reads': '1', 'null.names': '0',
        'owner.anim': 'die', 'owner.plays': '1', 'owner.reads': '1', 'owner.names': '1',
        'named.anim': 'die', 'named.plays': '1', 'named.reads': '1', 'named.names': '0',
        'coffin_existing.anim': 'coffin_animation', 'coffin_existing.plays': '0',
        'coffin_existing.reads': '1', 'coffin_existing.names': '0',
    }
    if got != expected:
        raise AssertionError(f'actual={got}\nexpected={expected}')
    print('237 JVM execution: body, coffin, existing animation, null, UUID and name branches OK')


if __name__ == '__main__':
    main()
