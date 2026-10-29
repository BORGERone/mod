#!/usr/bin/env python3
"""Release gate 251: full 250–237/HANDOFF §8 on exact base, plus 251 regressions.

Genuine Java 21, BbModel, PAL, user-supplied HnS data, and external EMF/TnM
components are exercised with synthetic APIs/providers. This is NOT a game,
NeoForge/mixin application, or visual test. No external assets are distributed.
"""
import argparse,base64,hashlib,json,pathlib,subprocess,sys,tempfile,tomllib,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'tests'),str(ROOT/'tools')]
import classedit,gate_build237 as g237,gate_build242 as g242,gate_build247 as g247,gate_build249 as g249,gate_build250 as g250,patch_player_motion251 as p
PY=sys.executable
SHA='a3be663ea42de11e815b67b710e589bb92dd2752eb6fbd0d5408fdba3c9d256a';SIZE=1616133

def digest(data):return hashlib.sha256(data).hexdigest()

def static_semantics(base,jar):
 with zipfile.ZipFile(base) as b,zipfile.ZipFile(jar) as z:
  old={n:b.read(n) for n in b.namelist()};new={n:z.read(n) for n in z.namelist()}
 assert set(new)-set(old)==p.ADDED and not set(old)-set(new)
 assert {n for n in old if old[n]!=new[n]}==p.CHANGED
 assert p.ADDED=={p.HACK+'.class',p.HACK+'$Frame.class',p.LOGIN+'.class'}
 assert old['settledlands.mixins.json']==new['settledlands.mixins.json'] and old['settledlands.compat.mixins.json']==new['settledlands.compat.mixins.json']
 assert old['dev/settledlands/model/FreshEmfAccess.class']==new['dev/settledlands/model/FreshEmfAccess.class']
 key=p.MODEL+'.class';assert new[key]==p.patch_model(old[key])
 g242.compare_methods(old[key],new[key],changed={(p.ALIAS,p.DESC),('renderActorHeldItem',p.ITEM)})
 login_key=p.POSSESSION+'.class';assert new[login_key]==p.patch_login(old[login_key])
 g242.compare_methods(old[login_key],new[login_key],changed={('onPlayerLogin',p.LOGIN_DESC)})
 login_class=classedit.Klass(new[login_key]);login_refs=g242.refs(login_class,login_class.find_method('onPlayerLogin',p.LOGIN_DESC))
 assert [x[:2] for x in login_refs[:3]]==[('dev/settledlands/npc/BodyWear','login'),('dev/settledlands/npc/NpcBodyStats','login'),(p.LOGIN,'login')]
 assert not any(x[:2]==(p.SYNC,'pushAllTo') for x in login_refs)
 model=classedit.Klass(new[key]);refs=g242.refs(model,model.find_method(p.ALIAS,p.DESC))
 def unique(owner,name,refs):
  hits=[i for i,r in enumerate(refs) if r[:2]==(owner,name)];assert len(hits)==1,(owner,name,hits);return hits[0]
 root=unique(p.BRIDGE,'root',refs);before=unique(p.BRIDGE,'before',refs);setup=unique(p.BRIDGE,'setup',refs)
 assert refs[root-1][:2]==('com/mojang/blaze3d/vertex/PoseStack','mulPose')
 assert refs[root+1][:2]==('com/mojang/blaze3d/vertex/PoseStack','scale') and refs[root+2][:2]==('com/mojang/blaze3d/vertex/PoseStack','translate')
 assert root<before<setup<unique('net/minecraft/client/model/geom/ModelPart','render',refs)
 item=g242.refs(model,model.find_method('renderActorHeldItem',p.ITEM));pos=unique(p.BRIDGE,'itemPosition',item);render=unique(p.BRIDGE,'renderHeldItem',item)
 assert pos<next(i for i,r in enumerate(item) if r[:2]==('com/mojang/blaze3d/vertex/PoseStack','mulPose'))<render
 assert not any(r[:2]==('net/minecraft/client/renderer/ItemInHandRenderer','renderItem') for r in item)
 assert unique(p.BRIDGE,'render',g242.refs(model,model.find_method('renderActor',p.DESC)))==0
 npc=model.find_method('setupAnim','(Ldev/settledlands/npc/GuideNpc;FFFFFF)V');assert (p.MODEL,'setupAnimRaw',g250.p.RAW) in g242.refs(model,npc)
 for name in p.ADDED|p.CHANGED:
  if name.endswith('.class'):assert classedit.Klass(new[name]).major==65
 meta=tomllib.loads(new['META-INF/neoforge.mods.toml'].decode())['mods'][0];assert meta['version']==p.NEW_VERSION and meta['description'].startswith(p.CHANGELOG)
 assert new['settledlands-patch-info.txt'].decode().startswith(p.PATCHINFO)
 print('OK exact 251 diff: two actor methods and login appearance-sync hook; other NPC/gameplay/FA helper unchanged; no external libraries bundled',flush=True)

