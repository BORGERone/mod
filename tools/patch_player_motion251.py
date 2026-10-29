#!/usr/bin/env python3
"""Candidate 251 from the exact released 250; no third-party JARs are bundled."""
import argparse,hashlib,pathlib,struct,subprocess,sys,tempfile,tomllib,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'))
import build_joml248,gen_api251,classedit,decode,patch_npc_stats246 as prev,patch_touchmotion248 as p248
from patch_corpse_pose237 import find_invoke,constant_ref
BASE_SHA='2187bd12cbbd252570280c850c665945d6ad87b929fd80a4f400217e4c11082d'
NEW_VERSION='0.8.0-build251-crawl-hns'
MODEL=p248.MODEL;BRIDGE='dev/settledlands/model/PlayerMotionBridge';HACK='dev/settledlands/model/HackSlashAccess';ENTITY=p248.ENTITY;STACK=p248.STACK
POSSESSION='dev/settledlands/npc/PossessionSystem';LOGIN='dev/settledlands/npc/PossessionLoginSync251';EVENT='net/neoforged/neoforge/event/entity/player/PlayerEvent$PlayerLoggedInEvent';SYNC='dev/settledlands/npc/PossessionClientSync';SP='net/minecraft/server/level/ServerPlayer'
DESC=p248.DESC;ALIAS=p248.ALIAS;LOGIN_DESC='(L'+EVENT+';)V'
ITEM='(L'+ENTITY+';L'+STACK+';Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ItemStack;Z)V'
BRIDGE_CLASSES={BRIDGE+x+'.class' for x in ('','$Pose','$Scope','$Rig','$Biped')}
ADDED={HACK+x+'.class' for x in ('','$Frame')}|{LOGIN+'.class'}
CHANGED={BRIDGE+'.class',BRIDGE+'$Scope.class',MODEL+'.class',POSSESSION+'.class','META-INF/neoforge.mods.toml','settledlands-patch-info.txt'}
CHANGELOG='Build 251: experimental crawl root rotation, HnS beta2.5/PAL 1.1.6 attack layer transfer after Fresh Animations and Touch\'n Motion, and authoritative possessed appearance resync on login. NPC rendering, first person and gameplay remain unchanged.'
PATCHINFO='сборка 251: экспериментальный наклон ползания, атаки HnS beta2.5/PAL 1.1.6 поверх FA/TnM для Иви-игрока и повторная синхронизация внешности при входе; рендер NPC, первое лицо и игровые механики не изменены.'

def compile_sources(java,work):
 joml=work/'joml';build_joml248.build(java,joml);api=work/'api';out=work/'classes';gen_api251.main(api);out.mkdir()
 compiler=ROOT/'tools/vendor/ecj-3.46.100.jar';assert hashlib.sha256(compiler.read_bytes()).hexdigest()==prev.ECJ_SHA
 sources=sorted(api.rglob('*.java'))+sorted((ROOT/'src/build251').rglob('*.java'))
 subprocess.run([str(java),'-jar',str(compiler),'-21','-encoding','UTF-8','-g:none','-nowarn','-cp',str(joml),'-d',str(out),*map(str,sources)],check=True)
 return {name:(out/name).read_bytes() for name in BRIDGE_CLASSES|ADDED}

def root_position(k,code):
 pc,size=find_invoke(k,code,'com/mojang/blaze3d/vertex/PoseStack','mulPose','(Lorg/joml/Quaternionf;)V')
 assert size==3 and pc==163 and code[pc+size:pc+size+2]==b'\x19\x04'
 return pc+size

def item_position(k,code):
 candidates=[]
 for pc,raw in decode.walk(code):
  if raw[0]==0xb2 and constant_ref(k,struct.unpack('>H',raw[1:3])[0])==('com/mojang/math/Axis','XP','Lcom/mojang/math/Axis;'):
   candidates.append(pc)
 assert len(candidates)==2 and candidates[0]==147 and code[146]==0x2c,candidates
 # A branch targets the preceding aload_2. Insert after that load so every
 # branch runs the hook, preserving the existing stack receiver beneath it.
 return candidates[0]

def item_render(k,code):
 desc='(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V'
 return find_invoke(k,code,'net/minecraft/client/renderer/ItemInHandRenderer','renderItem',desc)[0]

