#!/usr/bin/env python3
"""Deterministic build 246 from exact 245; require full gate before distribution."""
import argparse,hashlib,json,pathlib,struct,subprocess,sys,tempfile,tomllib,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'))
import classedit,decode,gen_api246,patch_body_wear245 as prev,patch_corpse_click244 as v244
from patch_corpse_pose237 import assert_pool_roundtrip,find_invoke,constant_ref
from patch_earlytex_mixin import u2
BASE_SHA='b6825213caa98096881f02659f81f719cb5f86588c22c2899ccca09f139aca86'
ECJ_SHA='74c93172475fee0cd4e6eefb44233516dd40525642da35d009146d97ea598229'
NEW_VERSION='0.8.0-build246-npc-characteristics'
PS='dev/settledlands/npc/PossessionSystem';POSS='dev/settledlands/npc/PlayerCorpsePossession'
WEAR='dev/settledlands/npc/BodyWear';WEAR_MIXIN='dev/settledlands/mixin/BodyWearAttributeMixin'
EFFECT='dev/settledlands/npc/BodilessEffect';GRAPH='dev/settledlands/npc/NpcDialogueGraph';NODE=GRAPH+'$Node'
SCREEN='dev/settledlands/npc/client/NpcDialogueGraphScreen';CANVAS='dev/settledlands/npc/client/NpcGraphCanvas'
CLIENT='dev/settledlands/npc/client/NpcClient';NET='dev/settledlands/npc/NpcNet'
SG='dev/settledlands/npc/NpcStatsGraph';BS='dev/settledlands/npc/NpcBodyStats';SI='dev/settledlands/npc/NpcStatsInteraction';SN='dev/settledlands/npc/NpcStatsNetwork'
UI='dev/settledlands/npc/client/NpcStatsGraphUi';STATS_SCREEN='dev/settledlands/npc/client/NpcStatsScreen';TARGET_MIXIN='dev/settledlands/mixin/BodilessTargetMixin'
GUIDE='dev/settledlands/npc/GuideNpc';SP='net/minecraft/server/level/ServerPlayer';P='net/minecraft/world/entity/player/Player';HOLDER='net/minecraft/core/Holder'
ITEMSTACK='net/minecraft/world/item/ItemStack';GUI='net/minecraft/client/gui/GuiGraphics';FONT='net/minecraft/client/gui/Font'
CLONE='net/neoforged/neoforge/event/entity/player/PlayerEvent$Clone';LOGIN='net/neoforged/neoforge/event/entity/player/PlayerEvent$PlayerLoggedInEvent'
EVENT_S='net/neoforged/neoforge/event/entity/player/PlayerInteractEvent$EntityInteractSpecific';EVENT_G='net/neoforged/neoforge/event/entity/player/PlayerInteractEvent$EntityInteract'
REG='net/neoforged/neoforge/network/event/RegisterPayloadHandlersEvent';ATTRMAP='net/minecraft/world/entity/ai/attributes/AttributeMap';MOBEFFECT='net/minecraft/world/effect/MobEffect'
ATTRIBUTES='net/minecraft/world/entity/ai/attributes/Attributes';OP='net/minecraft/world/entity/ai/attributes/AttributeModifier$Operation';RL='net/minecraft/resources/ResourceLocation'
ADDED={x+'.class' for x in [SG,BS,SI,SN,SN+'$Open',SN+'$Enter',SN+'$Pending','dev/settledlands/npc/NpcBodilessRules',UI,STATS_SCREEN,TARGET_MIXIN]}
CHANGED={x+'.class' for x in [PS,POSS,WEAR_MIXIN,EFFECT,GRAPH,SCREEN,CANVAS,CLIENT,NET]}|{'assets/settledlands/lang/en_us.json','assets/settledlands/lang/ru_ru.json','META-INF/neoforge.mods.toml','settledlands-patch-info.txt','settledlands.mixins.json'}
CHANGELOG=('Build 246: bodiless players have half gravity and are ignored by standard mob targeting. NPCs have individual health, damage and speed characteristics; a server-validated inspection screen precedes possession. Body wear and corpse recovery are retained.')
PATCHINFO=('сборка 246: половинная гравитация и защита бестелесного от стандартного выбора цели мобами; индивидуальные характеристики NPC и экран осмотра с серверной проверкой вселения. Износ тела и возврат вещей сохранены.')
LANG={
'ru_ru':{'gui.settledlands.stats.title':'Характеристики','gui.settledlands.stats.health':'Здоровье','gui.settledlands.stats.damage':'Урон','gui.settledlands.stats.speed':'Скорость','gui.settledlands.stats.enter':'Вселиться','gui.settledlands.stats.health_value':'Здоровье: %s (%s HP)','gui.settledlands.stats.damage_value':'Урон: %s (%s%%)','gui.settledlands.stats.speed_value':'Скорость: %s (%s%%)','gui.settledlands.stats.needs_cult':'Требуется локальный культ ≥ 1','message.settledlands.stats.stale':'Осмотр устарел или условия изменились. Осмотрите NPC снова.'},
'en_us':{'gui.settledlands.stats.title':'Characteristics','gui.settledlands.stats.health':'Health','gui.settledlands.stats.damage':'Damage','gui.settledlands.stats.speed':'Speed','gui.settledlands.stats.enter':'Possess','gui.settledlands.stats.health_value':'Health: %s (%s HP)','gui.settledlands.stats.damage_value':'Damage: %s (%s%%)','gui.settledlands.stats.speed_value':'Speed: %s (%s%%)','gui.settledlands.stats.needs_cult':'Requires local cult ≥ 1','message.settledlands.stats.stale':'Inspection expired or conditions changed. Inspect the NPC again.'}}

