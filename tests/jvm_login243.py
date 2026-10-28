#!/usr/bin/env python3
"""Legacy pending and awaiting login, actual manager/gate bytecodes on Java."""
import argparse,pathlib,subprocess,tempfile
import jvm_noqueue243 as v
from patch_probe233 import Asm

t,r,f,p=v.t,v.r,v.f,v.p
E,SP,SL,L,SERVER,NBT,PD,PM,KEY,BLOCK,PLAYERLIST=(v.E,v.SP,v.SL,v.L,v.SERVER,v.NBT,v.PD,v.PM,v.KEY,v.BLOCK,v.PLAYERLIST)
LOGIN=t.LOGIN


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
        def uuid(i):string('00000000-0000-0000-0000-%012d'%i);a.op(0xb8,cp.method('java/util/UUID','fromString','(Ljava/lang/String;)Ljava/util/UUID;'))
        def data():load(3);a.op(0xb6,cp.method(E,'getPersistentData','()L'+NBT+';'))
        def flag(value):data();string('settledlands_awaiting_purgatory');a.raw(0x04 if value else 0x03);a.op(0xb6,cp.method(NBT,'putBoolean','(Ljava/lang/String;Z)V'))
        def login():obj(LOGIN,'(L'+r.P+';)V',lambda:load(3));a.op(0xb8,cp.method(PM,'onPlayerLogin','(L'+LOGIN+';)V'))
        def in_purg():get(3,E,'world','L'+L+';');load(11);a.op(0xb8,cp.method('java/util/Objects','equals','(Ljava/lang/Object;Ljava/lang/Object;)Z'))
        def awaiting():data();string('settledlands_awaiting_purgatory');a.op(0xb6,cp.method(NBT,'getBoolean','(Ljava/lang/String;)Z'))
        def player(i):obj(SP,'(Ljava/util/UUID;L'+SL+';I)V',lambda:(uuid(i),load(2),n(30)));store(3)
        obj(SERVER);store(1);obj(KEY);store(10);obj(SL,'(L'+KEY+';L'+SERVER+';)V',lambda:(load(10),load(1)));store(2)
        obj(SL,'(L'+KEY+';L'+SERVER+';)V',lambda:(a.op(0xb2,cp.field(PM,'PURGATORY_LEVEL','L'+KEY+';')),load(1)));store(11)
        obj(PLAYERLIST);store(9);set_(1,SERVER,'players','L'+PLAYERLIST+';',lambda:load(9))
        set_(1,SERVER,'world','L'+SL+';',lambda:load(2));set_(1,SERVER,'purgatory','L'+SL+';',lambda:load(11))
        # A legacy pending target does not prove a paid, matching body.
        player(1);load(1);a.op(0xb8,cp.method(PD,'getMaster','(L'+SERVER+';)L'+PD+';'))
        load(3);a.op(0xb6,cp.method(SP,'getUUID','()Ljava/util/UUID;'));string('old:world')
        obj(BLOCK,'(III)V',lambda:(n(0),n(64),n(0)))
        a.op(0xb6,cp.method(PD,'setPendingRevive','(Ljava/util/UUID;Ljava/lang/String;L'+BLOCK+';)V'))
        login();val('pending.in_purg','Z',in_purg);val('pending.await','Z',awaiting)
        val('pending.cleared','Z',lambda:(a.op(0xb2,cp.field(PD,'pending','L'+t.TARGET+';')),
            a.op(0xb8,cp.method('java/util/Objects','isNull','(Ljava/lang/Object;)Z'))))
        # Queue no longer exists, but death awaiting a respawn/login still goes
        # to purgatory; otherwise disabling the old login queue would strand it.
        a.raw(0x01);a.op(0xb3,cp.field(PD,'queued','Ljava/util/UUID;'))
        a.raw(0x01);a.op(0xb3,cp.field(PD,'second','Ljava/util/UUID;'))
        player(2);flag(True);login();val('await.in_purg','Z',in_purg)
        # No pending/await flag: old queue alone cannot force a living player.
        player(3);flag(False);uuid(3);a.op(0xb3,cp.field(PD,'queued','Ljava/util/UUID;'))
        login();val('ordinary.in_purg','Z',in_purg)
        a.raw(0xb1);return a.finish()
    return f.fake_class('Login243Harness',[],[(9,'main','([Ljava/lang/String;)V',8,12,main)])


def run(java,jar):
    classes,_=v.build_classes(jar);classes['Login243Harness']=harness()
    with tempfile.TemporaryDirectory(prefix='login243-jvm-') as td:
        root=pathlib.Path(td)
        for name,raw in classes.items():
            dest=root/(name+'.class');dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw)
        proc=subprocess.run([str(java),'-Xshare:off','-Xverify:all','-cp',str(root),'Login243Harness'],capture_output=True,text=True)
        if proc.returncode:raise RuntimeError(proc.stdout+'\n'+proc.stderr)
        return dict(line.split('=',1) for line in proc.stdout.splitlines() if '=' in line)


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--java',required=True);ap.add_argument('--base242',required=True);ap.add_argument('--jar',required=True);a=ap.parse_args()
    old=run(a.java,a.base242);assert old['await.in_purg']=='false',old
    got=run(a.java,a.jar);expected={'pending.in_purg':'true','pending.await':'true','pending.cleared':'true',
                                     'await.in_purg':'true','ordinary.in_purg':'false'}
    assert got==expected,(got,expected)
    print('243 JVM pending/login: unknown legacy target rejected, awaiting death reaches purgatory without queue; 242 control OK')
    print('Synthetic game/teleport APIs; Minecraft/NeoForge not run.')


if __name__=='__main__':main()
