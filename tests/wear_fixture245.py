#!/usr/bin/env python3
"""Executable stand-ins for Minecraft 1.21.1's max-health attribute APIs.

The game and mixin transformer are NOT present. calculateValue follows vanilla's
three operation passes and RangedAttribute 1..1024 clamp. Real BodyWear and real
mod lifecycle bytecode are loaded from the candidate jar unchanged.
"""
import zipfile
import jvm_noqueue243 as v
import patch_body_wear245 as w
from patch_probe233 import Asm

r, f, t, p = v.r, v.f, v.t, v.p
E, SP, P, NBT, LIVING = w.E, w.SP, w.P, w.NBT, w.LIVING
H, A, AA, ATTR, MOD, OP, RL = w.HOLDER, w.ATTRIBUTE, w.ATTRIBUTES, w.ATTR, w.MOD, w.OP, w.RL
FH = 'fixture/HealthHolder'
LONGSET, LONGITER = t.LONGSET, t.LONGITER
ONEITER = 'fixture/OneLongIterator'


def obj(a, cp, name, desc='()V', args=None): t.obj(a, cp, name, desc, args)
def text(a, cp, s): t.text(a, cp, s)
def interface(a, cp, owner, name, desc, n): p.invokeinterface(a, cp, owner, name, desc, n)
def load(a, i): t.local(a, 'aload', i)
def store(a, i): t.local(a, 'astore', i)
def dl(a, i): p.xlocal(a, 'dload', i)
def ds(a, i): a.raw(0x47+i) if i<4 else a.raw(0x39,i)


def resource():
    def init(cp):
        return (b'\x2a'+t.op(0xb7,cp.method('java/lang/Object','<init>','()V'))+
                b'\x2a\x2b'+t.op(0xb5,cp.field(RL,'name','Ljava/lang/String;'))+b'\xb1')
    def build(cp):
        a=Asm(); a.op(0xbb,cp.cls(RL)); a.raw(0x59,0x2a); text(a,cp,':')
        a.op(0xb6,cp.method('java/lang/String','concat','(Ljava/lang/String;)Ljava/lang/String;'))
        a.raw(0x2b); a.op(0xb6,cp.method('java/lang/String','concat','(Ljava/lang/String;)Ljava/lang/String;'))
        a.op(0xb7,cp.method(RL,'<init>','(Ljava/lang/String;)V')); a.raw(0xb0); return a.finish()
    def equals(cp):
        a=Asm(); a.raw(0x2b); a.op(0xc1,cp.cls(RL)); a.jump(0x99,'no')
        a.raw(0x2a); a.op(0xb4,cp.field(RL,'name','Ljava/lang/String;')); a.raw(0x2b)
        a.op(0xc0,cp.cls(RL)); a.op(0xb4,cp.field(RL,'name','Ljava/lang/String;'))
        a.op(0xb6,cp.method('java/lang/String','equals','(Ljava/lang/Object;)Z')); a.raw(0xac)
        a.label('no'); a.raw(0x03,0xac); return a.finish()
    def init_empty(cp):
        return (b'\x2a'+f.ldc(cp.string(''))+t.op(0xb7,cp.method(RL,'<init>','(Ljava/lang/String;)V'))+b'\xb1')
    return f.fake_class(RL,[(1,'name','Ljava/lang/String;')],[
        (1,'<init>','()V',2,1,init_empty),
        (1,'<init>','(Ljava/lang/String;)V',2,2,init),
        (1,'getNamespace','()Ljava/lang/String;',1,1,lambda cp:f.ldc(cp.string('minecraft'))+b'\xb0'),
        (1,'getPath','()Ljava/lang/String;',1,1,lambda cp:f.ldc(cp.string('overworld'))+b'\xb0'),
        (9,'tryParse','(Ljava/lang/String;)L'+RL+';',3,1,lambda cp:t.op(0xbb,cp.cls(RL))+b'\x59\x2a'+t.op(0xb7,cp.method(RL,'<init>','(Ljava/lang/String;)V'))+b'\xb0'),
        (9,'fromNamespaceAndPath','(Ljava/lang/String;Ljava/lang/String;)L'+RL+';',4,2,build),
        (1,'equals','(Ljava/lang/Object;)Z',2,2,equals),
        (1,'hashCode','()I',1,1,lambda cp:b'\x2a'+t.op(0xb4,cp.field(RL,'name','Ljava/lang/String;'))+
                                        t.op(0xb6,cp.method('java/lang/String','hashCode','()I'))+b'\xac')])