def op(byte,index):return bytes([byte])+u2(index)
def compile_sources(java,work):
 compiler=ROOT/'tools/vendor/ecj-3.46.100.jar';assert hashlib.sha256(compiler.read_bytes()).hexdigest()==ECJ_SHA
 api,out=work/'api',work/'classes';gen_api246.main(api);out.mkdir()
 sources=sorted(api.rglob('*.java'))+sorted((ROOT/'src/build246').rglob('*.java'))
 subprocess.run([str(java),'-jar',str(compiler),'-21','-encoding','UTF-8','-g:none','-nowarn','-d',str(out),*map(str,sources)],check=True)
 return {name:(out/name).read_bytes() for name in ADDED}

def new_method(k,name,desc,code,stack,locals_,acc=0x0001):
 assert k.find_method(name,desc) is None,(name,desc)
 return {'acc':acc,'name_idx':k.add_utf8(name),'name':name,'desc_idx':k.add_utf8(desc),'desc':desc,'attrs':[{'name_idx':k.add_utf8('Code'),'name':'Code','body':v244.code_body(stack,locals_,code)}]}

def replace_code(raw,name,desc,code,stack,locals_):
 k=classedit.Klass(raw);assert_pool_roundtrip(k,raw);m=k.find_method(name,desc);assert m
 if callable(code):code=code(k)
 return k.dump(m,v244.code_body(stack,locals_,code))

def forward_alias(raw,name,desc,alias,helper,helper_desc,loads,ret,stack,locals_):
 k=classedit.Klass(raw);assert_pool_roundtrip(k,raw);m=k.find_method(name,desc);assert m and not k.find_method(alias,desc)
 old=next(x['body'] for x in m['attrs'] if x['name']=='Code')
 code=loads+op(0xb8,k.add_methodref(helper[0],helper[1],helper_desc))+bytes([ret])
 raw=k.dump(m,v244.code_body(stack,locals_,code));k=classedit.Klass(raw);m=k.find_method(name,desc)
 alt=dict(m);alt['acc']=(m['acc']&~0x0006)|0x0001;alt['name_idx']=k.add_utf8(alias);alt['name']=alias
 alt['attrs']=[{'name_idx':k.add_utf8('Code'),'name':'Code','body':old}]
 return v244.rebuild(k,k.methods+[alt])

def insert_before(raw,name,desc,selector,build,stack=None):
 k=classedit.Klass(raw);assert_pool_roundtrip(k,raw);m=k.find_method(name,desc);assert m
 parts=classedit.code_parts(k,m);at=selector(k,parts['code']);ins=build(k)
 assert at not in decode.targets(parts['code']),(name,at,'branch would skip insertion')
 parts=classedit.insert(parts,at,ins)
 if stack is not None:parts['max_stack']=max(parts['max_stack'],stack)
 return k.dump(m,classedit.build_code_body(k,parts))

