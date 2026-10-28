#!/usr/bin/env python3
"""Exercise no-queue selection and 9-emerald transactions on real JVM bytecode.

The world, network, storage and event dispatch are stand-ins, not NeoForge.
"""
import argparse
import pathlib
import subprocess
import tempfile

import jvm_noqueue243 as v
from patch_probe233 import Asm

t, r, f, p, q = v.t, v.r, v.f, v.p, v.q
E, SP, SL, L, SERVER, C, NBT, PD, PM, KEY = v.E, v.SP, v.SL, v.L, v.SERVER, v.C, v.NBT, v.PD, v.PM, v.KEY
INV, IT, ITEM, ITEMS, IE, BLOCK, LIST, TAG = v.INV, v.IT, v.ITEM, v.ITEMS, v.ITEM_ENTITY, v.BLOCK, v.LIST, v.TAG
PLAYERLIST, MIXIN = v.PLAYERLIST, t.MIXIN


def harness():
    def main(cp):
        a = Asm(); out = cp.field('java/lang/System', 'out', 'Ljava/io/PrintStream;')
        def load(i): t.local(a, 'aload', i)
        def store(i): t.local(a, 'astore', i)
        def num(n): r.const(a, n)
        def string(s): t.text(a, cp, s)
        def boolean(b): a.raw(0x04 if b else 0x03)
        def make(name, desc='()V', args=None): t.obj(a, cp, name, desc, args)
        def get(i, owner, name, desc): load(i); a.op(0xb4, cp.field(owner, name, desc))
        def put(i, owner, name, desc, emit): load(i); emit(); a.op(0xb5, cp.field(owner, name, desc))
        def value(label, typ, emit):
            a.op(0xb2, out); string(label + '='); a.op(0xb6, cp.method('java/io/PrintStream', 'print', '(Ljava/lang/String;)V'))
            a.op(0xb2, out); emit(); a.op(0xb6, cp.method('java/io/PrintStream', 'println', '(' + typ + ')V'))
        def uuid(n):
            string('00000000-0000-0000-0000-%012d' % n); a.op(0xb8, cp.method('java/util/UUID', 'fromString', '(Ljava/lang/String;)Ljava/util/UUID;'))
        def data(i): load(i); a.op(0xb6, cp.method(E, 'getPersistentData', '()L' + NBT + ';'))
        def position(i, x, y=64, z=2):
            for axis, n in zip('xyz', (x, y, z)):
                put(i, E, axis, 'D', lambda n=n: (num(n), a.raw(0x87)))
        def add_online(i):
            get(9, PLAYERLIST, 'list', 'Ljava/util/List;'); load(i)
            p.invokeinterface(a, cp, 'java/util/List', 'add', '(Ljava/lang/Object;)Z', 2); a.raw(0x57)
        def add_body(i):
            get(2, SL, 'near', 'Ljava/util/List;'); load(i)
            p.invokeinterface(a, cp, 'java/util/List', 'add', '(Ljava/lang/Object;)Z', 2); a.raw(0x57)
        def env(*, online_a=True):
            a.raw(0x01); a.op(0xb3, cp.field(PD, 'queued', 'Ljava/util/UUID;'))
            a.raw(0x01); a.op(0xb3, cp.field(PD, 'second', 'Ljava/util/UUID;'))
            a.raw(0x01); a.op(0xb3, cp.field(PD, 'pending', 'L' + t.TARGET + ';'))
            make(SERVER); store(1); make(KEY); store(10)
            make(SL, '(L' + KEY + ';L' + SERVER + ';)V', lambda: (load(10), load(1))); store(2)
            make(SL, '(L' + KEY + ';L' + SERVER + ';)V',
                 lambda: (a.op(0xb2, cp.field(PM, 'PURGATORY_LEVEL', 'L' + KEY + ';')), load(1))); store(11)
            make(PLAYERLIST); store(9); put(1, SERVER, 'players', 'L' + PLAYERLIST + ';', lambda: load(9))
            put(1, SERVER, 'world', 'L' + SL + ';', lambda: load(2)); put(1, SERVER, 'purgatory', 'L' + SL + ';', lambda: load(11))
            make(SP, '(Ljava/util/UUID;L' + SL + ';I)V', lambda: (uuid(1), load(11), num(30))); store(3)
            if online_a: add_online(3)
            body(4, 3, 3); make(BLOCK, '(III)V', lambda: (num(0), num(64), num(0))); store(5)
        def body(i, owner, x):
            make(C, '(L' + r.TYPE + ';L' + L + ';)V', lambda: (a.raw(0x01), load(2))); store(i)
            load(i); load(owner); a.op(0xb6, cp.method(C, 'setOwner', '(L' + SP + ';)V')); position(i, x)
            data(owner); string(p.DEATH_CORPSE); load(i); a.op(0xb6, cp.method(E, 'getUUID', '()Ljava/util/UUID;'))
            a.op(0xb6, cp.method(NBT, 'putUUID', '(Ljava/lang/String;Ljava/util/UUID;)V')); add_body(i)
        def player_b():
            make(SP, '(Ljava/util/UUID;L' + SL + ';I)V', lambda: (uuid(2), load(11), num(30))); store(12); add_online(12)
            body(13, 12, 2)
        def emeralds(amount, meta='payment'):
            make(IT); store(14); put(14, IT, 'count', 'I', lambda: num(amount))
            put(14, IT, 'meta', 'Ljava/lang/String;', lambda: string(meta))
            put(14, IT, 'kind', 'L' + ITEM + ';', lambda: a.op(0xb2, cp.field(ITEMS, 'EMERALD', 'L' + ITEM + ';')))
            make(IE, '(L' + IT + ';)V', lambda: load(14)); store(8)
            get(2, SL, 'altarItems', 'Ljava/util/List;'); load(8)
            p.invokeinterface(a, cp, 'java/util/List', 'add', '(Ljava/lang/Object;)Z', 2); a.raw(0x57)
        def altar(catch=False):
            load(2); load(5); a.op(0xb8, cp.method('fixture/TryAltar' if catch else PM,
                'call' if catch else 'tryReviveAtAltar', q.TRY_DESC)); a.raw(0x57)
        def direct():
            make(MIXIN); store(16); load(16); load(4); load(1); load(2)
            load(1); a.op(0xb8, cp.method(PD, 'getMaster', '(L' + SERVER + ';)L' + PD + ';'))
            load(3); load(5); a.op(0xb6, cp.method(MIXIN, 'settledlands$reviveOnlyFromQueue', q.MIX_DESC))
        def result(label, who=3, bodyidx=4):
            value(label + '.x', 'D', lambda: get(who, E, 'x', 'D'))
            value(label + '.in_purg', 'Z', lambda: (load(who), a.op(0xb6, cp.method(SP, 'serverLevel', '()L' + SL + ';')),
                a.op(0xb6, cp.method(SL, 'dimension', '()L' + KEY + ';')), a.op(0xb2, cp.field(PM, 'PURGATORY_LEVEL', 'L' + KEY + ';')),
                a.op(0xb6, cp.method('java/lang/Object', 'equals', '(Ljava/lang/Object;)Z'))))
            value(label + '.removed', 'Z', lambda: get(bodyidx, E, 'removed', 'Z'))
            value(label + '.money', 'I', lambda: (get(8, IE, 'item', 'L' + IT + ';'), a.op(0xb4, cp.field(IT, 'count', 'I'))))
            value(label + '.money_removed', 'Z', lambda: get(8, E, 'removed', 'Z'))
            value(label + '.meta', 'Ljava/lang/String;', lambda: (get(8, IE, 'item', 'L' + IT + ';'), a.op(0xb4, cp.field(IT, 'meta', 'Ljava/lang/String;'))))
        # No saved queue entry exists. A valid body still permits revival.
        env(); emeralds(12); altar(); result('valid')
        # Offline owner doesn't obstruct another online body's owner.
        env(online_a=False); player_b(); emeralds(9); altar(); result('offline_b', 12, 13)
        value('offline_a.removed', 'Z', lambda: get(4, E, 'removed', 'Z'))
        # Both online: one body per call, nine each; no FIFO state involved.
        env(); player_b(); emeralds(18); altar(); result('both_a')
        value('both_b.before', 'D', lambda: get(12, E, 'x', 'D')); altar(); result('both_b', 12, 13)
        # Earlier online player's body too far doesn't block the second.
        env(); position(4, 20); player_b(); emeralds(9); altar(); result('far_b', 12, 13)
        # No body, foreign owner, old ID, eight emeralds, and outside purgatory
        # must never charge or revive anybody.
        env(); data(3); string(p.DEATH_CORPSE); a.op(0xb6, cp.method(NBT, 'remove', '(Ljava/lang/String;)V'))
        emeralds(9); altar(); result('missing_id')
        env(); data(3); string(p.DEATH_CORPSE); a.op(0xb6, cp.method(NBT, 'remove', '(Ljava/lang/String;)V'))
        emeralds(9); direct(); result('direct_missing_id')
        env(); get(2, SL, 'near', 'Ljava/util/List;'); p.invokeinterface(a, cp, 'java/util/List', 'clear', '()V', 1)
        emeralds(9); altar(); result('unloaded')
        env(); position(4, 20); emeralds(9); altar(); result('far')
        env(); emeralds(8); altar(); result('eight')
        env(); put(3, E, 'world', 'L' + L + ';', lambda: load(2)); emeralds(9); altar(); result('outside')
        env(); make(SP, '(Ljava/util/UUID;L' + SL + ';I)V', lambda: (uuid(2), load(11), num(30))); store(12)
        load(4); load(12); a.op(0xb6, cp.method(C, 'setOwner', '(L' + SP + ';)V'))
        emeralds(9); altar(); result('foreign')
        # Direct and general use the same nine-emerald rule.
        env(); load(4); boolean(True); a.op(0xb6, cp.method(C, 'setCoffin', '(Z)V'))
        emeralds(9); direct(); result('direct')
        # Failed teleport: full original stack and components restored.
        env(); put(3, SP, 'failTeleport', 'Z', lambda: boolean(True)); emeralds(12, 'original-components')
        altar(); result('failed')
        # Throw before arrival refunds; throw after arrival charges and completes.
        env(); put(3, SP, 'throwTeleport', 'Z', lambda: boolean(True)); emeralds(12, 'before')
        altar(catch=True); result('throw_before')
        env(); put(3, SP, 'throwAfterTeleport', 'Z', lambda: boolean(True)); emeralds(12, 'after')
        altar(catch=True); result('throw_after')
        a.raw(0xb1); return a.finish()
    return f.fake_class('NoQueueAltarHarness', [], [(9, 'main', '([Ljava/lang/String;)V', 20, 20, main)])