def holder_classes():
    iface=r.interface(H,[r.abstract('value','()Ljava/lang/Object;'),r.abstract('is','(L'+H+';)Z')])
    def init(cp):
        return (b'\x2a'+t.op(0xb7,cp.method('java/lang/Object','<init>','()V'))+
                b'\x2a\x2b'+t.op(0xb5,cp.field(FH,'value','Ljava/lang/Object;'))+b'\xb1')
    def is_(cp):
        a=Asm(); a.raw(0x2a,0x2b); a.jump(0xa6,'no'); a.raw(0x04,0xac); a.label('no'); a.raw(0x03,0xac); return a.finish()
    concrete=f.fake_class(FH,[(1,'value','Ljava/lang/Object;')],[
        (1,'<init>','(Ljava/lang/Object;)V',2,2,init),
        (1,'value','()Ljava/lang/Object;',1,1,r.getfield(FH,'value','Ljava/lang/Object;')),
        (1,'is','(L'+H+';)Z',2,2,is_)],interfaces=[H])
    return iface,concrete


def attribute_type():
    def sanitize(cp):
        # Equivalent to RangedAttribute for ordinary finite numbers.
        a=Asm(); a.raw(0x0f,0x27); a.raw(0x11,0x04,0x00,0x87)
        a.op(0xb8,cp.method('java/lang/Math','min','(DD)D'))
        a.op(0xb8,cp.method('java/lang/Math','max','(DD)D')); a.raw(0xaf); return a.finish()
    return f.fake_class(A,[],[(1,'<init>','()V',1,1,f.empty_init()),(1,'sanitizeValue','(D)D',6,3,sanitize)])


def attributes_class():
    def init(cp):
        a=Asm(); obj(a,cp,FH,'(Ljava/lang/Object;)V',lambda:obj(a,cp,A))
        a.op(0xb3,cp.field(AA,'MAX_HEALTH','L'+H+';')); a.raw(0xb1); return a.finish()
    return f.fake_class(AA,[(9,'MAX_HEALTH','L'+H+';')],[(8,'<clinit>','()V',5,0,init)])


def operations_class():
    def init(cp):
        a=Asm()
        for name in ('ADD_VALUE','ADD_MULTIPLIED_BASE','ADD_MULTIPLIED_TOTAL'):
            obj(a,cp,OP); a.op(0xb3,cp.field(OP,name,'L'+OP+';'))
        a.raw(0xb1); return a.finish()
    return f.fake_class(OP,[(9,n,'L'+OP+';') for n in ('ADD_VALUE','ADD_MULTIPLIED_BASE','ADD_MULTIPLIED_TOTAL')],
                        [(1,'<init>','()V',1,1,f.empty_init()),(8,'<clinit>','()V',2,0,init)])


def modifier_class():
    def init(cp):
        return (b'\x2a'+t.op(0xb7,cp.method('java/lang/Object','<init>','()V'))+
                b'\x2a\x2b'+t.op(0xb5,cp.field(MOD,'id','L'+RL+';'))+
                b'\x2a\x28'+t.op(0xb5,cp.field(MOD,'amount','D'))+
                b'\x2a\x19\x04'+t.op(0xb5,cp.field(MOD,'op','L'+OP+';'))+b'\xb1')
    return f.fake_class(MOD,[(1,'id','L'+RL+';'),(1,'amount','D'),(1,'op','L'+OP+';')],[
        (1,'<init>','(L'+RL+';DL'+OP+';)V',3,5,init),
        (1,'id','()L'+RL+';',1,1,r.getfield(MOD,'id','L'+RL+';')),
        (1,'amount','()D',2,1,r.getfield(MOD,'amount','D',0xaf)),
        (1,'operation','()L'+OP+';',1,1,r.getfield(MOD,'op','L'+OP+';'))])


