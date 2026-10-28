#!/usr/bin/env python3
"""Experimental candidate 248, built ONLY from exact 247. Do not distribute without the complete gate."""
import argparse,hashlib,pathlib,subprocess,sys,tempfile,tomllib,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'))
import build_joml248,gen_api248,classedit,decode,patch_npc_stats246 as prev
from patch_corpse_pose237 import find_invoke
BASE_SHA='c3130e136018342d133b710812e6c86fc0fcf5f181146c6973f055abba3798f1'
NEW_VERSION='0.8.0-build248-touchmotion-player'
MODEL='dev/settledlands/model/BbModel';BRIDGE='dev/settledlands/model/TouchMotionBridge'
ENTITY='net/minecraft/world/entity/LivingEntity';STACK='com/mojang/blaze3d/vertex/PoseStack';BUF='net/minecraft/client/renderer/MultiBufferSource'
DESC=f'(L{ENTITY};FFL{STACK};L{BUF};I)V';RAW='(ZZLjava/lang/String;Ljava/lang/String;ZIIFFFFFF)V'
ALIAS='settledlands$renderActorOriginal'
ADDED={BRIDGE+x+'.class' for x in ('','$Pose','$Scope','$Rig')}
CHANGED={MODEL+'.class','META-INF/neoforge.mods.toml','settledlands-patch-info.txt'}
CHANGELOG="Build 248: experimental optional Touch'n Motion 1.0.4 bridge for players using the Ivy model. The NPC itself and first-person arms are not supported by this bridge."
PATCHINFO="сборка 248: экспериментальный необязательный мост Touch'n Motion 1.0.4 для игрока с моделью Иви; сам NPC и руки от первого лица пока не поддерживаются."

def compile_sources(java,work):
 joml=work/'joml';build_joml248.build(java,joml)
 api=work/'api';out=work/'classes';gen_api248.main(api);out.mkdir()
 compiler=ROOT/'tools/vendor/ecj-3.46.100.jar';assert hashlib.sha256(compiler.read_bytes()).hexdigest()==prev.ECJ_SHA
 sources=sorted(api.rglob('*.java'))+sorted((ROOT/'src/build248').rglob('*.java'))
 subprocess.run([str(java),'-jar',str(compiler),'-21','-encoding','UTF-8','-g:none','-nowarn','-cp',str(joml),'-d',str(out),*map(str,sources)],check=True)
 return {name:(out/name).read_bytes() for name in ADDED}

def before_pos(k,c):
 pc,_=find_invoke(k,c,MODEL,'setupAnimRaw',RAW)
 # The empty stack just before setupAnimRaw's argument sequence.
 ins=list(decode.walk(c));idx=next(i for i,(p,_) in enumerate(ins) if p==pc)
 assert [x[1] for x in ins[idx-15:idx-12]]==[b'\x2a',b'\x03',b'\x04']
 pos=ins[idx-15][0];assert c[pos:pos+3]==b'\x2a\x03\x04';return pos

def injected(k,name):
 # this, actor, pose stack, [partial tick], parent map
 loads=b'\x2a\x2b\x19\x04'
 if name=='before':loads+=b'\x25'
 loads+=b'\x2a'+prev.op(0xb4,k.add_fieldref(MODEL,'partParents','Ljava/util/IdentityHashMap;'))
 descriptor=f'(L{MODEL};L{ENTITY};L{STACK};'+('F' if name=='before' else '')+'Ljava/util/IdentityHashMap;)V'
 return prev.static_call(k,BRIDGE,name,descriptor,loads)

def patch_model(raw):
 raw=prev.insert_before(raw,'renderActor',DESC,before_pos,lambda k:injected(k,'before'))
 raw=prev.insert_before(raw,'renderActor',DESC,prev.after_call(MODEL,'setupAnimRaw',RAW),lambda k:injected(k,'after'))
 wrapper_desc=f'(L{MODEL};L{ENTITY};FFL{STACK};L{BUF};I)V'
 loads=b'\x2a\x2b\x24\x25\x19\x04\x19\x05\x15\x06'
 return prev.forward_alias(raw,'renderActor',DESC,ALIAS,(BRIDGE,'render'),wrapper_desc,loads,0xb1,7,7)

def patch_entries(entries,compiled):
 assert set(compiled)==ADDED and not ADDED.intersection(entries);updated=dict(entries);updated.update(compiled)
 updated[MODEL+'.class']=patch_model(entries[MODEL+'.class'])
 name='META-INF/neoforge.mods.toml';text=entries[name].decode();old='version="0.8.0-build247-bodiless-protection"';marker="description='''"
 assert text.count(old)==1 and text.count(marker)==1
 text=text.replace(old,'version="'+NEW_VERSION+'"',1).replace(marker,marker+CHANGELOG+'\n\n',1)
 assert tomllib.loads(text)['mods'][0]['version']==NEW_VERSION;updated[name]=text.encode()
 updated['settledlands-patch-info.txt']=(PATCHINFO+'\n'+entries['settledlands-patch-info.txt'].decode()).encode()
 assert {n for n in entries if entries[n]!=updated[n]}==CHANGED and set(updated)-set(entries)==ADDED
 return updated

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--base',type=pathlib.Path,required=True);ap.add_argument('--out',type=pathlib.Path,required=True);ap.add_argument('--java',type=pathlib.Path,required=True);a=ap.parse_args()
 assert a.base.resolve()!=a.out.resolve() and hashlib.sha256(a.base.read_bytes()).hexdigest()==BASE_SHA
 with tempfile.TemporaryDirectory(prefix='settledlands-248-compile-') as td:
  compiled=compile_sources(a.java.resolve(),pathlib.Path(td))
  with zipfile.ZipFile(a.base) as base:
   assert base.testzip() is None and len(base.namelist())==len(set(base.namelist()))
   entries={i.filename:base.read(i.filename) for i in base.infolist()};updated=patch_entries(entries,compiled);a.out.parent.mkdir(parents=True,exist_ok=True)
   with zipfile.ZipFile(a.out,'w') as out:
    for info in base.infolist():out.writestr(info,updated[info.filename])
    for name in sorted(ADDED):
     info=zipfile.ZipInfo(name,(2026,10,28,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;out.writestr(info,updated[name],compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
 with zipfile.ZipFile(a.out) as z:assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
 data=a.out.read_bytes();print(f'CANDIDATE ONLY {a.out}: {len(data)} bytes sha256 {hashlib.sha256(data).hexdigest()}')
if __name__=='__main__':main()
