#!/usr/bin/env python3
"""Fail-closed 247 gate: full 246–237 / HANDOFF §8 + 247 regressions.

Real candidate bytecode is exercised with Java 21 and synthetic APIs. This is
not a Minecraft/NeoForge run, real event-bus dispatch or Mixin transformation.
"""
import argparse,hashlib,pathlib,subprocess,sys,tempfile,tomllib,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'tests'),str(ROOT/'tools')]
import classedit,gate_build237 as g237,gate_build242 as g242,gate_build245 as g245,gate_build246 as g246,patch_bodiless247 as p
PY=sys.executable;SHA='c3130e136018342d133b710812e6c86fc0fcf5f181146c6973f055abba3798f1';SIZE=1589582
TARGETS=[
 p.PROTECTION+'#onAttack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent)',
 p.PROTECTION+'#onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent)',
 p.PROTECTION+'#onEffectApplicable(net.neoforged.neoforge.event.entity.living.MobEffectEvent$Applicable)',
 p.PROTECTION+'#onEffectRemoved(net.neoforged.neoforge.event.entity.living.MobEffectEvent$Remove)',
]

def static_semantics(base,jar):
 with zipfile.ZipFile(base) as b,zipfile.ZipFile(jar) as z:
  old={n:b.read(n) for n in b.namelist()};new={n:z.read(n) for n in z.namelist()}
 assert set(new)-set(old)==p.ADDED and not set(old)-set(new)
 assert {n for n in old if old[n]!=new[n]}==p.CHANGED
 assert all(not n.startswith(('net/minecraft/','net/neoforged/','org/spongepowered/','com/mojang/')) for n in p.ADDED)
 assert new['settledlands.mixins.json']==old['settledlands.mixins.json']
 k=classedit.Klass(new[p.PROTECTION+'.class']);assert k.major==65
 assert g246.annotations(k,k.attrs)==[('Lnet/neoforged/fml/common/EventBusSubscriber;',{'modid':('s','settledlands')})]
 handlers={'onAttack':'net/neoforged/neoforge/event/entity/player/AttackEntityEvent',
           'onIncomingDamage':'net/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent',
           'onEffectApplicable':'net/neoforged/neoforge/event/entity/living/MobEffectEvent$Applicable',
           'onEffectRemoved':'net/neoforged/neoforge/event/entity/living/MobEffectEvent$Remove'}
 for name,parameter in handlers.items():
  m=k.find_method(name,'(L'+parameter+';)V');assert m and m['acc']&0x1 and m['acc']&0x8
  assert g246.annotations(k,m['attrs'])==[('Lnet/neoforged/bus/api/SubscribeEvent;',{'priority':('e','Lnet/neoforged/bus/api/EventPriority;','LOWEST')})]
 refs={name:set(g242.refs(k,k.find_method(name))) for name in [*handlers,'blocked']}
 for name,wants in {
  'blocked':[('net/minecraft/world/level/Level','dimension','()Lnet/minecraft/resources/ResourceKey;'),('dev/settledlands/npc/NpcStatsInteraction','isBodiless','(Lnet/minecraft/world/entity/player/Player;)Z'),('net/minecraft/world/entity/projectile/Projectile','getOwner','()Lnet/minecraft/world/entity/Entity;'),('net/minecraft/world/entity/AreaEffectCloud','getOwner','()Lnet/minecraft/world/entity/LivingEntity;')],
  'onAttack':[('net/neoforged/neoforge/event/entity/player/AttackEntityEvent','setCanceled','(Z)V')],
  'onIncomingDamage':[('net/minecraft/world/damagesource/DamageSource','getEntity','()Lnet/minecraft/world/entity/Entity;'),('net/minecraft/world/damagesource/DamageSource','getDirectEntity','()Lnet/minecraft/world/entity/Entity;'),('net/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent','setCanceled','(Z)V')],
  'onEffectApplicable':[('net/minecraft/world/effect/MobEffectInstance','is','(Lnet/minecraft/core/Holder;)Z'),('net/neoforged/neoforge/event/entity/living/MobEffectEvent$Applicable','getEffectSource','()Lnet/minecraft/world/entity/Entity;'),('net/neoforged/neoforge/event/entity/living/MobEffectEvent$Applicable','setResult','(Lnet/neoforged/neoforge/event/entity/living/MobEffectEvent$Applicable$Result;)V')],
  'onEffectRemoved':[('net/neoforged/neoforge/event/entity/living/MobEffectEvent$Remove','getCure','()Lnet/neoforged/neoforge/common/EffectCure;'),('net/neoforged/neoforge/event/entity/living/MobEffectEvent$Remove','getEffect','()Lnet/minecraft/core/Holder;'),('net/minecraft/core/Holder','is','(Lnet/minecraft/resources/ResourceLocation;)Z'),('net/neoforged/neoforge/event/entity/living/MobEffectEvent$Remove','setCanceled','(Z)V')],
 }.items():
  for ref in wants:assert ref in refs[name],(name,ref)
 assert old['dev/settledlands/npc/NpcStatsInteraction.class']==new['dev/settledlands/npc/NpcStatsInteraction.class']
 meta=tomllib.loads(new['META-INF/neoforge.mods.toml'].decode())['mods'][0];assert meta['version']==p.NEW_VERSION and meta['description'].startswith(p.CHANGELOG)
 print('OK 247 exact entry diff, no stubs or new mixin, subscriber/method annotations and API descriptors',flush=True)

