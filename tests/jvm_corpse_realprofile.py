#!/usr/bin/env python3
"""Exercise real dressBodyCorpse AND real GuideNpc profile methods on HotSpot.

Minecraft dependencies are executable test doubles. The fixture retains the
actual Code/StackMapTable of applyProfile, setProfile, profile, texturePath,
setPoseIndex and dressBodyCorpse. It replaces GuideNpc's constructor and static
initialization for isolation and trims unrelated methods. This tests side
 effects and caught failures; it is not a NeoForge or in-game test.
"""
import argparse
import pathlib
import subprocess
import tempfile
import zipfile

import jvm_corpse_dress as d
import jvm_corpse_load as f
from patch_earlytex_mixin import u2, u4
from patch_probe233 import Asm

GUIDE, CORPSE, PROFILE, POSS, PENDING = d.GUIDE, d.CORPSE, d.PROFILE, d.POSS, d.PENDING
PARENT = 'net/minecraft/world/entity/PathfinderMob'
HOLDER = 'net/minecraft/core/Holder'
ATTRS = 'net/minecraft/world/entity/ai/attributes/Attributes'
ATTR = 'net/minecraft/world/entity/ai/attributes/AttributeInstance'
STYLE = 'net/minecraft/network/chat/Style'
ESCORT = 'dev/settledlands/npc/NpcEscort'


def real_guide_subset(jar_path):
    with zipfile.ZipFile(jar_path) as jar:
        raw = jar.read(GUIDE + '.class')
    k = f.classedit.Klass(raw)
    names = {'profile', 'texturePath', 'applyProfile', 'setProfile', 'setPoseIndex', 'isEscorting',
             'lambda$applyProfile$0'}
    selected = [m for m in k.methods if m['name'] in names]
    assert {m['name'] for m in selected} == names

    # Generated fixture constructor uses the real declared profile field.
    ctor = (b'\x2a' + f.op(0xb7, k.add_methodref(PARENT, '<init>', '()V')) +
            b'\x2a' + f.op(0xbb, k.add_class(PROFILE)) + b'\x59' +
            f.op(0xb7, k.add_methodref(PROFILE, '<init>', '()V')) +
            f.op(0xb5, k.add_fieldref(GUIDE, 'profile', 'L' + PROFILE + ';')) + b'\xb1')
    clinit = bytearray()
    for name in ('DATA_TEXTURE', 'DATA_SCALE', 'DATA_POSE', 'DATA_AI_MODE'):
        clinit += (f.op(0xbb, k.add_class(f.ACCESSOR)) + b'\x59' +
                   f.op(0xb7, k.add_methodref(f.ACCESSOR, '<init>', '()V')) +
                   f.op(0xb3, k.add_fieldref(GUIDE, name, 'L' + f.ACCESSOR + ';')))
    clinit += b'\xb1'

    def added_method(flags, name, desc, stack, locals_, code):
        body = u2(stack) + u2(locals_) + u4(len(code)) + code + u2(0) + u2(0)
        return (u2(flags) + u2(k.add_utf8(name)) + u2(k.add_utf8(desc)) + u2(1) +
                u2(k.add_utf8('Code')) + u4(len(body)) + body)
    added = [added_method(1, '<init>', '()V', 3, 1, ctor),
             added_method(8, '<clinit>', '()V', 2, 0, bytes(clinit))]
    attrs = b''.join(u2(a['name_idx']) + u4(len(a['body'])) + a['body'] for a in k.attrs)
    return (raw[:8] + u2(len(k.cp)) + k._pool_bytes() +
            u2(k.access) + u2(k.this) + u2(k.super) + u2(len(k.ifaces)) +
            b''.join(u2(i) for i in k.ifaces) + u2(len(k.fields)) +
            b''.join(f.encode_member(x) for x in k.fields) + u2(len(selected) + len(added)) +
            b''.join(f.encode_member(x) for x in selected) + b''.join(added) + u2(len(k.attrs)) + attrs)


def profile_class():
    fields = [(1, 'texture', 'Ljava/lang/String;'), (1, 'name', 'Ljava/lang/String;'),
              (1, 'nameColor', 'I'), (1, 'scale', 'I'), (1, 'pose', 'I'),
              (1, 'nameTag', 'Z'), (1, 'aiMode', 'I')]
    def ctor(cp):
        return (b'\x2a' + f.op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
                b'\x2a' + f.ldc(cp.string('')) + f.op(0xb5, cp.field(PROFILE, 'texture', 'Ljava/lang/String;')) +
                b'\x2a' + f.ldc(cp.string('')) + f.op(0xb5, cp.field(PROFILE, 'name', 'Ljava/lang/String;')) +
                b'\x2a\x10\x64' + f.op(0xb5, cp.field(PROFILE, 'scale', 'I')) +
                b'\x2a\x04' + f.op(0xb5, cp.field(PROFILE, 'nameTag', 'Z')) +
                b'\x2a\x04' + f.op(0xb5, cp.field(PROFILE, 'nameColor', 'I')) + b'\xb1')
    def clamp(cp):
        return (b'\x1a\x1b' + f.op(0xb8, cp.method('java/lang/Math', 'max', '(II)I')) +
                b'\x1c' + f.op(0xb8, cp.method('java/lang/Math', 'min', '(II)I')) + b'\xac')
    return f.fake_class(PROFILE, fields, [(1, '<init>', '()V', 2, 1, ctor),
                                         (9, 'clamp', '(III)I', 2, 3, clamp)])


