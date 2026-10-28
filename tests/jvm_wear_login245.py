#!/usr/bin/env python3
"""Run actual PossessionSystem login hook with synthetic saved attributes."""
import argparse
import pathlib
import subprocess
import tempfile
import jvm_npc_cult240 as c
import wear_fixture245 as x
from patch_probe233 import Asm

r,f,t,w=c.r,c.f,x.t,x.w
E,P,SP,L,SERVER,NBT=c.E,c.P,c.SP,c.L,c.SERVER,c.NBT
PS,H,AA,ATTR,MOD,OP,RL,LIVING,LOGIN=w.PS,w.HOLDER,w.ATTRIBUTES,w.ATTR,w.MOD,w.OP,w.RL,w.LIVING,w.LOGIN


def harness():
    def main(cp):
        a=Asm(); out=cp.field('java/lang/System','out','Ljava/io/PrintStream;')
        def ld(i): t.local(a,'aload',i)
        def st(i): t.local(a,'astore',i)
        def n(i): r.const(a,i)
        def s(v): t.text(a,cp,v)
        def make(name,desc='()V',args=None): t.obj(a,cp,name,desc,args)
        def val(label,typ,emit):
            a.op(0xb2,out); s(label+'='); a.op(0xb6,cp.method('java/io/PrintStream','print','(Ljava/lang/String;)V'))
            a.op(0xb2,out); emit(); a.op(0xb6,cp.method('java/io/PrintStream','println','('+typ+')V'))
        def data(): ld(3); a.op(0xb6,cp.method(E,'getPersistentData','()L'+NBT+';'))
        def attr(): ld(3); a.op(0xb2,cp.field(AA,'MAX_HEALTH','L'+H+';')); a.op(0xb6,cp.method(LIVING,'getAttribute','(L'+H+';)L'+ATTR+';'))
        def id_(ns,path): s(ns); s(path); a.op(0xb8,cp.method(RL,'fromNamespaceAndPath','(Ljava/lang/String;Ljava/lang/String;)L'+RL+';'))
        def env(active):
            make(SERVER); st(1); make(L,'(ZL'+SERVER+';)V',lambda:(a.raw(0x03),ld(1))); st(2)
            make(SP,'(Ljava/util/UUID;L'+L+';I)V',lambda:(s('00000000-0000-0000-0000-000000000001'),
                a.op(0xb8,cp.method('java/util/UUID','fromString','(Ljava/lang/String;)Ljava/util/UUID;')),ld(2),n(20))); st(3)
            data(); s('settledlands_possess'); a.raw(0x04); a.op(0xb6,cp.method(NBT,'putBoolean','(Ljava/lang/String;Z)V'))
            data(); s(w.COUNT); n(2); a.op(0xb6,cp.method(NBT,'putInt','(Ljava/lang/String;I)V'))
            data(); s(w.ACTIVE); a.raw(0x04 if active else 0x03); a.op(0xb6,cp.method(NBT,'putBoolean','(Ljava/lang/String;Z)V'))
            attr(); a.op(0xbb,cp.cls(MOD)); a.raw(0x59); id_('othermod','extra'); n(10); a.raw(0x87)
            a.op(0xb2,cp.field(OP,'ADD_VALUE','L'+OP+';')); a.op(0xb7,cp.method(MOD,'<init>','(L'+RL+';DL'+OP+';)V'))
            a.op(0xb6,cp.method(ATTR,'addOrReplacePermanentModifier','(L'+MOD+';)V'))
            if not active:
                # A stale saved copy of the mod's own modifier must be removed.
                attr(); a.op(0xbb,cp.cls(MOD)); a.raw(0x59); id_('settledlands','body_wear')
                a.raw(0x0f,0x77); a.op(0xb2,cp.field(OP,'ADD_VALUE','L'+OP+';'))
                a.op(0xb7,cp.method(MOD,'<init>','(L'+RL+';DL'+OP+';)V'))
                a.op(0xb6,cp.method(ATTR,'addOrReplacePermanentModifier','(L'+MOD+';)V'))
        def hp(): ld(3); a.op(0xb6,cp.method(LIVING,'getMaxHealth','()F'))
        def count(): data(); s(w.COUNT); a.op(0xb6,cp.method(NBT,'getInt','(Ljava/lang/String;)I'))
        for label,active in [('active',True),('ghost',False)]:
            env(active); val(label+'.before','F',hp)
            make(LOGIN,'(L'+P+';)V',lambda:ld(3)); a.op(0xb8,cp.method(PS,'onPlayerLogin','(L'+LOGIN+';)V'))
            val(label+'.max','F',hp); val(label+'.count','I',count)
        a.raw(0xb1); return a.finish()
    return f.fake_class('WearLoginHarness',[],[(9,'main','([Ljava/lang/String;)V',12,5,main)])


def run(java,jar):
    classes=c.build_classes(jar)
    classes[PS]=r.subset(jar,PS,{'<clinit>','data','bodiless','isPossessed','possessModelKey','possessName','makeBodiless','onPlayerLogin'})
    classes[LOGIN]=f.fake_class(LOGIN,[],[(1,'<init>','(L'+P+';)V',2,2,lambda cp:
        b'\x2a\x2b'+t.op(0xb7,cp.method(r.PLAYER_EVENT,'<init>','(L'+P+';)V'))+b'\xb1')],super_name=r.PLAYER_EVENT)
    classes['WearLoginHarness']=harness()
    with tempfile.TemporaryDirectory(prefix='wear-login245-jvm-') as td:
        root=pathlib.Path(td)
        for name,raw in classes.items():
            path=root/(name+'.class'); path.parent.mkdir(parents=True,exist_ok=True); path.write_bytes(raw)
        result=subprocess.run([str(java),'-Xshare:off','-Xverify:all','-cp',str(root),'WearLoginHarness'],text=True,capture_output=True)
        if result.returncode: raise RuntimeError(f'{result.stdout}\n{result.stderr}')
        return dict(line.split('=',1) for line in result.stdout.splitlines() if '=' in line)


def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--java',required=True); ap.add_argument('--jar',required=True); a=ap.parse_args()
    got=run(a.java,a.jar); expected={'active.before':'30.0','active.max':'22.0','active.count':'2',
                                      'ghost.before':'29.0','ghost.max':'30.0','ghost.count':'2'}
    bad={k:(got.get(k),v) for k,v in expected.items() if got.get(k)!=v}
    if bad: raise AssertionError(f'{bad}\n{got}')
    print('Actual possession login hook reconciles saved wear and preserves another mod: PASS')
    print('Persistence and event dispatch are synthetic, not a game reload.')


if __name__=='__main__': main()
