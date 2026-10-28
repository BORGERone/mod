#!/usr/bin/env python3
"""Execute actual read-only probe + original graph/gate on JVM with test doubles."""
import argparse
import pathlib
import subprocess
import tempfile
import jvm_npc_cult240 as c
import patch_cultprobe243 as probe
from patch_probe233 import Asm

r,f = c.r,c.f
E,P,SP,G,C,L,SERVER,NBT,IT,ITEM,EVENT = c.E,c.P,c.SP,c.G,c.C,c.L,c.SERVER,c.NBT,c.IT,c.ITEM,c.EVENT
op,text,obj,const = c.op,c.text,c.obj,c.const


def fake_logger():
    def info(cp):
        a=Asm(); a.op(0xb2,cp.field(r.FLOGGER,'fail','Z')); a.jump(0x99,'record')
        obj(a,cp,'java/lang/IllegalStateException'); a.raw(0xbf)
        a.label('record'); a.op(0xb2,cp.field(r.FLOGGER,'count','I')); a.raw(0x04,0x60)
        a.op(0xb3,cp.field(r.FLOGGER,'count','I')); a.raw(0x2b); a.op(0xb3,cp.field(r.FLOGGER,'message','Ljava/lang/String;'))
        a.raw(0x2c); a.op(0xb3,cp.field(r.FLOGGER,'last','[Ljava/lang/Object;')); a.raw(0xb1); return a.finish()
    return f.fake_class(r.FLOGGER,[(9,'count','I'),(9,'fail','Z'),(9,'message','Ljava/lang/String;'),(9,'last','[Ljava/lang/Object;')],[
        (1,'<init>','()V',1,1,f.empty_init()), (1,'info','(Ljava/lang/String;[Ljava/lang/Object;)V',2,3,info),
        (1,'warn','(Ljava/lang/String;Ljava/lang/Object;)V',0,3,lambda cp:b'\xb1')],interfaces=[r.LOGGER])


def harness():
    def main(cp):
        a=Asm(); out=cp.field('java/lang/System','out','Ljava/io/PrintStream;')
        def load(i):r.insn_local(a,'aload',i)
        def store(i):r.insn_local(a,'astore',i)
        def string(s):text(a,cp,s)
        def object_(name,desc='()V',args=None):obj(a,cp,name,desc,args)
        def field(i,owner,name,desc):load(i); a.op(0xb4,cp.field(owner,name,desc))
        def set_field(i,owner,name,desc,emit):load(i); emit(); a.op(0xb5,cp.field(owner,name,desc))
        def uuid(i):string('00000000-0000-0000-0000-%012d'%i);a.op(0xb8,cp.method('java/util/UUID','fromString','(Ljava/lang/String;)Ljava/util/UUID;'))
        def player(n,local=3):object_(SP,'(Ljava/util/UUID;L'+L+';I)V',lambda:(uuid(n),load(2),const(a,20)));store(local)
        def event(player_index=3,target_index=4):
            object_(EVENT,'(L'+P+';L'+E+';L'+IT+';)V',lambda:(load(player_index),load(target_index),load(6)));store(5)
        def click():load(5);a.op(0xb8,cp.method(c.BRIDGE,'onInteractSpecific','(L'+EVENT+';)V'))
        def print_(label,desc,emit):
            a.op(0xb2,out);string(label+'=');a.op(0xb6,cp.method('java/io/PrintStream','print','(Ljava/lang/String;)V'))
            a.op(0xb2,out);emit();a.op(0xb6,cp.method('java/io/PrintStream','println','('+desc+')V'))
        def logs():a.op(0xb2,cp.field(r.FLOGGER,'count','I'))
        def entry(index):
            a.op(0xb2,cp.field(r.FLOGGER,'last','[Ljava/lang/Object;'));const(a,index);a.raw(0x32)
        def data(index):load(index);a.op(0xb6,cp.method(E,'getPersistentData','()L'+NBT+';'))
        def local():data(4);string('settledlands_local_vars');a.op(0xb6,cp.method(NBT,'getCompound','(Ljava/lang/String;)L'+NBT+';'))
        def add_local():
            load(3);load(4);string('культ');a.raw(0x03,0x04,0x04)
            a.op(0xb8,cp.method(c.DIALOGUE,'addVarValue','(L'+SP+';L'+G+';Ljava/lang/String;ZZI)V'))
        object_(SERVER);store(1);object_(L,'(ZL'+SERVER+';)V',lambda:(a.raw(0x03),load(1)));store(2)
        player(1);object_(G,'(L'+L+';)V',lambda:load(2));store(4)
        object_(IT,'(I)V',lambda:a.raw(0x04));store(6);object_(ITEM);store(7)
        set_field(6,IT,'kind','L'+ITEM+';',lambda:load(7))
        add_local();event();click()
        print_('first.count','I',logs)
        for i in range(8):print_('first.arg'+str(i),'Ljava/lang/Object;',lambda i=i:entry(i))
        print_('first.message','Ljava/lang/String;',lambda:a.op(0xb2,cp.field(r.FLOGGER,'message','Ljava/lang/String;')))
        print_('first.nbt','I',lambda:(local(),string('культ'),a.op(0xb6,cp.method(NBT,'getInt','(Ljava/lang/String;)I'))))
        print_('first.canceled','Z',lambda:field(5,EVENT,'canceled','Z'))
        print_('first.removed','Z',lambda:field(4,E,'removed','Z'))
        click();print_('rate.count','I',logs)
        # Different player, same NPC; string instead of numeric and no effect.
        local();string('культ');string('1');a.op(0xb6,cp.method(NBT,'putString','(Ljava/lang/String;Ljava/lang/String;)V'))
        player(2);set_field(3,SP,'bodiless','Z',lambda:a.raw(0x03));event();click()
        print_('second.count','I',logs)
        for i in (0,2,3,4,5):print_('second.arg'+str(i),'Ljava/lang/Object;',lambda i=i:entry(i))
        print_('second.nbt','Ljava/lang/String;',lambda:(local(),string('культ'),a.op(0xb6,cp.method(NBT,'getString','(Ljava/lang/String;)Ljava/lang/String;'))))
        # A corpse or client player is not considered an attempt and never logged.
        player(3);object_(C,'(L'+L+';)V',lambda:load(2));store(8);event(target_index=8);click()
        object_(P,'(Ljava/util/UUID;L'+L+';)V',lambda:(uuid(4),load(2)));store(8);event(player_index=8);click()
        print_('excluded.count','I',logs)
        # Logger failure must not escape and must not stop the real gate.
        a.raw(0x04);a.op(0xb3,cp.field(r.FLOGGER,'fail','Z'))
        player(5);object_(G,'(L'+L+';)V',lambda:load(2));store(4);add_local();event();click()
        load(5);a.op(0xb8,cp.method(c.PS,'onEntityInteractSpecific','(L'+EVENT+';)V'))
        print_('failure.removed','Z',lambda:field(4,E,'removed','Z'))
        print_('failure.canceled','Z',lambda:field(5,EVENT,'canceled','Z'))
        print_('points.reads','I',lambda:a.op(0xb2,cp.field(c.POINTS,'reads','I')))
        a.raw(0xb1);return a.finish()
    return f.fake_class('CultProbeHarness',[],[(9,'main','([Ljava/lang/String;)V',12,10,main)])