def synced_class():
    fields = [(1, 'values', 'Ljava/util/HashMap;'), (1, 'writes', 'I')]
    def ctor(cp):
        return (b'\x2a' + f.op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
                b'\x2a' + f.op(0xbb, cp.cls('java/util/HashMap')) + b'\x59' +
                f.op(0xb7, cp.method('java/util/HashMap', '<init>', '()V')) +
                f.op(0xb5, cp.field(f.SED, 'values', 'Ljava/util/HashMap;')) + b'\xb1')
    def set_value(cp):
        writes = cp.field(f.SED, 'writes', 'I')
        return (b'\x2a\x59' + f.op(0xb4, writes) + b'\x04\x60' + f.op(0xb5, writes) +
                b'\x2a' + f.op(0xb4, cp.field(f.SED, 'values', 'Ljava/util/HashMap;')) + b'\x2b\x2c' +
                f.op(0xb6, cp.method('java/util/HashMap', 'put', '(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;')) +
                b'\x57\xb1')
    def get_value(cp):
        return (b'\x2a' + f.op(0xb4, cp.field(f.SED, 'values', 'Ljava/util/HashMap;')) + b'\x2b' +
                f.op(0xb6, cp.method('java/util/HashMap', 'get', '(Ljava/lang/Object;)Ljava/lang/Object;')) +
                b'\xb0')
    return f.fake_class(f.SED, fields, [(1, '<init>', '()V', 3, 1, ctor),
                        (1, 'set', '(L' + f.ACCESSOR + ';Ljava/lang/Object;)V', 3, 3, set_value),
                        (1, 'get', '(L' + f.ACCESSOR + ';)Ljava/lang/Object;', 2, 2, get_value)])


def parent_class():
    fields = [(1, 'entityData', 'L' + f.SED + ';'), (1, 'failMobility', 'Z'), (1, 'failApply', 'Z'),
              (1, 'visible', 'Z'), (1, 'nameWrites', 'I'), (1, 'refreshes', 'I')]
    def ctor(cp):
        return (b'\x2a' + f.op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
                b'\x2a' + f.op(0xbb, cp.cls(f.SED)) + b'\x59' +
                f.op(0xb7, cp.method(f.SED, '<init>', '()V')) +
                f.op(0xb5, cp.field(PARENT, 'entityData', 'L' + f.SED + ';')) + b'\xb1')
    def inc(cp, field):
        ref = cp.field(PARENT, field, 'I')
        return b'\x2a\x59' + f.op(0xb4, ref) + b'\x04\x60' + f.op(0xb5, ref) + b'\xb1'
    def throw_or(cp, flag, tail):
        a = Asm(); a.raw(0x2a); a.op(0xb4, cp.field(PARENT, flag, 'Z')); a.jump(0x99, 'ok')
        a.op(0xbb, cp.cls('java/lang/RuntimeException')); a.raw(0x59)
        a.op(0xb7, cp.method('java/lang/RuntimeException', '<init>', '()V')); a.raw(0xbf)
        a.label('ok'); a.raw(tail); return a.finish()
    def visible(cp):
        return b'\x2a\x1b' + f.op(0xb5, cp.field(PARENT, 'visible', 'Z')) + b'\xb1'
    return f.fake_class(PARENT, fields, [
        (1, '<init>', '()V', 3, 1, ctor),
        (1, 'setCustomName', '(L' + d.COMPONENT + ';)V', 3, 2, lambda cp: inc(cp, 'nameWrites')),
        (1, 'setCustomNameVisible', '(Z)V', 2, 2, visible),
        (1, 'getAttribute', '(L' + HOLDER + ';)L' + ATTR + ';', 2, 2,
         lambda cp: throw_or(cp, 'failApply', b'\x01\xb0')),
        (1, 'refreshDimensions', '()V', 3, 1, lambda cp: inc(cp, 'refreshes')),
        (1, 'mobilitySettingsChanged', '(L' + PROFILE + ';L' + PROFILE + ';)Z', 2, 3,
         lambda cp: throw_or(cp, 'failMobility', b'\x03\xac')),
        (1, 'resetMobility', '()V', 0, 1, lambda cp: b'\xb1'),
    ])


