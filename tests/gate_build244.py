#!/usr/bin/env python3
"""Fail-closed build 244 gate, including the full historical HANDOFF §8 gate.

Java 21 runs actual mod bytecode with synthetic Minecraft/NeoForge APIs. This is
not Minecraft, real event-bus/mixin startup, a game server, or an in-game test.
"""
import argparse
import hashlib
import json
import pathlib
import struct
import sys
import tempfile
import tomllib
import zipfile

import gate_build237 as g237
import gate_build238 as g238
import gate_build242 as g242

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import classedit
import decode
import patch_corpse_click244 as p

PY = sys.executable
EXPECTED_SHA = 'd9e558d17ba4ff123e6588d7048a5b0cdfbf359eac092101553da54b1eaae2c8'
EXPECTED_SIZE = 1559202
TARGETS = [
    p.IVY + '#onInteractCorpse(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent$EntityInteract)',
    p.IVY + '#onInteractSpecificCorpse(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent$EntityInteractSpecific)',
    p.IVY + '#' + p.EDIT_BRIDGE + '(net.minecraft.world.item.ItemStack)',
    p.REC + '#tryClaim(net.minecraft.world.entity.player.Player,net.minecraft.world.entity.Entity)',
    p.REC + '#' + p.ALIAS_CLAIM + '(net.minecraft.world.entity.player.Player,net.minecraft.world.entity.Entity)',
    p.C + '#mobInteract(net.minecraft.world.entity.player.Player,net.minecraft.world.InteractionHand)',
    p.C + '#' + p.ALIAS_MOB + '(net.minecraft.world.entity.player.Player,net.minecraft.world.InteractionHand)',
    p.DIALOGUE + '#start(net.minecraft.world.entity.player.Player,dev.settledlands.npc.GuideNpc)',
    p.DIALOGUE + '#listenToNpc(net.minecraft.server.level.ServerPlayer,dev.settledlands.npc.GuideNpc)',
    p.DIALOGUE + '#' + p.ALIAS_START + '(net.minecraft.world.entity.player.Player,dev.settledlands.npc.GuideNpc)',
    p.DIALOGUE + '#' + p.ALIAS_LISTEN + '(net.minecraft.server.level.ServerPlayer,dev.settledlands.npc.GuideNpc)',
    p.ALTAR + '#guardLegacyRestore(net.minecraft.server.level.ServerPlayer)',
    p.ALTAR + '#movePendingSafely(net.minecraft.server.level.ServerPlayer,dev.settledlands.npc.GuideCorpseNpc)',
    p.GUARD + '#claim(net.minecraft.world.entity.player.Player,net.minecraft.world.entity.Entity)',
    p.GUARD + '#isTool(dev.settledlands.npc.GuideCorpseNpc,net.minecraft.world.item.ItemStack)',
    p.GUARD + '#deferOffhand(dev.settledlands.npc.GuideCorpseNpc,net.minecraft.world.entity.player.Player,net.minecraft.world.InteractionHand)',
    p.GUARD + '#mayUnlink(dev.settledlands.npc.GuideCorpseNpc,net.minecraft.world.entity.player.Player,net.minecraft.world.item.ItemStack)',
    p.GUARD + '#onGeneral(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent$EntityInteract)',
    p.GUARD + '#onSpecific(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent$EntityInteractSpecific)',
    p.GUARD + '#interact(dev.settledlands.npc.GuideCorpseNpc,net.minecraft.world.entity.player.Player,net.minecraft.world.InteractionHand)',
    p.GUARD + '#dialogueStart(net.minecraft.world.entity.player.Player,dev.settledlands.npc.GuideNpc)',
    p.GUARD + '#dialogueListen(net.minecraft.server.level.ServerPlayer,dev.settledlands.npc.GuideNpc)',
    p.GUARD + '#guardLegacyRestore(net.minecraft.server.level.ServerPlayer)',
    p.GUIDE + '#mobInteract(net.minecraft.world.entity.player.Player,net.minecraft.world.InteractionHand)',
]