def run(java, jar):
    classes, _ = v.build_classes(jar); classes['NoQueueAltarHarness'] = harness()
    with tempfile.TemporaryDirectory(prefix='noqueue-altar-jvm-') as td:
        root = pathlib.Path(td)
        for name, raw in classes.items():
            path = root / (name + '.class'); path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(raw)
        res = subprocess.run([str(java), '-Xshare:off', '-Xverify:all', '-cp', str(root), 'NoQueueAltarHarness'], text=True, capture_output=True)
        if res.returncode: raise RuntimeError(f'{jar}: {res.returncode}\n{res.stdout}\n{res.stderr}')
        return dict(line.split('=', 1) for line in res.stdout.splitlines() if '=' in line)


def check(got):
    expected = {
        'valid.x':'3.0', 'valid.in_purg':'false', 'valid.removed':'true', 'valid.money':'3',
        'offline_b.x':'2.0', 'offline_b.in_purg':'false', 'offline_b.removed':'true', 'offline_b.money_removed':'true',
        'offline_a.removed':'false', 'both_a.x':'3.0', 'both_a.money':'9', 'both_b.before':'0.0',
        'both_b.x':'2.0', 'both_b.money_removed':'true', 'both_b.removed':'true',
        'far_b.x':'2.0', 'far_b.removed':'true', 'far_b.money_removed':'true',
        'missing_id.in_purg':'true', 'missing_id.removed':'false', 'missing_id.money':'9',
        'direct_missing_id.in_purg':'true', 'direct_missing_id.removed':'false', 'direct_missing_id.money':'9',
        'unloaded.in_purg':'true', 'unloaded.removed':'false', 'unloaded.money':'9',
        'far.in_purg':'true', 'far.removed':'false', 'far.money':'9',
        'eight.in_purg':'true', 'eight.removed':'false', 'eight.money':'8',
        'outside.x':'0.0', 'outside.removed':'false', 'outside.money':'9',
        'foreign.in_purg':'true', 'foreign.removed':'false', 'foreign.money':'9',
        'direct.x':'3.0', 'direct.removed':'true', 'direct.money_removed':'true',
        'failed.in_purg':'true', 'failed.removed':'false', 'failed.money':'12', 'failed.meta':'original-components',
        'throw_before.in_purg':'true', 'throw_before.removed':'false', 'throw_before.money':'12', 'throw_before.meta':'before',
        'throw_after.x':'3.0', 'throw_after.removed':'true', 'throw_after.money':'3', 'throw_after.meta':'after',
    }
    bad = {k:(got.get(k), value) for k,value in expected.items() if got.get(k) != value}
    if bad: raise AssertionError(f'mismatched={bad}\nall={got}')


def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--java', required=True); ap.add_argument('--base242', required=True); ap.add_argument('--jar', required=True)
    a=ap.parse_args(); old=run(a.java,a.base242)
    assert old['valid.in_purg']=='true' and old['valid.money']=='12' and old['offline_b.in_purg']=='true', old
    print('242 negative control: no FIFO entry / offline first owner blocks players')
    check(run(a.java,a.jar))
    print('243 JVM: no FIFO, multiple players, absent/offline/foreign/distant bodies, 9 emeralds and rollback OK')
    print('Synthetic game APIs; Minecraft/NeoForge not run.')


if __name__=='__main__': main()
