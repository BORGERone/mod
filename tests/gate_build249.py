#!/usr/bin/env python3
"""Build 249 release gate: complete 248–237/§8 + player animation/asset checks.
Real bytecode runs on Java 21 with synthetic game dependencies; NOT a game/visual test.
"""
import argparse,base64,hashlib,json,pathlib,subprocess,sys,tempfile,tomllib,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'tests'),str(ROOT/'tools')]
import classedit,gate_build237 as g237,gate_build242 as g242,gate_build247 as g247,gate_build248 as g248,patch_player_motion249 as p
PY=sys.executable
SHA='904ea1cb82c43735ec6f18dce60382c0ccafe7654045556fe1dccee10187b272';SIZE=1602595

def digest(b):return hashlib.sha256(b).hexdigest()

def assets_semantics(base,jar):
 with zipfile.ZipFile(base) as b,zipfile.ZipFile(jar) as z:
  names=[n for n in b.namelist() if n.startswith('assets/')]
  assert names and all(b.read(n)==z.read(n) for n in names),'asset or UV data changed relative to 247'
  model=json.loads(z.read('assets/settledlands/dragon_default/ivy.bbmodel'))
  texture=base64.b64decode(model['textures'][0]['source'].split(',',1)[1],validate=True)
  assert texture==z.read('assets/settledlands/dragon_default/ivy.png')
  assert model['resolution']=={'width':128,'height':128} and len(model['animations'])==82
  assert len(model['elements'])==27 and digest(texture)=='cdca7f12987915cb5b590a0060f1c4388c8bcc3ada28241ebcd237c1bf49e0ba'
  # Both paths load a single model name at possession; renderer bytecode remains unchanged.
  for name in ('dev/settledlands/model/BbModelParser.class','dev/settledlands/model/BbModelManager.class','dev/settledlands/npc/client/GuideRenderer.class','dev/settledlands/npc/client/PlayerBodyRenderer.class','dev/settledlands/npc/PossessionSystem.class'):
   assert b.read(name)==z.read(name),name
 print('OK identical 247 asset/UV/texture/parser/renderer bytes; embedded Ivy PNG equals external 128x128; NPC clips retained',flush=True)

def static_semantics(base,jar):
 with zipfile.ZipFile(base) as b,zipfile.ZipFile(jar) as z:
  old={n:b.read(n) for n in b.namelist()};new={n:z.read(n) for n in z.namelist()}
 assert set(new)-set(old)==p.ADDED and not set(old)-set(new)
 assert {n for n in old if old[n]!=new[n]}==p.CHANGED
 assert all(not n.startswith(('net/minecraft/','net/neoforged/','org/spongepowered/','com/mojang/','org/joml/','strm/','traben/')) for n in p.ADDED)
 assert old['settledlands.mixins.json']==new['settledlands.mixins.json'] and old['settledlands.compat.mixins.json']==new['settledlands.compat.mixins.json']
 key=p.MODEL+'.class';assert new[key]==p.patch_model(old[key])
 g242.compare_methods(old[key],new[key],changed={('renderActor',p.DESC)},added={(p.ALIAS,p.DESC)})
 k=classedit.Klass(new[key]);wrapper=k.find_method('renderActor',p.DESC);alias=k.find_method(p.ALIAS,p.DESC)
 refs=g242.refs(k,alias);before=[i for i,r in enumerate(refs) if r[:2]==(p.BRIDGE,'before')];setup=[i for i,r in enumerate(refs) if r==(p.BRIDGE,'setup',p.SETUP)]
 assert len(before)==len(setup)==1 and before[0]<setup[0]<next(i for i,r in enumerate(refs) if r[:2]==('net/minecraft/client/model/geom/ModelPart','render'))
 assert not any(r==(p.MODEL,'setupAnimRaw',p.RAW) for r in refs)
 assert len([r for r in g242.refs(k,wrapper) if r[:2]==(p.BRIDGE,'render')])==1
 npc=k.find_method('setupAnim','(Ldev/settledlands/npc/GuideNpc;FFFFFF)V');assert (p.MODEL,'setupAnimRaw',p.RAW) in g242.refs(k,npc)
 bridge=classedit.Klass(new[p.BRIDGE+'.class']);assert (p.MODEL,'setupAnimRaw',p.RAW) in g242.refs(bridge,bridge.find_method('setup',p.SETUP))
 for name in p.ADDED:assert classedit.Klass(new[name]).major==65
 meta=tomllib.loads(new['META-INF/neoforge.mods.toml'].decode())['mods'][0];assert meta['version']==p.NEW_VERSION and meta['description'].startswith(p.CHANGELOG)
 for important in ('dev/settledlands/npc/BodilessProtection.class','dev/settledlands/npc/GuideCorpseNpc.class','dev/settledlands/npc/GuideNpc.class','dev/settledlands/npc/BodyWear.class'):
  assert old[important]==new[important],important
 print('OK exact 249 patch + wrapper/vanilla bridge; NPC setupAnim and original setupAnimRaw byte-for-byte unchanged',flush=True)

