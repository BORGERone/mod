#!/usr/bin/env python3
"""Experimental 249 from exact 247: isolated vanilla player pose + real TnM retarget."""
import argparse,hashlib,pathlib,struct,subprocess,sys,tempfile,tomllib,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'))
import build_joml248,gen_api249,classedit,decode,patch_npc_stats246 as prev,patch_touchmotion248 as p248
from patch_corpse_pose237 import find_invoke,assert_pool_roundtrip
BASE_SHA=p248.BASE_SHA
NEW_VERSION='0.8.0-build249-player-motion'
MODEL=p248.MODEL;BRIDGE='dev/settledlands/model/PlayerMotionBridge';ENTITY=p248.ENTITY;STACK=p248.STACK;BUF=p248.BUF
DESC=p248.DESC;RAW=p248.RAW;ALIAS=p248.ALIAS
SETUP='(L'+MODEL+';'+RAW[1:]
ADDED={BRIDGE+x+'.class' for x in ('','$Pose','$Scope','$Rig','$Biped')}
CHANGED={MODEL+'.class','META-INF/neoforge.mods.toml','settledlands-patch-info.txt'}
CHANGELOG="Build 249: experimental vanilla player poses plus Touch'n Motion 1.0.4 retargeted to Ivy; embedded actor clips are skipped when the bridge is active. NPC and first person are unchanged."
PATCHINFO="сборка 249: экспериментальный перенос ванильной позы игрока и Touch'n Motion 1.0.4 на Иви; встроенные клипы игрока пропускаются при активном мосте. NPC и первое лицо без изменений."

def compile_sources(java,work):
 joml=work/'joml';build_joml248.build(java,joml);api=work/'api';out=work/'classes';gen_api249.main(api);out.mkdir()
 compiler=ROOT/'tools/vendor/ecj-3.46.100.jar';assert hashlib.sha256(compiler.read_bytes()).hexdigest()==prev.ECJ_SHA
 sources=sorted(api.rglob('*.java'))+sorted((ROOT/'src/build249').rglob('*.java'))
 subprocess.run([str(java),'-jar',str(compiler),'-21','-encoding','UTF-8','-g:none','-nowarn','-cp',str(joml),'-d',str(out),*map(str,sources)],check=True)
 return {name:(out/name).read_bytes() for name in ADDED}

def patch_model(raw):
 raw=prev.insert_before(raw,'renderActor',DESC,p248.before_pos,lambda k:prev.static_call(k,BRIDGE,'before',f'(L{MODEL};L{ENTITY};L{STACK};FLjava/util/IdentityHashMap;)V',b'\x2a\x2b\x19\x04\x25\x2a'+prev.op(0xb4,k.add_fieldref(MODEL,'partParents','Ljava/util/IdentityHashMap;'))))
 k=classedit.Klass(raw);assert_pool_roundtrip(k,raw);m=k.find_method('renderActor',DESC);parts=classedit.code_parts(k,m);code=bytearray(parts['code'])
 at,size=find_invoke(k,code,MODEL,'setupAnimRaw',RAW);assert size==3 and code[at]==0xb6
 code[at:at+3]=prev.op(0xb8,k.add_methodref(BRIDGE,'setup',SETUP));parts['code']=bytes(code);raw=k.dump(m,classedit.build_code_body(k,parts))
 wrapper_desc=f'(L{MODEL};L{ENTITY};FFL{STACK};L{BUF};I)V';loads=b'\x2a\x2b\x24\x25\x19\x04\x19\x05\x15\x06'
 return prev.forward_alias(raw,'renderActor',DESC,ALIAS,(BRIDGE,'render'),wrapper_desc,loads,0xb1,7,7)

def patch_entries(entries,compiled):
 assert set(compiled)==ADDED and not ADDED.intersection(entries);updated=dict(entries);updated.update(compiled);updated[MODEL+'.class']=patch_model(entries[MODEL+'.class'])
 name='META-INF/neoforge.mods.toml';text=entries[name].decode();old='version="0.8.0-build247-bodiless-protection"';marker="description='''"
 assert text.count(old)==1 and text.count(marker)==1;text=text.replace(old,'version="'+NEW_VERSION+'"',1).replace(marker,marker+CHANGELOG+'\n\n',1)
 assert tomllib.loads(text)['mods'][0]['version']==NEW_VERSION;updated[name]=text.encode();updated['settledlands-patch-info.txt']=(PATCHINFO+'\n'+entries['settledlands-patch-info.txt'].decode()).encode()
 assert {n for n in entries if entries[n]!=updated[n]}==CHANGED and set(updated)-set(entries)==ADDED
 return updated

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--base',type=pathlib.Path,required=True);ap.add_argument('--out',type=pathlib.Path,required=True);ap.add_argument('--java',type=pathlib.Path,required=True);a=ap.parse_args()
 assert a.base.resolve()!=a.out.resolve() and hashlib.sha256(a.base.read_bytes()).hexdigest()==BASE_SHA
 with tempfile.TemporaryDirectory(prefix='settledlands-249-compile-') as td:
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
