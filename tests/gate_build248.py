#!/usr/bin/env python3
"""Fail-closed experimental player bridge gate: complete 247–237 / HANDOFF §8.
Actual bytecode runs on Java 21 with controlled test doubles; even the external
TnM algorithm checks are NOT Minecraft, NeoForge, real world IK or visual tests.
The official TnM 1.0.4 JAR is an external GPL input, never bundled/distributed.
"""
import argparse,hashlib,pathlib,subprocess,sys,tempfile,tomllib,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'tests'),str(ROOT/'tools')]
import classedit,gate_build237 as g237,gate_build242 as g242,gate_build247 as g247,patch_touchmotion248 as p
PY=sys.executable;SHA='a07262fb01223f5418e124bd4ad4715d4b8d23bdc318d9ec324c46970e5ed782';SIZE=1599840
T_SH='3754909bf0d12445b18a60de315619734c1229bcc621f88f315ccf076701ab56'


def static_semantics(base,jar):
 with zipfile.ZipFile(base) as b,zipfile.ZipFile(jar) as z:
  old={n:b.read(n) for n in b.namelist()};new={n:z.read(n) for n in z.namelist()}
 assert set(new)-set(old)==p.ADDED and not set(old)-set(new)
 assert {n for n in old if old[n]!=new[n]}==p.CHANGED
 assert all(not n.startswith(('net/minecraft/','net/neoforged/','org/spongepowered/','com/mojang/','org/joml/','strm/','traben/')) for n in p.ADDED)
 assert old['settledlands.mixins.json']==new['settledlands.mixins.json']
 assert old['settledlands.compat.mixins.json']==new['settledlands.compat.mixins.json']
 key=p.MODEL+'.class';assert new[key]==p.patch_model(old[key])
 g242.compare_methods(old[key],new[key],changed={('renderActor',p.DESC)},added={(p.ALIAS,p.DESC)})
 k=classedit.Klass(new[key]);wrapper=k.find_method('renderActor',p.DESC);alias=k.find_method(p.ALIAS,p.DESC)
 refs=g242.refs(k,alias);befores=[i for i,r in enumerate(refs) if r[:2]==(p.BRIDGE,'before')];afters=[i for i,r in enumerate(refs) if r[:2]==(p.BRIDGE,'after')];raw=[i for i,r in enumerate(refs) if r==(p.MODEL,'setupAnimRaw',p.RAW)]
 assert len(befores)==len(afters)==len(raw)==1 and befores[0]<raw[0]<afters[0]
 assert afters[0]<next(i for i,r in enumerate(refs) if r[:2]==('net/minecraft/client/model/geom/ModelPart','render'))
 assert len([r for r in g242.refs(k,wrapper) if r[:2]==(p.BRIDGE,'render')])==1
 for name in p.ADDED:assert classedit.Klass(new[name]).major==65
 meta=tomllib.loads(new['META-INF/neoforge.mods.toml'].decode())['mods'][0];assert meta['version']==p.NEW_VERSION and meta['description'].startswith(p.CHANGELOG)
 for important in ('dev/settledlands/npc/BodilessProtection.class','dev/settledlands/npc/GuideCorpseNpc.class','dev/settledlands/npc/GuideNpc.class','dev/settledlands/npc/BodyWear.class'):
  assert old[important]==new[important],important
 print('OK 248 exact entries + methods; wrapper/before/after around native pose; no stub or TnM bundled',flush=True)


def tnm_signatures(jar):
 assert hashlib.sha256(jar.read_bytes()).hexdigest()==T_SH,'external TnM does not match the indexed 1.0.4 binary'
 checks=[
  ('ModelSpace','before','(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FLjava/util/function/Consumer;)V',0x9),
  ('TouchNMotionHook','applyAll','(Ljava/util/UUID;Ljava/util/function/Function;ZZ)V',0xa),
  ('footgrounding/compat/FootGrounding','recordAnimated','(Ljava/util/UUID;Ljava/util/function/Function;)V',0x9),
  ('interaction/DrawnEntities','inWorld','(Lnet/minecraft/world/entity/Entity;)Z',0x9),
  ('compat/ParCoolActivity','active','(Lnet/minecraft/client/player/AbstractClientPlayer;)Z',0x9),
  ('compat/WholeBody','held','(Lnet/minecraft/client/player/AbstractClientPlayer;)Z',0x9),
 ]
 with zipfile.ZipFile(jar) as z:
  assert z.testzip() is None
  meta=tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode());assert meta['mods'][0]['version']=='1.0.4' and meta['mods'][0]['modId']=='touch_n_motion'
  for owner,name,desc,flags in checks:
   k=classedit.Klass(z.read('strm/touchnmotion/'+owner+'.class'));m=k.find_method(name,desc);assert m and m['acc']&0xb==flags,(owner,name,desc)
 print('OK indexed real TnM 1.0.4 private/public descriptors and exact external SHA',flush=True)


