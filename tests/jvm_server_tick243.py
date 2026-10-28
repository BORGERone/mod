#!/usr/bin/env python3
"""Execute real manager tick -> level altars -> paid body ritual on Java."""
import argparse
import pathlib
import subprocess
import tempfile
import zipfile
import jvm_noqueue243 as v
from patch_probe233 import Asm

t,r,f,p,q=v.t,v.r,v.f,v.p,v.q
E,SP,SL,L,SERVER,C,NBT,PD,PM,KEY,IT,ITEM,ITEMS,ITEM_ENTITY,BLOCK,PLAYERLIST,POST=(
    v.E,v.SP,v.SL,v.L,v.SERVER,v.C,v.NBT,v.PD,v.PM,v.KEY,v.IT,v.ITEM,v.ITEMS,v.ITEM_ENTITY,v.BLOCK,v.PLAYERLIST,v.POST)
LONGSET=t.LONGSET
LONGITER='it/unimi/dsi/fastutil/longs/LongIterator'
IT_IMPL='fixture/LongIterator'
DEFER='net/neoforged/neoforge/registries/DeferredBlock'
ALTAR='dev/settledlands/revival/RevivalAltarBlock'
BLK='net/minecraft/world/level/block/Block'
STATE='net/minecraft/world/level/block/state/BlockState'
op=t.op


def fake_longset():
    def iterator(cp):
        a=Asm();t.obj(a,cp,IT_IMPL);a.raw(0xb0);return a.finish()
    return f.fake_class(LONGSET,[],[(1,'<init>','()V',1,1,f.empty_init()),
        (1,'isEmpty','()Z',1,1,lambda cp:b'\x03\xac'),
        (1,'iterator','()L'+LONGITER+';',2,1,iterator)])


def fake_iterator():
    def has(cp):
        a=Asm();a.raw(0x2a);a.op(0xb4,cp.field(IT_IMPL,'done','Z'));a.jump(0x9a,'no')
        a.raw(0x04,0xac);a.label('no');a.raw(0x03,0xac);return a.finish()
    return f.fake_class(IT_IMPL,[(1,'done','Z')],[(1,'<init>','()V',1,1,f.empty_init()),
        (1,'hasNext','()Z',1,1,has),
        (1,'nextLong','()J',2,1,lambda cp:b'\x2a\x04'+op(0xb5,cp.field(IT_IMPL,'done','Z'))+b'\x0a\xad')],interfaces=[LONGITER])


