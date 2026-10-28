#!/usr/bin/env python3
"""Fail-closed 246 gate including 245–237 and HANDOFF §8.

Java 21 executes actual candidate bytecode on documented synthetic Minecraft,
NeoForge, graphics, event and attribute APIs. This is NOT a game run or actual
Mixin transformation. The old direct-click possession test is replaced by
separate real inspection/network and real possession-bridge fixtures.
"""
import argparse,hashlib,json,pathlib,subprocess,sys,tempfile,tomllib,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'tests'),str(ROOT/'tools')]
import classedit,gate_build237 as g237,gate_build242 as g242,gate_build245 as g245,patch_npc_stats246 as p
PY=sys.executable;SHA='fe62c48b5bfbb0607cf1fa1e9996649bcd90a144de57ca991dfb737ff78cc819';SIZE=1587651
NAMES={237:'settledlands-0.8.0-build237-corpse-pose.jar',238:'settledlands-0.8.0-build238-corpse-recovery.jar',239:'settledlands-0.8.0-build239-empty-corpse-cleanup.jar',240:'settledlands-0.8.0-build240-npc-local-cult.jar',241:'settledlands-0.8.0-build241-altar-body-respawn.jar',242:'settledlands-0.8.0-build242-body-required.jar',243:'settledlands-0.8.0-build243-body-without-queue.jar',244:'settledlands-0.8.0-build244-corpse-click.jar'}
TARGETS=[
 p.SG+'#validateLoaded(dev.settledlands.npc.NpcDialogueGraph,net.minecraft.nbt.CompoundTag)',p.SG+'#ensure(dev.settledlands.npc.NpcDialogueGraph)',
 p.BS+'#activate(net.minecraft.server.level.ServerPlayer,dev.settledlands.npc.GuideNpc)',p.BS+'#deactivate(net.minecraft.server.level.ServerPlayer)',p.BS+'#resume(net.minecraft.server.level.ServerPlayer)',p.BS+'#onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent)',p.BS+'#clonePlayer(net.neoforged.neoforge.event.entity.player.PlayerEvent$Clone)',
 p.SI+'#specific(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent$EntityInteractSpecific)',p.SI+'#generic(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent$EntityInteract)',p.SN+'#open(net.minecraft.server.level.ServerPlayer,dev.settledlands.npc.GuideNpc,net.minecraft.world.InteractionHand)',p.SN+'#enter(net.minecraft.server.level.ServerPlayer,dev.settledlands.npc.NpcStatsNetwork$Enter)',p.SN+'#register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent)',
 'dev/settledlands/npc/NpcBodilessRules#onTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent)','dev/settledlands/npc/NpcBodilessRules#onEntityTick(net.neoforged.neoforge.event.tick.EntityTickEvent$Pre)',
 p.TARGET_MIXIN+'#settledlands$ignoreBodiless(net.minecraft.world.entity.LivingEntity,net.minecraft.world.entity.LivingEntity,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable)',p.STATS_SCREEN+'#install()']

def annotation(k,body):
 c=classedit.Cursor(body)
 def value():
  tag=chr(c.u1())
  if tag in ('s','c'):return (tag,k.utf8(c.u2()))
  if tag=='e':return (tag,k.utf8(c.u2()),k.utf8(c.u2()))
  if tag=='Z':
   e=k.cp[c.u2()];assert e['tag']==3;return (tag,(e['a']<<16)|e['b'])
  if tag=='@':return (tag,ann())
  if tag=='[':return (tag,[value() for _ in range(c.u2())])
  raise AssertionError('unsupported annotation '+tag)
 def ann():return (k.utf8(c.u2()),{k.utf8(c.u2()):value() for _ in range(c.u2())})
 result=[ann() for _ in range(c.u2())];assert c.p==len(body);return result

def annotations(k,attrs,kind='RuntimeVisibleAnnotations'):
 return annotation(k,next(a['body'] for a in attrs if a['name']==kind))