def patch_model(raw):
 root_desc=f'(L{MODEL};L{ENTITY};L{STACK};FLjava/util/IdentityHashMap;)V'
 def root_call(k):
  loads=b'\x2a\x2b\x19\x04\x25\x2a'+prev.op(0xb4,k.add_fieldref(MODEL,'partParents','Ljava/util/IdentityHashMap;'))
  return prev.static_call(k,BRIDGE,'root',root_desc,loads)
 raw=prev.insert_before(raw,ALIAS,DESC,root_position,root_call,stack=5)
 item_desc=f'(L{MODEL};L{ENTITY};L{STACK};Z)V';loads=b'\x2a\x2b\x2c\x15\x06'
 raw=prev.insert_before(raw,'renderActorHeldItem',ITEM,item_position,lambda k:prev.static_call(k,BRIDGE,'itemPosition',item_desc,loads),stack=12)
 # The renderer and all seven original arguments are already on the operand
 # stack. Append this BbModel, then replace only the call (same opcode width).
 # The helper guards PAL item scaling with push/finally-pop, preserving the
 # private Pose.trustedNormals flag even if the optional stage throws.
 raw=prev.insert_before(raw,'renderActorHeldItem',ITEM,item_render,lambda k:b'\x2a',stack=12)
 k=classedit.Klass(raw);m=k.find_method('renderActorHeldItem',ITEM);parts=classedit.code_parts(k,m);code=bytearray(parts['code'])
 at=item_render(k,code);assert code[at]==0xb6
 renderer='net/minecraft/client/renderer/ItemInHandRenderer';buf='net/minecraft/client/renderer/MultiBufferSource'
 call=f'(L{renderer};L{ENTITY};Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZL{STACK};L{buf};IL{MODEL};)V'
 code[at:at+3]=prev.op(0xb8,k.add_methodref(BRIDGE,'renderHeldItem',call));parts['code']=bytes(code)
 return k.dump(m,classedit.build_code_body(k,parts))

def patch_login(raw):
 # Run exactly once after the existing wear/stats hooks and before every early
 # return. Never rewrite the underlying possession, effect or attribute logic.
 after=prev.after_call('dev/settledlands/npc/NpcBodyStats','login',LOGIN_DESC)
 raw=prev.insert_before(raw,'onPlayerLogin',LOGIN_DESC,after,
  lambda k:prev.static_call(k,LOGIN,'login',LOGIN_DESC,b'\x2a'),stack=4)
 # The old sync was located after early returns and now duplicates the new
 # snapshot for a brand-new player. Remove only that old call, preserving its
 # surrounding exception handler and its later possession-change broadcast.
 k=classedit.Klass(raw);m=k.find_method('onPlayerLogin',LOGIN_DESC);parts=classedit.code_parts(k,m);code=bytearray(parts['code'])
 at,size=find_invoke(k,code,SYNC,'pushAllTo','(L'+SP+';)V');assert size==3 and code[at]==0xb8
 code[at:at+3]=b'\x57\x00\x00';parts['code']=bytes(code)
 return k.dump(m,classedit.build_code_body(k,parts))

def patch_entries(entries,compiled):
 assert set(compiled)==BRIDGE_CLASSES|ADDED and not ADDED.intersection(entries)
 updated=dict(entries);updated.update(compiled);updated[MODEL+'.class']=patch_model(entries[MODEL+'.class']);updated[POSSESSION+'.class']=patch_login(entries[POSSESSION+'.class'])
 name='META-INF/neoforge.mods.toml';text=entries[name].decode();old='version="0.8.0-build250-fresh-motion"';marker="description='''"
 assert text.count(old)==1 and text.count(marker)==1;text=text.replace(old,'version="'+NEW_VERSION+'"',1).replace(marker,marker+CHANGELOG+'\n\n',1)
 assert tomllib.loads(text)['mods'][0]['version']==NEW_VERSION;updated[name]=text.encode();updated['settledlands-patch-info.txt']=(PATCHINFO+'\n'+entries['settledlands-patch-info.txt'].decode()).encode()
 changed={n for n in entries if entries[n]!=updated[n]}
 assert changed==CHANGED,changed
 assert set(updated)-set(entries)==ADDED
 return updated

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--base',type=pathlib.Path,required=True);ap.add_argument('--out',type=pathlib.Path,required=True);ap.add_argument('--java',type=pathlib.Path,required=True);a=ap.parse_args()
 assert a.base.resolve()!=a.out.resolve() and hashlib.sha256(a.base.read_bytes()).hexdigest()==BASE_SHA
 with tempfile.TemporaryDirectory(prefix='settledlands-251-compile-') as td:
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
