#!/usr/bin/env python3
"""Execute the original GuideNpc.mobInteract branch on a real JVM.

Minecraft, event dispatch, the NPC constructor and the final dialogue side
 effect are synthetic. GuideNpc.mobInteract, GuideCorpseNpc.mobInteract, both
NeoForge-handler bodies and 244's dialogue guard are actual release bytecode.
"""
import argparse
import pathlib
import subprocess
import tempfile

import jvm_corpse_click244 as b
from patch_probe233 import Asm

p, r, t, f = b.p, b.r, b.t, b.f
G, C, E, P, SP, L, SL, SERVER, IT, HAND = b.G, b.C, b.E, b.P, b.SP, b.L, b.SL, b.SERVER, b.IT, b.HAND
DIALOGUE, RESULT = b.DIALOGUE, b.RESULT
PATH = 'net/minecraft/world/entity/PathfinderMob'
PROFILE = 'dev/settledlands/npc/NpcProfile'
PHRASE = 'dev/settledlands/npc/GuidePhrase'
TOME = 'dev/settledlands/npc/GuideTomeItem'


def real_guide(jar):
    raw = r.subset(jar, G, {'mobInteract'}, fields={'profile'}, expose={'mobInteract'})
    def ctor(k):
        return (b'\x2a\x2b\x2c' + t.op(0xb7, k.add_methodref(PATH, '<init>', '(L' + r.TYPE + ';L' + L + ';)V')) +
                b'\x2a' + t.op(0xbb, k.add_class(PROFILE)) + b'\x59' +
                t.op(0xb7, k.add_methodref(PROFILE, '<init>', '()V')) +
                t.op(0xb5, k.add_fieldref(G, 'profile', 'L' + PROFILE + ';')) + b'\xb1')
    return t.rewrite(raw, add_methods=[
        (1, '<init>', '(L' + r.TYPE + ';L' + L + ';)V', 3, 3, ctor),
        (1, 'isEscorting', '()Z', 1, 1, lambda k: b'\x03\xac'),
        (1, 'sendConfigView', '(L' + SP + ';)V', 0, 2, lambda k: b'\xb1'),
        (1, 'teach', '(L' + PHRASE + ';)Z', 1, 2, lambda k: b'\x03\xac'),
        (1, 'phraseCount', '()I', 1, 1, lambda k: b'\x03\xac'),
        (1, 'getBbHeight', '()F', 1, 1, lambda k: b'\x0c\xae'),
    ])


def pathfinder():
    # The old recovery fixture supplies an executable base with all required
    # entity/leash mechanics. Rename that synthetic class to the real super.
    k = f.classedit.Klass(r.guide_class())
    matches = [entry for entry in k.cp if entry and entry['tag'] == 1 and entry['s'] == G.encode()]
    assert len(matches) == 1
    matches[0]['s'] = PATH.encode(); k._dirty = True
    return p.rebuild(k, k.methods)


def build_classes(jar):
    classes = b.build_classes(jar)
    classes[PATH] = pathfinder(); classes[G] = real_guide(jar)
    classes[PROFILE] = f.fake_class(PROFILE, [(1, 'lockInteraction', 'Z')], [
        (1, '<init>', '()V', 1, 1, f.empty_init())])
    classes[PHRASE] = f.fake_class(PHRASE, [], [(1, 'number', '()I', 1, 1, lambda cp: b'\x03\xac')])
    particle = 'net/minecraft/core/particles/ParticleOptions'
    simple = 'net/minecraft/core/particles/SimpleParticleType'
    classes[particle] = r.interface(particle)
    classes[simple] = f.fake_class(simple, [], [(1, '<init>', '()V', 1, 1, f.empty_init())], interfaces=[particle])
    classes['net/minecraft/core/particles/ParticleTypes'] = f.fake_class('net/minecraft/core/particles/ParticleTypes', [
        (9, 'ENCHANT', 'L' + simple + ';')], [])
    classes[TOME] = t.rewrite(classes[TOME], add_methods=[
        (9, 'phraseOf', '(L' + IT + ';)L' + PHRASE + ';', 1, 1, lambda k: b'\x01\xb0')])
    classes[DIALOGUE] = t.rewrite(classes[DIALOGUE], add_methods=[
        (9, 'stop', '(L' + SP + ';)V', 0, 1, lambda k: b'\xb1')], replace=[
        (p.ALIAS_LISTEN if p.GUARD in classes else 'listenToNpc', p.LISTEN_DESC, 1, 2, lambda k: b'\x03\xac')])
    classes['RealDialogueHarness'] = harness()
    return classes