def after_call(owner,name,desc):
 def select(k,c):pc,size=find_invoke(k,c,owner,name,desc);assert size==3;return pc+size
 return select

def static_call(k,owner,name,desc,loads=b''):return loads+op(0xb8,k.add_methodref(owner,name,desc))

def patch_graph(raw):
 k=classedit.Klass(raw);m=k.find_method('error','()Ljava/lang/String;');parts=classedit.code_parts(k,m);code=bytearray(parts['code']);walk=list(decode.walk(code));hits=[]
 for i,(pc,ins) in enumerate(walk[:-2]):
  if ins[0]==0xb4 and constant_ref(k,struct.unpack('>H',ins[1:3])[0])==(NODE,'type','I'):
   pc2,ins2=walk[i+1];_,ins3=walk[i+2]
   if ins2==b'\x10\x0f' and ins3[0]==0xa4:hits.append(pc2)
 assert len(hits)==1,hits;code[hits[0]+1]=16;assert code[7:10]==b'\x11\x08\x00';code[9]=1
 parts['code']=bytes(code);raw=k.dump(m,classedit.build_code_body(k,parts))
 k=classedit.Klass(raw);m=k.find_method('load','(Lnet/minecraft/nbt/CompoundTag;)L'+GRAPH+';');parts=classedit.code_parts(k,m);code=bytearray(parts['code'])
 assert code[31:34]==b'\x11\x08\x00';code[33]=1;parts['code']=bytes(code);raw=k.dump(m,classedit.build_code_body(k,parts))
 k=classedit.Klass(raw);m=k.find_method('error','()Ljava/lang/String;');original=next(x['body'] for x in m['attrs'] if x['name']=='Code')
 wrapper=b'\x2a'+op(0xb6,k.add_methodref(GRAPH,'settledlands$errorOriginal','()Ljava/lang/String;'))+b'\x2a'+op(0xb8,k.add_methodref(SG,'validate','(Ljava/lang/String;L'+GRAPH+';)Ljava/lang/String;'))+b'\xb0'
 raw=k.dump(m,v244.code_body(2,1,wrapper));k=classedit.Klass(raw);m=k.find_method('error','()Ljava/lang/String;')
 alt=dict(m);alt.update(acc=0x0001,name_idx=k.add_utf8('settledlands$errorOriginal'),name='settledlands$errorOriginal',attrs=[{'name_idx':k.add_utf8('Code'),'name':'Code','body':original}]);raw=v244.rebuild(k,k.methods+[alt])
 raw=forward_alias(raw,'add','(III)L'+NODE+';','settledlands$addOriginal',(SG,'add'),'(L'+GRAPH+';III)L'+NODE+';',b'\x2a\x1b\x1c\x1d',0xb0,4,4)
 raw=forward_alias(raw,'remove','(I)Z','settledlands$removeOriginal',(SG,'remove'),'(L'+GRAPH+';I)Z',b'\x2a\x1b',0xac,2,2)
 raw=forward_alias(raw,'connect','(III)Z','settledlands$connectOriginal',(SG,'connect'),'(L'+GRAPH+';III)Z',b'\x2a\x1b\x1c\x1d',0xac,4,4)
 def load_return(k,c):
  ins=list(decode.walk(c));matches=[pc for pc,b in ins if b==b'\xb0'];assert matches and ins[-1][1]==b'\xb0';return matches[-1]
 return insert_before(raw,'load','(Lnet/minecraft/nbt/CompoundTag;)L'+GRAPH+';',load_return,lambda k:static_call(k,SG,'validateLoaded','(L'+GRAPH+';Lnet/minecraft/nbt/CompoundTag;)L'+GRAPH+';',b'\x2a'),2)

def patch_canvas(raw):
 raw=replace_code(raw,'visible','(L'+NODE+';)Z',lambda k:static_call(k,UI,'visible','(L'+CANVAS+';L'+NODE+';)Z',b'\x2a\x2b')+b'\xac',2,2)
 raw=replace_code(raw,'hit','(L'+GRAPH+';DD)I',lambda k:static_call(k,UI,'hit','(L'+CANVAS+';L'+GRAPH+';DD)I',b'\x2a\x2b\x28\x18\x04')+b'\xac',6,6)
 raw=forward_alias(raw,'hitPort','(L'+GRAPH+';DDZ)I','settledlands$hitPortOriginal',(UI,'hitPort'),'(L'+CANVAS+';L'+GRAPH+';DDZ)I',b'\x2a\x2b\x28\x18\x04\x15\x06',0xac,7,7)
 for name,owner in [('focus',NODE),('frameAll',GRAPH)]:
  raw=replace_code(raw,name,'(L'+owner+';)V',lambda k:static_call(k,UI,name,'(L'+CANVAS+';L'+owner+';)V',b'\x2a\x2b')+b'\xb1',2,2)
 return raw

