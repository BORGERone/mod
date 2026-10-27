#!/usr/bin/env python3
"""Execute the candidate's real dressBodyCorpse Code on HotSpot with stand-ins.

The fixture keeps its original constant pool, fields, Code and stack maps, but
trims unrelated methods and makes the private test entry point public. It tests
normal and caught-exception paths, including the build 236 illegal-access bug.
"""
import argparse
import pathlib
import subprocess
import tempfile
import zipfile

import jvm_corpse_load as f
from patch_earlytex_mixin import u2, u4
from patch_probe233 import Asm

CORPSE, GUIDE = f.CORPSE, f.GUIDE
PROFILE = 'dev/settledlands/npc/NpcProfile'
POSS = 'dev/settledlands/npc/PlayerCorpsePossession'
PENDING = POSS + '$PendingBody'
LOGGER = 'org/slf4j/Logger'
LOGUTILS = 'com/mojang/logging/LogUtils'
FAKELOG = 'test/FakeLogger'
COMPONENT, MUTABLE = f.COMPONENT, f.MUTABLE


def profile_class():
    def ctor(cp):
        return (b'\x2a' + f.op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
                b'\x2a' + f.ldc(cp.string('')) + f.op(0xb5, cp.field(PROFILE, 'texture', 'Ljava/lang/String;')) +
                b'\x2a' + f.ldc(cp.string('')) + f.op(0xb5, cp.field(PROFILE, 'name', 'Ljava/lang/String;')) + b'\xb1')
    return f.fake_class(PROFILE, [(1, 'texture', 'Ljava/lang/String;'), (1, 'name', 'Ljava/lang/String;')],
                        [(1, '<init>', '()V', 2, 1, ctor)])


def guide_class():
    # Match the original's private static DATA_TEXTURE; 236's catch-only
    # insertion must raise IllegalAccessError when exercised.
    return f.fake_class(GUIDE, [(4, 'entityData', 'L' + f.SED + ';'),
                                (10, 'DATA_TEXTURE', 'L' + f.ACCESSOR + ';')],
                        [(1, '<init>', '()V', 1, 1, f.empty_init())])


def corpse_class():
    fields = [(1, 'profile', 'L' + PROFILE + ';'), (1, 'texture', 'Ljava/lang/String;'),
              (1, 'anim', 'Ljava/lang/String;'), (1, 'failSetProfile', 'Z'),
              (1, 'failApply', 'Z'), (1, 'failHealth', 'Z'), (1, 'invulnerable', 'Z'),
              (1, 'health', 'F')]

    def ctor(cp):
        return (b'\x2a' + f.op(0xb7, cp.method(GUIDE, '<init>', '()V')) +
                b'\x2a' + f.op(0xbb, cp.cls(PROFILE)) + b'\x59' +
                f.op(0xb7, cp.method(PROFILE, '<init>', '()V')) +
                f.op(0xb5, cp.field(CORPSE, 'profile', 'L' + PROFILE + ';')) +
                b'\x2a' + f.ldc(cp.string('')) + f.op(0xb5, cp.field(CORPSE, 'texture', 'Ljava/lang/String;')) +
                b'\x2a' + f.ldc(cp.string('')) + f.op(0xb5, cp.field(CORPSE, 'anim', 'Ljava/lang/String;')) + b'\xb1')

    def throw_if(a, cp, flag):
        a.raw(0x2a); a.op(0xb4, cp.field(CORPSE, flag, 'Z')); a.jump(0x99, 'continue')
        a.op(0xbb, cp.cls('java/lang/RuntimeException')); a.raw(0x59)
        a.op(0xb7, cp.method('java/lang/RuntimeException', '<init>', '()V')); a.raw(0xbf)
        a.label('continue')

    def apply(cp):
        a = Asm()
        a.raw(0x2a, 0x2a); a.op(0xb4, cp.field(CORPSE, 'profile', 'L' + PROFILE + ';'))
        a.op(0xb4, cp.field(PROFILE, 'texture', 'Ljava/lang/String;'))
        a.op(0xb5, cp.field(CORPSE, 'texture', 'Ljava/lang/String;'))
        throw_if(a, cp, 'failApply')
        a.raw(0xb1)
        return a.finish()

    def set_profile(cp):
        a = Asm(); throw_if(a, cp, 'failSetProfile')
        a.raw(0x2a, 0x2b); a.op(0xb5, cp.field(CORPSE, 'profile', 'L' + PROFILE + ';'))
        a.raw(0x2a); a.op(0xb6, cp.method(CORPSE, 'applyProfile', '()V')); a.raw(0xb1)
        return a.finish()

    def setter(field, desc, load):
        return lambda cp: b'\x2a' + bytes([load]) + f.op(0xb5, cp.field(CORPSE, field, desc)) + b'\xb1'

    def getter(field, desc):
        return lambda cp: b'\x2a' + f.op(0xb4, cp.field(CORPSE, field, desc)) + b'\xb0'

    def health(cp):
        a = Asm(); throw_if(a, cp, 'failHealth')
        a.raw(0x2a, 0x23); a.op(0xb5, cp.field(CORPSE, 'health', 'F')); a.raw(0xb1)
        return a.finish()

    def get_name(cp):
        return (f.op(0xbb, cp.cls(MUTABLE)) + b'\x59' +
                f.op(0xb7, cp.method(MUTABLE, '<init>', '()V')) + b'\xb0')

    methods = [
        (1, '<init>', '()V', 3, 1, ctor),
        (1, 'profile', '()L' + PROFILE + ';', 1, 1, getter('profile', 'L' + PROFILE + ';')),
        (1, 'texturePath', '()Ljava/lang/String;', 1, 1, getter('texture', 'Ljava/lang/String;')),
        (1, 'applyProfile', '()V', 2, 1, apply),
        (1, 'setProfile', '(L' + PROFILE + ';)V', 2, 2, set_profile),
        (1, 'playCustomAnimation', '(Ljava/lang/String;Z)V', 2, 3, setter('anim', 'Ljava/lang/String;', 0x2b)),
        (1, 'setInvulnerable', '(Z)V', 2, 2, setter('invulnerable', 'Z', 0x1b)),
        (1, 'setHealth', '(F)V', 2, 2, health),
        (1, 'getName', '()L' + COMPONENT + ';', 2, 1, get_name),
        (1, 'setCustomName', '(L' + COMPONENT + ';)V', 0, 2, lambda cp: b'\xb1'),
        (1, 'setCustomNameVisible', '(Z)V', 0, 2, lambda cp: b'\xb1'),
        (1, 'setYRot', '(F)V', 0, 2, lambda cp: b'\xb1'),
        (1, 'setYBodyRot', '(F)V', 0, 2, lambda cp: b'\xb1'),
        (1, 'setYHeadRot', '(F)V', 0, 2, lambda cp: b'\xb1'),
    ]
    return f.fake_class(CORPSE, fields, methods, super_name=GUIDE)