def static_semantics(base,jar):
 with zipfile.ZipFile(base) as b,zipfile.ZipFile(jar) as z:
  old={n:b.read(n) for n in b.namelist()};new={n:z.read(n) for n in z.namelist()}
 assert set(new)-set(old)==p.ADDED and not set(old)-set(new)
 assert {n for n in old if old[n]!=new[n]}==p.CHANGED
 assert all(not n.startswith(('net/minecraft/','net/neoforged/','org/spongepowered/','com/mojang/')) for n in p.ADDED),'API stubs packaged'
 before=json.loads(old['settledlands.mixins.json']);after=json.loads(new['settledlands.mixins.json'])
 assert after['mixins']==before['mixins']+['BodilessTargetMixin']
 assert {k:v for k,v in after.items() if k!='mixins'}=={k:v for k,v in before.items() if k!='mixins'}
 mix=classedit.Klass(new[p.TARGET_MIXIN+'.class'])
 assert annotations(mix,mix.attrs,'RuntimeInvisibleAnnotations')==[('Lorg/spongepowered/asm/mixin/Mixin;',{'value':('[',[('c','Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;')])})]
 desc='(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V'
 method=mix.find_method('settledlands$ignoreBodiless',desc);assert method and method['acc']&2
 assert annotations(mix,method['attrs'])==[('Lorg/spongepowered/asm/mixin/injection/Inject;',{'method':('[',[('s','test(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z')]),'at':('[',[('@',('Lorg/spongepowered/asm/mixin/injection/At;',{'value':('s','HEAD')}))]),'cancellable':('Z',1)})]
 assert ('dev/settledlands/npc/NpcBodilessRules','rejectTarget',desc) in g242.refs(mix,method)
 for cls,methods in [(p.BS,['onIncomingDamage']),('dev/settledlands/npc/NpcBodilessRules',['onTarget','onEntityTick']),(p.SN,['logout'])]:
  k=classedit.Klass(new[cls+'.class']);assert annotations(k,k.attrs)==[('Lnet/neoforged/fml/common/EventBusSubscriber;',{'modid':('s','settledlands')})]
  for name in methods:
   m=k.find_method(name);assert m and m['acc']&8
   a=annotations(k,m['attrs']);assert len(a)==1 and a[0][0]=='Lnet/neoforged/bus/api/SubscribeEvent;'
   if name=='onIncomingDamage':assert a[0][1]['priority']==('e','Lnet/neoforged/bus/api/EventPriority;','LOWEST')
 ps=classedit.Klass(new[p.PS+'.class']);poss=classedit.Klass(new[p.POSS+'.class'])
 checks=[(ps,'onEntityInteractSpecific',(p.SI,'specific')),(ps,'onWandOpenInventory',(p.SI,'generic')),(ps,'possess',(p.BS,'activate')),(ps,'clearPossessed',(p.BS,'deactivate')),(ps,'onPlayerClone',(p.BS,'clonePlayer')),(ps,'onPlayerLogin',(p.BS,'login')),(poss,'repossess',(p.BS,'resume'))]
 for k,name,want in checks:
  refs=g242.refs(k,k.find_method(name));assert sum(1 for owner,method,_ in refs if (owner,method)==want)==1,(name,want)
 for cls,name,want in [(p.NET,'register',(p.SN,'register')),(p.CLIENT,'lambda$setup$1',(p.STATS_SCREEN,'install')),(p.WEAR_MIXIN,'settledlands$wearAttributeDirty',(p.BS,'attributeDirty')),(p.SCREEN,'<init>',(p.UI,'ensure'))]:
  k=classedit.Klass(new[cls+'.class']);assert any((owner,method)==want for owner,method,_ in g242.refs(k,k.find_method(name))),want
 meta=tomllib.loads(new['META-INF/neoforge.mods.toml'].decode())['mods'][0];assert meta['version']==p.NEW_VERSION and meta['description'].startswith(p.CHANGELOG)
 for name in ('dev/settledlands/npc/GuideCorpseNpc.class','dev/settledlands/npc/GuideNpc.class','dev/settledlands/npc/CorpseDeathGuard.class','dev/settledlands/npc/NoQueueRitual.class','dev/settledlands/npc/BodyWear.class'):
  assert old[name]==new[name],name
 print('OK 246 exact entry diff, no API stubs, mixin arrays/descriptor, events and lifecycle hooks',flush=True)

