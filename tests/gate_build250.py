#!/usr/bin/env python3
"""Release gate 250: complete 249–237/HANDOFF §8 plus 250 regressions.
Real bytecode on Java 21 with controlled game dependencies; never a game test.
"""
import argparse,hashlib,pathlib,subprocess,sys,tempfile,tomllib,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'tests'),str(ROOT/'tools')]
import classedit,gate_build237 as g237,gate_build242 as g242,gate_build247 as g247,gate_build249 as g249,patch_player_motion250 as p
PY=sys.executable
SHA='2187bd12cbbd252570280c850c665945d6ad87b929fd80a4f400217e4c11082d';SIZE=1608517
def digest(data):return hashlib.sha256(data).hexdigest()

def static_semantics(base,jar):
 with zipfile.ZipFile(base) as b,zipfile.ZipFile(jar) as z:
  old={n:b.read(n) for n in b.namelist()};new={n:z.read(n) for n in z.namelist()}
 assert set(new)-set(old)==p.ADDED and not set(old)-set(new)
 assert {n for n in old if old[n]!=new[n]}==p.CHANGED
 assert all(not n.startswith(('net/minecraft/','net/neoforged/','org/spongepowered/','com/mojang/','org/joml/','strm/','traben/')) for n in p.ADDED)
 assert old['settledlands.mixins.json']==new['settledlands.mixins.json'] and old['settledlands.compat.mixins.json']==new['settledlands.compat.mixins.json']
 key=p.MODEL+'.class';assert new[key]==p.patch_model(old[key]);g242.compare_methods(old[key],new[key],changed={('renderActor',p.DESC)},added={(p.ALIAS,p.DESC)})
 model=classedit.Klass(new[key]);alias=model.find_method(p.ALIAS,p.DESC);refs=g242.refs(model,alias)
 before=[i for i,r in enumerate(refs) if r[:2]==(p.BRIDGE,'before')];setup=[i for i,r in enumerate(refs) if r==(p.BRIDGE,'setup',p.SETUP)]
 assert len(before)==len(setup)==1 and before[0]<setup[0]<next(i for i,r in enumerate(refs) if r[:2]==('net/minecraft/client/model/geom/ModelPart','render'))
 assert not any(r==(p.MODEL,'setupAnimRaw',p.RAW) for r in refs)
 npc=model.find_method('setupAnim','(Ldev/settledlands/npc/GuideNpc;FFFFFF)V');assert (p.MODEL,'setupAnimRaw',p.RAW) in g242.refs(model,npc)
 bridge=classedit.Klass(new[p.BRIDGE+'.class']);assert (p.MODEL,'setupAnimRaw',p.RAW) in g242.refs(bridge,bridge.find_method('setup',p.SETUP))
 for name in p.ADDED:assert classedit.Klass(new[name]).major==65
 meta=tomllib.loads(new['META-INF/neoforge.mods.toml'].decode())['mods'][0];assert meta['version']==p.NEW_VERSION and meta['description'].startswith(p.CHANGELOG)
 for important in ('dev/settledlands/npc/BodilessProtection.class','dev/settledlands/npc/GuideCorpseNpc.class','dev/settledlands/npc/GuideNpc.class','dev/settledlands/npc/BodyWear.class'):
  assert old[important]==new[important],important
 print('OK exact 250 patch; optional API stubs absent; NPC and protection code unchanged',flush=True)

