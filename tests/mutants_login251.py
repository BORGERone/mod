#!/usr/bin/env python3
"""Verifier-valid negative controls for the login-appearance fixture."""
import argparse,pathlib,struct,subprocess,sys,tempfile,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
sys.path[:0]=[str(ROOT/'tests'),str(ROOT/'tools')]
import classedit,decode,gate_build237 as g,patch_player_motion251 as p

CASES=[
 ('no-login-hook','complete multiplayer snapshots'),
 ('trust-stale-model','stale unpossessed player retained'),
 ('no-self-snapshot','complete multiplayer snapshots'),
 ('no-other-snapshots','complete multiplayer snapshots'),
 ('no-observer-update','complete multiplayer snapshots'),
 ('duplicate-old-login-sync','fresh login duplicate/missing snapshot'),
 ('no-failure-isolation','one failed direction suppressed observers'),
 ('no-self-fallback','self snapshot missing when not in online list'),
 ('wrong-model-key','own body lost on relog'),
]

def mutate(jar,out,mode):
 cls=p.POSSESSION if mode in ('no-login-hook','duplicate-old-login-sync') else p.LOGIN
 with zipfile.ZipFile(jar) as z:raw=z.read(cls+'.class')
 k=classedit.Klass(raw);method='onPlayerLogin' if cls==p.POSSESSION else ('snapshot' if mode in ('trust-stale-model','wrong-model-key') else 'login')
 m=k.find_method(method);parts=classedit.code_parts(k,m);code=bytearray(parts['code'])
 def refs(owner,name):
  hits=[]
  for pc,ins in decode.walk(code):
   if ins[0] in (0xb6,0xb8) and p.constant_ref(k,struct.unpack('>H',ins[1:3])[0])[:2]==(owner,name):hits.append(pc)
  return hits
 if mode=='no-login-hook':
  hits=refs(p.LOGIN,'login');assert len(hits)==1;code[hits[0]:hits[0]+3]=b'\x57\x00\x00'
 elif mode=='trust-stale-model':
  hits=refs(p.POSSESSION,'isPossessed');assert len(hits)==1;code[hits[0]:hits[0]+3]=b'\x57\x04\x00'
 elif mode in ('no-self-snapshot','no-other-snapshots','no-observer-update','no-self-fallback'):
  hits=refs(p.LOGIN,'send');assert len(hits)==4,hits
  indexes={'no-self-snapshot':(0,3),'no-other-snapshots':(1,),'no-observer-update':(2,),'no-self-fallback':(3,)}[mode]
  for i in indexes:code[hits[i]:hits[i]+3]=b'\x58\x00\x00'
 elif mode=='duplicate-old-login-sync':
  needle=b'\x2b\x57\x00\x00\x2b';assert bytes(code).count(needle)==1;at=bytes(code).index(needle)+1
  code[at:at+3]=p.prev.op(0xb8,k.add_methodref(p.SYNC,'pushAllTo','(L'+p.SP+';)V'))
 elif mode=='no-failure-isolation':
  # Route only the per-snapshot catch to the outer catch, preserving valid stack
  # map frames while deliberately terminating the rest of the login iteration.
  hits=refs(p.LOGIN,'send');assert len(hits)==4
  changed=0
  for row in parts['exc']:
   if row[0] <= hits[1] < row[1] and row[2]!=parts['exc'][-1][2]:
    row[2]=parts['exc'][-1][2];changed+=1
  assert changed==2,parts['exc']
 elif mode=='wrong-model-key':
  hits=refs(p.POSSESSION,'possessModelKey');assert len(hits)==1
  desc='(Lnet/minecraft/world/entity/Entity;)Ljava/lang/String;';code[hits[0]:hits[0]+3]=p.prev.op(0xb8,k.add_methodref(p.POSSESSION,'possessName',desc))
 else:raise ValueError(mode)
 parts['code']=bytes(code);updated=k.dump(m,classedit.build_code_body(k,parts));g.alter_zip(jar,out,replace={cls+'.class':updated})

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--java',type=pathlib.Path,required=True);ap.add_argument('--base250',type=pathlib.Path,required=True);ap.add_argument('--jar',type=pathlib.Path,required=True);a=ap.parse_args()
 with tempfile.TemporaryDirectory(prefix='login-mutants251-') as d:
  for mode,signal in CASES:
   bad=pathlib.Path(d)/(mode+'.jar');mutate(a.jar,bad,mode)
   r=subprocess.run([sys.executable,str(ROOT/'tests/jvm_login_model251.py'),'--java',str(a.java.resolve()),'--base250',str(a.base250.resolve()),'--jar',str(bad)],capture_output=True,text=True)
   text=r.stdout+r.stderr
   if r.returncode==0 or 'AssertionError' not in text or signal not in text or any(x in text for x in ('VerifyError','ClassFormatError','NoClassDefFoundError','NoSuchMethodError')):
    raise AssertionError(f'uncalibrated mutant {mode}: rc={r.returncode}, expected {signal!r}\n{text}')
   print('DETECTED verifier-valid login mutant:',mode,flush=True)
 print('251 possessed model login behavioral mutants: PASS',len(CASES))
if __name__=='__main__':main()