def patch_screen(raw):
 for method,desc,at in [('addAt','(IDD)L'+NODE+';',22),('duplicateNode','(L'+NODE+';DD)L'+NODE+';',26)]:
  k=classedit.Klass(raw);m=k.find_method(method,desc);parts=classedit.code_parts(k,m);code=bytearray(parts['code']);assert code[at:at+3]==b'\x11\x08\x00';code[at+2]=1;parts['code']=bytes(code);raw=k.dump(m,classedit.build_code_body(k,parts))
 k=classedit.Klass(raw);m=k.find_method('renderBackground','(L'+GUI+';IIF)V');parts=classedit.code_parts(k,m);code=bytearray(parts['code'])
 pc,size=find_invoke(k,code,SCREEN,'drawNode','(L'+GUI+';L'+NODE+';)V');assert size==3 and code[pc]==0xb6
 code[pc:pc+3]=op(0xb8,k.add_methodref(UI,'draw','(L'+SCREEN+';L'+GUI+';L'+NODE+';)V'));parts['code']=bytes(code);raw=k.dump(m,classedit.build_code_body(k,parts))
 raw=forward_alias(raw,'mouseClicked','(DDI)Z','settledlands$mouseClickedOriginal',(UI,'mouseClicked'),'(L'+SCREEN+';DDI)Z',b'\x2a\x27\x29\x15\x05',0xac,6,6)
 k=classedit.Klass(raw);methods=[]
 def add(name,desc,body,stack,locals_):methods.append(new_method(k,name,desc,body,stack,locals_))
 def field(name,desc):return op(0xb4,k.add_fieldref(SCREEN,name,desc))
 def call(name,desc):return op(0xb7,k.add_methodref(SCREEN,name,desc))
 add('settledlands$graph','()L'+GRAPH+';',b'\x2a'+field('graph','L'+GRAPH+';')+b'\xb0',1,1)
 add('settledlands$canvas','()L'+CANVAS+';',b'\x2a'+field('canvas','L'+CANVAS+';')+b'\xb0',1,1)
 add('settledlands$font','()L'+FONT+';',b'\x2a'+field('font','L'+FONT+';')+b'\xb0',1,1)
 setref=k.add_interfaceref('java/util/Set','contains','(Ljava/lang/Object;)Z');integer=k.add_methodref('java/lang/Integer','valueOf','(I)Ljava/lang/Integer;')
 add('settledlands$isSelected','(I)Z',b'\x2a'+field('multiSelected','Ljava/util/Set;')+b'\x1b'+op(0xb8,integer)+op(0xb9,setref)+b'\x02\x00\xac',2,2)
 add('settledlands$drawOriginal','(L'+GUI+';L'+NODE+';)V',b'\x2a\x2b\x2c'+call('drawNode','(L'+GUI+';L'+NODE+';)V')+b'\xb1',3,3)
 for bridge,original in [('settledlands$remember','remember'),('settledlands$changed','changed')]:add(bridge,'()V',b'\x2a'+call(original,'()V')+b'\xb1',1,1)
 menu='dev/settledlands/npc/client/NpcGraphContextMenu'
 add('settledlands$contextOpen','()Z',b'\x2a'+field('contextMenu','L'+menu+';')+op(0xb4,k.add_fieldref(menu,'open','Z'))+b'\xac',1,1)
 add('settledlands$insideInspector','(DD)Z',b'\x2a\x27\x29'+call('isInsideInspector','(DD)Z')+b'\xac',5,5)
 add('settledlands$hideStatsInspector','()V',b'\x2a\x02'+op(0xb5,k.add_fieldref(SCREEN,'selected','I'))+b'\x2a'+call('widgets','()V')+b'\xb1',2,1)
 raw=v244.rebuild(k,k.methods+methods)
 def pos(k,c):
  ins=list(decode.walk(c));hits=[]
  for i,(pc,b) in enumerate(ins[:-4]):
   if b[0]!=0xb4 or constant_ref(k,struct.unpack('>H',b[1:3])[0])!=(SCREEN,'graphs','[L'+GRAPH+';'):continue
   if ins[i+1][1]!=b'\x03' or ins[i+2][1]!=b'\x2a':continue
   v=ins[i+3][1]
   if v[0]==0xb4 and constant_ref(k,struct.unpack('>H',v[1:3])[0])==(SCREEN,'graph','L'+GRAPH+';') and ins[i+4][1]==b'\x53':hits.append(ins[i+4][0]+1)
  assert len(hits)==1,hits;return hits[0]
 ctor='(ILdev/settledlands/npc/NpcProfile;Lnet/minecraft/client/gui/screens/Screen;I)V'
 return insert_before(raw,'<init>',ctor,pos,lambda k:static_call(k,UI,'ensure','(L'+SCREEN+';)V',b'\x2a'))