def attribute_instance():
    def init(cp):
        a=Asm(); a.raw(0x2a); a.op(0xb7,cp.method('java/lang/Object','<init>','()V'))
        a.raw(0x2a,0x27); a.op(0xb5,cp.field(ATTR,'base','D'))
        a.raw(0x2a); obj(a,cp,'java/util/LinkedHashMap'); a.op(0xb5,cp.field(ATTR,'mods','Ljava/util/LinkedHashMap;'))
        a.raw(0xb1); return a.finish()
    def getmods(cp):
        a=Asm(); a.op(0xbb,cp.cls('java/util/LinkedHashSet')); a.raw(0x59,0x2a)
        a.op(0xb4,cp.field(ATTR,'mods','Ljava/util/LinkedHashMap;'))
        a.op(0xb6,cp.method('java/util/LinkedHashMap','values','()Ljava/util/Collection;'))
        a.op(0xb7,cp.method('java/util/LinkedHashSet','<init>','(Ljava/util/Collection;)V')); a.raw(0xb0); return a.finish()
    def addmod(cp):
        a=Asm(); a.raw(0x2a); a.op(0xb4,cp.field(ATTR,'mods','Ljava/util/LinkedHashMap;')); a.raw(0x2b)
        a.op(0xb6,cp.method(MOD,'id','()L'+RL+';')); a.raw(0x2b)
        a.op(0xb6,cp.method('java/util/LinkedHashMap','put','(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;'))
        a.raw(0x57,0xb1); return a.finish()
    def getmod(cp):
        a=Asm(); a.raw(0x2a); a.op(0xb4,cp.field(ATTR,'mods','Ljava/util/LinkedHashMap;')); a.raw(0x2b)
        a.op(0xb6,cp.method('java/util/LinkedHashMap','get','(Ljava/lang/Object;)Ljava/lang/Object;'))
        a.op(0xc0,cp.cls(MOD)); a.raw(0xb0); return a.finish()
    def remove(cp):
        a=Asm(); a.raw(0x2a); a.op(0xb4,cp.field(ATTR,'mods','Ljava/util/LinkedHashMap;')); a.raw(0x2b)
        a.op(0xb6,cp.method('java/util/LinkedHashMap','remove','(Ljava/lang/Object;)Ljava/lang/Object;'))
        a.jump(0xc6,'no'); a.raw(0x04,0xac); a.label('no'); a.raw(0x03,0xac); return a.finish()
    def value(cp):
        # Independent execution of the Minecraft 1.21 algorithm. This model
        # includes the wear modifier; the mod helper excludes it internally.
        a=Asm(); a.raw(0x2a); a.op(0xb4,cp.field(ATTR,'base','D')); ds(a,1)
        for stage,op in enumerate(('ADD_VALUE','ADD_MULTIPLIED_BASE','ADD_MULTIPLIED_TOTAL')):
            if stage==1: dl(a,1); ds(a,3)
            a.raw(0x2a); a.op(0xb4,cp.field(ATTR,'mods','Ljava/util/LinkedHashMap;'))
            a.op(0xb6,cp.method('java/util/LinkedHashMap','values','()Ljava/util/Collection;'))
            interface(a,cp,'java/util/Collection','iterator','()Ljava/util/Iterator;',1); store(a,5)
            a.label('loop'+str(stage)); load(a,5); interface(a,cp,'java/util/Iterator','hasNext','()Z',1)
            a.jump(0x99,'end'+str(stage)); load(a,5); interface(a,cp,'java/util/Iterator','next','()Ljava/lang/Object;',1)
            a.op(0xc0,cp.cls(MOD)); store(a,6); load(a,6); a.op(0xb6,cp.method(MOD,'operation','()L'+OP+';'))
            a.op(0xb2,cp.field(OP,op,'L'+OP+';')); a.jump(0xa6,'loop'+str(stage))
            if stage==0:
                dl(a,1); load(a,6); a.op(0xb6,cp.method(MOD,'amount','()D')); a.raw(0x63); ds(a,1)
            elif stage==1:
                dl(a,3); dl(a,1); load(a,6); a.op(0xb6,cp.method(MOD,'amount','()D')); a.raw(0x6b,0x63); ds(a,3)
            else:
                dl(a,3); a.raw(0x0f); load(a,6); a.op(0xb6,cp.method(MOD,'amount','()D')); a.raw(0x63,0x6b); ds(a,3)
            a.jump(0xa7,'loop'+str(stage)); a.label('end'+str(stage))
        a.op(0xb2,cp.field(AA,'MAX_HEALTH','L'+H+';')); interface(a,cp,H,'value','()Ljava/lang/Object;',1)
        a.op(0xc0,cp.cls(A)); dl(a,3); a.op(0xb6,cp.method(A,'sanitizeValue','(D)D')); a.raw(0xaf)
        return a.finish()
    return f.fake_class(ATTR,[(1,'base','D'),(1,'mods','Ljava/util/LinkedHashMap;')],[
        (1,'<init>','(D)V',3,3,init),
        (1,'setBaseValue','(D)V',3,3,r.setter(ATTR,'base','D',0x27)),
        (1,'getBaseValue','()D',2,1,r.getfield(ATTR,'base','D',0xaf)),
        (1,'getAttribute','()L'+H+';',1,1,lambda cp:t.op(0xb2,cp.field(AA,'MAX_HEALTH','L'+H+';'))+b'\xb0'),
        (1,'getModifiers','()Ljava/util/Set;',3,1,getmods),
        (1,'getModifier','(L'+RL+';)L'+MOD+';',2,2,getmod),
        (1,'addOrReplacePermanentModifier','(L'+MOD+';)V',3,2,addmod),
        (1,'removeModifier','(L'+RL+';)Z',2,2,remove),
        (1,'getValue','()D',6,7,value)])