def build_classes(jar):
    classes,noqueue=v.build_with_wear(jar)
    def clinit(k):
        code=t.pm_clinit(k)[:-1]
        code+=(op(0xbb,k.add_class('java/util/ArrayList'))+b'\x59'+op(0xb7,k.add_methodref('java/util/ArrayList','<init>','()V'))+
               op(0xb3,k.add_fieldref(PM,'WHISPERS','Ljava/util/List;'))+b'\x10\x08\xbc\x0b'+
               op(0xb3,k.add_fieldref(PM,'REMOVE_BUF','[J'))+op(0xbb,k.add_class(DEFER))+b'\x59'+
               op(0xb7,k.add_methodref(DEFER,'<init>','()V'))+op(0xb3,k.add_fieldref(PM,'REVIVAL_ALTAR','L'+DEFER+';'))+b'\xb1')
        return code
    names={'tryReviveAtAltar','isValidEmeraldEntity','spawnVisualLightning','teleportPlayerOntoAltar','teleportToTarget',
           'onPlayerLogin','onDimensionChange','onServerTick','tickLevelAltars','keepPurgatoryFatigue'}
    with zipfile.ZipFile(jar) as z:
        if r.f.classedit.Klass(z.read(PM+'.class')).find_method(q.PLAYER_ONLY):names.add(q.PLAYER_ONLY)
    classes[PM]=r.subset(jar,PM,names,fields={'PURGATORY_LEVEL','NO_RELATIVES','WHISPERS','REMOVE_BUF','REVIVAL_ALTAR'},add_clinit=clinit)
    classes[PM]=t.rewrite(classes[PM],replace=[('keepPurgatoryFatigue','(L'+SP+';)V',0,1,lambda k:b'\xb1')],
      add_methods=[(9,'teleportToPurgatory','(L'+SERVER+';L'+SP+';Z)V',0,3,lambda k:b'\xb1')])
    def new_obj(k,name):return op(0xbb,k.add_class(name))+b'\x59'+op(0xb7,k.add_methodref(name,'<init>','()V'))+b'\xb0'
    classes[PD]=t.rewrite(classes[PD],add_methods=[
        (9,'get','(L'+SL+';)L'+PD+';',2,1,lambda k:new_obj(k,PD)),
        (1,'hasPendingRevive','(Ljava/util/UUID;)Z',1,2,lambda k:b'\x03\xac'),
        (1,'removeAltar','(J)V',0,3,lambda k:b'\xb1')])
    classes[SERVER]=t.rewrite(classes[SERVER],add_methods=[
        (1,'getTickCount','()I',1,1,lambda k:b'\x10\x08\xac'),
        (1,'getAllLevels','()Ljava/lang/Iterable;',1,1,lambda k:b'\x2a'+op(0xb4,k.add_fieldref(SERVER,'world','L'+SL+';'))+
         op(0xb8,k.add_methodref('java/util/Collections','singletonList','(Ljava/lang/Object;)Ljava/util/List;'))+b'\xb0')])
    classes[SP]=t.rewrite(classes[SP],add_methods=[(1,'isSpectator','()Z',1,1,lambda k:b'\x03\xac')])
    classes[SL]=t.rewrite(classes[SL],add_methods=[
        (1,'hasChunkAt','(L'+BLOCK+';)Z',1,2,lambda k:b'\x04\xac'),
        (1,'getBlockState','(L'+BLOCK+';)L'+STATE+';',2,2,lambda k:new_obj(k,STATE))])
    classes[LONGSET]=fake_longset();classes[LONGITER]=r.interface(LONGITER,[r.abstract('hasNext','()Z'),r.abstract('nextLong','()J')])
    classes[IT_IMPL]=fake_iterator();classes[BLK]=f.blank_class(BLK)
    classes[ALTAR]=f.fake_class(ALTAR,[],[(1,'<init>','()V',1,1,f.empty_init(BLK))],super_name=BLK)
    def value(cp):
        a=Asm();t.obj(a,cp,ALTAR);a.raw(0xb0);return a.finish()
    classes[DEFER]=f.fake_class(DEFER,[],[(1,'<init>','()V',1,1,f.empty_init()),(1,'get','()Ljava/lang/Object;',2,1,value)])
    classes[STATE]=f.fake_class(STATE,[],[(1,'<init>','()V',1,1,f.empty_init()),(1,'is','(L'+BLK+';)Z',1,2,lambda cp:b'\x04\xac')])
    return classes,noqueue


