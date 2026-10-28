#!/usr/bin/env python3
"""Calibrate behavior fixtures using verifier-valid targeted 246 mutants."""
import argparse,pathlib,struct,subprocess,sys,tempfile,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'tools'),str(ROOT/'tests')]
import classedit,decode,patch_npc_stats246 as p,gate_build244 as g244,gate_build237 as g237
GRAPH='dev/settledlands/npc/NpcDialogueGraph';NODE=GRAPH+'$Node';GUI=p.GUI;S=p.SCREEN;CANVAS=p.CANVAS
BASE='dev/settledlands/npc/';NETWORK=BASE+'NpcStatsNetwork';RULES=BASE+'NpcBodilessRules';DAMAGE='net/neoforged/neoforge/event/entity/living/LivingIncomingDamageEvent';LIVING='net/minecraft/world/entity/LivingEntity'

def mutate(jar,dest,mode):
 with zipfile.ZipFile(jar) as z:raw={name:z.read(name) for name in z.namelist()}
 repl={}
 def replacement(cls,name,body,stack=4):
  key=cls+'.class';k=classedit.Klass(repl.get(key,raw[key]));m=k.find_method(name);assert m,(cls,name)
  parts=classedit.code_parts(k,m);repl[key]=p.replace_code(repl.get(key,raw[key]),name,m['desc'],body,stack,parts['max_locals'])
 def change(cls,name,fn):
  key=cls+'.class';k=classedit.Klass(repl.get(key,raw[key]));m=k.find_method(name);assert m,(cls,name)
  repl[key]=g244.change(repl.get(key,raw[key]),name,m['desc'],fn)
 def alias(name,desc):return lambda k:b'\x2a\x1b\x1c\x1d'+p.op(0xb6,k.add_methodref(GRAPH,name,desc))+b'\xb0'
 def remove_alias(k):return b'\x2a\x1b'+p.op(0xb6,k.add_methodref(GRAPH,'settledlands$removeOriginal','(I)Z'))+b'\xac'
 def connect_alias(k):return b'\x2a\x1b\x1c\x1d'+p.op(0xb6,k.add_methodref(GRAPH,'settledlands$connectOriginal','(III)Z'))+b'\xac'
 def invoke(ref,replacement,index=0):
  def edit(k,c):
   hits=[pc for pc,ins in decode.walk(c) if ins[0] in (0xb6,0xb8) and p.constant_ref(k,struct.unpack('>H',ins[1:3])[0])==ref]
   assert len(hits)>index,(ref,hits);pc=hits[index];assert len(replacement)==3;c[pc:pc+3]=replacement
  return edit
 if mode=='no-node':replacement(p.SG,'ensure',b'\x03\xac')
 elif mode=='no-singleton':replacement(p.SG,'add',alias('settledlands$addOriginal','(III)L'+NODE+';'))
 elif mode=='allow-delete':replacement(p.SG,'remove',remove_alias)
 elif mode=='allow-wires':replacement(p.SG,'connect',connect_alias)
 elif mode=='bad-stat-load':replacement(p.SG,'validateLoaded',b'\x2a\xb0')
 elif mode=='no-clamp':replacement(p.SG,'clamp',b'\x1a\xac')
 elif mode=='no-spinners':replacement(p.UI,'mouseClicked',lambda k:b'\x2a\x27\x29\x15\x05'+p.op(0xb6,k.add_methodref(S,'settledlands$mouseClickedOriginal','(DDI)Z'))+b'\xac',6)
 elif mode=='no-stat-drawing':replacement(p.UI,'draw',b'\xb1')
 elif mode=='screen-capacity':
  def cap(k,c):assert c[22:25]==b'\x11\x08\x01';c[24]=0
  change(S,'addAt',cap)
 elif mode=='no-history':replacement(S,'remember',b'\xb1')
 elif mode=='specific-pass':replacement(p.SI,'specific',b'\xb1')
 elif mode=='generic-pass':replacement(p.SI,'generic',b'\xb1')
 elif mode=='nonce-bypass':change(NETWORK,'enter',invoke(('java/util/UUID','equals','(Ljava/lang/Object;)Z'),b'\x58\x04\x00'))
 elif mode=='nonce-reusable':
  def reuse(k,c):
   hits=[pc for pc,ins in decode.walk(c) if ins[0]==0xb9 and p.constant_ref(k,struct.unpack('>H',ins[1:3])[0])==('java/util/Map','remove','(Ljava/lang/Object;)Ljava/lang/Object;')]
   assert len(hits)==1;c[hits[0]+1:hits[0]+3]=struct.pack('>H',k.add_interfaceref('java/util/Map','get','(Ljava/lang/Object;)Ljava/lang/Object;'))
  change(NETWORK,'enter',reuse)
 elif mode=='cult-bypass':change(NETWORK,'enter',invoke((p.PS,'cult','(L'+p.GUIDE+';)I'),b'\x57\x04\x00'))
 elif mode=='snapshot-bypass':change(NETWORK,'enter',invoke((p.SG,'health','(L'+GRAPH+';)I'),b'\x57\x59\x00'))
 elif mode=='range-bypass':change(NETWORK,'enter',invoke((p.SP,'distanceToSqr','(Lnet/minecraft/world/entity/Entity;)D'),b'\x58\x0e\x00'))
 elif mode=='tool-bypass':change(NETWORK,'enter',invoke((p.PS,'settledlands$isOpTool','(L'+p.ITEMSTACK+';)Z'),b'\x57\x03\x00'))
 elif mode=='effect-bypass':change(NETWORK,'enter',invoke((p.PS,'isBodiless','(L'+p.SP+';)Z'),b'\x57\x04\x00'))
 elif mode=='no-target-event':replacement(RULES,'onTarget',b'\xb1')
 elif mode=='no-target-mixin':replacement(RULES,'rejectTarget',b'\xb1')
 elif mode=='no-target-cleanup':replacement(RULES,'onEntityTick',b'\xb1')
 elif mode=='no-health':replacement(p.BS,'syncHealth',b'\xb1')
 elif mode=='no-damage':replacement(p.BS,'onIncomingDamage',b'\xb1')
 elif mode=='no-speed-or-health-modifier':replacement(p.BS,'setModifier',b'\xb1')
 elif mode=='no-stat-dirty':replacement(p.BS,'attributeDirty',b'\xb1')
 elif mode=='no-stat-clone':replacement(p.BS,'clonePlayer',b'\xb1')
 elif mode=='no-stat-deactivate':replacement(p.BS,'deactivate',b'\xb1')
 elif mode=='no-activation':change(p.PS,'possess',invoke((p.BS,'activate','(L'+p.SP+';L'+p.GUIDE+';)V'),b'\x58\x00\x00'))
 elif mode=='amplified-gravity':
  def amp(k,c):assert c[:3]==b'\x2a\x2b\x03';c[2]=0x1c
  change(p.EFFECT,'addAttributeModifiers',amp)
 elif mode=='reverse-gravity':
  def reverse(k,c):
   needle=b'\x0f\x0f\x0f\x63\x6f\x77';at=bytes(c).find(needle);assert at>=0;c[at+5]=0
  change(p.EFFECT,'<init>',reverse)
 elif mode=='no-stats-screen-button':replacement(p.STATS_SCREEN,'init',b'\xb1')
 else:raise ValueError(mode)
 g237.alter_zip(jar,dest,replace=repl)

