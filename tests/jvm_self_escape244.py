#!/usr/bin/env python3
"""Real death -> drop -> clone -> purgatory exit -> right-click bytecodes.

Uses the real 243/244 mod methods with synthetic game/event APIs. The delayed
case temporarily hides the player from the synthetic online lookup during drop
capture, so provenance exists but the list cannot yet reach the body. Not a
Minecraft/NeoForge test.
"""
import argparse
import pathlib
import subprocess
import tempfile

import jvm_noqueue243 as v
import jvm_corpse_click244 as b
from patch_probe233 import Asm

p, r, t, f = b.p, b.r, b.t, b.f
E, P, SP, C, G, L, SL, SERVER, NBT, LIST = b.E, b.P, b.SP, b.C, b.G, b.L, b.SL, b.SERVER, b.NBT, b.LIST
IT, INV, KEY, HAND, PM, POSS = b.IT, b.INV, b.KEY, b.HAND, b.PM, b.POSS
PLAYERLIST, DEATH, CLONE, RESPAWN, POST = v.PLAYERLIST, v.DEATH, v.CLONE, v.RESPAWN, v.POST


def build_classes(jar):
    classes, _ = v.build_classes(jar)
    click = b.build_classes(jar)
    # Keep the real lifecycle/clone/server-tick/altar methods from v; overlay
    # only the UI dispatch stand-ins and actual event/corpse-click entry points.
    for name in (C, G, P, HAND, b.RESULT, b.IVY, b.ES, b.EG, b.DIALOGUE, b.DISPATCH, 'fixture/DialogueCounter'):
        classes[name] = click[name]
    if p.GUARD in click: classes[p.GUARD] = click[p.GUARD]
    classes['SelfEscapeHarness'] = harness()
    return classes


