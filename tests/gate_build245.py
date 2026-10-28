#!/usr/bin/env python3
"""Fail-closed build 245 gate, including build 244 and all HANDOFF §8 checks.

A real Java 21 JVM verifies and executes mod bytecode with synthetic APIs. It is
NOT a Minecraft/NeoForge runtime, actual mixin application, or an in-game test.
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
import gate_build242 as g242
import gate_build244 as g244

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import classedit
import decode
import patch_body_wear245 as p

PY = sys.executable
EXPECTED_SHA = 'b6825213caa98096881f02659f81f719cb5f86588c22c2899ccca09f139aca86'
EXPECTED_SIZE = 1563087
TARGETS = [
    p.GUARD+'#eligible(net.minecraft.server.level.ServerPlayer)',
    p.GUARD+'#selectLevel(net.minecraft.server.level.ServerPlayer)',
    p.NQ+'#transact(net.minecraft.server.level.ServerLevel,net.minecraft.core.BlockPos,java.util.List,net.minecraft.server.level.ServerPlayer,dev.settledlands.npc.GuideCorpseNpc)',
    p.POSS+'#onPlayerClone(net.neoforged.neoforge.event.entity.player.PlayerEvent$Clone)',
    p.POSS+'#repossess(net.minecraft.server.level.ServerPlayer,java.lang.String,java.lang.String)',
    p.POSS+'#'+p.CANCEL_BRIDGE+'(java.util.UUID)',
    p.PS+'#possess(net.minecraft.server.level.ServerPlayer,dev.settledlands.npc.GuideNpc)',
    p.PS+'#clearPossessed(net.minecraft.server.level.ServerPlayer)',
    p.PS+'#onPlayerLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent)',
    p.WEAR+'#canSpawn(net.minecraft.server.level.ServerPlayer)',
    p.WEAR+'#cancelWatches(java.util.List,java.util.UUID)',
    p.WEAR+'#recordPaid(net.minecraft.server.level.ServerPlayer,dev.settledlands.npc.GuideCorpseNpc)',
    p.WEAR+'#resume(net.minecraft.server.level.ServerPlayer)',
    p.WEAR+'#reset(net.minecraft.server.level.ServerPlayer)',
    p.WEAR+'#deactivate(net.minecraft.server.level.ServerPlayer)',
    p.WEAR+'#clonePlayer(net.neoforged.neoforge.event.entity.player.PlayerEvent$Clone)',
    p.WEAR+'#login(net.neoforged.neoforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent)',
    p.WEAR+'#sync(net.minecraft.server.level.ServerPlayer)',
    p.WEAR+'#attributeDirty(java.lang.Object,net.minecraft.core.Holder)',
    p.MIXIN+'#settledlands$wearAttributeDirty(net.minecraft.core.Holder,org.spongepowered.asm.mixin.injection.callback.CallbackInfo)',
]


def read_annotations(k, body):
    cur = classedit.Cursor(body)
    def annotation():
        typename = k.utf8(cur.u2())
        pairs = {}
        for _ in range(cur.u2()):
            name = k.utf8(cur.u2()); pairs[name] = value()
        return (typename, pairs)
    def value():
        tag = chr(cur.u1())
        if tag in ('s', 'c'): return (tag, k.utf8(cur.u2()))
        if tag == '@': return (tag, annotation())
        if tag == '[': return (tag, [value() for _ in range(cur.u2())])
        raise AssertionError('unexpected annotation tag: '+tag)
    result = [annotation() for _ in range(cur.u2())]
    assert cur.p == len(body)
    return result


def static_semantics(base, jar):
    with zipfile.ZipFile(base) as b, zipfile.ZipFile(jar) as z:
        old = {name:b.read(name) for name in b.namelist()}; new = {name:z.read(name) for name in z.namelist()}
    assert set(new)-set(old) == p.ADDED and not set(old)-set(new)
    assert {name for name in old if old[name] != new[name]} == p.CHANGED
    assert p.patch_entries(old) == new, 'candidate differs from exact transform'
    g242.compare_methods(old[p.GUARD+'.class'],new[p.GUARD+'.class'],
        {('eligible',p.SPBOOL),('selectLevel','(L'+p.SP+';)L'+p.p.L+';')})
    txdesc = '(L'+p.SL+';L'+p.p.BLOCK+';Ljava/util/List;L'+p.SP+';L'+p.C+';)Z'
    g242.compare_methods(old[p.NQ+'.class'],new[p.NQ+'.class'],{('transact',txdesc)})
    g242.compare_methods(old[p.PS+'.class'],new[p.PS+'.class'],{
        ('possess','(L'+p.SP+';L'+p.prev.GUIDE+';)V'),('clearPossessed',p.SPVOID),
        ('onPlayerLogin','(L'+p.LOGIN+';)V')})
    g242.compare_methods(old[p.POSS+'.class'],new[p.POSS+'.class'],{
        ('onPlayerClone','(L'+p.CLONE+';)V'),('repossess','(L'+p.SP+';Ljava/lang/String;Ljava/lang/String;)V')},
        {(p.CANCEL_BRIDGE,'(Ljava/util/UUID;)V')})
    # Exactly one injected mixin in the common list; all old config keys remain.
    c0=json.loads(old['settledlands.mixins.json']); c1=json.loads(new['settledlands.mixins.json'])
    assert c1['mixins']==c0['mixins']+[p.MIXIN_SIMPLE]
    assert {k:v for k,v in c1.items() if k!='mixins'}=={k:v for k,v in c0.items() if k!='mixins'}
    mix=classedit.Klass(new[p.MIXIN+'.class'])
    class_ann=read_annotations(mix,next(x['body'] for x in mix.attrs if x['name']=='RuntimeInvisibleAnnotations'))
    assert class_ann==[('Lorg/spongepowered/asm/mixin/Mixin;',{'value':('[',[('c','L'+p.LIVING+';')])})]
    handler=mix.find_method('settledlands$wearAttributeDirty','(L'+p.HOLDER+';L'+p.CALLBACK+';)V')
    assert handler and handler['acc'] & 0x0002
    inject=read_annotations(mix,next(x['body'] for x in handler['attrs'] if x['name']=='RuntimeVisibleAnnotations'))
    assert inject==[('Lorg/spongepowered/asm/mixin/injection/Inject;',{
        'method':('[',[('s','onAttributeUpdated(L'+p.HOLDER+';)V')]),
        'at':('[',[('@',('Lorg/spongepowered/asm/mixin/injection/At;',{'value':('s','HEAD')}))])})]
    assert (p.WEAR,'attributeDirty',p.DIRTY_DESC) in g242.refs(mix,handler)
    meta=tomllib.loads(new['META-INF/neoforge.mods.toml'].decode())['mods'][0]
    assert meta['version']==p.NEW_VERSION and meta['description'].startswith(p.CHANGELOG)
    tx=classedit.Klass(new[p.NQ+'.class']); calls=g242.refs(tx,tx.find_method('transact',txdesc))
    assert calls.count((p.WEAR,'recordPaid',p.PAID_DESC))==2
    assert calls.count((p.q.GATE,'finishStacks','(Ljava/util/List;)V'))==2
    assert [x[1] for x in calls if x[1] in ('recordPaid','finishStacks')]==[
        'finishStacks','recordPaid','finishStacks','recordPaid']
    # Unchanged item recovery, click/lead, vanilla death mechanics and cult gate.
    for name in (p.prev.REC,p.prev.IVY,p.prev.C,p.prev.DIALOGUE,p.prev.GUIDE,p.p.LIFECYCLE,p.p.PM):
        assert old[name+'.class']==new[name+'.class'],name
    print('OK exact diff/methods, mixin annotation arrays/target/HEAD, commit ordering and unchanged 244 protections')


def mutate(jar, dest, mode):
    with zipfile.ZipFile(jar) as z: raw={name:z.read(name) for name in z.namelist()}
    repl={}
    def edit(cls,name,desc,fn):
        key=cls+'.class'; repl[key]=g244.change(repl.get(key,raw[key]),name,desc,fn)
    def skip(ref, index=0):
        def action(k,c):
            hits=[pc for pc,ins in decode.walk(c) if ins[0] in (0xb6,0xb8) and
                p.constant_ref(k,struct.unpack('>H',ins[1:3])[0])==ref]
            assert len(hits)>index,(ref,hits)
            # Call consumes either one reference or two references; these
            # substitutions preserve instruction count and existing frames.
            desc=ref[2]; pop=b'\x58' if desc==p.PAID_DESC or ref[0]=='java/util/UUID' else b'\x57'
            c[hits[index]:hits[index]+3]=pop+b'\x00\x00'
        return action
    if mode in ('normal-not-paid','exception-not-paid'):
        txdesc='(L'+p.SL+';L'+p.p.BLOCK+';Ljava/util/List;L'+p.SP+';L'+p.C+';)Z'
        edit(p.NQ,'transact',txdesc,skip((p.WEAR,'recordPaid',p.PAID_DESC),0 if mode=='normal-not-paid' else 1))
    elif mode in ('no-resume','no-sync','no-reset','no-deactivate','no-clone','no-dirty'):
        name={'no-resume':'resume','no-sync':'sync','no-reset':'reset','no-deactivate':'deactivate',
              'no-clone':'clonePlayer','no-dirty':'attributeDirty'}[mode]
        desc={'no-clone':'(L'+p.CLONE+';)V','no-dirty':p.DIRTY_DESC}.get(mode,p.SPVOID)
        edit(p.WEAR,name,desc,lambda k,c:b'\xb1')
    elif mode=='wrong-penalty':
        edit(p.WEAR,'penalty','(I)D',lambda k,c:b'\x07\x87\xaf')  # always -4
    elif mode=='fourth-body':
        edit(p.WEAR,'canSpawn',p.SPBOOL,lambda k,c:b'\x04\xac')
    elif mode=='paid-watch-not-cancelled':
        edit(p.WEAR,'recordPaid',p.PAID_DESC,skip((p.POSS,p.CANCEL_BRIDGE,'(Ljava/util/UUID;)V')))
    elif mode=='fourth-watch-not-cancelled':
        edit(p.WEAR,'canSpawn',p.SPBOOL,skip((p.POSS,p.CANCEL_BRIDGE,'(Ljava/util/UUID;)V')))
    elif mode=='cancel-all-owners':
        def clear(k,c):
            return b'\x2a\xb9'+struct.pack('>H',k.add_interfaceref('java/util/List','clear','()V'))+b'\x01\x00\xb1'
        edit(p.WEAR,'cancelWatches',p.CANCEL_DESC,clear)
    elif mode in ('uuid-mismatch-accepted','duplicate-counted'):
        def bypass(k,c):
            hits=[pc for pc,ins in decode.walk(c) if ins[0]==0xb6 and
                p.constant_ref(k,struct.unpack('>H',ins[1:3])[0])==('java/util/UUID','equals','(Ljava/lang/Object;)Z')]
            assert len(hits)==2,hits
            # first equality must be true, second equality must be false
            pc=hits[0 if mode=='uuid-mismatch-accepted' else 1]
            c[pc:pc+3]=b'\x58'+(b'\x04' if mode=='uuid-mismatch-accepted' else b'\x03')+b'\x00'
        edit(p.WEAR,'resume',p.SPVOID,bypass)
    elif mode=='nondeath-treated-as-death':
        def always_death(k,c):
            ref=(p.CLONE,'isWasDeath','()Z')
            hits=[pc for pc,ins in decode.walk(c) if ins[0]==0xb6 and
                p.constant_ref(k,struct.unpack('>H',ins[1:3])[0])==ref]
            assert len(hits)==1; pc=hits[0]; c[pc:pc+3]=b'\x57\x04\x00'
        edit(p.WEAR,'clonePlayer','(L'+p.CLONE+';)V',always_death)
    elif mode=='no-reset-on-new-npc':
        edit(p.PS,'possess','(L'+p.SP+';L'+p.prev.GUIDE+';)V',skip((p.WEAR,'reset',p.SPVOID)))
    elif mode=='no-login-sync':
        edit(p.PS,'onPlayerLogin','(L'+p.LOGIN+';)V',skip((p.WEAR,'login','(L'+p.LOGIN+';)V')))
    else: raise ValueError(mode)
    g237.alter_zip(jar,dest,replace=repl)


def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--inputs',required=True,type=pathlib.Path)
    for i in range(237,245): ap.add_argument('--base'+str(i),required=True,type=pathlib.Path)
    ap.add_argument('--java',required=True,type=pathlib.Path); ap.add_argument('--jar',required=True,type=pathlib.Path)
    a=ap.parse_args(); inputs=a.inputs.resolve(); java=a.java.resolve(); jar=a.jar.resolve()
    bases={i:getattr(a,'base'+str(i)).resolve() for i in range(237,245)}
    assert hashlib.sha256(bases[244].read_bytes()).hexdigest()==p.BASE_SHA
    digest=hashlib.sha256(jar.read_bytes()).hexdigest()
    assert digest==EXPECTED_SHA and jar.stat().st_size==EXPECTED_SIZE,(digest,jar.stat().st_size)
    print(f'build245 size={jar.stat().st_size} sha256={digest}',flush=True)
    g237.run('complete 244 gate incl. 243–237 and HANDOFF §8',
        [PY,ROOT/'tests/gate_build244.py','--inputs',inputs,
         *sum((['--base'+str(i),bases[i]] for i in range(237,244)),[]),'--java',java,'--jar',bases[244]],
        contains=('BUILD 244 GATE: PASS',))
    with tempfile.TemporaryDirectory(prefix='gate-build245-') as td:
        tmp=pathlib.Path(td); rebuilt=tmp/'rebuilt.jar'
        g237.run('245 deterministic build',[PY,ROOT/'tools/patch_body_wear245.py','--base',bases[244],'--out',rebuilt],contains=(EXPECTED_SHA,))
        assert rebuilt.read_bytes()==jar.read_bytes()
        g237.run('245 structural verifier',[PY,ROOT/'tools/validate_jar.py',jar,'--base',bases[244]],contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        verify=[PY,ROOT/'tools/verify_jar.py']
        g237.run('245 CRC/TOML/languages/mixins/diff',verify+[jar,'--base',bases[244]]+
            g237.option_entries('--expect-added',sorted(p.ADDED))+g237.option_entries('--expect-changed',sorted(p.CHANGED)),
            contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        static_semantics(bases[244],jar)
        bad=tmp/'crc.jar'; bad.write_bytes(g237.corrupt_crc(jar.read_bytes()))
        g237.run('245 CRC negative',verify+[bad],success=False,contains=('ПРОБЛЕМЫ: целостность',))
        bad=tmp/'lang.jar'; g237.alter_zip(jar,bad,remove={'assets/settledlands/lang/ru_ru.json'})
        g237.run('245 missing language negative',verify+[bad],success=False,contains=('ПРОБЛЕМЫ: языки',))
        bad=tmp/'mixin.jar'; g237.alter_zip(jar,bad,remove={'settledlands.mixins.json'})
        g237.run('245 missing mixin negative',verify+[bad],success=False,contains=('ПРОБЛЕМЫ: mixins',))
        with zipfile.ZipFile(jar) as z:
            mix=tmp/'BodyWearAttributeMixin.class'; mix.write_bytes(z.read(p.MIXIN+'.class'))
        g237.run('245 mixin annotation checker',[PY,ROOT/'tools/check_mixin_class.py',mix],contains=('OK',))
        for target in TARGETS:
            g237.run('245 JVM verify '+target.split('#')[1].split('(')[0],
                [PY,ROOT/'tools/jvm_verify.py',jar,target,'--java',java],contains=('DECLARED ','ИТОГ: OK'))
        for fixture in ('jvm_corpse_load.py','jvm_corpse_spawn.py','jvm_corpse_dress.py','jvm_corpse_realprofile.py'):
            g237.run('245 renderer regression '+fixture,[PY,ROOT/'tests'/fixture,'--java',java,
                '--base236',inputs/g237.NAMES[236],'--jar',jar],contains=('236 negative control','237 JVM execution'))
        for fixture,flag,idx,sig in [('jvm_cleanup239.py','--base238',238,'239 JVM execution'),
                                     ('jvm_npc_cult240.py','--base239',239,'240 JVM execution')]:
            g237.run('245 component regression '+fixture,[PY,ROOT/'tests'/fixture,'--java',java,flag,bases[idx],'--jar',jar],contains=(sig,))
        for fixture,sig in [('jvm_noqueue243.py','real death/join/clone/drops/altar/recovery'),
                            ('jvm_noqueue_altar243.py','no FIFO, multiple players'),
                            ('jvm_queue_disabled243.py','real persisted queue API is disabled'),
                            ('jvm_server_tick243.py','real server tick -> real level altar scan'),
                            ('jvm_login243.py','pending/login')]:
            g237.run('245 no-queue regression '+fixture,[PY,ROOT/'tests'/fixture,'--java',java,'--base242',bases[242],'--jar',jar],contains=(sig,))
        g237.run('245 cult diagnostic regression',[PY,ROOT/'tests/jvm_cultprobe243.py','--java',java,'--jar',jar],contains=('243 JVM probe:',))
        for fixture,sig in [('jvm_corpse_click244.py','complete/partial recovery'),
                            ('jvm_real_corpse_dialogue244.py','Real GuideNpc.mobInteract JVM'),
                            ('jvm_self_escape244.py','Real death/drop/clone/purgatory-exit/click JVM')]:
            g237.run('245 click/exit regression '+fixture,[PY,ROOT/'tests'/fixture,'--java',java,'--base243',bases[243],'--jar',jar],contains=(sig,))
        fixtures=[('jvm_body_wear245.py','Body wear: 3 paid lives'),
                  ('jvm_new_body245.py','Real NPC interaction/possess'),
                  ('jvm_wear_login245.py','Actual possession login hook')]
        for fixture,sig in fixtures:
            g237.run('245 behavior '+fixture,[PY,ROOT/'tests'/fixture,'--java',java,'--jar',jar],contains=(sig,'PASS'))
        mutants=[
          ('normal-not-paid','jvm_body_wear245.py','payment1.marked'),
          ('exception-not-paid','jvm_body_wear245.py','after.paid'),
          ('no-resume','jvm_body_wear245.py','life1.count'),
          ('no-sync','jvm_body_wear245.py','life1.max'),
          ('wrong-penalty','jvm_body_wear245.py','life2.max'),
          ('fourth-body','jvm_body_wear245.py','death4.bodies'),
          ('no-clone','jvm_body_wear245.py','ghost2.count'),
          ('nondeath-treated-as-death','jvm_body_wear245.py','end_clone.max'),
          ('no-deactivate','jvm_body_wear245.py','self.ghost_max'),
          ('no-reset','jvm_body_wear245.py','new_body.count'),
          ('no-dirty','jvm_body_wear245.py','buff.flat'),
          ('uuid-mismatch-accepted','jvm_body_wear245.py','mismatch.count'),
          ('duplicate-counted','jvm_body_wear245.py','replay_forced.count'),
          ('paid-watch-not-cancelled','jvm_body_wear245.py','payment1.watch_after'),
          ('fourth-watch-not-cancelled','jvm_body_wear245.py','watches.two_after'),
          ('cancel-all-owners','jvm_body_wear245.py','paid_multi.after'),
          ('no-reset-on-new-npc','jvm_new_body245.py','new.count'),
          ('no-login-sync','jvm_wear_login245.py','active.max'),
        ]
        for mode,fixture,signal in mutants:
            bad=tmp/(mode+'.jar'); mutate(jar,bad,mode)
            g237.run('245 negative mutant '+mode,[PY,ROOT/'tests'/fixture,'--java',java,'--jar',bad],
                success=False,contains=("'"+signal+"': (",))
    print('BUILD 245 GATE: PASS (Java 21 + synthetic APIs; Minecraft/NeoForge and actual mixin application not run)')


if __name__=='__main__': main()