def pending_class():
    fields = [(1, 'model', 'Ljava/lang/String;'), (1, 'name', 'Ljava/lang/String;')]
    def ctor(cp):
        return (b'\x2a' + f.op(0xb7, cp.method('java/lang/Object', '<init>', '()V')) +
                b'\x2a\x2b' + f.op(0xb5, cp.field(PENDING, 'model', 'Ljava/lang/String;')) +
                b'\x2a\x2c' + f.op(0xb5, cp.field(PENDING, 'name', 'Ljava/lang/String;')) + b'\xb1')
    def getter(field):
        return lambda cp: b'\x2a' + f.op(0xb4, cp.field(PENDING, field, 'Ljava/lang/String;')) + b'\xb0'
    methods = [(1, '<init>', '(Ljava/lang/String;Ljava/lang/String;)V', 2, 3, ctor),
               (1, 'model', '()Ljava/lang/String;', 1, 1, getter('model')),
               (1, 'name', '()Ljava/lang/String;', 1, 1, getter('name'))]
    for name in ('yRot', 'yBodyRot', 'yHeadRot'):
        methods.append((1, name, '()F', 1, 1, lambda cp: b'\x0b\xae'))
    return f.fake_class(PENDING, fields, methods)


def component_interface():
    def literal(cp):
        return (f.op(0xbb, cp.cls(MUTABLE)) + b'\x59' +
                f.op(0xb7, cp.method(MUTABLE, '<init>', '()V')) + b'\xb0')
    return f.fake_class(COMPONENT, [], [
        (0x0401, 'getString', '()Ljava/lang/String;', 0, 1, None),
        (9, 'literal', '(Ljava/lang/String;)L' + MUTABLE + ';', 2, 1, literal),
    ], access=0x0601, major=52)


def mutable_component():
    return f.fake_class(MUTABLE, [], [
        (1, '<init>', '()V', 1, 1, f.empty_init()),
        (1, 'getString', '()Ljava/lang/String;', 1, 1, lambda cp: f.ldc(cp.string('Fallback')) + b'\xb0'),
    ], interfaces=[COMPONENT])


def logger_interface():
    return f.fake_class(LOGGER, [], [
        (0x0401, 'info', '(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V', 0, 4, None),
    ], access=0x0601, major=52)


