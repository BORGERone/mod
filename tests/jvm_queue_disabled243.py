#!/usr/bin/env python3
"""Execute REAL PurgatoryData queue methods with synthetic SavedData on JVM."""
import argparse
import pathlib
import subprocess
import tempfile
import jvm_queue242 as old
import jvm_noqueue243 as v
from patch_probe233 import Asm

t, r, f, q, PD = v.t, v.r, v.f, v.q, v.PD


def harness():
    def main(cp):
        a=Asm(); out=cp.field('java/lang/System','out','Ljava/io/PrintStream;')
        def text(s): t.text(a,cp,s)
        def val(name,emit):
            a.op(0xb2,out); text(name+'='); a.op(0xb6,cp.method('java/io/PrintStream','print','(Ljava/lang/String;)V'))
            a.op(0xb2,out); emit(); a.op(0xb6,cp.method('java/io/PrintStream','println','(Z)V'))
        t.obj(a,cp,PD); a.raw(0x4c)
        text('00000000-0000-0000-0000-000000000001'); a.op(0xb8,cp.method('java/util/UUID','fromString','(Ljava/lang/String;)Ljava/util/UUID;')); a.raw(0x4d)
        # Seed an actual legacy entry directly, as a previously saved world may
        # load it. Calling the API should neither honour nor mutate it in 243.
        a.raw(0x2b,0x2c); a.op(0xb6,cp.method(PD,'fixtureInject','(Ljava/util/UUID;)V'))
        val('has',lambda:(a.raw(0x2b),a.op(0xb6,cp.method(PD,'hasDeadPlayers','()Z'))))
        val('contains',lambda:(a.raw(0x2b,0x2c),a.op(0xb6,cp.method(PD,'isDeadInQueue','(Ljava/util/UUID;)Z'))))
        val('peek_nonnull',lambda:(a.raw(0x2b),a.op(0xb6,cp.method(PD,q.PEEK,'()Ljava/util/UUID;')),
            a.op(0xb8,cp.method('java/util/Objects','nonNull','(Ljava/lang/Object;)Z'))))
        val('poll_nonnull',lambda:(a.raw(0x2b),a.op(0xb6,cp.method(PD,'pollFirstDead','()Ljava/util/UUID;')),
            a.op(0xb8,cp.method('java/util/Objects','nonNull','(Ljava/lang/Object;)Z'))))
        # Re-seed and invoke all modification APIs.
        a.raw(0x2b,0x2c); a.op(0xb6,cp.method(PD,'fixtureInject','(Ljava/util/UUID;)V'))
        a.raw(0x2b,0x2c); a.op(0xb6,cp.method(PD,'enqueueDead','(Ljava/util/UUID;)V'))
        a.raw(0x2b,0x2c); a.op(0xb6,cp.method(PD,q.RESTORE_QUEUE,'(Ljava/util/UUID;)V'))
        val('remove',lambda:(a.raw(0x2b,0x2c),a.op(0xb6,cp.method(PD,'removeFromQueue','(Ljava/util/UUID;)Z'))))
        val('still_unused',lambda:(a.raw(0x2b,0x2c),a.op(0xb6,cp.method(PD,'isDeadInQueue','(Ljava/util/UUID;)Z'))))
        a.raw(0xb1); return a.finish()
    return f.fake_class('DisabledQueueHarness',[],[(9,'main','([Ljava/lang/String;)V',5,3,main)])


def run(java,jar):
    names={'<init>','enqueueDead','pollFirstDead','hasDeadPlayers','isDeadInQueue','removeFromQueue',q.PEEK,q.RESTORE_QUEUE}
    pd=r.subset(jar,PD,names)
    def inject(k):
        return (b'\x2a'+t.op(0xb4,k.add_fieldref(PD,'deadQueue','Ljava/util/List;'))+b'\x2b'+
                t.op(0xb9,k.add_interfaceref('java/util/List','add','(Ljava/lang/Object;)Z'))+b'\x02\x00\x57\xb1')
    pd=t.rewrite(pd,add_methods=[(1,'fixtureInject','(Ljava/util/UUID;)V',2,2,inject)])
    classes={PD:pd,old.SAVED:old.saved_class(),old.LONGSET:f.fake_class(old.LONGSET,[],[(1,'<init>','()V',1,1,f.empty_init())]),
             'DisabledQueueHarness':harness()}
    with tempfile.TemporaryDirectory(prefix='disabled-queue-jvm-') as td:
        root=pathlib.Path(td)
        for name,raw in classes.items():
            path=root/(name+'.class'); path.parent.mkdir(parents=True,exist_ok=True); path.write_bytes(raw)
        proc=subprocess.run([str(java),'-Xshare:off','-Xverify:all','-cp',str(root),'DisabledQueueHarness'],capture_output=True,text=True)
        if proc.returncode: raise RuntimeError(proc.stdout+'\n'+proc.stderr)
        return dict(line.split('=',1) for line in proc.stdout.splitlines() if '=' in line)


def main():
    ap=argparse.ArgumentParser(); ap.add_argument('--java',required=True); ap.add_argument('--base242',required=True); ap.add_argument('--jar',required=True)
    a=ap.parse_args(); oldrun=run(a.java,a.base242)
    assert oldrun['has']=='true' and oldrun['peek_nonnull']=='true' and oldrun['poll_nonnull']=='true',oldrun
    got=run(a.java,a.jar); expected={k:'false' for k in ('has','contains','peek_nonnull','poll_nonnull','remove','still_unused')}
    assert got==expected,(got,expected)
    print('243 real persisted queue API is disabled, including pre-existing entries; 242 negative control OK')


if __name__=='__main__':main()