def patch_effect(raw):
 def injection(k):
  code=b'\x2a'+op(0xb2,k.add_fieldref(ATTRIBUTES,'GRAVITY','L'+HOLDER+';'))
  code+=op(0x13,k.add_string('settledlands'))+op(0x13,k.add_string('bodiless_gravity'))
  code+=op(0xb8,k.add_methodref(RL,'fromNamespaceAndPath','(Ljava/lang/String;Ljava/lang/String;)L'+RL+';'))
  code+=b'\x0f\x0f\x0f\x63\x6f\x77'
  code+=op(0xb2,k.add_fieldref(OP,'ADD_MULTIPLIED_TOTAL','L'+OP+';'))
  code+=op(0xb6,k.add_methodref(MOBEFFECT,'addAttributeModifier','(L'+HOLDER+';L'+RL+';DL'+OP+';)L'+MOBEFFECT+';'))+b'\x57'
  return code
 raw=insert_before(raw,'<init>','()V',after_call(MOBEFFECT,'<init>','(Lnet/minecraft/world/effect/MobEffectCategory;I)V'),injection,9)
 k=classedit.Klass(raw);desc='(L'+ATTRMAP+';I)V';body=b'\x2a\x2b\x03'+op(0xb7,k.add_methodref(MOBEFFECT,'addAttributeModifiers',desc))+b'\xb1'
 return v244.rebuild(k,k.methods+[new_method(k,'addAttributeModifiers',desc,body,3,3)])

def patch_possession(raw):
 d='(L'+EVENT_S+';)V';raw=replace_code(raw,'onEntityInteractSpecific',d,lambda k:static_call(k,SI,'specific',d,b'\x2a')+b'\xb1',1,1)
 d='(L'+EVENT_G+';)V';raw=insert_before(raw,'onWandOpenInventory',d,lambda k,c:0,lambda k:static_call(k,SI,'generic',d,b'\x2a'))
 d='(L'+SP+';L'+GUIDE+';)V';sd='(L'+SP+';)V'
 raw=insert_before(raw,'possess',d,after_call(WEAR,'reset',sd),lambda k:static_call(k,BS,'activate',d,b'\x2a\x2b'))
 raw=insert_before(raw,'clearPossessed',sd,after_call(WEAR,'deactivate',sd),lambda k:static_call(k,BS,'deactivate',sd,b'\x2a'))
 ld='(L'+LOGIN+';)V';cd='(L'+CLONE+';)V'
 raw=insert_before(raw,'onPlayerLogin',ld,after_call(WEAR,'login',ld),lambda k:static_call(k,BS,'login',ld,b'\x2a'))
 raw=insert_before(raw,'onPlayerClone',cd,lambda k,c:0,lambda k:static_call(k,BS,'clonePlayer',cd,b'\x2a'))
 k=classedit.Klass(raw);methods=[];tool='(L'+ITEMSTACK+';)Z'
 methods.append(new_method(k,'settledlands$isOpTool',tool,b'\x2a'+op(0xb8,k.add_methodref(PS,'isOpTool',tool))+b'\xac',1,1,0x0009))
 methods.append(new_method(k,'settledlands$possess',d,b'\x2a\x2b'+op(0xb8,k.add_methodref(PS,'possess',d))+b'\xb1',2,2,0x0009))
 return v244.rebuild(k,k.methods+methods)