def main():
 q=argparse.ArgumentParser();q.add_argument('--inputs',required=True,type=pathlib.Path);q.add_argument('--base245',required=True,type=pathlib.Path);q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);a=q.parse_args()
 inputs=a.inputs.resolve();base=a.base245.resolve();java=a.java.resolve();jar=a.jar.resolve();bases={i:inputs/name for i,name in NAMES.items()}
 assert hashlib.sha256(base.read_bytes()).hexdigest()==p.BASE_SHA,'wrong base245'
 assert jar.stat().st_size==SIZE and hashlib.sha256(jar.read_bytes()).hexdigest()==SHA,'wrong 246 candidate'
 version=subprocess.run([str(java),'-version'],capture_output=True,text=True);assert version.returncode==0 and 'version "21.' in version.stderr,version.stderr
 print(f'build246 size={SIZE} sha256={SHA}\nJava: {version.stderr.splitlines()[0]}',flush=True)
 g237.run('complete build245 gate incl. 244–237 and HANDOFF §8',[PY,ROOT/'tests/gate_build245.py','--inputs',inputs,*sum((['--base'+str(i),bases[i]] for i in range(237,245)),[]),'--java',java,'--jar',base],contains=('BUILD 245 GATE: PASS',))
 with tempfile.TemporaryDirectory(prefix='gate-build246-') as d:
  tmp=pathlib.Path(d);rebuilt=tmp/'rebuild.jar'
  g237.run('246 deterministic build',[PY,ROOT/'tools/patch_npc_stats246.py','--base',base,'--out',rebuilt,'--java',java],contains=(SHA,));assert rebuilt.read_bytes()==jar.read_bytes()
  g237.run('246 structural validator',[PY,ROOT/'tools/validate_jar.py',jar,'--base',base],contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
  verify=[PY,ROOT/'tools/verify_jar.py'];g237.run('246 CRC, TOML, locales, mixins and strict diff',verify+[jar,'--base',base]+g237.option_entries('--expect-added',sorted(p.ADDED))+g237.option_entries('--expect-changed',sorted(p.CHANGED)),contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
  static_semantics(base,jar)
  bad=tmp/'crc.jar';bad.write_bytes(g237.corrupt_crc(jar.read_bytes()));g237.run('246 broken CRC is rejected',verify+[bad],success=False,contains=('ПРОБЛЕМЫ: целостность',))
  bad=tmp/'language.jar';g237.alter_zip(jar,bad,remove={'assets/settledlands/lang/ru_ru.json'});g237.run('246 missing language is rejected',verify+[bad],success=False,contains=('ПРОБЛЕМЫ: языки',))
  bad=tmp/'mixin.jar';g237.alter_zip(jar,bad,remove={'settledlands.mixins.json'});g237.run('246 missing mixin config is rejected',verify+[bad],success=False,contains=('ПРОБЛЕМЫ: mixins',))
  with zipfile.ZipFile(jar) as z:mix=tmp/'BodilessTargetMixin.class';mix.write_bytes(z.read(p.TARGET_MIXIN+'.class'))
  g237.run('246 branchless mixin checker',[PY,ROOT/'tools/check_mixin_class.py',mix],contains=('ИТОГ: OK',))
  g237.run('JVM verifier negative control build234',[PY,ROOT/'tools/jvm_verify.py',inputs/g237.NAMES[234],g237.PROBE,'--java',java],success=False,contains=('VerifyError','ИТОГ: ПРОВАЛ'))
  for label,targets in [('245 inherited targets',g245.TARGETS),('246 new targets',TARGETS)]:
   out=g237.run('JVM verifies '+label,[PY,ROOT/'tools/jvm_verify.py',jar,*targets,'--java',java],contains=('ИТОГ: OK',))
   for target in targets:assert 'DECLARED '+target.split('(')[0] in out,target
  for fixture in ('jvm_corpse_load.py','jvm_corpse_spawn.py','jvm_corpse_dress.py','jvm_corpse_realprofile.py'):
   g237.run('237 renderer regression '+fixture,[PY,ROOT/'tests'/fixture,'--java',java,'--base236',inputs/g237.NAMES[236],'--jar',jar],contains=('236 negative control','237 JVM execution'))
  g237.run('239 cleanup regression',[PY,ROOT/'tests/jvm_cleanup239.py','--java',java,'--base238',bases[238],'--jar',jar],contains=('239 JVM execution',))
  for fixture,sig in [('jvm_noqueue243.py','real death/join/clone/drops/altar/recovery'),('jvm_noqueue_altar243.py','no FIFO, multiple players'),('jvm_queue_disabled243.py','real persisted queue API is disabled'),('jvm_server_tick243.py','real server tick -> real level altar scan'),('jvm_login243.py','pending/login')]:
   cmd=[PY,ROOT/'tests/run_legacy246.py',fixture] if fixture=='jvm_noqueue243.py' else [PY,ROOT/'tests'/fixture]
   g237.run('243 no-queue regression '+fixture,cmd+['--java',java,'--base242',bases[242],'--jar',jar],contains=(sig,))
  g237.run('243 read-only cult diagnostic',[PY,ROOT/'tests/jvm_cultprobe246.py','--java',java,'--jar',jar],contains=('read-only cult probe:',))
  for fixture,sig in [('jvm_corpse_click244.py','complete/partial recovery'),('jvm_real_corpse_dialogue244.py','Real GuideNpc.mobInteract JVM'),('jvm_self_escape244.py','Real death/drop/clone/purgatory-exit/click JVM')]:
   g237.run('244 regression '+fixture,[PY,ROOT/'tests'/fixture,'--java',java,'--base243',bases[243],'--jar',jar],contains=(sig,))
  for fixture,sig in [('jvm_body_wear245.py','Body wear: 3 paid lives'),('jvm_wear_login245.py','Actual possession login hook')]:
   g237.run('245 regression '+fixture,[PY,ROOT/'tests/run_legacy246.py',fixture,'--java',java,'--jar',jar],contains=(sig,'PASS'))
  for fixture,sig in [('jvm_graph_stats246.py','Real graph and NBT roundtrip'),('jvm_graph_ui246.py','Real canvas and UI spinners'),('jvm_graph_undo246.py','Real spinner, screen undo/redo'),('jvm_network_stats246.py','Actual network, guards'),('jvm_body_stats246.py','Actual NPC body stats'),('jvm_possession_stats246.py','Real possession bridge'),('jvm_stats_screen246.py','Actual stats screen'),('jvm_gravity246.py','Actual BodilessEffect')]:
   g237.run('246 behavior '+fixture,[PY,ROOT/'tests'/fixture,'--java',java,'--jar',jar],contains=(sig,'PASS'))
  g237.run('245 negative mutants on 246',[PY,ROOT/'tests/mutants_legacy246.py','--java',java,'--jar',jar],contains=('245 behavioral mutants against 246: PASS 18',))
  g237.run('246 negative mutants',[PY,ROOT/'tests/mutants246.py','--java',java,'--jar',jar],contains=('246 behavioral mutants: PASS 32',))
 print('BUILD 246 GATE: PASS (Java 21 + synthetic APIs; Minecraft/NeoForge and actual mixin application NOT run)',flush=True)
if __name__=='__main__':main()
