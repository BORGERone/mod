#!/usr/bin/env python3
"""Fail-closed build 243 release gate; Java 21 with synthetic game APIs, NOT Minecraft."""
import argparse,hashlib,pathlib,struct,sys,tempfile,tomllib,zipfile
import gate_build237 as g237
import gate_build238 as g238
import gate_build242 as g242
ROOT=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
import classedit,decode
import patch_noqueue243 as n
import patch_death_scope243 as d
import patch_cultprobe243 as cult

PY=sys.executable
EXPECTED_SHA='a78c2943ec481aab0ed9b9fa67b6aa09a905d019b46b9866a5e6feab91cc584c'
EXPECTED_SIZE=1555767
TARGETS=[
 n.p.LIFECYCLE+'#onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent)',
 n.p.POSS+'#onPlayerDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent)',
 n.p.POSS+'#onPlayerDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent)',
 n.p.POSS+'#onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent$Post)',
 n.p.PD+'#enqueueDead(java.util.UUID)', n.p.PD+'#hasDeadPlayers',n.p.PD+'#pollFirstDead',
 n.p.PD+'#isDeadInQueue(java.util.UUID)',n.p.PD+'#removeFromQueue(java.util.UUID)',
 n.p.PD+'#'+n.q.PEEK,n.p.PD+'#'+n.q.RESTORE_QUEUE+'(java.util.UUID)',
 n.p.PM+'#onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent$Post)',
 n.p.PM+'#tickLevelAltars(net.minecraft.server.level.ServerLevel)',
 n.p.PM+'#onPlayerLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent)',
 n.p.PM+'#onPlayerRespawn(net.neoforged.neoforge.event.entity.player.PlayerEvent$PlayerRespawnEvent)',
 n.p.PM+'#tryReviveAtAltar(net.minecraft.server.level.ServerLevel,net.minecraft.core.BlockPos)',
 n.q.GATE+'#tryFirst(dev.settledlands.revival.PurgatoryData,net.minecraft.server.level.ServerLevel,net.minecraft.core.BlockPos,java.util.List)',
 n.q.GATE+'#tryDirect(dev.settledlands.npc.GuideCorpseNpc,net.minecraft.server.MinecraftServer,net.minecraft.server.level.ServerLevel,dev.settledlands.revival.PurgatoryData,net.minecraft.server.level.ServerPlayer,net.minecraft.core.BlockPos)',
 n.NQ+'#hasPlayers(net.minecraft.server.MinecraftServer)',
 n.NQ+'#tryFirst(dev.settledlands.revival.PurgatoryData,net.minecraft.server.level.ServerLevel,net.minecraft.core.BlockPos,java.util.List)',
 d.GUARD+'#selectLevel(net.minecraft.server.level.ServerPlayer)',d.GUARD+'#eligible(net.minecraft.server.level.ServerPlayer)',
 cult.BRIDGE+'#onInteractSpecific(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent$EntityInteractSpecific)',
 cult.PROBE+'#observe(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent$EntityInteractSpecific)',
]