def assets_semantics(base,jar):
 # 249's helper also requires PossessionSystem byte-identical; 251 changes
 # only its login hook, independently compared method-for-method above.
 with zipfile.ZipFile(base) as b,zipfile.ZipFile(jar) as z:
  names=[n for n in b.namelist() if n.startswith('assets/')]
  assert names and all(b.read(n)==z.read(n) for n in names),'asset or UV data changed'
  model=json.loads(z.read('assets/settledlands/dragon_default/ivy.bbmodel'))
  texture=base64.b64decode(model['textures'][0]['source'].split(',',1)[1],validate=True)
  assert texture==z.read('assets/settledlands/dragon_default/ivy.png')
  assert model['resolution']=={'width':128,'height':128} and len(model['animations'])==82 and len(model['elements'])==27
  assert digest(texture)=='cdca7f12987915cb5b590a0060f1c4388c8bcc3ada28241ebcd237c1bf49e0ba'
  for name in ('dev/settledlands/model/BbModelParser.class','dev/settledlands/model/BbModelManager.class','dev/settledlands/npc/client/GuideRenderer.class','dev/settledlands/npc/client/PlayerBodyRenderer.class'):
   assert b.read(name)==z.read(name),name
 print('OK identical asset/UV/texture/parser/NPC renderer; possession differs only in verified login method',flush=True)