def corpse_class():
    fields = [(1, 'anim', 'Ljava/lang/String;'), (1, 'invulnerable', 'Z'), (1, 'health', 'F')]
    def ctor(cp):
        return (b'\x2a' + f.op(0xb7, cp.method(GUIDE, '<init>', '()V')) +
                b'\x2a' + f.ldc(cp.string('')) + f.op(0xb5, cp.field(CORPSE, 'anim', 'Ljava/lang/String;')) + b'\xb1')
    def setter(field, desc, load):
        return lambda cp: b'\x2a' + bytes([load]) + f.op(0xb5, cp.field(CORPSE, field, desc)) + b'\xb1'
    def get_name(cp):
        return (f.op(0xbb, cp.cls(d.MUTABLE)) + b'\x59' +
                f.op(0xb7, cp.method(d.MUTABLE, '<init>', '()V')) + b'\xb0')
    return f.fake_class(CORPSE, fields, [
        (1, '<init>', '()V', 2, 1, ctor),
        (1, 'playCustomAnimation', '(Ljava/lang/String;Z)V', 2, 3, setter('anim', 'Ljava/lang/String;', 0x2b)),
        (1, 'setHealth', '(F)V', 2, 2, setter('health', 'F', 0x23)),
        (1, 'setInvulnerable', '(Z)V', 2, 2, setter('invulnerable', 'Z', 0x1b)),
        (1, 'getName', '()L' + d.COMPONENT + ';', 2, 1, get_name),
        (1, 'setYRot', '(F)V', 0, 2, lambda cp: b'\xb1'),
        (1, 'setYBodyRot', '(F)V', 0, 2, lambda cp: b'\xb1'),
        (1, 'setYHeadRot', '(F)V', 0, 2, lambda cp: b'\xb1'),
    ], super_name=GUIDE)


def mutable_class():
    return f.fake_class(d.MUTABLE, [], [
        (1, '<init>', '()V', 1, 1, f.empty_init()),
        (1, 'getString', '()Ljava/lang/String;', 1, 1, lambda cp: f.ldc(cp.string('Fallback')) + b'\xb0'),
        (1, 'withStyle', '(Ljava/util/function/UnaryOperator;)L' + d.MUTABLE + ';', 1, 2, lambda cp: b'\x2a\xb0'),
    ], interfaces=[d.COMPONENT])


def style_class():
    return f.fake_class(STYLE, [], [(1, '<init>', '()V', 1, 1, f.empty_init()),
                                   (1, 'withColor', '(I)L' + STYLE + ';', 1, 2, lambda cp: b'\x2a\xb0')])


def attribute_class():
    return f.fake_class(ATTR, [], [(1, '<init>', '()V', 1, 1, f.empty_init()),
                                  (1, 'setBaseValue', '(D)V', 0, 3, lambda cp: b'\xb1')])


def harness_class():
    def main(cp):
        a = Asm()
        out = cp.field('java/lang/System', 'out', 'Ljava/io/PrintStream;')
        print_s = cp.method('java/io/PrintStream', 'print', '(Ljava/lang/String;)V')
        println_s = cp.method('java/io/PrintStream', 'println', '(Ljava/lang/String;)V')
        println_i = cp.method('java/io/PrintStream', 'println', '(I)V')
        println_z = cp.method('java/io/PrintStream', 'println', '(Z)V')
        println_f = cp.method('java/io/PrintStream', 'println', '(F)V')
        def label(name):
            a.op(0xb2, out); a.raw(f.ldc(cp.string(name + '='))); a.op(0xb6, print_s); a.op(0xb2, out)
        def print_field(name, owner, field, desc, printer):
            label(name); a.raw(0x2b); a.op(0xb4, cp.field(owner, field, desc)); a.op(0xb6, printer)
        def scenario(name, fail=None):
            a.raw(0x03); a.op(0xb3, cp.field(d.LOGUTILS, 'count', 'I'))
            a.op(0xbb, cp.cls(CORPSE)); a.raw(0x59); a.op(0xb7, cp.method(CORPSE, '<init>', '()V')); a.raw(0x4c)
            a.op(0xbb, cp.cls(PENDING)); a.raw(0x59); a.raw(f.ldc(cp.string('ivy_oleander.bbmodel')))
            a.raw(f.ldc(cp.string('Ivy'))); a.op(0xb7, cp.method(PENDING, '<init>', '(Ljava/lang/String;Ljava/lang/String;)V')); a.raw(0x4d)
            if fail:
                a.raw(0x2b, 0x04); a.op(0xb5, cp.field(PARENT, fail, 'Z'))
            a.raw(0x2b, 0x2c); a.op(0xb8, cp.method(POSS, 'dressBodyCorpse', '(L' + CORPSE + ';L' + PENDING + ';)V'))
            label(name + '.texture'); a.raw(0x2b); a.op(0xb6, cp.method(GUIDE, 'texturePath', '()Ljava/lang/String;')); a.op(0xb6, println_s)
            label(name + '.profile'); a.raw(0x2b); a.op(0xb6, cp.method(GUIDE, 'profile', '()L' + PROFILE + ';'))
            a.op(0xb4, cp.field(PROFILE, 'texture', 'Ljava/lang/String;')); a.op(0xb6, println_s)
            print_field(name + '.anim', CORPSE, 'anim', 'Ljava/lang/String;', println_s)
            print_field(name + '.health', CORPSE, 'health', 'F', println_f)
            print_field(name + '.invulnerable', CORPSE, 'invulnerable', 'Z', println_z)
            print_field(name + '.visible', PARENT, 'visible', 'Z', println_z)
            print_field(name + '.refreshes', PARENT, 'refreshes', 'I', println_i)
            print_field(name + '.names', PARENT, 'nameWrites', 'I', println_i)
            label(name + '.dataWrites'); a.raw(0x2b); a.op(0xb4, cp.field(PARENT, 'entityData', 'L' + f.SED + ';'))
            a.op(0xb4, cp.field(f.SED, 'writes', 'I')); a.op(0xb6, println_i)
            label(name + '.logs'); a.op(0xb2, cp.field(d.LOGUTILS, 'count', 'I')); a.op(0xb6, println_i)
        scenario('normal')
        scenario('mobility_throws', 'failMobility')
        scenario('apply_throws', 'failApply')
        a.raw(0xb1); return a.finish()
    return f.fake_class('RealProfileHarness', [], [(9, 'main', '([Ljava/lang/String;)V', 7, 3, main)])