def static_semantics(base,jar):
    with zipfile.ZipFile(base) as b,zipfile.ZipFile(jar) as z:
        old=set(b.namelist());assert set(z.namelist())-old==n.ADDED and not old-set(z.namelist())
        assert {name for name in old if b.read(name)!=z.read(name)}==n.CHANGED
        for name,fn in [(n.p.LIFECYCLE,d.patch_lifecycle),(n.p.POSS,n.patch_possession),
                        (n.p.PD,n.patch_data),(n.p.PM,n.patch_manager),(n.q.GATE,n.patch_gate),(cult.BRIDGE,cult.patch_bridge)]:
            assert z.read(name+'.class')==fn(b.read(name+'.class')),name
        assert z.read(d.GUARD+'.class')==d.build_guard()
        assert z.read(n.NQ+'.class')==n.build_noqueue()
        assert z.read(cult.PROBE+'.class')==cult.build_probe()
        death='(Lnet/neoforged/neoforge/event/entity/living/LivingDeathEvent;)V'
        drops='(Lnet/neoforged/neoforge/event/entity/living/LivingDropsEvent;)V'
        post='(Lnet/neoforged/neoforge/event/tick/ServerTickEvent$Post;)V'
        g242.compare_methods(b.read(n.p.LIFECYCLE+'.class'),z.read(n.p.LIFECYCLE+'.class'),{('onPlayerDeath',death)})
        g242.compare_methods(b.read(n.p.POSS+'.class'),z.read(n.p.POSS+'.class'),{('onPlayerDeath',death),('onPlayerDrops',drops),('onServerTick',post)})
        pdchanges={(name,desc) for name,desc in [('enqueueDead','(Ljava/util/UUID;)V'),('hasDeadPlayers','()Z'),
            ('pollFirstDead','()Ljava/util/UUID;'),('isDeadInQueue','(Ljava/util/UUID;)Z'),('removeFromQueue','(Ljava/util/UUID;)Z'),
            (n.q.PEEK,'()Ljava/util/UUID;'),(n.q.RESTORE_QUEUE,'(Ljava/util/UUID;)V')]}
        g242.compare_methods(b.read(n.p.PD+'.class'),z.read(n.p.PD+'.class'),pdchanges)
        g242.compare_methods(b.read(n.p.PM+'.class'),z.read(n.p.PM+'.class'),{('onPlayerLogin','(Lnet/neoforged/neoforge/event/entity/player/PlayerEvent$PlayerLoggedInEvent;)V'),('onServerTick',post),('tickLevelAltars','(L'+n.p.SL+';)V'),('tryReviveAtAltar',n.q.TRY_DESC),(n.q.PLAYER_ONLY,n.q.TRY_DESC)})
        old_gate=classedit.Klass(b.read(n.q.GATE+'.class'));new_gate=classedit.Klass(z.read(n.q.GATE+'.class'))
        old_methods={g238.member_key(m):m for m in old_gate.methods};new_methods={g238.member_key(m):m for m in new_gate.methods}
        assert old_methods.keys()==new_methods.keys()
        exposed={'findBody','alreadyAtBody','restoreStacks','finishStacks'}
        for key,m in old_methods.items():
            other=new_methods[key]
            assert other['acc']==(0x0009 if key[0] in exposed else m['acc']),key
            if key[0] not in {'tryFirst','tryDirect'}:assert g238.attributes(m)==g238.attributes(other),key
            else:assert [(x,v) for x,v in g238.attributes(m) if x!='Code']==[(x,v) for x,v in g238.attributes(other) if x!='Code'],key
        g242.compare_methods(b.read(cult.BRIDGE+'.class'),z.read(cult.BRIDGE+'.class'),{('onInteractSpecific',cult.DESC)})
        for name in (n.p.HELPER,n.p.RECOVERY,n.p.CORPSE,n.p.MIXIN,'dev/settledlands/npc/PossessionSystem',
                     'dev/settledlands/npc/GuideDialogue','dev/settledlands/npc/IvyDeathScene',
                     'dev/settledlands/revival/RevivalNpcHelper','dev/settledlands/mixin/AltarNpcReviveMixin',
                     'settledlands.mixins.json','assets/settledlands/lang/ru_ru.json','assets/settledlands/lang/en_us.json'):
            key=name if '.' in name.split('/')[-1] else name+'.class'
            assert b.read(key)==z.read(key),key
        mod=tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode())['mods'][0]
        assert mod['version']==n.NEW_VERSION and mod['description'].startswith(n.CHANGELOG)
        assert z.read('settledlands-patch-info.txt').decode().startswith(n.PATCHINFO)
    print('OK exact bytecode transform/allowlist; unchanged recovery, player gate, NPC and mixins')


