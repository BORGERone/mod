#!/usr/bin/env python3
"""Candidate 252 from the exact released 251; no third-party JARs are bundled."""
import argparse,hashlib,pathlib,subprocess,sys,tempfile,tomllib,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'))
import build_joml248,classedit,gen_api252,patch_npc_stats246 as prev
BASE_SHA='a3be663ea42de11e815b67b710e589bb92dd2752eb6fbd0d5408fdba3c9d256a'
NEW_VERSION='0.8.0-build252-fp-hns'
RENDERER='dev/settledlands/npc/client/PlayerBodyRenderer'
PRE_EVENT='net/neoforged/neoforge/client/event/RenderPlayerEvent$Pre'
PRE_DESC='(L'+PRE_EVENT+';)V'
FP_BRIDGE='dev/settledlands/model/FirstPersonSwordBridge252'
FP_ACCESS='dev/settledlands/model/FirstPersonPalEmfAccess252'
ADDED={
 FP_BRIDGE+'.class',
 FP_ACCESS+'.class',
 FP_ACCESS+'$1.class',
 FP_ACCESS+'$2.class',
 FP_ACCESS+'$Target.class',
 FP_ACCESS+'$PartState.class',
 FP_ACCESS+'$Snapshot.class',
}
CHANGED={RENDERER+'.class','META-INF/neoforge.mods.toml','settledlands-patch-info.txt'}
CHANGELOG='Build 252: preserve HnS beta2.5/PAL 1.1.6 first-person weapon and arm poses across EMF 3.3.9 / Fresh Animations / Touch\'n Motion 1.0.4 so first-person sword swings and BladeTracker reach match PAL. Third-person Ivy rendering, login sync, crawl, NPC rendering and gameplay remain unchanged.'
PATCHINFO='сборка 252: сохранение поз рук и оружия HnS beta2.5/PAL 1.1.6 от первого лица поверх EMF 3.3.9 / Fresh Animations / Touch\'n Motion 1.0.4 (точные взмахи и хитбокс меча по прицелу); 3-е лицо Иви, ползание, синхронизация модели при входе, рендер NPC и игровые механики не изменены.'

def compile_sources(java,work):
 joml=work/'joml';build_joml248.build(java,joml);api=work/'api';out=work/'classes';gen_api252.main(api);out.mkdir()
 compiler=ROOT/'tools/vendor/ecj-3.46.100.jar';assert hashlib.sha256(compiler.read_bytes()).hexdigest()==prev.ECJ_SHA
 sources=sorted(api.rglob('*.java'))+sorted((ROOT/'src/build252').rglob('*.java'))
 subprocess.run([str(java),'-jar',str(compiler),'-21','-encoding','UTF-8','-g:none','-nowarn','-cp',str(joml),'-d',str(out),*map(str,sources)],check=True)
 produced={p.relative_to(out).as_posix() for p in out.rglob('*.class') if p.relative_to(out).as_posix().startswith('dev/settledlands/model/')}-{'dev/settledlands/model/BbModel.class'}
 assert produced==ADDED,produced
 return {name:(out/name).read_bytes() for name in ADDED}

def patch_renderer(raw):
 # Insert 4-byte static call at offset 0 of onRenderPlayerPre(RenderPlayerEvent$Pre)V
 # before spectator/invisible/modelKey/firstPerson early returns, leaving third-person
 # cancellation and BbModel.renderActor untouched.
 return prev.insert_before(
  raw,'onRenderPlayerPre',PRE_DESC,lambda k,code:0,
  lambda k:prev.static_call(k,FP_BRIDGE,'onRenderPlayerPre',PRE_DESC,b'\x2a'),
  stack=7)

def patch_entries(entries,compiled):
 assert set(compiled)==ADDED and not ADDED.intersection(entries)
 updated=dict(entries);updated.update(compiled);updated[RENDERER+'.class']=patch_renderer(entries[RENDERER+'.class'])
 name='META-INF/neoforge.mods.toml';text=entries[name].decode();old='version="0.8.0-build251-crawl-hns"';marker="description='''"
 assert text.count(old)==1 and text.count(marker)==1;text=text.replace(old,'version="'+NEW_VERSION+'"',1).replace(marker,marker+CHANGELOG+'\n\n',1)
 assert tomllib.loads(text)['mods'][0]['version']==NEW_VERSION;updated[name]=text.encode()
 updated['settledlands-patch-info.txt']=(PATCHINFO+'\n'+entries['settledlands-patch-info.txt'].decode()).encode()
 changed={n for n in entries if entries[n]!=updated[n]}
 assert changed==CHANGED,changed
 assert set(updated)-set(entries)==ADDED
 return updated

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--base',type=pathlib.Path,required=True);ap.add_argument('--out',type=pathlib.Path,required=True);ap.add_argument('--java',type=pathlib.Path,required=True);a=ap.parse_args()
 assert a.base.resolve()!=a.out.resolve() and hashlib.sha256(a.base.read_bytes()).hexdigest()==BASE_SHA
 with tempfile.TemporaryDirectory(prefix='settledlands-252-compile-') as td:
  compiled=compile_sources(a.java.resolve(),pathlib.Path(td))
  with zipfile.ZipFile(a.base) as base:
   assert base.testzip() is None and len(base.namelist())==len(set(base.namelist()))
   entries={i.filename:base.read(i.filename) for i in base.infolist()};updated=patch_entries(entries,compiled);a.out.parent.mkdir(parents=True,exist_ok=True)
   with zipfile.ZipFile(a.out,'w') as out:
    for info in base.infolist():out.writestr(info,updated[info.filename])
    for name in sorted(ADDED):
     info=zipfile.ZipInfo(name,(2026,10,29,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;out.writestr(info,updated[name],compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
 with zipfile.ZipFile(a.out) as z:assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
 data=a.out.read_bytes();print(f'CANDIDATE ONLY {a.out}: {len(data)} bytes sha256 {hashlib.sha256(data).hexdigest()}')

if __name__=='__main__':main()