def candidate_regressions(jar,inputs,java):
 bases={i:inputs/name for i,name in g246.NAMES.items()}
 for label,targets in [('245 inherited targets',g245.TARGETS),('246 inherited targets',g246.TARGETS),('247 new targets',TARGETS)]:
  out=g237.run('247 JVM verifies '+label,[PY,ROOT/'tools/jvm_verify.py',jar,*targets,'--java',java],contains=('ИТОГ: OK',))
  for target in targets:assert 'DECLARED '+target.split('(')[0] in out,target
 for fixture in ('jvm_corpse_load.py','jvm_corpse_spawn.py','jvm_corpse_dress.py','jvm_corpse_realprofile.py'):
  g237.run('247 renderer regression '+fixture,[PY,ROOT/'tests'/fixture,'--java',java,'--base236',inputs/g237.NAMES[236],'--jar',jar],contains=('236 negative control','237 JVM execution'))
 g237.run('247 cleanup regression',[PY,ROOT/'tests/jvm_cleanup239.py','--java',java,'--base238',bases[238],'--jar',jar],contains=('239 JVM execution',))
 for fixture,sig in [('jvm_noqueue243.py','real death/join/clone/drops/altar/recovery'),('jvm_noqueue_altar243.py','no FIFO, multiple players'),('jvm_queue_disabled243.py','real persisted queue API is disabled'),('jvm_server_tick243.py','real server tick -> real level altar scan'),('jvm_login243.py','pending/login')]:
  cmd=[PY,ROOT/'tests/run_legacy246.py',fixture] if fixture=='jvm_noqueue243.py' else [PY,ROOT/'tests'/fixture]
  g237.run('247 no-queue regression '+fixture,cmd+['--java',java,'--base242',bases[242],'--jar',jar],contains=(sig,))
 g237.run('247 read-only cult diagnostic',[PY,ROOT/'tests/jvm_cultprobe246.py','--java',java,'--jar',jar],contains=('read-only cult probe:',))
 for fixture,sig in [('jvm_corpse_click244.py','complete/partial recovery'),('jvm_real_corpse_dialogue244.py','Real GuideNpc.mobInteract JVM'),('jvm_self_escape244.py','Real death/drop/clone/purgatory-exit/click JVM')]:
  g237.run('247 corpse regression '+fixture,[PY,ROOT/'tests'/fixture,'--java',java,'--base243',bases[243],'--jar',jar],contains=(sig,))
 for fixture,sig in [('jvm_body_wear245.py','Body wear: 3 paid lives'),('jvm_wear_login245.py','Actual possession login hook')]:
  g237.run('247 wear regression '+fixture,[PY,ROOT/'tests/run_legacy246.py',fixture,'--java',java,'--jar',jar],contains=(sig,'PASS'))
 for fixture,sig in [('jvm_graph_stats246.py','Real graph and NBT roundtrip'),('jvm_graph_ui246.py','Real canvas and UI spinners'),('jvm_graph_undo246.py','Real spinner, screen undo/redo'),('jvm_network_stats246.py','Actual network, guards'),('jvm_body_stats246.py','Actual NPC body stats'),('jvm_possession_stats246.py','Real possession bridge'),('jvm_stats_screen246.py','Actual stats screen'),('jvm_gravity246.py','Actual BodilessEffect')]:
  g237.run('247 246 regression '+fixture,[PY,ROOT/'tests'/fixture,'--java',java,'--jar',jar],contains=(sig,'PASS'))
 g237.run('247 legacy wear mutants',[PY,ROOT/'tests/mutants_legacy246.py','--java',java,'--jar',jar],contains=('245 behavioral mutants against 246: PASS 18',))
 g237.run('247 legacy stats mutants',[PY,ROOT/'tests/mutants246.py','--java',java,'--jar',jar],contains=('246 behavioral mutants: PASS 32',))