def living_entity():
    def init(cp):
        a=Asm(); a.raw(0x2a); a.op(0xb7,cp.method(E,'<init>','()V'))
        a.raw(0x2a); obj(a,cp,ATTR,'(D)V',lambda:a.raw(0x10,20,0x87))
        a.op(0xb5,cp.field(LIVING,'attribute','L'+ATTR+';'))
        a.raw(0x2a,0x10,20,0x86); a.op(0xb5,cp.field(LIVING,'health','F')); a.raw(0xb1); return a.finish()
    def maxhealth(cp):
        return b'\x2a'+t.op(0xb4,cp.field(LIVING,'attribute','L'+ATTR+';'))+t.op(0xb6,cp.method(ATTR,'getValue','()D'))+b'\x90\xae'
    def sethealth(cp):
        return (b'\x2a\x0b\x23\x2a'+t.op(0xb6,cp.method(LIVING,'getMaxHealth','()F'))+
                t.op(0xb8,cp.method('java/lang/Math','min','(FF)F'))+
                t.op(0xb8,cp.method('java/lang/Math','max','(FF)F'))+
                t.op(0xb5,cp.field(LIVING,'health','F'))+b'\xb1')
    def dirty(cp):
        # Explicit simulation of the mixin HEAD callback and vanilla clamp.
        a=Asm(); a.raw(0x2a,0x2b); a.op(0xb8,cp.method(w.WEAR,'attributeDirty',w.DIRTY_DESC))
        a.raw(0x2a,0x2a); a.op(0xb6,cp.method(LIVING,'getHealth','()F'))
        a.op(0xb6,cp.method(LIVING,'setHealth','(F)V')); a.raw(0xb1); return a.finish()
    return f.fake_class(LIVING,[(1,'attribute','L'+ATTR+';'),(1,'health','F')],[
        (1,'<init>','()V',5,1,init),
        (1,'getAttribute','(L'+H+';)L'+ATTR+';',1,2,r.getfield(LIVING,'attribute','L'+ATTR+';')),
        (1,'getHealth','()F',1,1,r.getfield(LIVING,'health','F',0xae)),
        (1,'getMaxHealth','()F',2,1,maxhealth),(1,'setHealth','(F)V',4,2,sethealth),
        (1,'fixtureDirty','(L'+H+';)V',2,2,dirty)],super_name=E)


def add_nbt_int(raw):
    def put(k):
        return (b'\x2a'+t.op(0xb4,k.add_fieldref(NBT,'map','Ljava/util/HashMap;'))+b'\x2b\x1c'+
                t.op(0xb8,k.add_methodref('java/lang/Integer','valueOf','(I)Ljava/lang/Integer;'))+
                t.op(0xb6,k.add_methodref('java/util/HashMap','put','(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;'))+b'\x57\xb1')
    def get(k):
        a=Asm(); a.raw(0x2a); a.op(0xb4,k.add_fieldref(NBT,'map','Ljava/util/HashMap;')); a.raw(0x2b)
        a.op(0xb6,k.add_methodref('java/util/HashMap','get','(Ljava/lang/Object;)Ljava/lang/Object;')); a.raw(0x4d,0x2c)
        a.op(0xc1,k.add_class('java/lang/Integer')); a.jump(0x99,'no'); a.raw(0x2c)
        a.op(0xc0,k.add_class('java/lang/Integer')); a.op(0xb6,k.add_methodref('java/lang/Integer','intValue','()I'))
        a.raw(0xac); a.label('no'); a.raw(0x03,0xac); return a.finish()
    return t.rewrite(raw,add_methods=[(1,'getInt','(Ljava/lang/String;)I',2,3,get)],
                     replace=[('putInt','(Ljava/lang/String;I)V',3,3,put)])