def main():
 q=argparse.ArgumentParser()
 for name in ('inputs','base245','base246','base247','base248','base249','base250','java','jar','tnm','emf','etf','pal','hns','gson-source'):q.add_argument('--'+name,required=True,type=pathlib.Path)
 a=q.parse_args();inputs=a.inputs.resolve();b245=a.base245.resolve();b246=a.base246.resolve();b247=a.base247.resolve();b248=a.base248.resolve();b249=a.base249.resolve();b250=a.base250.resolve();java=a.java.resolve();jar=a.jar.resolve();tnm=a.tnm.resolve();emf=a.emf.resolve();etf=a.etf.resolve();pal=a.pal.resolve();hns=a.hns.resolve();gson=a.gson_source.resolve()
 assert digest(b245.read_bytes())==g247.g246.p.BASE_SHA and digest(b246.read_bytes())==g247.p.BASE_SHA
 assert digest(b247.read_bytes())==g250.p.BASE_SHA and digest(b248.read_bytes())==g249.g248.SHA and digest(b249.read_bytes())==g249.SHA and digest(b250.read_bytes())==g250.SHA
 assert jar.stat().st_size==SIZE and digest(jar.read_bytes())==SHA,'wrong 251 candidate'
 ver=subprocess.run([str(java),'-version'],capture_output=True,text=True);assert ver.returncode==0 and 'version "21.' in ver.stderr,ver.stderr
 print(f'build251 experimental size={SIZE} sha256={SHA}\nJava: {ver.stderr.splitlines()[0]}',flush=True)
 g237.run('complete 250 gate incl. 249–237 / HANDOFF §8 on the exact 250 base',[PY,ROOT/'tests/gate_build250.py','--inputs',inputs,'--base245',b245,'--base246',b246,'--base247',b247,'--base248',b248,'--base249',b249,'--java',java,'--jar',b250,'--tnm',tnm,'--emf',emf,'--etf',etf],contains=('BUILD 250 GATE: PASS',))
 with tempfile.TemporaryDirectory(prefix='gate-build251-') as d:
  tmp=pathlib.Path(d);rebuilt=tmp/'rebuild.jar'
  g237.run('251 deterministic rebuild',[PY,ROOT/'tools/patch_player_motion251.py','--base',b250,'--out',rebuilt,'--java',java],contains=(SHA,));assert rebuilt.read_bytes()==jar.read_bytes()
  g237.run('251 structural validator',[PY,ROOT/'tools/validate_jar.py',jar,'--base',b250],contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',))
  verify=[PY,ROOT/'tools/verify_jar.py'];opts=['--base',b250]+g237.option_entries('--expect-added',sorted(p.ADDED))+g237.option_entries('--expect-changed',sorted(p.CHANGED))
  g237.run('251 CRC TOML locales mixins exact diff',verify+[jar]+opts,contains=('ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ',));static_semantics(b250,jar);assets_semantics(b250,jar)
  bad=tmp/'crc.jar';bad.write_bytes(g237.corrupt_crc(jar.read_bytes()));g237.run('251 broken CRC rejected',verify+[bad],success=False,contains=('ПРОБЛЕМЫ: целостность',))
  for label,missing in [('language','assets/settledlands/lang/ru_ru.json'),('mixin','settledlands.mixins.json')]:
   bad=tmp/(label+'.jar');g237.alter_zip(jar,bad,remove={missing});g237.run('251 missing '+label+' rejected',verify+[bad],success=False,contains=('ПРОБЛЕМЫ',))
  bad=tmp/'bridge.jar';g237.alter_zip(jar,bad,remove={p.HACK+'$Frame.class'});g237.run('251 incomplete bridge rejected',verify+[bad]+opts,success=False,contains=('ПРОБЛЕМЫ',))
  bad=tmp/'unhooked.jar'
  with zipfile.ZipFile(b250) as z:orig=z.read(p.MODEL+'.class')
  g237.alter_zip(jar,bad,replace={p.MODEL+'.class':orig})
  try:static_semantics(b250,bad)
  except AssertionError:print('OK missing actor/item bridge rejected',flush=True)
  else:raise AssertionError('missing actor bridge not detected')
  targets=['dev/settledlands/model/BbModel#getName','dev/settledlands/model/HackSlashAccess#prepare(net.minecraft.client.player.AbstractClientPlayer)','dev/settledlands/model/HackSlashAccess#parts(java.lang.Object,net.minecraft.client.model.PlayerModel)','dev/settledlands/npc/PossessionSystem#onPlayerLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent)','dev/settledlands/npc/PossessionLoginSync251#login(net.neoforged.neoforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent)']
  out=g237.run('251 JVM verifies real model and PAL adapter declarations',[PY,ROOT/'tools/jvm_verify.py',jar,*targets,'--java',java],contains=('ИТОГ: OK',))
  for target in targets:assert 'DECLARED '+target.split('(')[0] in out,target
  g247.candidate_regressions(jar,inputs,java)
  g237.run('251 relog appearance, real payload and renderer regression',[PY,ROOT/'tests/jvm_login_model251.py','--java',java,'--base250',b250,'--jar',jar],contains=('PASS baseline 250 reproduces missing possessed model','PASS relog model, real payload codec/client handler/renderer','NOT a game reload'))
  g237.run('251 nine verifier-valid relog appearance mutants',[PY,ROOT/'tests/mutants_login251.py','--java',java,'--base250',b250,'--jar',jar],contains=('251 possessed model login behavioral mutants: PASS 9',))
  g237.run('251 preserves bodiless damage/milk',[PY,ROOT/'tests/jvm_bodiless247.py','--java',java,'--jar',jar],contains=('247 actual protection + 246 predicate','PASS'))
  g237.run('251 legacy bodiless mutants',[PY,ROOT/'tests/mutants247.py','--java',java,'--jar',jar],contains=('247 behavioral mutants: PASS 17',))
  g237.run('251 FA bridge regression and 1200 poses',[PY,ROOT/'tests/jvm_fresh_motion250.py','--java',java,'--jar',jar],contains=('51 scenarios + 1200 JOML poses PASS','NOT a game test'))
  g237.run('251 inherited FA negative mutants',[PY,ROOT/'tests/mutants_fresh251.py','--java',java,'--jar',jar],contains=('250 FA behavioral mutants against 251: PASS 21',))
  g237.run('251 actual EMF root and TnM hook with controlled providers',[PY,ROOT/'tests/jvm_real_emf_tnm250.py','--java',java,'--jar',jar,'--tnm',tnm,'--emf',emf,'--etf',etf],contains=('PASS actual EMF 3.3.12 root and TnM hook','NOT a game test'))
  g237.run('251 actual actor, crawl, PAL, nested and rollback scenarios',[PY,ROOT/'tests/jvm_motion251.py','--java',java,'--jar',jar,'--pal',pal],contains=('PASS real BbModel/BbAnimation','PASS actual actor active','PASS actual actor absent','PASS swim-pitch-transition','PASS hns-item-throw','PASS nested-setup','PASS nested-draw','PASS render-error','251 JVM scenarios PASS','NOT a game'))
  g237.run('251 37 calibrated crawl/PAL negative mutants',[PY,ROOT/'tests/mutants251.py','--java',java,'--jar',jar,'--pal',pal],contains=('251 behavioral mutants: PASS 37',))
  g237.run('251 HnS JSON through real PAL controller and item mixin',[PY,ROOT/'tests/jvm_realhns251.py','--java',java,'--jar',jar,'--pal',pal,'--hns',hns,'--gson-source',gson],contains=('PASS real HnS JSON 39 attacks / 195 sampled frames','NOT a game'))
  g237.run('251 four genuine HnS modifiers plus negative controls',[PY,ROOT/'tests/jvm_hns_modifiers251.py','--java',java,'--jar',jar,'--pal',pal,'--hns',hns,'--gson-source',gson],contains=('PASS actual HnS modifiers BodyAlignment, RidingLegBlocker, WeaponReach and CameraHeightSync','4 negative controls','NOT a game'))
  g237.run('251 combined PAL + real EMF root/TnM hook with HnS frames',[PY,ROOT/'tests/jvm_realhns_tnm251.py','--java',java,'--jar',jar,'--pal',pal,'--hns',hns,'--gson-source',gson,'--tnm',tnm,'--emf',emf,'--etf',etf],contains=('PASS real HnS JSON 39 attacks / 195 sampled frames','actual EMF 3.3.12 root and TnM 1.0.4 hook','NOT a game'))
 print('BUILD 251 GATE: PASS (experimental player-only; actual game/NeoForge/visual NOT run; NPC/first person NOT supported)',flush=True)
if __name__=='__main__':main()