CASES=[
 ('no-node','jvm_graph_stats246.py','created node'),('no-singleton','jvm_graph_stats246.py','one node only'),('allow-delete','jvm_graph_stats246.py','not deletable'),('allow-wires','jvm_graph_stats246.py','no wires'),('bad-stat-load','jvm_graph_stats246.py','bad health rejected'),('no-clamp','jvm_graph_stats246.py','stat bounds'),
 ('no-spinners','jvm_graph_ui246.py','health plus'),('no-stat-drawing','jvm_graph_ui246.py','draw three rows'),('screen-capacity','jvm_graph_undo246.py','editor last ordinary slot'),('no-history','jvm_graph_undo246.py','real undo'),
 ('specific-pass','jvm_network_stats246.py','specific opens only'),('generic-pass','jvm_network_stats246.py','generic opens'),('nonce-bypass','jvm_network_stats246.py','forged nonce'),('nonce-reusable','jvm_network_stats246.py','failed token consumed'),('cult-bypass','jvm_network_stats246.py','cult denied'),('snapshot-bypass','jvm_network_stats246.py','changed stats'),('range-bypass','jvm_network_stats246.py','distance'),('tool-bypass','jvm_network_stats246.py','tool changed'),('effect-bypass','jvm_network_stats246.py','effect changed'),
 ('no-target-event','jvm_network_stats246.py','new target filtered'),('no-target-mixin','jvm_network_stats246.py','mixin callback'),('no-target-cleanup','jvm_network_stats246.py','clear existing target'),
 ('no-health','jvm_body_stats246.py','health 11 adds 2'),('no-damage','jvm_body_stats246.py','damage 11 incl weapon bonus'),('no-speed-or-health-modifier','jvm_body_stats246.py','health 11 adds 2'),('no-stat-dirty','jvm_body_stats246.py','external total bonus'),('no-stat-clone','jvm_body_stats246.py','death clone stores'),('no-stat-deactivate','jvm_body_stats246.py','self exit removes'),('no-activation','jvm_possession_stats246.py','NPC stats copied'),
 ('amplified-gravity','jvm_gravity246.py','fixed half gravity amplifier'),('reverse-gravity','jvm_gravity246.py','registered gravity modifier'),('no-stats-screen-button','jvm_stats_screen246.py','button'),
]

def main():
 q=argparse.ArgumentParser();q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);q.add_argument('--only');a=q.parse_args();selected=[x for x in CASES if not a.only or x[0]==a.only];assert selected
 with tempfile.TemporaryDirectory(prefix='mutants246-') as d:
  for mode,fixture,signal in selected:
   bad=pathlib.Path(d)/(mode+'.jar');mutate(a.jar,bad,mode)
   result=subprocess.run([sys.executable,str(ROOT/'tests'/fixture),'--java',str(a.java.resolve()),'--jar',str(bad)],capture_output=True,text=True)
   out=result.stdout+result.stderr
   if result.returncode==0 or 'AssertionError' not in out or signal not in out or 'VerifyError' in out or 'ClassFormatError' in out:
    raise AssertionError(f'uncalibrated mutant {mode}: rc={result.returncode}; expected assertion {signal!r}\n{out}')
   print('OK rejected verifier-valid mutant:',mode,flush=True)
 print('246 behavioral mutants: PASS',len(selected))
if __name__=='__main__':main()