def fake_logger():
    def info(cp):
        count = cp.field(LOGUTILS, 'count', 'I')
        return (f.op(0xb2, count) + b'\x04\x60' + f.op(0xb3, count) +
                b'\x2c' + f.op(0xc0, cp.cls('java/lang/String')) +
                f.op(0xb3, cp.field(LOGUTILS, 'lastModel', 'Ljava/lang/String;')) +
                b'\x2d' + f.op(0xc0, cp.cls('java/lang/String')) +
                f.op(0xb3, cp.field(LOGUTILS, 'lastTexture', 'Ljava/lang/String;')) + b'\xb1')
    return f.fake_class(FAKELOG, [], [
        (1, '<init>', '()V', 1, 1, f.empty_init()),
        (1, 'info', '(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V', 2, 4, info),
    ], interfaces=[LOGGER])


def logutils_class():
    def get_logger(cp):
        return (f.op(0xbb, cp.cls(FAKELOG)) + b'\x59' +
                f.op(0xb7, cp.method(FAKELOG, '<init>', '()V')) + b'\xb0')
    return f.fake_class(LOGUTILS, [(9, 'count', 'I'), (9, 'lastModel', 'Ljava/lang/String;'),
                                   (9, 'lastTexture', 'Ljava/lang/String;')],
                        [(9, 'getLogger', '()L' + LOGGER + ';', 2, 0, get_logger)])


def synched_stub():
    return f.fake_class(f.SED, [], [
        (1, '<init>', '()V', 1, 1, f.empty_init()),
        (1, 'set', '(L' + f.ACCESSOR + ';Ljava/lang/Object;)V', 0, 3, lambda cp: b'\xb1'),
    ])


def actual_possession_subset(jar_path):
    with zipfile.ZipFile(jar_path) as z:
        raw = z.read(POSS + '.class')
    k = f.classedit.Klass(raw)
    selected = [dict(m) for m in k.methods if m['name'] in {'dressBodyCorpse', '<clinit>'}]
    assert {m['name'] for m in selected} == {'dressBodyCorpse', '<clinit>'}
    for m in selected:
        if m['name'] == 'dressBodyCorpse':
            assert m['acc'] & 0x0002
            m['acc'] = (m['acc'] & ~0x0002) | 0x0001  # expose fixture only; Code unchanged
    attrs = b''.join(u2(a['name_idx']) + u4(len(a['body'])) + a['body'] for a in k.attrs)
    return (raw[:k.cp_end] + u2(k.access) + u2(k.this) + u2(k.super) +
            u2(len(k.ifaces)) + b''.join(u2(i) for i in k.ifaces) +
            u2(len(k.fields)) + b''.join(f.encode_member(x) for x in k.fields) +
            u2(len(selected)) + b''.join(f.encode_member(x) for x in selected) +
            u2(len(k.attrs)) + attrs)


def harness_class():
    def main(cp):
        a = Asm()
        out = cp.field('java/lang/System', 'out', 'Ljava/io/PrintStream;')
        print_s = cp.method('java/io/PrintStream', 'print', '(Ljava/lang/String;)V')
        println_s = cp.method('java/io/PrintStream', 'println', '(Ljava/lang/String;)V')
        println_i = cp.method('java/io/PrintStream', 'println', '(I)V')
        println_z = cp.method('java/io/PrintStream', 'println', '(Z)V')
        ctor = cp.method(CORPSE, '<init>', '()V')
        pending_ctor = cp.method(PENDING, '<init>', '(Ljava/lang/String;Ljava/lang/String;)V')
        dress = cp.method(POSS, 'dressBodyCorpse', '(L' + CORPSE + ';L' + PENDING + ';)V')

        def label(name):
            a.op(0xb2, out); a.raw(f.ldc(cp.string(name + '='))); a.op(0xb6, print_s)
            a.op(0xb2, out)

        def print_field(label_name, field, desc, println):
            label(label_name); a.raw(0x2b); a.op(0xb4, cp.field(CORPSE, field, desc)); a.op(0xb6, println)

        def scenario(name, model, npc_name, fail=None):
            a.raw(0x03); a.op(0xb3, cp.field(LOGUTILS, 'count', 'I'))
            a.op(0xbb, cp.cls(CORPSE)); a.raw(0x59); a.op(0xb7, ctor); a.raw(0x4c)
            a.op(0xbb, cp.cls(PENDING)); a.raw(0x59); a.raw(f.ldc(cp.string(model)))
            a.raw(f.ldc(cp.string(npc_name))); a.op(0xb7, pending_ctor); a.raw(0x4d)
            if fail:
                a.raw(0x2b, 0x04); a.op(0xb5, cp.field(CORPSE, fail, 'Z'))
            a.raw(0x2b, 0x2c); a.op(0xb8, dress)
            print_field(name + '.texture', 'texture', 'Ljava/lang/String;', println_s)
            label(name + '.profile'); a.raw(0x2b); a.op(0xb4, cp.field(CORPSE, 'profile', 'L' + PROFILE + ';'))
            a.op(0xb4, cp.field(PROFILE, 'texture', 'Ljava/lang/String;')); a.op(0xb6, println_s)
            print_field(name + '.anim', 'anim', 'Ljava/lang/String;', println_s)
            print_field(name + '.invulnerable', 'invulnerable', 'Z', println_z)
            label(name + '.logs'); a.op(0xb2, cp.field(LOGUTILS, 'count', 'I')); a.op(0xb6, println_i)
            label(name + '.loggedModel'); a.op(0xb2, cp.field(LOGUTILS, 'lastModel', 'Ljava/lang/String;')); a.op(0xb6, println_s)
            label(name + '.loggedTexture'); a.op(0xb2, cp.field(LOGUTILS, 'lastTexture', 'Ljava/lang/String;')); a.op(0xb6, println_s)

        scenario('ok', 'ivy_oleander.bbmodel', 'Ivy')
        scenario('set_throws', 'ivy_oleander.bbmodel', 'Ivy', 'failSetProfile')
        scenario('apply_throws', 'ivy_oleander.bbmodel', 'Ivy', 'failApply')
        scenario('empty', '', '')
        scenario('fallback_name', 'ivy_oleander.bbmodel', '')
        scenario('health_throws', 'ivy_oleander.bbmodel', 'Ivy', 'failHealth')
        a.raw(0xb1)
        return a.finish()
    return f.fake_class('CorpseDressHarness', [], [(9, 'main', '([Ljava/lang/String;)V', 7, 3, main)])