def static_semantics(base, jar):
    with zipfile.ZipFile(base) as b, zipfile.ZipFile(jar) as z:
        old = {name: b.read(name) for name in b.namelist()}
        new = {name: z.read(name) for name in z.namelist()}
        assert set(new) - set(old) == p.ADDED and not set(old) - set(new)
        assert {name for name in old if old[name] != new[name]} == p.CHANGED
        assert p.patch_entries(old) == new, 'candidate differs from exact allowed transform'
        g242.compare_methods(old[p.IVY+'.class'], new[p.IVY+'.class'],
            {('onInteractCorpse', '(L'+p.EVENT_G+';)V'), ('onInteractSpecificCorpse', '(L'+p.EVENT_S+';)V')},
            {(p.EDIT_BRIDGE, '(L'+p.IT+';)Z')})
        checks = [
            (p.REC, 'tryClaim', p.CLAIM_DESC, p.ALIAS_CLAIM),
            (p.C, 'mobInteract', p.MOB_DESC, p.ALIAS_MOB),
            (p.DIALOGUE, 'start', p.START_DESC, p.ALIAS_START),
            (p.DIALOGUE, 'listenToNpc', p.LISTEN_DESC, p.ALIAS_LISTEN),
        ]
        for cls in (p.REC, p.C, p.DIALOGUE):
            subset = [x for x in checks if x[0] == cls]
            g242.compare_methods(old[cls+'.class'], new[cls+'.class'],
                {(name,desc) for _,name,desc,_ in subset}, {(alias,desc) for _,_,desc,alias in subset})
            before, after = classedit.Klass(old[cls+'.class']), classedit.Klass(new[cls+'.class'])
            for _, name, desc, alias in subset:
                orig = before.find_method(name, desc); saved = after.find_method(alias, desc)
                assert saved['acc'] == ((orig['acc'] & ~0x0006) | 0x0001), alias
                assert next(x['body'] for x in orig['attrs'] if x['name']=='Code') == \
                       next(x['body'] for x in saved['attrs'] if x['name']=='Code'), alias
        g242.compare_methods(old[p.ALTAR+'.class'], new[p.ALTAR+'.class'],
                             {('guardLegacyRestore', '(L'+p.SP+';)V')})
        ivy = classedit.Klass(new[p.IVY+'.class'])
        class_ann = next(x['body'] for x in ivy.attrs if x['name']=='RuntimeVisibleAnnotations')
        assert struct.unpack_from('>H', class_ann)[0] >= 1
        assert ivy.utf8(struct.unpack_from('>H', class_ann, 2)[0]) == 'Lnet/neoforged/fml/common/EventBusSubscriber;'
        for method in ('onInteractCorpse', 'onInteractSpecificCorpse'):
            ann = next(x['body'] for x in ivy.find_method(method)['attrs'] if x['name']=='RuntimeVisibleAnnotations')
            assert struct.unpack_from('>H', ann)[0] == 1
            assert ivy.utf8(struct.unpack_from('>H', ann, 2)[0]) == 'Lnet/neoforged/bus/api/SubscribeEvent;'
        k = classedit.Klass(new[p.GUIDE+'.class']); calls = g242.refs(k, k.find_method('mobInteract', p.MOB_DESC))
        assert (p.DIALOGUE, 'start', p.START_DESC) in calls and (p.DIALOGUE, 'listenToNpc', p.LISTEN_DESC) in calls
        mod = tomllib.loads(new['META-INF/neoforge.mods.toml'].decode())['mods'][0]
        assert mod['version'] == p.NEW_VERSION and mod['description'].startswith(p.CHANGELOG)
        for lang in ('en_us','ru_ru'):
            before = json.loads(old['assets/settledlands/lang/'+lang+'.json'])
            after = json.loads(new['assets/settledlands/lang/'+lang+'.json'])
            assert set(after)-set(before) == {p.MISSING,p.UNVERIFIED,p.CONFLICT}
            assert {k:v for k,v in after.items() if k in before} == before
        for unchanged in (p.GUIDE, p.POSS, p.PD, p.PM, 'dev/settledlands/npc/PossessionSystem',
                          'dev/settledlands/npc/CorpseDeathGuard', 'dev/settledlands/revival/AltarCorpseNoQueue',
                          'dev/settledlands/mixin/AltarNpcReviveMixin'):
            key = unchanged+'.class'
            if key in old: assert old[key] == new[key], key
    print('OK exact bytecode diff, original aliased bodies, preserved event annotations, live dialogue and languages')


def call_positions(k, code, ref):
    return [pc for pc, ins in decode.walk(code) if ins[0] in (0xb6,0xb7,0xb8,0xb9) and
            g242.constant_ref(k, struct.unpack('>H',ins[1:3])[0]) == ref]


def skip_call(k, code, ref):
    hits = call_positions(k, code, ref); assert len(hits) == 1, (ref,hits)
    pc = hits[0]; assert code[pc] == 0xb8
    code[pc:pc+3] = b'\x58\x00\x00'  # drop two reference args