def clone_event(raw):
    # 2-arg fixture constructor = death; explicit 3-arg ctor can model End.
    def init_death(k):
        return (b'\x2a\x2c'+t.op(0xb7,k.add_methodref(r.PLAYER_EVENT,'<init>','(L'+P+';)V'))+
                b'\x2a\x2b'+t.op(0xb5,k.add_fieldref(w.CLONE,'original','L'+P+';'))+
                b'\x2a\x04'+t.op(0xb5,k.add_fieldref(w.CLONE,'death','Z'))+b'\xb1')
    def init_flag(k):
        return (b'\x2a\x2b\x2c'+t.op(0xb7,k.add_methodref(w.CLONE,'<init>','(L'+P+';L'+P+';)V'))+
                b'\x2a\x1d'+t.op(0xb5,k.add_fieldref(w.CLONE,'death','Z'))+b'\xb1')
    return t.rewrite(raw,add_fields=[(1,'death','Z')],add_methods=[
        (1,'<init>','(L'+P+';L'+P+';Z)V',3,4,init_flag),
        (1,'isWasDeath','()Z',1,1,lambda k:b'\x2a'+t.op(0xb4,k.add_fieldref(w.CLONE,'death','Z'))+b'\xac')],
        replace=[('<init>','(L'+P+';L'+P+';)V',2,3,init_death)])


def altar_iterator_classes():
    # One optional synthetic altar, used only to exercise the pre-existing
    # near-altar self-exit fallback. No real fastutil or game registry.
    def empty(cp):
        a=Asm(); a.op(0xb2,cp.field(LONGSET,'enabled','Z')); a.jump(0x9a,'not_empty')
        a.raw(0x04,0xac); a.label('not_empty'); a.raw(0x03,0xac); return a.finish()
    def iterator(cp):
        a=Asm(); obj(a,cp,ONEITER); a.raw(0xb0); return a.finish()
    set_class=f.fake_class(LONGSET,[(9,'enabled','Z')],[
        (1,'<init>','()V',1,1,f.empty_init()),(1,'isEmpty','()Z',1,1,empty),
        (1,'iterator','()L'+LONGITER+';',2,1,iterator)])
    def hasnext(cp):
        a=Asm(); a.raw(0x2a); a.op(0xb4,cp.field(ONEITER,'done','Z')); a.jump(0x9a,'no')
        a.raw(0x04,0xac); a.label('no'); a.raw(0x03,0xac); return a.finish()
    def next_(cp):
        a=Asm(); a.raw(0x2a,0x04); a.op(0xb5,cp.field(ONEITER,'done','Z')); a.raw(0x0a)
        a.op(0xb8,cp.method('java/lang/Long','valueOf','(J)Ljava/lang/Long;')); a.raw(0xb0); return a.finish()
    iterator_class=f.fake_class(ONEITER,[(1,'done','Z')],[
        (1,'<init>','()V',1,1,f.empty_init()),(1,'hasNext','()Z',1,1,hasnext),
        (1,'next','()Ljava/lang/Object;',2,1,next_)],interfaces=[LONGITER])
    return set_class,iterator_class


def build(jar):
    classes,_=v.build_classes(jar)
    with zipfile.ZipFile(jar) as z:
        classes[w.WEAR]=z.read(w.WEAR+'.class')
        classes[w.MIXIN]=z.read(w.MIXIN+'.class')
    classes[NBT]=add_nbt_int(classes[NBT]); classes[w.CLONE]=clone_event(classes[w.CLONE])
    classes[RL]=resource(); classes[H],classes[FH]=holder_classes(); classes[A]=attribute_type()
    classes[AA]=attributes_class(); classes[OP]=operations_class(); classes[MOD]=modifier_class()
    classes[ATTR]=attribute_instance(); classes[LIVING]=living_entity()
    classes[LONGSET],classes[ONEITER]=altar_iterator_classes()
    classes[w.POSS]=t.rewrite(classes[w.POSS],add_methods=[
        (9,'fixtureHas'+field,'(Ljava/util/UUID;)Z',2,1,lambda k,field=field:
            t.op(0xb2,k.add_fieldref(w.POSS,field,'Ljava/util/Map;'))+b'\x2a'+
            t.op(0xb9,k.add_interfaceref('java/util/Map','containsKey','(Ljava/lang/Object;)Z'))+b'\x02\x00\xac')
        for field in ('PENDING','STOCK_CORPSES')])
    # Existing sound-holder fixture also implements Holder.is in real MC;
    # no health test calls it, but provide the method to match interface.
    classes[r.FAKE_HOLDER]=t.rewrite(classes[r.FAKE_HOLDER],add_methods=[
        (1,'is','(L'+H+';)Z',1,2,lambda k:b'\x03\xac')])
    return classes