def run_fixture(java, jar_path):
    with tempfile.TemporaryDirectory(prefix='corpse-realprofile-jvm-') as temp:
        folder = pathlib.Path(temp)
        classes = {
            POSS: d.actual_possession_subset(jar_path), GUIDE: real_guide_subset(jar_path), CORPSE: corpse_class(),
            PARENT: parent_class(), PROFILE: profile_class(), PENDING: d.pending_class(),
            f.SED: synced_class(), f.ACCESSOR: f.blank_class(f.ACCESSOR),
            d.COMPONENT: d.component_interface(), d.MUTABLE: mutable_class(), STYLE: style_class(),
            HOLDER: f.blank_class(HOLDER), ATTR: attribute_class(),
            ATTRS: f.fake_class(ATTRS, [(9, 'SCALE', 'L' + HOLDER + ';')], []), ESCORT: f.blank_class(ESCORT),
            d.LOGGER: d.logger_interface(), d.FAKELOG: d.fake_logger(), d.LOGUTILS: d.logutils_class(),
            'RealProfileHarness': harness_class(),
        }
        for name, data in classes.items():
            path = folder / (name + '.class'); path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
        r = subprocess.run([java, '-Xshare:off', '-Xverify:all', '-cp', str(folder), 'RealProfileHarness'],
                           text=True, capture_output=True)
        if r.returncode:
            raise RuntimeError(f'JVM real-profile fixture failed:\n{r.stdout}\n{r.stderr}')
        return dict(line.split('=', 1) for line in r.stdout.splitlines() if '=' in line)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--java', required=True)
    ap.add_argument('--base236', required=True)
    ap.add_argument('--jar', required=True)
    args = ap.parse_args()
    old = run_fixture(args.java, args.base236)
    assert old['normal.dataWrites'] == '10' and old['normal.refreshes'] == '2', old
    assert old['mobility_throws.texture'] == 'null' and old['normal.logs'] == '0', old
    print('236 negative control with real profile methods: failed setProfile leaves texture unset')
    got = run_fixture(args.java, args.jar)
    for case in ('normal', 'mobility_throws', 'apply_throws'):
        assert got[case + '.texture'] == got[case + '.profile'] == 'ivy_oleander.bbmodel', got
        assert got[case + '.anim'] == 'die' and got[case + '.health'] == '1.0', got
        assert got[case + '.invulnerable'] == got[case + '.visible'] == 'true', got
        assert got[case + '.logs'] == '1', got
    assert (got['normal.dataWrites'], got['normal.refreshes'], got['normal.names']) == ('15', '3', '4'), got
    assert (got['mobility_throws.dataWrites'], got['mobility_throws.refreshes'], got['mobility_throws.names']) == ('5', '1', '2'), got
    assert (got['apply_throws.dataWrites'], got['apply_throws.refreshes'], got['apply_throws.names']) == ('2', '0', '2'), got
    print('237 JVM execution with real applyProfile/setProfile: normal and injected exceptions OK; extra side effects counted')


if __name__ == '__main__':
    main()