def harness():
    def main(cp):
        a = Asm(); out = cp.field('java/lang/System', 'out', 'Ljava/io/PrintStream;')
        def load(i): t.local(a, 'aload', i)
        def store(i): t.local(a, 'astore', i)
        def n(i): r.const(a, i)
        def s(text): t.text(a, cp, text)
        def make(name, desc='()V', args=None): t.obj(a, cp, name, desc, args)
        def field(i, owner, name, desc): load(i); a.op(0xb4, cp.field(owner, name, desc))
        def put(i, owner, name, desc, emit): load(i); emit(); a.op(0xb5, cp.field(owner, name, desc))
        def val(label, typ, emit):
            a.op(0xb2, out); s(label + '='); a.op(0xb6, cp.method('java/io/PrintStream', 'print', '(Ljava/lang/String;)V'))
            a.op(0xb2, out); emit(); a.op(0xb6, cp.method('java/io/PrintStream', 'println', '(' + typ + ')V'))
        def uuid(): s('00000000-0000-0000-0000-000000000001'); a.op(0xb8, cp.method('java/util/UUID', 'fromString', '(Ljava/lang/String;)Ljava/util/UUID;'))
        def data(i): load(i); a.op(0xb6, cp.method(E, 'getPersistentData', '()L' + NBT + ';'))
        def has(i, key): data(i); s(key); a.op(0xb6, cp.method(NBT, 'contains', '(Ljava/lang/String;)Z'))
        def in_purg():
            load(4); a.op(0xb6, cp.method(SP, 'serverLevel', '()L' + SL + ';'))
            a.op(0xb6, cp.method(SL, 'dimension', '()L' + KEY + ';'))
            a.op(0xb2, cp.field(PM, 'PURGATORY_LEVEL', 'L' + KEY + ';'))
            a.op(0xb6, cp.method('java/lang/Object', 'equals', '(Ljava/lang/Object;)Z'))
        def held(i):
            for hand in ('held', 'offhand'):
                make(IT); store(14); put(i, P, hand, 'L' + IT + ';', lambda: load(14))
        def add_online():
            field(9, PLAYERLIST, 'list', 'Ljava/util/List;'); load(4)
            r.p.invokeinterface(a, cp, 'java/util/List', 'add', '(Ljava/lang/Object;)Z', 2); a.raw(0x57)
        def env():
            a.op(0xb8, cp.method(POSS, 'fixtureReset', '()V'))
            a.raw(0x04); a.op(0xb3, cp.field(r.POSS_SYS, 'possessed', 'Z'))
            make(SERVER); store(1); make(KEY); store(10)
            make(SL, '(L' + KEY + ';L' + SERVER + ';)V', lambda: (load(10), load(1))); store(2)
            make(SL, '(L' + KEY + ';L' + SERVER + ';)V',
                 lambda: (a.op(0xb2, cp.field(PM, 'PURGATORY_LEVEL', 'L' + KEY + ';')), load(1))); store(11)
            make(PLAYERLIST); store(9); put(1, SERVER, 'players', 'L' + PLAYERLIST + ';', lambda: load(9))
            put(1, SERVER, 'world', 'L' + SL + ';', lambda: load(2)); put(1, SERVER, 'purgatory', 'L' + SL + ';', lambda: load(11))
            make(SP, '(Ljava/util/UUID;L' + SL + ';I)V', lambda: (uuid(), load(2), n(30))); store(4); add_online()
            make(DEATH, '(L' + r.LIVING + ';)V', lambda: load(4)); store(6)
            for cls in (POSS, v.p.LIFECYCLE, PM): load(6); a.op(0xb8, cp.method(cls, 'onPlayerDeath', '(L' + DEATH + ';)V'))
            field(2, SL, 'near', 'Ljava/util/List;'); n(0)
            r.p.invokeinterface(a, cp, 'java/util/List', 'get', '(I)Ljava/lang/Object;', 2); a.op(0xc0, cp.cls(C)); store(5)
        def drop(delayed):
            if delayed:
                field(9, PLAYERLIST, 'list', 'Ljava/util/List;'); r.p.invokeinterface(a, cp, 'java/util/List', 'clear', '()V', 1)
            make('java/util/ArrayList'); store(12); make(IT); store(14)
            put(14, IT, 'count', 'I', lambda: n(5)); put(14, IT, 'id', 'Ljava/lang/String;', lambda: s('test:relic'))
            put(14, IT, 'meta', 'Ljava/lang/String;', lambda: s('enchanted'))
            make(r.ITEM_ENTITY, '(L' + IT + ';)V', lambda: load(14)); store(13)
            load(12); load(13); a.op(0xb6, cp.method('java/util/ArrayList', 'add', '(Ljava/lang/Object;)Z')); a.raw(0x57)
            make(r.DROP_EVENT, '(L' + r.LIVING + ';Ljava/util/Collection;)V', lambda: (load(4), load(12)))
            a.op(0xb8, cp.method(POSS, 'onPlayerDrops', '(L' + r.DROP_EVENT + ';)V'))
            if delayed: add_online()
        def clone_and_escape():
            make(SP, '(Ljava/util/UUID;L' + SL + ';I)V', lambda: (uuid(), load(2), n(30))); store(15)
            make(CLONE, '(L' + P + ';L' + P + ';)V', lambda: (load(4), load(15)))
            a.op(0xb8, cp.method(POSS, 'onPlayerClone', '(L' + CLONE + ';)V'))
            field(9, PLAYERLIST, 'list', 'Ljava/util/List;'); n(0); load(15)
            r.p.invokeinterface(a, cp, 'java/util/List', 'set', '(ILjava/lang/Object;)Ljava/lang/Object;', 3); a.raw(0x57)
            load(15); store(4)
            make(RESPAWN, '(L' + P + ';)V', lambda: load(4)); a.op(0xb8, cp.method(PM, 'onPlayerRespawn', '(L' + RESPAWN + ';)V'))
            # A self-exit moves to overworld; then NeoForge reports the change.
            put(4, E, 'world', 'L' + L + ';', lambda: load(2))
            make(r.DIM_EVENT, '(L' + P + ';L' + KEY + ';L' + KEY + ';)V',
                 lambda: (load(4), a.op(0xb2, cp.field(PM, 'PURGATORY_LEVEL', 'L' + KEY + ';')), load(10)))
            a.op(0xb8, cp.method(PM, 'onDimensionChange', '(L' + r.DIM_EVENT + ';)V'))
            make(POST, '(L' + SERVER + ';)V', lambda: load(1)); a.op(0xb8, cp.method(POSS, 'onServerTick', '(L' + POST + ';)V'))
        def click():
            make(L, '(ZL' + KEY + ';L' + SERVER + ';)V', lambda: (a.raw(0x04), load(10), a.raw(0x01))); store(7)
            make(P, '(Ljava/util/UUID;L' + L + ';)V', lambda: (uuid(), load(7))); store(8); held(4); held(8)
            make(C, '(L' + r.TYPE + ';L' + L + ';)V', lambda: (a.raw(0x01), load(7))); store(16)
            load(8); load(16); load(4); load(5); a.op(0xb2, cp.field(HAND, 'MAIN_HAND', 'L' + HAND + ';'))
            a.op(0xb8, cp.method(b.DISPATCH, 'oneHand', '(L' + P + ';L' + C + ';L' + SP + ';L' + C + ';L' + HAND + ';)Z'))
            a.raw(0x57)
        def collected(): field(4, SP, 'inventory', 'L' + INV + ';'); a.op(0xb4, cp.field(INV, 'collected', 'I'))
        for label, delayed in [('normal', False), ('delayed', True)]:
            env(); drop(delayed)
            val(label + '.drop_pending', 'Z', lambda: has(4, p.PLAYER_ITEMS))
            val(label + '.drop_body', 'Z', lambda: has(5, p.CORPSE_ITEMS))
            clone_and_escape(); val(label + '.after_escape_in_purg', 'Z', in_purg)
            val(label + '.clone_pending', 'Z', lambda: has(4, p.PLAYER_ITEMS)); click()
            val(label + '.items', 'I', collected); val(label + '.pending', 'Z', lambda: has(4, p.PLAYER_ITEMS))
            val(label + '.removed', 'Z', lambda: field(5, E, 'removed', 'Z'))
        a.raw(0xb1); return a.finish()
    return f.fake_class('SelfEscapeHarness', [], [(9, 'main', '([Ljava/lang/String;)V', 20, 18, main)])