def run(java,jar):
    classes=c.build_classes(jar);classes[r.FLOGGER]=fake_logger();classes['CultProbeHarness']=harness()
    with tempfile.TemporaryDirectory(prefix='cultprobe-jvm-') as td:
        root=pathlib.Path(td)
        for name,raw in classes.items():
            file=root/(name+'.class');file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(raw)
        p=subprocess.run([str(java),'-Xshare:off','-Xverify:all','-cp',str(root),'CultProbeHarness'],capture_output=True,text=True)
        if p.returncode:raise RuntimeError(p.stdout+'\n'+p.stderr)
        return dict(line.split('=',1) for line in p.stdout.splitlines() if '=' in line)


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--java',required=True);ap.add_argument('--jar',required=True);a=ap.parse_args()
    got=run(a.java,a.jar)
    expected={'first.count':'1','first.arg0':'00000000-0000-0000-0000-000000000001',
              'first.arg1':'00000000-0000-0000-0000-000000000099','first.arg2':'10','first.arg3':'3',
              'first.arg4':'1','first.arg5':'true','first.arg6':'null','first.nbt':'1','first.canceled':'false',
              'first.removed':'false','rate.count':'1','second.count':'2',
              'second.arg0':'00000000-0000-0000-0000-000000000002','second.arg2':'10','second.arg3':'8',
              'second.arg4':'0','second.arg5':'false','second.nbt':'1','excluded.count':'2',
              'failure.removed':'true','failure.canceled':'true','points.reads':'0'}
    diff={k:(got.get(k),v) for k,v in expected.items() if got.get(k)!=v}
    assert not diff,(diff,got)
    assert got['first.arg7'].startswith('net.minecraft.world.item.Item@'),got
    assert '[SettledLands cult probe]' in got['first.message'],got
    print('243 JVM probe: actual NPC-local types/value, owner, effect, hand, item, rate limit, exclusions and safe logger failure OK')
    print('Real graph/gate bytecode; synthetic game APIs; no Minecraft/NeoForge interaction performed.')


if __name__=='__main__':main()