def mutate(jar,base,dest,mode):
    if mode.startswith('old-class-'):
        names={'old-class-life':n.p.LIFECYCLE,'old-class-watch':n.p.POSS,'old-class-queue':n.p.PD,
               'old-class-manager':n.p.PM,'old-class-gate':n.q.GATE,'old-class-probe':cult.BRIDGE}
        name=names[mode]+'.class'
        with zipfile.ZipFile(base) as b:g237.alter_zip(jar,dest,replace={name:b.read(name)})
    elif mode=='legacy-direct-bind':
        # Valid major-49 mutant: manufacture a missing current UUID from the
        # direct corpse. Raise max_locals to cover the scratch NBT variable.
        with zipfile.ZipFile(jar) as z:raw=z.read(n.q.GATE+'.class')
        k=classedit.Klass(raw);method=k.find_method('tryDirect',n.q.MIX_DESC);parts=classedit.code_parts(k,method)
        a=n.q.Asm();a.raw(0x19,4);a.op(0xc0,k.add_class(n.p.EXT))
        a.op(0xb9,k.add_interfaceref(n.p.EXT,'getPersistentData','()L'+n.q.NBT+';'));a.raw(1,0)
        a.raw(0x3a,7,0x19,7);a.op(0x13,k.add_string(n.p.DEATH_CORPSE))
        a.op(0xb6,k.add_methodref(n.q.NBT,'hasUUID','(Ljava/lang/String;)Z'));a.jump(0x9a,'bound')
        a.raw(0x19,7);a.op(0x13,k.add_string(n.p.DEATH_CORPSE));a.raw(0x2a)
        a.op(0xb6,k.add_methodref(n.q.E,'getUUID','()Ljava/util/UUID;'))
        a.op(0xb6,k.add_methodref(n.q.NBT,'putUUID','(Ljava/lang/String;Ljava/util/UUID;)V'))
        a.label('bound');a.raw(0x00);parts=classedit.insert(parts,0,a.finish());parts['max_locals']=max(parts['max_locals'],8)
        raw=k.dump(method,classedit.build_code_body(k,parts));g237.alter_zip(jar,dest,replace={n.q.GATE+'.class':raw})
    elif mode in ('price-eight','no-money-rollback','no-exception-rollback'):
        with zipfile.ZipFile(jar) as z:raw=z.read(n.NQ+'.class')
        k=classedit.Klass(raw);method=k.find_method('transact');parts=classedit.code_parts(k,method)
        if mode=='price-eight':
            code=bytearray(parts['code']);hits=[pc for pc,ins in decode.walk(code) if ins==b'\x10\x09'];assert len(hits)==1
            code[hits[0]+1]=8;parts['code']=bytes(code)
        elif mode=='no-money-rollback':
            code=bytearray(parts['code']);hits=[]
            for pc,ins in decode.walk(code):
                if ins[0]==0xb8 and g242.constant_ref(k,struct.unpack('>H',ins[1:3])[0])==(n.q.GATE,'restoreStacks','(Ljava/util/List;Ljava/util/List;)V'):
                    hits.append(pc)
            assert len(hits)==2
            for pc in hits:code[pc:pc+3]=b'\x58\x00\x00'
            parts['code']=bytes(code)
        else:
            assert len(parts['exc'])==1;parts['exc']=[]
        raw=k.dump(method,classedit.build_code_body(k,parts));g237.alter_zip(jar,dest,replace={n.NQ+'.class':raw})
    elif mode=='drop-purgatory-bypass':
        with zipfile.ZipFile(jar) as z:raw=z.read(d.GUARD+'.class')
        k=classedit.Klass(raw);method=k.find_method('eligible','(L'+n.p.SP+';)Z');parts=classedit.code_parts(k,method)
        parts['code']=b'\x2a\xb8'+struct.pack('>H',k.add_methodref('dev/settledlands/npc/PossessionSystem','isPossessed','(L'+n.p.E+';)Z'))+b'\xac'
        parts['exc']=[];parts['nested']=[];raw=k.dump(method,classedit.build_code_body(k,parts))
        g237.alter_zip(jar,dest,replace={d.GUARD+'.class':raw})
    else:g242.mutate(jar,base,dest,mode)