def run(java, jar):
    classes = build_classes(jar)
    with tempfile.TemporaryDirectory(prefix='self-escape244-jvm-') as td:
        root = pathlib.Path(td)
        for name, raw in classes.items():
            target = root / (name + '.class'); target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(raw)
        result = subprocess.run([str(java), '-Xshare:off', '-Xverify:all', '-cp', str(root), 'SelfEscapeHarness'],
                                text=True, capture_output=True)
        if result.returncode: raise RuntimeError(f'{jar}:\n{result.stdout}\n{result.stderr}')
        return dict(line.split('=', 1) for line in result.stdout.splitlines() if '=' in line)


def main():
    ap = argparse.ArgumentParser(); ap.add_argument('--java', required=True); ap.add_argument('--base243', required=True); ap.add_argument('--jar', required=True)
    args = ap.parse_args(); old = run(args.java, args.base243); new = run(args.java, args.jar)
    expected = {'normal.drop_pending':'false', 'normal.drop_body':'true', 'normal.after_escape_in_purg':'false',
                'normal.clone_pending':'false', 'normal.items':'5', 'normal.pending':'false', 'normal.removed':'true',
                'delayed.drop_pending':'true', 'delayed.drop_body':'false', 'delayed.after_escape_in_purg':'false',
                'delayed.clone_pending':'true', 'delayed.items':'5', 'delayed.pending':'false', 'delayed.removed':'true'}
    assert {k:(old.get(k),v) for k,v in expected.items() if k.startswith('normal') and old.get(k)!=v} == {}, old
    assert old['delayed.items'] == '0' and old['delayed.pending'] == 'true' and old['delayed.removed'] == 'false', old
    bad = {k:(new.get(k),v) for k,v in expected.items() if new.get(k)!=v}
    if bad: raise AssertionError(f'{bad}\n{new}')
    print('Real death/drop/clone/purgatory-exit/click JVM: normal and delayed proven captures recovered; 243 delayed control reproduced')
    print('Synthetic online lookup, game and event APIs; Minecraft/NeoForge not run.')


if __name__ == '__main__': main()