def patch_playercorpse(raw):
 d='(L'+SP+';Ljava/lang/String;Ljava/lang/String;)V';sd='(L'+SP+';)V'
 return insert_before(raw,'repossess',d,after_call(WEAR,'resume',sd),lambda k:static_call(k,BS,'resume',sd,b'\x2a'))

def patch_wear_mixin(raw):
 d='(L'+HOLDER+';Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V';hd='(Ljava/lang/Object;L'+HOLDER+';)V'
 return insert_before(raw,'settledlands$wearAttributeDirty',d,lambda k,c:0,lambda k:static_call(k,BS,'attributeDirty',hd,b'\x2a\x2b'))

def patch_net(raw):
 d='(L'+REG+';)V';return insert_before(raw,'register',d,lambda k,c:0,lambda k:static_call(k,SN,'register',d,b'\x2a'))

def patch_client(raw):return insert_before(raw,'lambda$setup$1','()V',lambda k,c:0,lambda k:static_call(k,STATS_SCREEN,'install','()V'))

def patch_entries(entries,compiled):
 assert set(compiled)==ADDED and not ADDED.intersection(entries);updated=dict(entries)
 for cls,fn in [(PS,patch_possession),(POSS,patch_playercorpse),(WEAR_MIXIN,patch_wear_mixin),(EFFECT,patch_effect),(GRAPH,patch_graph),(SCREEN,patch_screen),(CANVAS,patch_canvas),(CLIENT,patch_client),(NET,patch_net)]:updated[cls+'.class']=fn(entries[cls+'.class'])
 updated.update(compiled);config=json.loads(entries['settledlands.mixins.json']);assert 'BodilessTargetMixin' not in config['mixins'];config['mixins'].append('BodilessTargetMixin')
 updated['settledlands.mixins.json']=(json.dumps(config,indent=2,ensure_ascii=False)+'\n').encode()
 for locale,extra in LANG.items():
  name='assets/settledlands/lang/'+locale+'.json';doc=json.loads(entries[name]);assert not(set(doc)&set(extra));doc.update(extra);updated[name]=(json.dumps(doc,indent=2,ensure_ascii=False)+'\n').encode()
 name='META-INF/neoforge.mods.toml';text=entries[name].decode();old='version="'+prev.NEW_VERSION+'"';marker="description='''"
 assert text.count(old)==1 and text.count(marker)==1;text=text.replace(old,'version="'+NEW_VERSION+'"',1).replace(marker,marker+CHANGELOG+'\n\n',1);assert tomllib.loads(text)['mods'][0]['version']==NEW_VERSION
 updated[name]=text.encode();updated['settledlands-patch-info.txt']=(PATCHINFO+'\n'+entries['settledlands-patch-info.txt'].decode()).encode()
 assert {n for n in entries if entries[n]!=updated[n]}==CHANGED and set(updated)-set(entries)==ADDED
 return updated

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--base',type=pathlib.Path,required=True);ap.add_argument('--out',type=pathlib.Path,required=True);ap.add_argument('--java',type=pathlib.Path,required=True);a=ap.parse_args()
 assert a.base.resolve()!=a.out.resolve() and hashlib.sha256(a.base.read_bytes()).hexdigest()==BASE_SHA
 with tempfile.TemporaryDirectory(prefix='settledlands-246-compile-') as td:
  compiled=compile_sources(a.java.resolve(),pathlib.Path(td))
  with zipfile.ZipFile(a.base) as base:
   assert base.testzip() is None and len(base.namelist())==len(set(base.namelist()))
   entries={i.filename:base.read(i.filename) for i in base.infolist()};updated=patch_entries(entries,compiled);a.out.parent.mkdir(parents=True,exist_ok=True)
   with zipfile.ZipFile(a.out,'w') as out:
    for info in base.infolist():out.writestr(info,updated[info.filename])
    for name in sorted(ADDED):
     info=zipfile.ZipInfo(name,(2026,10,28,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;out.writestr(info,updated[name],compress_type=zipfile.ZIP_DEFLATED,compresslevel=9)
 with zipfile.ZipFile(a.out) as z:assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
 data=a.out.read_bytes();print(f'{a.out}: {len(data)} bytes sha256 {hashlib.sha256(data).hexdigest()}')
if __name__=='__main__':main()