def run_fixture(java, jar_path):
    with tempfile.TemporaryDirectory(prefix='corpse-dress-jvm-') as temp:
        folder = pathlib.Path(temp)
        classes = {POSS: actual_possession_subset(jar_path), PENDING: pending_class(), GUIDE: guide_class(),
                   CORPSE: corpse_class(), PROFILE: profile_class(), COMPONENT: component_interface(),
                   MUTABLE: mutable_component(), LOGGER: logger_interface(), FAKELOG: fake_logger(),
                   LOGUTILS: logutils_class(), f.SED: synched_stub(), f.ACCESSOR: f.blank_class(f.ACCESSOR),
                   'CorpseDressHarness': harness_class()}
        for name, data in classes.items():
            path = folder / (name + '.class'); path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
        r = subprocess.run([java, '-Xshare:off', '-Xverify:all', '-cp', str(folder), 'CorpseDressHarness'],
                           text=True, capture_output=True)
        values = dict(line.split('=', 1) for line in r.stdout.splitlines() if '=' in line)
        return r, values


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--java', required=True)
    ap.add_argument('--base236', required=True)
    ap.add_argument('--jar', required=True)
    a = ap.parse_args()
    old_run, old = run_fixture(a.java, a.base236)
    assert old.get('ok.logs') == '0', (old_run.stdout, old_run.stderr)
    assert old.get('set_throws.texture') == '', (old_run.stdout, old_run.stderr)
    assert old_run.returncode != 0 and 'IllegalAccessError' in old_run.stderr, (old_run.stdout, old_run.stderr)
    print('236 negative control: skipped log, lost texture and illegal access reproduced')
    run, got = run_fixture(a.java, a.jar)
    if run.returncode:
        raise RuntimeError(f'candidate JVM fixture failed:\n{run.stdout}\n{run.stderr}')
    for name in ('ok', 'set_throws', 'apply_throws', 'fallback_name'):
        assert got[name + '.texture'] == got[name + '.profile'] == 'ivy_oleander.bbmodel', got
        assert got[name + '.anim'] == 'die' and got[name + '.invulnerable'] == 'true', got
        assert got[name + '.logs'] == '1', got
        assert got[name + '.loggedModel'] == got[name + '.loggedTexture'] == 'ivy_oleander.bbmodel', got
    assert got['empty.texture'] == got['empty.profile'] == '', got
    assert got['empty.anim'] == 'die' and got['empty.invulnerable'] == 'true' and got['empty.logs'] == '1', got
    assert got['health_throws.texture'] == 'ivy_oleander.bbmodel' and got['health_throws.anim'] == 'die', got
    assert got['health_throws.invulnerable'] == 'false' and got['health_throws.logs'] == '0', got
    print('237 JVM execution: texture, saved profile, animation, invulnerability, logging and exception paths OK')


if __name__ == '__main__':
    main()
