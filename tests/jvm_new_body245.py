#!/usr/bin/env python3
"""Real NPC interaction/possess() resets body wear; no Java compiler or game."""
import argparse
import pathlib
import subprocess
import tempfile

import jvm_npc_cult240 as c
import wear_fixture245 as x
from patch_probe233 import Asm

r,f,t,w=c.r,c.f,x.t,x.w
E,P,SP,G,L,SERVER,NBT,ITEM,IT,EVENT=c.E,c.P,c.SP,c.G,c.L,c.SERVER,c.NBT,c.ITEM,c.IT,c.EVENT
PS,DIALOGUE=c.PS,c.DIALOGUE
H,AA,ATTR,MOD,OP,RL,LIVING=w.HOLDER,w.ATTRIBUTES,w.ATTR,w.MOD,w.OP,w.RL,w.LIVING


def harness():
    def main(cp):
        a=Asm(); out=cp.field('java/lang/System','out','Ljava/io/PrintStream;')
        def ld(i): t.local(a,'aload',i)
        def st(i): t.local(a,'astore',i)
        def n(v): r.const(a,v)
        def s(v): t.text(a,cp,v)
        def make(name,desc='()V',args=None): t.obj(a,cp,name,desc,args)
        def val(label,typ,emit):
            a.op(0xb2,out); s(label+'='); a.op(0xb6,cp.method('java/io/PrintStream','print','(Ljava/lang/String;)V'))
            a.op(0xb2,out); emit(); a.op(0xb6,cp.method('java/io/PrintStream','println','('+typ+')V'))
        def data(i): ld(i); a.op(0xb6,cp.method(E,'getPersistentData','()L'+NBT+';'))
        def count(): data(3); s(w.COUNT); a.op(0xb6,cp.method(NBT,'getInt','(Ljava/lang/String;)I'))
        def flag(key): data(3); s(key); a.op(0xb6,cp.method(NBT,'contains','(Ljava/lang/String;)Z'))
        def hp(): ld(3); a.op(0xb6,cp.method(LIVING,'getMaxHealth','()F'))
        def current(): ld(3); a.op(0xb6,cp.method(LIVING,'getHealth','()F'))
        def attr(): ld(3); a.op(0xb2,cp.field(AA,'MAX_HEALTH','L'+H+';')); a.op(0xb6,cp.method(LIVING,'getAttribute','(L'+H+';)L'+ATTR+';'))
        def id_(ns,path): s(ns); s(path); a.op(0xb8,cp.method(RL,'fromNamespaceAndPath','(Ljava/lang/String;Ljava/lang/String;)L'+RL+';'))
        def env():
            make(SERVER); st(1); make(L,'(ZL'+SERVER+';)V',lambda:(a.raw(0x03),ld(1))); st(2)
            make(SP,'(Ljava/util/UUID;L'+L+';I)V',lambda:(s('00000000-0000-0000-0000-000000000001'),
                a.op(0xb8,cp.method('java/util/UUID','fromString','(Ljava/lang/String;)Ljava/util/UUID;')),ld(2),n(20))); st(3)
            make(G,'(L'+L+';)V',lambda:ld(2)); st(4)
            data(3); s(w.COUNT); n(3); a.op(0xb6,cp.method(NBT,'putInt','(Ljava/lang/String;I)V'))
            data(3); s(w.ACTIVE); a.raw(0x04); a.op(0xb6,cp.method(NBT,'putBoolean','(Ljava/lang/String;Z)V'))
            # Keep a separate external +10 HP modifier and set wear to -16.
            attr(); a.op(0xbb,cp.cls(MOD)); a.raw(0x59); id_('othermod','extra_health'); n(10); a.raw(0x87)
            a.op(0xb2,cp.field(OP,'ADD_VALUE','L'+OP+';')); a.op(0xb7,cp.method(MOD,'<init>','(L'+RL+';DL'+OP+';)V'))
            a.op(0xb6,cp.method(ATTR,'addOrReplacePermanentModifier','(L'+MOD+';)V'))
            ld(3); a.op(0xb8,cp.method(w.WEAR,'sync',w.SPVOID))
        def click(valid):
            if valid:
                ld(3); ld(4); s('культ'); a.raw(0x03,0x04,0x04)
                a.op(0xb8,cp.method(DIALOGUE,'addVarValue','(L'+SP+';L'+G+';Ljava/lang/String;ZZI)V'))
            make(IT,'(I)V',lambda:n(0)); st(7)
            make(EVENT,'(L'+P+';L'+E+';L'+IT+';)V',lambda:(ld(3),ld(4),ld(7)))
            a.op(0xb8,cp.method(PS,'onEntityInteractSpecific','(L'+EVENT+';)V'))
        def other_present():
            attr(); id_('othermod','extra_health'); a.op(0xb6,cp.method(ATTR,'getModifier','(L'+RL+';)L'+MOD+';'))
            a.jump(0xc6,'not_present_'+str(len(a.buf))); lab='not_present_'+str(len(a.buf)-3)
            a.raw(0x04); a.jump(0xa7,lab+'_end'); a.label(lab); a.raw(0x03); a.label(lab+'_end')
        for label,valid in [('denied',False),('new',True)]:
            env(); val(label+'.before_max','F',hp); val(label+'.before_count','I',count); click(valid)
            val(label+'.removed','Z',lambda:(ld(4),a.op(0xb4,cp.field(E,'removed','Z'))))
            val(label+'.possessed','Z',lambda:(data(3),s('settledlands_possess'),a.op(0xb6,cp.method(NBT,'getBoolean','(Ljava/lang/String;)Z'))))
            val(label+'.count','I',count); val(label+'.max','F',hp); val(label+'.current','F',current)
            val(label+'.own_active','Z',lambda:flag(w.ACTIVE)); val(label+'.other','Z',other_present)
        a.raw(0xb1); return a.finish()
    return f.fake_class('NewBodyHarness',[],[(9,'main','([Ljava/lang/String;)V',16,10,main)])


def run(java,jar):
    classes=c.build_classes(jar); classes['NewBodyHarness']=harness()
    with tempfile.TemporaryDirectory(prefix='newbody245-jvm-') as td:
        root=pathlib.Path(td)
        for name,raw in classes.items():
            path=root/(name+'.class'); path.parent.mkdir(parents=True,exist_ok=True); path.write_bytes(raw)
        result=subprocess.run([str(java),'-Xshare:off','-Xverify:all','-cp',str(root),'NewBodyHarness'],text=True,capture_output=True)
        if result.returncode: raise RuntimeError(f'{result.stdout}\n{result.stderr}')
        return dict(line.split('=',1) for line in result.stdout.splitlines() if '=' in line)


def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--java',required=True); ap.add_argument('--jar',required=True); a=ap.parse_args()
    got=run(a.java,a.jar)
    expected={'denied.before_max':'14.0','denied.before_count':'3','denied.removed':'false','denied.possessed':'false',
              'denied.count':'3','denied.max':'14.0','denied.current':'14.0','denied.own_active':'true','denied.other':'true',
              'new.before_max':'14.0','new.before_count':'3','new.removed':'true','new.possessed':'true',
              'new.count':'0','new.max':'30.0','new.current':'14.0','new.own_active':'false','new.other':'true'}
    bad={k:(got.get(k),v) for k,v in expected.items() if got.get(k)!=v}
    if bad: raise AssertionError(f'{bad}\n{got}')
    print('Real NPC interaction/possess: successful new body resets only wear; denied cult preserves it: PASS')
    print('Minecraft/NeoForge and actual modded health providers were not run.')


if __name__=='__main__': main()