def skip_guard(k, code, ref, index=0):
    hits = call_positions(k, code, ref); assert len(hits) > index, (ref,hits)
    pc = hits[index] + 3; assert code[pc] in (0x99,0x9a), (ref,pc,code[pc])
    code[pc:pc+3] = b'\x57\x00\x00'  # discard boolean instead of branching


def change(raw, method, desc, action):
    k = classedit.Klass(raw); m = k.find_method(method, desc); assert m
    parts = classedit.code_parts(k, m); code = bytearray(parts['code'])
    replacement = action(k, code)
    parts['code'] = bytes(code if replacement is None else replacement)
    return k.dump(m, classedit.build_code_body(k, parts))


def mutate(jar, base, dest, mode):
    with zipfile.ZipFile(jar) as z: raw = {name:z.read(name) for name in z.namelist()}
    repl = {}
    def edit(cls, meth, desc, fn):
        key = cls+'.class'; repl[key] = change(repl.get(key,raw[key]), meth, desc, fn)
    guard = p.GUARD
    if mode == 'no-pending-transfer':
        edit(guard,'claim',p.CLAIM_DESC,lambda k,c:skip_call(k,c,(p.ALTAR,'movePendingSafely','(L'+p.SP+';L'+p.C+';)V')))
    elif mode == 'current-uuid-bypass':
        def bypass(k,c):
            assert c[40:43] == b'\x99\x00\x6b'; c[40:43] = b'\x57\x00\x00'
        edit(p.ALTAR,'movePendingSafely','(L'+p.SP+';L'+p.C+';)V',bypass)
    elif mode == 'source-uuid-bypass':
        def bypass(k,c):
            assert c[49:52] == b'\x99\x00\x62' and c[62] == 0xb6
            c[49:52] = b'\x57\x00\x00'; c[62:65] = b'\x58\x04\x00'  # null-safe equality bypass
        edit(p.ALTAR,'movePendingSafely','(L'+p.SP+';L'+p.C+';)V',bypass)
    elif mode == 'conflict-bypass':
        def bypass(k,c):
            assert c[135:138] == b'\x99\x00\x36' and c[175] == 0x2c and c[181] == 0xb1
            c[175:182] = b'\xa7'+struct.pack('>h',189-175)+b'\x00'*4
        edit(guard,'claim',p.CLAIM_DESC,bypass)
    elif mode in ('owner-bypass','dead-bypass','dimension-bypass'):
        if mode == 'owner-bypass':
            ref=('java/util/UUID','equals','(Ljava/lang/Object;)Z')
            edit(guard,'claim',p.CLAIM_DESC,lambda k,c:skip_guard(k,c,ref,0))
            edit(p.ALTAR,'movePendingSafely','(L'+p.SP+';L'+p.C+';)V',lambda k,c:skip_guard(k,c,ref,2))
            edit(p.REC,p.ALIAS_CLAIM,p.CLAIM_DESC,lambda k,c:skip_guard(k,c,ref,0))
        elif mode == 'dead-bypass':
            ref=(p.SP,'isAlive','()Z')
            for cls,method in ((guard,'claim'),(p.REC,p.ALIAS_CLAIM)):
                edit(cls,method,p.CLAIM_DESC,lambda k,c:skip_guard(k,c,ref))
        else:
            ref=('java/lang/Object','equals','(Ljava/lang/Object;)Z')
            for cls,method in ((guard,'claim'),(p.REC,p.ALIAS_CLAIM)):
                edit(cls,method,p.CLAIM_DESC,lambda k,c:skip_guard(k,c,ref))
    elif mode == 'removed-replay':
        edit(guard,'claim',p.CLAIM_DESC,lambda k,c:skip_guard(k,c,(p.E,'isRemoved','()Z')))
    elif mode in ('no-general-claim','no-specific-claim'):
        meth,evt = ('onGeneral',p.EVENT_G) if mode == 'no-general-claim' else ('onSpecific',p.EVENT_S)
        edit(guard,meth,'(L'+evt+';)V',lambda k,c:skip_call(k,c,(p.REC,'tryClaim',p.CLAIM_DESC)))
    elif mode == 'fallback-bypass':
        def fallback(k,c):
            return b'\x2a\x2b\x2c\xb6'+struct.pack('>H',k.add_methodref(p.C,p.ALIAS_MOB,p.MOB_DESC))+b'\xb0'
        edit(guard,'interact',p.INTERACT_DESC,fallback)
    elif mode in ('client-specific-consumed','client-general-pass'):
        meth,evt,old,new = ('onSpecific',p.EVENT_S,'PASS','SUCCESS') if mode=='client-specific-consumed' else ('onGeneral',p.EVENT_G,'SUCCESS','PASS')
        def swap(k,c):
            wanted = (p.RESULT,old,'L'+p.RESULT+';')
            hits = [pc for pc,i in decode.walk(c) if i[0]==0xb2 and g242.constant_ref(k,struct.unpack('>H',i[1:3])[0])==wanted]
            assert len(hits)==1; c[hits[0]+1:hits[0]+3] = struct.pack('>H',k.add_fieldref(p.RESULT,new,'L'+p.RESULT+';'))
        edit(guard,meth,'(L'+evt+';)V',swap)
    elif mode == 'offhand-priority-bypass':
        edit(guard,'deferOffhand','(L'+p.C+';L'+p.P+';L'+p.HAND+';)Z',lambda k,c:b'\x03\xac')
    elif mode == 'editing-tools-bypass':
        edit(guard,'isTool','(L'+p.C+';L'+p.IT+';)Z',lambda k,c:b'\x03\xac')
    elif mode in ('dialogue-start-bypass','dialogue-listen-bypass'):
        start = mode == 'dialogue-start-bypass'; name = p.ALIAS_START if start else p.ALIAS_LISTEN
        desc = p.START_DESC if start else p.LISTEN_DESC
        edit(guard,'dialogueStart' if start else 'dialogueListen',desc,
             lambda k,c:b'\x2a\x2b\xb8'+struct.pack('>H',k.add_methodref(p.DIALOGUE,name,desc))+(b'\xb1' if start else b'\xac'))
    elif mode == 'unsafe-legacy-restore':
        with zipfile.ZipFile(base) as z: repl[p.ALTAR+'.class'] = z.read(p.ALTAR+'.class')
    elif mode == 'missing-feedback':
        def change_key(k,c):
            old = k.add_string(p.MISSING); new = k.add_string(p.UNVERIFIED)
            hits = [pc for pc,i in decode.walk(c) if (i[0]==0x12 and i[1]==old) or (i[0]==0x13 and struct.unpack('>H',i[1:3])[0]==old)]
            assert len(hits)==1 and c[hits[0]]==0x12 and new<=255
            c[hits[0]+1] = new
        edit(guard,'claim',p.CLAIM_DESC,change_key)
    else: raise ValueError(mode)
    assert repl
    g237.alter_zip(jar,dest,replace=repl)