def main():
 q=argparse.ArgumentParser();q.add_argument('--inputs',required=True,type=pathlib.Path);q.add_argument('--base245',required=True,type=pathlib.Path);q.add_argument('--base246',required=True,type=pathlib.Path);q.add_argument('--base247',required=True,type=pathlib.Path);q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);q.add_argument('--tnm',required=True,type=pathlib.Path);a=q.parse_args()
 inputs=a.inputs.resolve();b245=a.base245.resolve();b246=a.base246.resolve();base=a.base247.resolve();java=a.java.resolve();jar=a.jar.resolve();tnm=a.tnm.resolve()
 assert hashlib.sha256(b245.read_bytes()).hexdigest()==g247.g246.p.BASE_SHA
 assert hashlib.sha256(b246.read_bytes()).hexdigest()==g247.p.BASE_SHA
 assert hashlib.sha256(base.read_bytes()).hexdigest()==p.BASE_SHA
 assert jar.stat().st_size==SIZE and hashlib.sha256(jar.read_bytes()).hexdigest()==SHA,'wrong 248 candidate'
 ver=subprocess.run([str(java),'-version'],capture_output=True,text=True);assert ver.returncode==0 and 'version "21.' in ver.stderr,ver.stderr
 print(f'build248 experimental size={SIZE} sha256={SHA}\nJava: {ver.stderr.splitlines()[0]}',flush=True)
 tnm_signatures(tnm)
 g237.run('complete build247 gate incl. 246–237 / HANDOFF §8',[PY,ROOT/'tests/gate_build247.py','--inputs',inputs,'--base245',b245,'--base246',b246,'--java',java,'--jar',base],contains=('BUILD 247 GATE: PASS',))
 with tempfile.TemporaryDirectory(prefix='gate-build248-') as d:
  tmp=pathlib.Path(d);rebuilt=tmp/'rebuild.jar'
  g237.run('248 deterministic build',[PY,ROOT/'tools/patch_touchmotion248.py','--base',base,'--out',rebuilt,'--java',java],contains=(SHA,));assert rebuilt.read_bytes()==jar.read_bytes()
  g237.run('248 structural validator',[PY,ROOT/'tools/validate_jar.py',jar,'--base',base],contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
  verify=[PY,ROOT/'tools/verify_jar.py'];opts=['--base',base]+g237.option_entries('--expect-added',sorted(p.ADDED))+g237.option_entries('--expect-changed',sorted(p.CHANGED))
  g237.run('248 CRC, TOML, locales, mixins and strict diff',verify+[jar]+opts,contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',));static_semantics(base,jar)
  bad=tmp/'crc.jar';bad.write_bytes(g237.corrupt_crc(jar.read_bytes()));g237.run('248 broken CRC rejected',verify+[bad],success=False,contains=('ПРОБЛЕМЫ: целостность',))
  bad=tmp/'language.jar';g237.alter_zip(jar,bad,remove={'assets/settledlands/lang/ru_ru.json'});g237.run('248 missing language rejected',verify+[bad],success=False,contains=('ПРОБЛЕМЫ: языки',))
  bad=tmp/'mixin.jar';g237.alter_zip(jar,bad,remove={'settledlands.mixins.json'});g237.run('248 missing mixin config rejected',verify+[bad],success=False,contains=('ПРОБЛЕМЫ: mixins',))
  bad=tmp/'bridge.jar';g237.alter_zip(jar,bad,remove={p.BRIDGE+'$Rig.class'});g237.run('248 incomplete bridge rejected',verify+[bad]+opts,success=False,contains=('ПРОБЛЕМЫ',))
  bad=tmp/'unhooked.jar';
  with zipfile.ZipFile(base) as b:original_model=b.read(p.MODEL+'.class')
  g237.alter_zip(jar,bad,replace={p.MODEL+'.class':original_model});g237.run('248 missing model hook rejected',verify+[bad]+opts,success=False,contains=('ПРОБЛЕМЫ',))
  g237.run('248 JVM verifies actual BbModel bytecode',[PY,ROOT/'tools/jvm_verify.py',jar,'dev/settledlands/model/BbModel#getName','--java',java],contains=('DECLARED dev/settledlands/model/BbModel#getName','ИТОГ: OK'))
  g247.candidate_regressions(jar,inputs,java)
  g237.run('248 preserves bodiless damage/milk',[PY,ROOT/'tests/jvm_bodiless247.py','--java',java,'--jar',jar],contains=('247 actual protection + 246 predicate','PASS'))
  g237.run('248 legacy bodiless mutants',[PY,ROOT/'tests/mutants247.py','--java',java,'--jar',jar],contains=('247 behavioral mutants: PASS 17',))
  g237.run('248 JVM bridge + 1200 real JOML property poses',[PY,ROOT/'tests/jvm_touchmotion248.py','--java',java,'--jar',jar],contains=('24 guard/rollback/normal scenarios + 1200','PASS'))
  g237.run('248 23 calibrated negative behavioral mutants',[PY,ROOT/'tests/mutants248.py','--java',java,'--jar',jar],contains=('248 behavioral mutants: PASS 23',))
  for extra,marker in [([], 'synthetic feature providers: PASS 23'),(['--real-foot'],'PASS real TnM 1.0.4 foot grounding'),(['--real-torso'],'PASS real TnM 1.0.4 torso')]:
   g237.run('248 actual TnM bytecode with controlled stubs '+(' '.join(extra) or 'hook'),[PY,ROOT/'tests/jvm_real_tnm104_248.py','--java',java,'--jar',jar,'--tnm',tnm,*extra],contains=(marker,'NOT a game test'))
 print('BUILD 248 GATE: PASS (experimental player-only; no actual Minecraft/NeoForge/game/visual test; NPC/first person NOT supported)',flush=True)
if __name__=='__main__':main()
