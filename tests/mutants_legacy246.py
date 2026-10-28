#!/usr/bin/env python3
"""Recalibrate prior body-wear regression mutants against build 246."""
import argparse,pathlib,subprocess,sys,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'tests'),str(ROOT/'tools')]
import gate_build245 as g,gate_build244 as g244,gate_build237 as g237
import patch_npc_stats246 as p,classedit,decode,struct,zipfile
CASES=[('normal-not-paid','jvm_body_wear245.py','payment1.marked'),('exception-not-paid','jvm_body_wear245.py','after.paid'),('no-resume','jvm_body_wear245.py','life1.count'),('no-sync','jvm_body_wear245.py','life1.max'),('wrong-penalty','jvm_body_wear245.py','life2.max'),('fourth-body','jvm_body_wear245.py','death4.bodies'),('no-clone','jvm_body_wear245.py','ghost2.count'),('nondeath-treated-as-death','jvm_body_wear245.py','end_clone.max'),('no-deactivate','jvm_body_wear245.py','self.ghost_max'),('no-reset','jvm_body_wear245.py','new_body.count'),('no-dirty','jvm_body_wear245.py','buff.flat'),('uuid-mismatch-accepted','jvm_body_wear245.py','mismatch.count'),('duplicate-counted','jvm_body_wear245.py','replay_forced.count'),('paid-watch-not-cancelled','jvm_body_wear245.py','payment1.watch_after'),('fourth-watch-not-cancelled','jvm_body_wear245.py','watches.two_after'),('cancel-all-owners','jvm_body_wear245.py','paid_multi.after'),('no-reset-on-new-npc','jvm_possession_stats246.py','new NPC resets wear'),('no-login-sync','jvm_wear_login245.py','active.max')]

def main():
 q=argparse.ArgumentParser();q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);a=q.parse_args()
 with tempfile.TemporaryDirectory(prefix='legacy-mutants246-') as d:
  for mode,fixture,signal in CASES:
   bad=pathlib.Path(d)/(mode+'.jar');g.mutate(a.jar,bad,mode)
   if mode=='no-login-sync':
    # 246's stats login hook independently calls BodyWear.sync; removing only
    # the old hook is now behaviorally equivalent. Remove BOTH for calibration.
    with zipfile.ZipFile(bad) as z:raw=z.read(p.PS+'.class')
    def skip(k,code):
     ref=(p.BS,'login','(L'+p.LOGIN+';)V');hits=[pc for pc,ins in decode.walk(code) if ins[0]==0xb8 and p.constant_ref(k,struct.unpack('>H',ins[1:3])[0])==ref]
     assert len(hits)==1;code[hits[0]:hits[0]+3]=b'\x57\x00\x00'
    raw=g244.change(raw,'onPlayerLogin','(L'+p.LOGIN+';)V',skip);tmp=pathlib.Path(d)/'both-login.jar';g237.alter_zip(bad,tmp,replace={p.PS+'.class':raw});bad=tmp
   cmd=[sys.executable,str(ROOT/'tests'/fixture)] if fixture=='jvm_possession_stats246.py' else [sys.executable,str(ROOT/'tests/run_legacy246.py'),fixture]
   result=subprocess.run(cmd+['--java',str(a.java.resolve()),'--jar',str(bad)],capture_output=True,text=True);out=result.stdout+result.stderr
   if result.returncode==0 or 'AssertionError' not in out or signal not in out or 'VerifyError' in out or 'ClassFormatError' in out:raise AssertionError(f'uncalibrated legacy mutant {mode}: expected {signal!r}\n{out}')
   print('OK rejected legacy mutant:',mode,flush=True)
 print('245 behavioral mutants against 246: PASS',len(CASES))
if __name__=='__main__':main()
