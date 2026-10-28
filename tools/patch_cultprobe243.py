#!/usr/bin/env python3
"""Read-only, rate-limited server-side attempt diagnostic; never changes gates."""
import pathlib
import sys
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classedit
import patch_altar_corpse241 as p
from patch_earlytex_mixin import u2, u4, attr
from patch_probe233 import Asm
from patch_corpse_pose237 import assert_pool_roundtrip

PROBE = 'dev/settledlands/npc/CultAttemptProbe'
BRIDGE = 'dev/settledlands/npc/PossessionCultBridge'
EV = 'net/neoforged/neoforge/event/entity/player/PlayerInteractEvent$EntityInteractSpecific'
PLAYER = 'net/minecraft/world/entity/player/Player'
HAND = 'net/minecraft/world/InteractionHand'
STACK = 'net/minecraft/world/item/ItemStack'
ITEM = 'net/minecraft/world/item/Item'
GUIDE = 'dev/settledlands/npc/GuideNpc'
LOGGER = 'org/slf4j/Logger'
DESC = '(L' + EV + ';)V'


def build_probe():
    cp=p.Pool(); this_i,super_i,code_name=cp.cls(PROBE),cp.cls('java/lang/Object'),cp.utf8('Code')
    m, fld=cp.method,cp.field
    a=Asm(); a.label('start')
    # Only a server player clicking a live GuideNpc, never a player corpse.
    a.raw(0x2a); a.op(0xb6,m(EV,'getEntity','()L'+PLAYER+';')); a.raw(0x4c,0x2b)
    a.op(0xc1,cp.cls(p.SP)); a.jump(0x99,'done')
    a.raw(0x2a); a.op(0xb6,m(EV,'getTarget','()L'+p.E+';')); a.raw(0x4d,0x2c)
    a.op(0xc1,cp.cls(GUIDE)); a.jump(0x99,'done')
    a.raw(0x2c); a.op(0xc1,cp.cls(p.CORPSE)); a.jump(0x9a,'done')
    a.raw(0x2b); a.op(0xc0,cp.cls(p.SP)); a.raw(0x4e)
    # Bound the per-player map to 256 entries; one log per player/500ms.
    a.op(0xb2,fld(PROBE,'LAST','Ljava/util/Map;'))
    p.invokeinterface(a,cp,'java/util/Map','size','()I',1); a.op(0x11,256); a.jump(0xa1,'map_ok')
    a.op(0xb2,fld(PROBE,'LAST','Ljava/util/Map;')); p.invokeinterface(a,cp,'java/util/Map','clear','()V',1)
    a.label('map_ok'); a.raw(0x2d); a.op(0xb6,m(p.SP,'getUUID','()Ljava/util/UUID;')); p.xlocal(a,'astore',4)
    a.op(0xb8,m('java/lang/System','currentTimeMillis','()J')); a.raw(0x37,5)
    a.op(0xb2,fld(PROBE,'LAST','Ljava/util/Map;')); p.xlocal(a,'aload',4)
    p.invokeinterface(a,cp,'java/util/Map','get','(Ljava/lang/Object;)Ljava/lang/Object;',2)
    a.op(0xc0,cp.cls('java/lang/Long')); p.xlocal(a,'astore',7)
    p.xlocal(a,'aload',7); a.jump(0xc6,'record'); a.raw(0x16,5); p.xlocal(a,'aload',7)
    a.op(0xb6,m('java/lang/Long','longValue','()J')); a.raw(0x65,0x11,0x01,0xf4,0x85,0x94)  # now-last, 500L, lcmp
    a.jump(0x9b,'done')
    a.label('record'); a.op(0xb2,fld(PROBE,'LAST','Ljava/util/Map;')); p.xlocal(a,'aload',4); a.raw(0x16,5)
    a.op(0xb8,m('java/lang/Long','valueOf','(J)Ljava/lang/Long;'))
    p.invokeinterface(a,cp,'java/util/Map','put','(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;',3); a.raw(0x57)
    # Read only exact target-local NBT, actual effect, hand and item type.
    a.raw(0x2c); a.op(0xc0,cp.cls(p.EXT)); p.invokeinterface(a,cp,p.EXT,'getPersistentData','()L'+p.NBT+';',1)
    p.xlocal(a,'astore',8); p.xlocal(a,'aload',8); p.ldc(a,cp,'settledlands_local_vars')
    a.op(0xb6,m(p.NBT,'getCompound','(Ljava/lang/String;)L'+p.NBT+';')); p.xlocal(a,'astore',9)
    a.op(0xb8,m('com/mojang/logging/LogUtils','getLogger','()L'+LOGGER+';'))
    p.ldc(a,cp,'[SettledLands cult probe] player={} npc={} varsType={} cultType={} cultValue={} bodiless={} hand={} item={}')
    a.raw(0x10,8); a.op(0xbd,cp.cls('java/lang/Object'))
    def slot(i, emit):
        a.raw(0x59)
        if i <= 5: a.raw(0x03+i)
        else: a.raw(0x10,i)
        emit(); a.raw(0x53)
    slot(0,lambda:p.xlocal(a,'aload',4))
    slot(1,lambda:(a.raw(0x2c),a.op(0xb6,m(p.E,'getUUID','()Ljava/util/UUID;'))))
    slot(2,lambda:(p.xlocal(a,'aload',8),p.ldc(a,cp,'settledlands_local_vars'),a.op(0xb6,m(p.NBT,'getTagType','(Ljava/lang/String;)B')),
                   a.op(0xb8,m('java/lang/Integer','valueOf','(I)Ljava/lang/Integer;'))))
    slot(3,lambda:(p.xlocal(a,'aload',9),p.ldc(a,cp,'культ'),a.op(0xb6,m(p.NBT,'getTagType','(Ljava/lang/String;)B')),
                   a.op(0xb8,m('java/lang/Integer','valueOf','(I)Ljava/lang/Integer;'))))
    slot(4,lambda:(p.xlocal(a,'aload',9),p.ldc(a,cp,'культ'),a.op(0xb6,m(p.NBT,'getInt','(Ljava/lang/String;)I')),
                   a.op(0xb8,m('java/lang/Integer','valueOf','(I)Ljava/lang/Integer;'))))
    slot(5,lambda:(a.raw(0x2d),a.op(0xb8,m('dev/settledlands/npc/PossessionSystem','isBodiless','(L'+p.SP+';)Z')),
                   a.op(0xb8,m('java/lang/Boolean','valueOf','(Z)Ljava/lang/Boolean;'))))
    slot(6,lambda:(a.raw(0x2a),a.op(0xb6,m(EV,'getHand','()L'+HAND+';'))))
    slot(7,lambda:(a.raw(0x2a),a.op(0xb6,m(EV,'getItemStack','()L'+STACK+';')),a.op(0xb6,m(STACK,'getItem','()L'+ITEM+';'))))
    p.invokeinterface(a,cp,LOGGER,'info','(Ljava/lang/String;[Ljava/lang/Object;)V',3)
    a.label('end'); a.label('done'); a.raw(0xb1)
    a.label('catch'); a.raw(0x57,0xb1)
    code=a.finish(); exc=((a.labels['start'],a.labels['end'],a.labels['catch'],cp.cls('java/lang/Throwable')),)
    clinit=(b'\xbb'+u2(cp.cls('java/util/HashMap'))+b'\x59\xb7'+u2(m('java/util/HashMap','<init>','()V'))+
            b'\xb3'+u2(fld(PROBE,'LAST','Ljava/util/Map;'))+b'\xb1')
    init=b'\x2a\xb7'+u2(m('java/lang/Object','<init>','()V'))+b'\xb1'
    specs=[(0x0002,'<init>','()V',1,1,init,()),(0x0008,'<clinit>','()V',2,0,clinit,()),
           (0x0009,'observe',DESC,8,10,code,exc)]
    fields=u2(1)+u2(0x001a)+u2(cp.utf8('LAST'))+u2(cp.utf8('Ljava/util/Map;'))+u2(0)
    bodies=[]
    for flags,name,desc,stack,locals_,body,exceptions in specs:
        ex=b''.join(u2(s)+u2(e)+u2(h)+u2(t) for s,e,h,t in exceptions)
        info=u2(stack)+u2(locals_)+u4(len(body))+body+u2(len(exceptions))+ex+u2(0)
        bodies.append(u2(flags)+u2(cp.utf8(name))+u2(cp.utf8(desc))+u2(1)+attr(code_name,info))
    return (b'\xca\xfe\xba\xbe'+u2(0)+u2(49)+cp.dump()+u2(0x0031)+u2(this_i)+u2(super_i)+u2(0)+
            fields+u2(len(bodies))+b''.join(bodies)+u2(0))


def patch_bridge(raw):
    k=classedit.Klass(raw);assert_pool_roundtrip(k,raw); method=k.find_method('onInteractSpecific',DESC)
    parts=classedit.code_parts(k,method); code=parts['code'];assert code==b'\x00'*11+b'\xb1'
    ref=k.add_methodref(PROBE,'observe',DESC)
    parts['code']=b'\x2a\xb8'+u2(ref)+b'\x00'*7+b'\xb1'
    return k.dump(method,classedit.build_code_body(k,parts))