def harness():
    def main(cp):
        a = Asm(); out = cp.field('java/lang/System', 'out', 'Ljava/io/PrintStream;')
        def load(i): r.p.local(a, 'aload', i)
        def store(i): r.p.local(a, 'astore', i)
        def make(name, desc='()V', args=None): r.obj(a, cp, name, desc, args)
        def text(x): r.text(a, cp, x)
        def num(n): r.const(a, n)
        def put(i, owner, field, desc, v): load(i); v(); a.op(0xb5, cp.field(owner, field, desc))
        def val(label, typ, v):
            a.op(0xb2, out); text(label + '='); a.op(0xb6, cp.method('java/io/PrintStream', 'print', '(Ljava/lang/String;)V'))
            a.op(0xb2, out); v(); a.op(0xb6, cp.method('java/io/PrintStream', 'println', '(' + typ + ')V'))
        def uuid(): text('00000000-0000-0000-0000-000000000001'); a.op(0xb8, cp.method('java/util/UUID', 'fromString', '(Ljava/lang/String;)Ljava/util/UUID;'))
        def held(i, tool=None):
            make(IT); store(9); put(9, IT, 'count', 'I', lambda: num(1 if tool else 0))
            if tool == 'lead': put(9, IT, 'kind', 'L' + b.ITEM + ';', lambda: a.op(0xb2, cp.field(b.ITEMS, 'LEAD', 'L' + b.ITEM + ';')))
            elif tool: put(9, IT, 'kind', 'L' + b.ITEM + ';', lambda: make(tool))
            put(i, P, 'held', 'L' + IT + ';', lambda: load(9))
            make(IT); store(9); put(i, P, 'offhand', 'L' + IT + ';', lambda: load(9))
        def env(tool):
            a.raw(0x03); a.op(0xb3, cp.field(DIALOGUE, 'opens', 'I'))
            make(SERVER); store(1); make(b.KEY); store(2)
            make(SL, '(L' + b.KEY + ';L' + SERVER + ';)V', lambda: (load(2), load(1))); store(3)
            make(SP, '(Ljava/util/UUID;L' + SL + ';I)V', lambda: (uuid(), load(3), num(30))); store(4)
            make(C, '(L' + r.TYPE + ';L' + L + ';)V', lambda: (a.raw(0x01), load(3))); store(5)
            load(5); load(4); a.op(0xb6, cp.method(C, 'setOwner', '(L' + SP + ';)V'))
            make(L, '(ZL' + b.KEY + ';L' + SERVER + ';)V', lambda: (a.raw(0x04), load(2), a.raw(0x01))); store(6)
            make(P, '(Ljava/util/UUID;L' + L + ';)V', lambda: (uuid(), load(6))); store(7)
            make(C, '(L' + r.TYPE + ';L' + L + ';)V', lambda: (a.raw(0x01), load(6))); store(8)
            held(4, tool); held(7, tool)
        def click():
            load(7); load(8); load(4); load(5); a.op(0xb2, cp.field(HAND, 'MAIN_HAND', 'L' + HAND + ';'))
            a.op(0xb8, cp.method(b.DISPATCH, 'oneHand', '(L' + P + ';L' + C + ';L' + SP + ';L' + C + ';L' + HAND + ';)Z'))
            a.raw(0x57)
        for label, tool in [('tome', TOME), ('lead', 'lead')]:
            env(tool); click(); val(label + '.corpse_dialogues', 'I', lambda: a.op(0xb2, cp.field(DIALOGUE, 'opens', 'I')))
        env(None); make(G, '(L' + r.TYPE + ';L' + L + ';)V', lambda: (a.raw(0x01), load(3))); store(10)
        load(10); load(4); a.op(0xb2, cp.field(HAND, 'MAIN_HAND', 'L' + HAND + ';'))
        a.op(0xb6, cp.method(G, 'mobInteract', p.MOB_DESC)); a.raw(0x57)
        val('live.dialogues', 'I', lambda: a.op(0xb2, cp.field(DIALOGUE, 'opens', 'I')))
        a.raw(0xb1); return a.finish()
    return f.fake_class('RealDialogueHarness', [], [(9, 'main', '([Ljava/lang/String;)V', 12, 12, main)])


def run(java, jar):
    classes = build_classes(jar)
    with tempfile.TemporaryDirectory(prefix='real-dialogue244-jvm-') as td:
        root = pathlib.Path(td)
        for name, raw in classes.items():
            target = root / (name + '.class'); target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes(raw)
        result = subprocess.run([str(java), '-Xshare:off', '-Xverify:all', '-cp', str(root), 'RealDialogueHarness'],
                                text=True, capture_output=True)
        if result.returncode: raise RuntimeError(f'{jar}:\n{result.stdout}\n{result.stderr}')
        return dict(line.split('=', 1) for line in result.stdout.splitlines() if '=' in line)


def main():
    ap = argparse.ArgumentParser(); ap.add_argument('--java', required=True); ap.add_argument('--base243', required=True); ap.add_argument('--jar', required=True)
    args = ap.parse_args(); old = run(args.java, args.base243); new = run(args.java, args.jar)
    assert old == {'tome.corpse_dialogues':'1', 'lead.corpse_dialogues':'1', 'live.dialogues':'1'}, old
    assert new == {'tome.corpse_dialogues':'0', 'lead.corpse_dialogues':'0', 'live.dialogues':'1'}, new
    print('Real GuideNpc.mobInteract JVM: 243 opens corpse dialogue for lead/tome; 244 blocks both and preserves live NPC dialogue')
    print('Synthetic game/event APIs and dialogue side effect; Minecraft/NeoForge not run.')


if __name__ == '__main__': main()