def main():
 q=argparse.ArgumentParser();q.add_argument('--inputs',required=True,type=pathlib.Path);q.add_argument('--base245',required=True,type=pathlib.Path);q.add_argument('--base246',required=True,type=pathlib.Path);q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);a=q.parse_args()
 inputs=a.inputs.resolve();base245=a.base245.resolve();base=a.base246.resolve();java=a.java.resolve();jar=a.jar.resolve()
 assert hashlib.sha256(base245.read_bytes()).hexdigest()==g246.p.BASE_SHA,'wrong base245'
 assert hashlib.sha256(base.read_bytes()).hexdigest()==p.BASE_SHA,'wrong base246'
 assert jar.stat().st_size==SIZE and hashlib.sha256(jar.read_bytes()).hexdigest()==SHA,'wrong 247 candidate'
 version=subprocess.run([str(java),'-version'],capture_output=True,text=True);assert version.returncode==0 and 'version "21.' in version.stderr,version.stderr
 print(f'build247 size={SIZE} sha256={SHA}\nJava: {version.stderr.splitlines()[0]}',flush=True)
 g237.run('complete build246 gate incl. 245–237 and HANDOFF §8',[PY,ROOT/'tests/gate_build246.py','--inputs',inputs,'--base245',base245,'--java',java,'--jar',base],contains=('BUILD 246 GATE: PASS',))
 with tempfile.TemporaryDirectory(prefix='gate-build247-') as d:
  tmp=pathlib.Path(d);rebuilt=tmp/'rebuild.jar';g237.run('247 deterministic build',[PY,ROOT/'tools/patch_bodiless247.py','--base',base,'--out',rebuilt,'--java',java],contains=(SHA,));assert rebuilt.read_bytes()==jar.read_bytes()
  g237.run('247 structural validator',[PY,ROOT/'tools/validate_jar.py',jar,'--base',base],contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
  verify=[PY,ROOT/'tools/verify_jar.py'];g237.run('247 CRC, TOML, locales, mixins and strict diff',verify+[jar,'--base',base]+g237.option_entries('--expect-added',sorted(p.ADDED))+g237.option_entries('--expect-changed',sorted(p.CHANGED)),contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
  static_semantics(base,jar)
  bad=tmp/'crc.jar';bad.write_bytes(g237.corrupt_crc(jar.read_bytes()));g237.run('247 broken CRC rejected',verify+[bad],success=False,contains=('ПРОБЛЕМЫ: целостность',))
  bad=tmp/'language.jar';g237.alter_zip(jar,bad,remove={'assets/settledlands/lang/ru_ru.json'});g237.run('247 missing language rejected',verify+[bad],success=False,contains=('ПРОБЛЕМЫ: языки',))
  bad=tmp/'mixin.jar';g237.alter_zip(jar,bad,remove={'settledlands.mixins.json'});g237.run('247 missing mixin config rejected',verify+[bad],success=False,contains=('ПРОБЛЕМЫ: mixins',))
  bad=tmp/'handler.jar';g237.alter_zip(jar,bad,remove=p.ADDED);g237.run('247 missing subscriber rejected by allowlist',verify+[bad,'--base',base]+g237.option_entries('--expect-added',sorted(p.ADDED))+g237.option_entries('--expect-changed',sorted(p.CHANGED)),success=False,contains=('ПРОБЛЕМЫ',))
  candidate_regressions(jar,inputs,java)
  g237.run('247 real bytecode: events, milk and attributed damage',[PY,ROOT/'tests/jvm_bodiless247.py','--java',java,'--jar',jar],contains=('247 actual protection + 246 predicate','PASS'))
  g237.run('247 negative behavioral mutants',[PY,ROOT/'tests/mutants247.py','--java',java,'--jar',jar],contains=('247 behavioral mutants: PASS 17',))
 print('BUILD 247 GATE: PASS (Java 21 + synthetic APIs; Minecraft/NeoForge and real event bus/mixins NOT run)',flush=True)
if __name__=='__main__':main()