def main():
 q=argparse.ArgumentParser();q.add_argument('--inputs',required=True,type=pathlib.Path);q.add_argument('--base245',required=True,type=pathlib.Path);q.add_argument('--base246',required=True,type=pathlib.Path);q.add_argument('--base247',required=True,type=pathlib.Path);q.add_argument('--base248',required=True,type=pathlib.Path);q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);q.add_argument('--tnm',required=True,type=pathlib.Path);a=q.parse_args()
 inputs=a.inputs.resolve();b245=a.base245.resolve();b246=a.base246.resolve();b247=a.base247.resolve();b248=a.base248.resolve();java=a.java.resolve();jar=a.jar.resolve();tnm=a.tnm.resolve()
 assert digest(b245.read_bytes())==g247.g246.p.BASE_SHA and digest(b246.read_bytes())==g247.p.BASE_SHA
 assert digest(b247.read_bytes())==p.BASE_SHA and digest(b248.read_bytes())==g248.SHA
 assert jar.stat().st_size==SIZE and digest(jar.read_bytes())==SHA,'wrong 249 candidate'
 ver=subprocess.run([str(java),'-version'],capture_output=True,text=True);assert ver.returncode==0 and 'version "21.' in ver.stderr,ver.stderr
 print(f'build249 experimental size={SIZE} sha256={SHA}\nJava: {ver.stderr.splitlines()[0]}',flush=True)
 g237.run('complete build248 gate incl. 247–237 / HANDOFF §8',[PY,ROOT/'tests/gate_build248.py','--inputs',inputs,'--base245',b245,'--base246',b246,'--base247',b247,'--java',java,'--jar',b248,'--tnm',tnm],contains=('BUILD 248 GATE: PASS',))
 with tempfile.TemporaryDirectory(prefix='gate-build249-') as d:
  tmp=pathlib.Path(d);rebuilt=tmp/'rebuild.jar'
  g237.run('249 deterministic rebuild',[PY,ROOT/'tools/patch_player_motion249.py','--base',b247,'--out',rebuilt,'--java',java],contains=(SHA,));assert rebuilt.read_bytes()==jar.read_bytes()
  g237.run('249 structural validator',[PY,ROOT/'tools/validate_jar.py',jar,'--base',b247],contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
  verify=[PY,ROOT/'tools/verify_jar.py'];opts=['--base',b247]+g237.option_entries('--expect-added',sorted(p.ADDED))+g237.option_entries('--expect-changed',sorted(p.CHANGED))
  g237.run('249 CRC TOML locales mixins strict diff',verify+[jar]+opts,contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',));static_semantics(b247,jar);assets_semantics(b247,jar)
  bad=tmp/'crc.jar';bad.write_bytes(g237.corrupt_crc(jar.read_bytes()));g237.run('249 broken CRC rejected',verify+[bad],success=False,contains=('ПРОБЛЕМЫ: целостность',))
  bad=tmp/'lang.jar';g237.alter_zip(jar,bad,remove={'assets/settledlands/lang/ru_ru.json'});g237.run('249 missing language rejected',verify+[bad],success=False,contains=('ПРОБЛЕМЫ: языки',))
  bad=tmp/'mixin.jar';g237.alter_zip(jar,bad,remove={'settledlands.mixins.json'});g237.run('249 missing mixin rejected',verify+[bad],success=False,contains=('ПРОБЛЕМЫ: mixins',))
  bad=tmp/'bridge.jar';g237.alter_zip(jar,bad,remove={p.BRIDGE+'$Rig.class'});g237.run('249 incomplete bridge rejected',verify+[bad]+opts,success=False,contains=('ПРОБЛЕМЫ',))
  bad=tmp/'texture.jar';
  with zipfile.ZipFile(jar) as z:png=bytearray(z.read('assets/settledlands/dragon_default/ivy.png'))
  png[-1]^=1;g237.alter_zip(jar,bad,replace={'assets/settledlands/dragon_default/ivy.png':bytes(png)})
  try:assets_semantics(b247,bad)
  except AssertionError:print('OK 249 changed texture/UV resource rejected',flush=True)
  else:raise AssertionError('texture mutation not detected')
  bad=tmp/'unhooked.jar';
  with zipfile.ZipFile(b247) as z:orig=z.read(p.MODEL+'.class')
  g237.alter_zip(jar,bad,replace={p.MODEL+'.class':orig})
  try:static_semantics(b247,bad)
  except AssertionError:print('OK 249 missing actor bridge rejected',flush=True)
  else:raise AssertionError('missing actor bridge not detected')
  g237.run('249 JVM verification of actual BbModel',[PY,ROOT/'tools/jvm_verify.py',jar,'dev/settledlands/model/BbModel#getName','--java',java],contains=('DECLARED dev/settledlands/model/BbModel#getName','ИТОГ: OK'))
  g247.candidate_regressions(jar,inputs,java)
  g237.run('249 preserves bodiless damage/milk',[PY,ROOT/'tests/jvm_bodiless247.py','--java',java,'--jar',jar],contains=('247 actual protection + 246 predicate','PASS'))
  g237.run('249 legacy bodiless mutants',[PY,ROOT/'tests/mutants247.py','--java',java,'--jar',jar],contains=('247 behavioral mutants: PASS 17',))
  g237.run('249 real BbModel/BbAnimation/actor with synthetic dependencies',[PY,ROOT/'tests/jvm_real_model249.py','--java',java,'--jar',jar],contains=('PASS real BbModel/BbAnimation','PASS actual actor active','PASS actual actor absent','NOT Minecraft'))
  g237.run('249 JVM bridge and 1200 JOML poses',[PY,ROOT/'tests/jvm_player_motion249.py','--java',java,'--jar',jar],contains=('28 scenarios + 1200 poses PASS','NOT a game test'))
  g237.run('249 32 calibrated negative behavioral mutants',[PY,ROOT/'tests/mutants249.py','--java',java,'--jar',jar],contains=('249 behavioral mutants: PASS 32',))
  for extra,marker in [([], 'synthetic feature providers: PASS 27'),(['--real-foot'],'PASS real TnM 1.0.4 foot grounding'),(['--real-torso'],'PASS real TnM 1.0.4 torso')]:
   g237.run('249 real TnM bytecode with controlled stubs '+(' '.join(extra) or 'hook'),[PY,ROOT/'tests/jvm_real_tnm104_249.py','--java',java,'--jar',jar,'--tnm',tnm,*extra],contains=(marker,'NOT a game test'))
 print('BUILD 249 GATE: PASS (experimental player-only; no Minecraft/NeoForge/game/visual test; NPC/first person NOT supported)',flush=True)
if __name__=='__main__':main()