def main():
    ap = argparse.ArgumentParser()
    for flag in ('inputs','base237','base238','base239','base240','base241','base242','base243','jar','java'):
        ap.add_argument('--'+flag,type=pathlib.Path,required=True)
    a = ap.parse_args(); inputs=a.inputs.resolve(); java=a.java.resolve(); jar=a.jar.resolve()
    bases = {i:getattr(a,'base'+str(i)).resolve() for i in range(237,244)}
    assert hashlib.sha256(bases[243].read_bytes()).hexdigest() == p.BASE_SHA
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    assert digest == EXPECTED_SHA and jar.stat().st_size == EXPECTED_SIZE, (digest,jar.stat().st_size)
    print(f'build244 size={jar.stat().st_size} sha256={digest}',flush=True)
    g237.run('complete build243 gate incl. HANDOFF §8',[PY,ROOT/'tests/gate_build243.py','--inputs',inputs,
        *sum((['--base'+str(i),bases[i]] for i in range(237,243)),[]),'--java',java,'--jar',bases[243]],
        contains=('BUILD 243 GATE: PASS',))
    with tempfile.TemporaryDirectory(prefix='gate-build244-') as td:
        tmp = pathlib.Path(td); rebuilt=tmp/'rebuilt.jar'
        g237.run('244 deterministic build',[PY,ROOT/'tools/patch_corpse_click244.py','--base',bases[243],'--out',rebuilt],contains=(EXPECTED_SHA,))
        assert rebuilt.read_bytes() == jar.read_bytes()
        g237.run('244 structural bytecode verifier',[PY,ROOT/'tools/validate_jar.py',jar,'--base',bases[243]],contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        verify = [PY,ROOT/'tools/verify_jar.py']
        g237.run('244 CRC/TOML/languages/mixins/diff',verify+[jar,'--base',bases[243]]+
            g237.option_entries('--expect-added',sorted(p.ADDED))+g237.option_entries('--expect-changed',sorted(p.CHANGED)),
            contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        static_semantics(bases[243],jar)
        bad=tmp/'crc.jar';bad.write_bytes(g237.corrupt_crc(jar.read_bytes()))
        g237.run('244 CRC negative',verify+[bad],success=False,contains=('ПРОБЛЕМЫ: целостность',))
        bad=tmp/'lang.jar';g237.alter_zip(jar,bad,remove={'assets/settledlands/lang/ru_ru.json'})
        g237.run('244 missing language negative',verify+[bad],success=False,contains=('ПРОБЛЕМЫ: языки',))
        bad=tmp/'mixin.jar';g237.alter_zip(jar,bad,remove={'settledlands.mixins.json'})
        g237.run('244 missing mixin negative',verify+[bad],success=False,contains=('ПРОБЛЕМЫ: mixins',))
        for target in TARGETS:
            g237.run('244 JVM verify '+target.split('#')[1].split('(')[0],
                [PY,ROOT/'tools/jvm_verify.py',jar,target,'--java',java],contains=('DECLARED ','ИТОГ: OK'))
        for fixture in ('jvm_corpse_load.py','jvm_corpse_spawn.py','jvm_corpse_dress.py','jvm_corpse_realprofile.py'):
            g237.run('244 renderer regression '+fixture,[PY,ROOT/'tests'/fixture,'--java',java,
                '--base236',inputs/g237.NAMES[236],'--jar',jar],contains=('236 negative control','237 JVM execution'))
        for fixture,flag,idx,sig in [('jvm_cleanup239.py','--base238',238,'239 JVM execution'),
                                     ('jvm_npc_cult240.py','--base239',239,'240 JVM execution')]:
            g237.run('244 component regression '+fixture,[PY,ROOT/'tests'/fixture,'--java',java,flag,bases[idx],'--jar',jar],contains=(sig,))
        for fixture,sig in [('jvm_noqueue243.py','real death/join/clone/drops/altar/recovery'),
                            ('jvm_noqueue_altar243.py','no FIFO, multiple players'),
                            ('jvm_queue_disabled243.py','real persisted queue API is disabled'),
                            ('jvm_server_tick243.py','real server tick -> real level altar scan'),
                            ('jvm_login243.py','pending/login')]:
            g237.run('244 no-queue regression '+fixture,[PY,ROOT/'tests'/fixture,'--java',java,'--base242',bases[242],'--jar',jar],contains=(sig,))
        g237.run('244 cult diagnostic regression',[PY,ROOT/'tests/jvm_cultprobe243.py','--java',java,'--jar',jar],contains=('243 JVM probe:',))
        for fixture,sig in [('jvm_corpse_click244.py','complete/partial recovery'),
                            ('jvm_real_corpse_dialogue244.py','Real GuideNpc.mobInteract JVM'),
                            ('jvm_self_escape244.py','Real death/drop/clone/purgatory-exit/click JVM')]:
            g237.run('244 behavior '+fixture,[PY,ROOT/'tests'/fixture,'--java',java,'--base243',bases[243],'--jar',jar],contains=(sig,))
        mutants = [
            ('no-pending-transfer','pending.items'),('current-uuid-bypass','stale.pending'),
            ('source-uuid-bypass','unknown.pending'),('conflict-bypass','conflict.items'),
            ('owner-bypass','foreign.pending'),('dead-bypass','dead.pending'),('dimension-bypass','purg.pending'),
            ('removed-replay','removed_replay.items'),('no-general-claim','general_main.items'),
            ('no-specific-claim','specific_off.items'),('fallback-bypass','fallback.items'),
            ('client-specific-consumed','tool.unlinked'),('client-general-pass','pending.consumed'),
            ('offhand-priority-bypass','offhand.main_consumed'),('editing-tools-bypass','editor.NpcWandItem.MAIN_HAND'),
            ('dialogue-start-bypass','direct.corpse'),('dialogue-listen-bypass','direct.corpse_listen'),
            ('unsafe-legacy-restore','legacy.pending'),('missing-feedback','missing.message'),
        ]
        for mode,signal in mutants:
            bad=tmp/(mode+'.jar');mutate(jar,bases[243],bad,mode)
            g237.run('244 negative mutant '+mode,[PY,ROOT/'tests/jvm_corpse_click244.py','--java',java,
                '--base243',bases[243],'--jar',bad],success=False,contains=("'" + signal + "': (",))
    print('BUILD 244 GATE: PASS (Java 21 + synthetic APIs; Minecraft/NeoForge not run)')


if __name__ == '__main__': main()
