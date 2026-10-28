#!/usr/bin/env python3
"""Supply explicit synthetic default-stats dependencies for legacy fixtures.

The 246 NpcBodyStats.class itself is loaded from the candidate jar. This module
exists only to keep 245 lifecycle fixtures runnable; non-default stat values
are exercised in jvm_body_stats246.py. No Minecraft/NeoForge runs here.
"""
import zipfile
import wear_fixture245 as x
from patch_probe233 import Asm
r,f,t,w=x.r,x.f,x.t,x.w
E,NBT,LIVING,ATTR,H,AA=x.E,x.NBT,x.LIVING,x.ATTR,x.H,x.AA
SG='dev/settledlands/npc/NpcStatsGraph';BS='dev/settledlands/npc/NpcBodyStats';GRAPH='dev/settledlands/npc/NpcDialogueGraph';PROFILE='dev/settledlands/npc/NpcProfile';GUIDE='dev/settledlands/npc/GuideNpc'

def attrs():
 def init(cp):
  a=Asm()
  for name in ('MAX_HEALTH','MOVEMENT_SPEED'):
   x.obj(a,cp,x.FH,'(Ljava/lang/Object;)V',lambda:x.obj(a,cp,x.A));a.op(0xb3,cp.field(AA,name,'L'+H+';'))
  a.raw(0xb1);return a.finish()
 return f.fake_class(AA,[(9,n,'L'+H+';') for n in ('MAX_HEALTH','MOVEMENT_SPEED')],[(8,'<clinit>','()V',5,0,init)])

def living():
 raw=x.living_entity()
 def init(k):
  a=Asm();a.raw(0x2a);a.op(0xb7,k.add_methodref(E,'<init>','()V'))
  a.raw(0x2a);a.op(0xbb,k.add_class(ATTR));a.raw(0x59,0x10,20,0x87);a.op(0xb7,k.add_methodref(ATTR,'<init>','(D)V'));a.op(0xb5,k.add_fieldref(LIVING,'attribute','L'+ATTR+';'))
  a.raw(0x2a);a.op(0xbb,k.add_class(ATTR));a.raw(0x59,0x10,10,0x87);a.op(0xb7,k.add_methodref(ATTR,'<init>','(D)V'));a.op(0xb5,k.add_fieldref(LIVING,'speedAttribute','L'+ATTR+';'))
  a.raw(0x2a,0x10,20,0x86);a.op(0xb5,k.add_fieldref(LIVING,'health','F'));a.raw(0xb1);return a.finish()
 def get(k):
  a=Asm();a.raw(0x2b);a.op(0xb2,k.add_fieldref(AA,'MOVEMENT_SPEED','L'+H+';'));a.jump(0xa6,'health');a.raw(0x2a);a.op(0xb4,k.add_fieldref(LIVING,'speedAttribute','L'+ATTR+';'));a.raw(0xb0)
  a.label('health');a.raw(0x2a);a.op(0xb4,k.add_fieldref(LIVING,'attribute','L'+ATTR+';'));a.raw(0xb0);return a.finish()
 return t.rewrite(raw,add_fields=[(1,'speedAttribute','L'+ATTR+';')],replace=[('<init>','()V',5,1,init),('getAttribute','(L'+H+';)L'+ATTR+';',2,2,get)])

def install(classes,jar):
 with zipfile.ZipFile(jar) as z:
  if BS+'.class' not in z.namelist():return classes
  classes[BS]=z.read(BS+'.class')
 classes[AA]=attrs();classes[LIVING]=living()
 from classedit import Klass
 def contains_type(k):
  a=Asm();a.raw(0x2a);a.op(0xb4,k.add_fieldref(NBT,'map','Ljava/util/HashMap;'));a.raw(0x2b);a.op(0xb6,k.add_methodref('java/util/HashMap','get','(Ljava/lang/Object;)Ljava/lang/Object;'));a.raw(0x4e,0x1c,0x06);a.jump(0xa0,'compound');a.raw(0x2d);a.op(0xc1,k.add_class('java/lang/Number'));a.raw(0xac);a.label('compound');a.raw(0x2d);a.op(0xc1,k.add_class(NBT));a.raw(0xac);return a.finish()
 if Klass(classes[NBT]).find_method('contains','(Ljava/lang/String;I)Z'):
  classes[NBT]=t.rewrite(classes[NBT],replace=[('contains','(Ljava/lang/String;I)Z',2,4,contains_type)])
 else:
  classes[NBT]=t.rewrite(classes[NBT],add_methods=[(1,'contains','(Ljava/lang/String;I)Z',2,4,contains_type)])
 classes[SG]=f.fake_class(SG,[],[(9,name,'(L'+GRAPH+';)I',1,1,lambda cp:b'\x10\x0a\xac') for name in ('health','damage','speed')]+[(9,'clamp','(I)I',1,1,lambda cp:b'\x1a\xac')])
 classes[GRAPH]=f.blank_class(GRAPH)
 classes[PROFILE]=f.fake_class(PROFILE,[(1,'dialogueGraph','L'+GRAPH+';')],[(1,'<init>','()V',1,1,f.empty_init())])
 if GUIDE in classes:
  from classedit import Klass
  desc='()L'+PROFILE+';';k=Klass(classes[GUIDE]);method=k.find_method('profile',desc)
  value=lambda k:b'\x01\xb0'
  classes[GUIDE]=t.rewrite(classes[GUIDE],**({'replace':[('profile',desc,1,1,value)]} if method else {'add_methods':[(1,'profile',desc,1,1,value)]}))
 return classes