def main():
    ap=argparse.ArgumentParser()
    for flag in ('inputs','base237','base238','base239','base240','base241','base242','jar','java'):
        ap.add_argument('--'+flag,type=pathlib.Path,required=True)
    a=ap.parse_args();inputs=a.inputs.resolve();jar=a.jar.resolve();java=a.java.resolve()
    bases={i:getattr(a,'base'+str(i)).resolve() for i in range(237,243)}
    assert hashlib.sha256(bases[242].read_bytes()).hexdigest()==d.BASE_SHA
    digest=hashlib.sha256(jar.read_bytes()).hexdigest()
    assert digest==EXPECTED_SHA and jar.stat().st_size==EXPECTED_SIZE,(digest,jar.stat().st_size)
    print(f'build243 size={jar.stat().st_size} sha256={digest}',flush=True)
    g237.run('complete build242 gate incl. HANDOFF §8',[PY,ROOT/'tests/gate_build242.py','--inputs',inputs,
        *sum((['--base'+str(i),bases[i]] for i in range(237,242)),[]),'--java',java,'--jar',bases[242]],
        contains=('BUILD 242 GATE: PASS',))
    with tempfile.TemporaryDirectory(prefix='gate-build243-') as td:
        tmp=pathlib.Path(td);rebuilt=tmp/'rebuilt.jar'
        g237.run('243 deterministic build',[PY,ROOT/'tools/patch_noqueue243.py','--base',bases[242],'--out',rebuilt],contains=('sha256 '+EXPECTED_SHA,))
        assert rebuilt.read_bytes()==jar.read_bytes()
        g237.run('243 structural class verifier',[PY,ROOT/'tools/validate_jar.py',jar,'--base',bases[242]],contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        g237.run('243 CRC/TOML/lang/mixins/diff',[PY,ROOT/'tools/verify_jar.py',jar,'--base',bases[242]]+
            g237.option_entries('--expect-added',sorted(n.ADDED))+g237.option_entries('--expect-changed',sorted(n.CHANGED)),contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
        static_semantics(bases[242],jar)
        bad=tmp/'corrupt.jar';bad.write_bytes(g237.corrupt_crc(jar.read_bytes()))
        g237.run('243 CRC negative',[PY,ROOT/'tools/verify_jar.py',bad],success=False,contains=('ПРОБЛЕМЫ: целостность',))
        bad=tmp/'lang.jar';g237.alter_zip(jar,bad,remove={'assets/settledlands/lang/ru_ru.json'})
        g237.run('243 missing language negative',[PY,ROOT/'tools/verify_jar.py',bad],success=False,contains=('ПРОБЛЕМЫ: языки',))
        bad=tmp/'mixin.jar';g237.alter_zip(jar,bad,remove={'settledlands.mixins.json'})
        g237.run('243 missing mixin negative',[PY,ROOT/'tools/verify_jar.py',bad],success=False,contains=('ПРОБЛЕМЫ: mixins',))
        for target in TARGETS:
            g237.run('243 JVM verifier '+target.split('#')[1].split('(')[0],
                [PY,ROOT/'tools/jvm_verify.py',jar,target,'--java',java],contains=('DECLARED ','ИТОГ: OK'))
        for fixture in ('jvm_corpse_load.py','jvm_corpse_spawn.py','jvm_corpse_dress.py','jvm_corpse_realprofile.py'):
            g237.run('243 renderer regression '+fixture,[PY,ROOT/'tests'/fixture,'--java',java,
                '--base236',inputs/g237.NAMES[236],'--jar',jar],contains=('236 negative control','237 JVM execution'))
        for fixture,flag,i,signature in [('jvm_recovery238.py','--base237',237,'238 JVM execution'),
                                          ('jvm_cleanup239.py','--base238',238,'239 JVM execution'),
                                          ('jvm_npc_cult240.py','--base239',239,'240 JVM execution')]:
            g237.run('243 component regression '+fixture,[PY,ROOT/'tests'/fixture,'--java',java,flag,bases[i],'--jar',jar],contains=(signature,))
        for fixture,signature in [('jvm_noqueue243.py','real death/join/clone/drops/altar/recovery'),
                                  ('jvm_noqueue_altar243.py','no FIFO, multiple players'),
                                  ('jvm_queue_disabled243.py','real persisted queue API is disabled'),
                                  ('jvm_server_tick243.py','real server tick -> real level altar scan'),
                                  ('jvm_login243.py','pending/login')]:
            g237.run('243 behavior '+fixture,[PY,ROOT/'tests'/fixture,'--java',java,'--base242',bases[242],'--jar',jar],contains=(signature,))
        g237.run('243 cult interaction diagnostic',[PY,ROOT/'tests/jvm_cultprobe243.py','--java',java,'--jar',jar],contains=('243 JVM probe:',))
        mutants=[
          ('old-class-life','jvm_noqueue243.py','ordinary.bodies'),('old-class-watch','jvm_noqueue243.py','ordinary.watches'),
          ('drop-purgatory-bypass','jvm_noqueue243.py','repeat.possessed_world_drops'),
          ('old-class-queue','jvm_queue_disabled243.py','AssertionError'),
          ('old-class-manager','jvm_server_tick243.py','AssertionError'),('old-class-gate','jvm_noqueue_altar243.py','valid.in_purg'),
          ('old-class-probe','jvm_cultprobe243.py','NullPointerException'),
          ('price-eight','jvm_noqueue_altar243.py','valid.money'),('range-bypass','jvm_noqueue_altar243.py','far.in_purg'),
          ('owner-bypass','jvm_noqueue_altar243.py','foreign.in_purg'),('legacy-direct-bind','jvm_noqueue_altar243.py','direct_missing_id.in_purg'),
          ('no-money-rollback','jvm_noqueue_altar243.py','failed.money'),('no-exception-rollback','jvm_noqueue_altar243.py','throw_before.money'),
          ('refund-after-arrival','jvm_noqueue_altar243.py','throw_after.money'),('no-empty-finish','jvm_noqueue_altar243.py','valid.removed')]
        for mode,fixture,signal in mutants:
            bad=tmp/(mode+'.jar');mutate(jar,bases[242],bad,mode)
            cmd=[PY,ROOT/'tests'/fixture,'--java',java]
            if fixture!='jvm_cultprobe243.py':cmd+=['--base242',bases[242]]
            g237.run('243 negative mutant '+mode,cmd+['--jar',bad],success=False,contains=(signal,))
    print('BUILD 243 GATE: PASS (Java 21 + synthetic APIs; Minecraft/NeoForge not run)')


if __name__=='__main__':main()