def harness():
    def main(cp):
        a=Asm();out=cp.field('java/lang/System','out','Ljava/io/PrintStream;')
        def load(i):t.local(a,'aload',i)
        def store(i):t.local(a,'astore',i)
        def n(i):r.const(a,i)
        def string(s):t.text(a,cp,s)
        def obj(name,desc='()V',args=None):t.obj(a,cp,name,desc,args)
        def get(i,owner,name,desc):load(i);a.op(0xb4,cp.field(owner,name,desc))
        def set_(i,owner,name,desc,emit):load(i);emit();a.op(0xb5,cp.field(owner,name,desc))
        def val(label,typ,emit):
            a.op(0xb2,out);string(label+'=');a.op(0xb6,cp.method('java/io/PrintStream','print','(Ljava/lang/String;)V'))
            a.op(0xb2,out);emit();a.op(0xb6,cp.method('java/io/PrintStream','println','('+typ+')V'))
        def uuid():string('00000000-0000-0000-0000-000000000001');a.op(0xb8,cp.method('java/util/UUID','fromString','(Ljava/lang/String;)Ljava/util/UUID;'))
        obj(SERVER);store(1);obj(KEY);store(10);obj(SL,'(L'+KEY+';L'+SERVER+';)V',lambda:(load(10),load(1)));store(2)
        obj(SL,'(L'+KEY+';L'+SERVER+';)V',lambda:(a.op(0xb2,cp.field(PM,'PURGATORY_LEVEL','L'+KEY+';')),load(1)));store(11)
        obj(PLAYERLIST);store(9);set_(1,SERVER,'players','L'+PLAYERLIST+';',lambda:load(9))
        set_(1,SERVER,'world','L'+SL+';',lambda:load(2));set_(1,SERVER,'purgatory','L'+SL+';',lambda:load(11))
        obj(SP,'(Ljava/util/UUID;L'+SL+';I)V',lambda:(uuid(),load(11),n(30)));store(3)
        get(9,PLAYERLIST,'list','Ljava/util/List;');load(3);r.p.invokeinterface(a,cp,'java/util/List','add','(Ljava/lang/Object;)Z',2);a.raw(0x57)
        obj(C,'(L'+r.TYPE+';L'+L+';)V',lambda:(a.raw(0x01),load(2)));store(4)
        load(4);load(3);a.op(0xb6,cp.method(C,'setOwner','(L'+SP+';)V'))
        for axis,value in zip('xyz',(2,64,2)):set_(4,E,axis,'D',lambda value=value:(n(value),a.raw(0x87)))
        get(2,SL,'near','Ljava/util/List;');load(4);r.p.invokeinterface(a,cp,'java/util/List','add','(Ljava/lang/Object;)Z',2);a.raw(0x57)
        load(3);a.op(0xb6,cp.method(E,'getPersistentData','()L'+NBT+';'));string(p.DEATH_CORPSE);load(4)
        a.op(0xb6,cp.method(E,'getUUID','()Ljava/util/UUID;'));a.op(0xb6,cp.method(NBT,'putUUID','(Ljava/lang/String;Ljava/util/UUID;)V'))
        obj(BLOCK,'(III)V',lambda:(n(0),n(64),n(0)));store(5);load(5);a.op(0xb6,cp.method(BLOCK,'asLong','()J'));a.raw(0x58)
        obj(IT);store(6);set_(6,IT,'count','I',lambda:n(12));set_(6,IT,'kind','L'+ITEM+';',lambda:a.op(0xb2,cp.field(ITEMS,'EMERALD','L'+ITEM+';')))
        obj(ITEM_ENTITY,'(L'+IT+';)V',lambda:load(6));store(7)
        get(2,SL,'altarItems','Ljava/util/List;');load(7);r.p.invokeinterface(a,cp,'java/util/List','add','(Ljava/lang/Object;)Z',2);a.raw(0x57)
        # Legacy FIFO head is an offline UUID. 242 keeps it first and blocks
        # this live player; 243 ignores stored queue state completely.
        string('00000000-0000-0000-0000-000000000999')
        a.op(0xb8,cp.method('java/util/UUID','fromString','(Ljava/lang/String;)Ljava/util/UUID;'))
        a.op(0xb3,cp.field(PD,'queued','Ljava/util/UUID;'))
        obj(POST,'(L'+SERVER+';)V',lambda:load(1));a.op(0xb8,cp.method(PM,'onServerTick','(L'+POST+';)V'))
        val('in_purg','Z',lambda:(get(3,E,'world','L'+L+';'),load(11),a.op(0xb8,cp.method('java/util/Objects','equals','(Ljava/lang/Object;Ljava/lang/Object;)Z'))))
        val('money','I',lambda:get(6,IT,'count','I'));val('body_removed','Z',lambda:get(4,E,'removed','Z'))
        a.raw(0xb1);return a.finish()
    return f.fake_class('ServerTickHarness',[],[(9,'main','([Ljava/lang/String;)V',12,12,main)])


def run(java,jar):
    classes,_=build_classes(jar);classes['ServerTickHarness']=harness()
    with tempfile.TemporaryDirectory(prefix='servertick-jvm-') as td:
        root=pathlib.Path(td)
        for name,raw in classes.items():
            dest=root/(name+'.class');dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw)
        p_=subprocess.run([str(java),'-Xshare:off','-Xverify:all','-cp',str(root),'ServerTickHarness'],capture_output=True,text=True)
        if p_.returncode:raise RuntimeError(p_.stdout+'\n'+p_.stderr)
        return dict(line.split('=',1) for line in p_.stdout.splitlines() if '=' in line)


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--java',required=True);ap.add_argument('--base242',required=True);ap.add_argument('--jar',required=True);a=ap.parse_args()
    old=run(a.java,a.base242);assert old=={'in_purg':'true','money':'12','body_removed':'false'},old
    got=run(a.java,a.jar);assert got=={'in_purg':'false','money':'3','body_removed':'true'},got
    print('243 JVM real server tick -> real level altar scan -> paid body ritual works with NO queue; 242 control blocked')
    print('Synthetic level/block/tick API and fatigue; mixin and Minecraft/NeoForge not run.')


if __name__=='__main__':main()