def main():
 q=argparse.ArgumentParser()
 for name in ('inputs','base245','base246','base247','base248','base249','java','jar','tnm','emf','etf'):q.add_argument('--'+name,required=True,type=pathlib.Path)
 q.add_argument('--prevalidated-base-log',type=pathlib.Path,help='use separately completed 249 gate from this workspace; omitted for independently reproducible full gate')
 a=q.parse_args();inputs=a.inputs.resolve();b245=a.base245.resolve();b246=a.base246.resolve();b247=a.base247.resolve();b248=a.base248.resolve();b249=a.base249.resolve();java=a.java.resolve();jar=a.jar.resolve();tnm=a.tnm.resolve();emf=a.emf.resolve();etf=a.etf.resolve()
 assert digest(b245.read_bytes())==g247.g246.p.BASE_SHA and digest(b246.read_bytes())==g247.p.BASE_SHA
 assert digest(b247.read_bytes())==p.BASE_SHA and digest(b248.read_bytes())==g249.g248.SHA and digest(b249.read_bytes())==g249.SHA
 assert jar.stat().st_size==SIZE and digest(jar.read_bytes())==SHA,'wrong 250 candidate'
 ver=subprocess.run([str(java),'-version'],capture_output=True,text=True);assert ver.returncode==0 and 'version "21.' in ver.stderr,ver.stderr
 print(f'build250 experimental size={SIZE} sha256={SHA}\nJava: {ver.stderr.splitlines()[0]}',flush=True)
 if a.prevalidated_base_log:
  log=a.prevalidated_base_log.read_text();assert 'BUILD 249 GATE: PASS' in log and g249.SHA in log;print('OK separately completed full 249 gate incl. §8:',a.prevalidated_base_log,flush=True)
 else:
  g237.run('complete build249 gate incl. 248–237 / HANDOFF §8',[PY,ROOT/'tests/gate_build249.py','--inputs',inputs,'--base245',b245,'--base246',b246,'--base247',b247,'--base248',b248,'--java',java,'--jar',b249,'--tnm',tnm],contains=('BUILD 249 GATE: PASS',))
 with tempfile.TemporaryDirectory(prefix='gate-build250-') as d:
  tmp=pathlib.Path(d);rebuilt=tmp/'rebuild.jar'
  g237.run('250 deterministic rebuild',[PY,ROOT/'tools/patch_player_motion250.py','--base',b247,'--out',rebuilt,'--java',java],contains=(SHA,));assert rebuilt.read_bytes()==jar.read_bytes()
  g237.run('250 structural validator',[PY,ROOT/'tools/validate_jar.py',jar,'--base',b247],contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
  verify=[PY,ROOT/'tools/verify_jar.py'];opts=['--base',b247]+g237.option_entries('--expect-added',sorted(p.ADDED))+g237.option_entries('--expect-changed',sorted(p.CHANGED))
  g237.run('250 CRC TOML locales mixins strict diff',verify+[jar]+opts,contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',));static_semantics(b247,jar);g249.assets_semantics(b247,jar)
  bad=tmp/'crc.jar';bad.write_bytes(g237.corrupt_crc(jar.read_bytes()));g237.run('250 broken CRC rejected',verify+[bad],success=False,contains=('ПРОБЛЕМЫ: целостность',))
  for label,missing in [('language','assets/settledlands/lang/ru_ru.json'),('mixin','settledlands.mixins.json')]:
   bad=tmp/(label+'.jar');g237.alter_zip(jar,bad,remove={missing});g237.run('250 missing '+label+' rejected',verify+[bad],success=False,contains=('ПРОБЛЕМЫ',))
  bad=tmp/'bridge.jar';g237.alter_zip(jar,bad,remove={p.FRESH+'$Run.class'});g237.run('250 incomplete bridge rejected',verify+[bad]+opts,success=False,contains=('ПРОБЛЕМЫ',))
  bad=tmp/'unhooked.jar';
  with zipfile.ZipFile(b247) as z:orig=z.read(p.MODEL+'.class')
  g237.alter_zip(jar,bad,replace={p.MODEL+'.class':orig})
  try:static_semantics(b247,bad)
  except AssertionError:print('OK missing player bridge rejected',flush=True)
  else:raise AssertionError('missing player bridge not detected')
  g237.run('250 JVM verification of actual BbModel',[PY,ROOT/'tools/jvm_verify.py',jar,'dev/settledlands/model/BbModel#getName','--java',java],contains=('DECLARED dev/settledlands/model/BbModel#getName','ИТОГ: OK'))
  g247.candidate_regressions(jar,inputs,java)
  g237.run('250 preserves bodiless damage/milk',[PY,ROOT/'tests/jvm_bodiless247.py','--java',java,'--jar',jar],contains=('247 actual protection + 246 predicate','PASS'))
  g237.run('250 legacy bodiless mutants',[PY,ROOT/'tests/mutants247.py','--java',java,'--jar',jar],contains=('247 behavioral mutants: PASS 17',))
  g237.run('250 real BbModel/BbAnimation with controlled dependencies',[PY,ROOT/'tests/jvm_real_model250.py','--java',java,'--jar',jar],contains=('PASS real BbModel/BbAnimation','PASS actual actor active','PASS actual actor absent','NOT Minecraft'))
  g237.run('250 JVM bridge and 1200 JOML poses',[PY,ROOT/'tests/jvm_fresh_motion250.py','--java',java,'--jar',jar],contains=('51 scenarios + 1200 JOML poses PASS','NOT a game test'))
  g237.run('250 calibrated negative behavioral mutants',[PY,ROOT/'tests/mutants250.py','--java',java,'--jar',jar],contains=('250 behavioral mutants: PASS 21',))
  g237.run('250 actual EMF 3.3.12 root and TnM 1.0.4 hook with controlled providers',[PY,ROOT/'tests/jvm_real_emf_tnm250.py','--java',java,'--jar',jar,'--tnm',tnm,'--emf',emf,'--etf',etf],contains=('PASS actual EMF 3.3.12 root and TnM hook','NOT a game test'))
 print('BUILD 250 GATE: PASS (experimental player-only; FA pack/game/NeoForge/visual test not run; NPC/first person NOT supported)',flush=True)
if __name__=='__main__':main()
