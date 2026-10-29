#!/usr/bin/env python3
"""Real actor/bridge and PAL 1.1.6; controlled PAL layer and synthetic game/EMF/TnM.

Pass the user's actual PAL-compatible jar separately; it is neither copied nor
redistributed. This exercises real PlayerAnimManager, AnimationStack,
PlayerAnimBone and RenderUtil, not a Minecraft/mixin or visual game test.
"""
import argparse,hashlib,os,pathlib,shutil,subprocess,sys,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'tools'),str(ROOT/'tests')]
import build_joml248
from jvm_fresh_motion250 import merge
PAL_SHA='e41ee06000fc95444a482018ec24b3f1e79eb111ca6b8ca119e61ceabf9136c5'
MODES='dry-full dry-half swim-water swim-fluid swim-transition swim-pitch-transition elytra fallback bad-world before-error hns-inactive hns-unknown hns-addon hns-active hns-blocking hns-bad-body hns-bad-item hns-item-throw fail-parts nested-draw nested-setup render-error'.split()
def make_fixture(java,jar,pal,tmp):
 """Compile test doubles once; the PAL and SettledLands classes remain real."""
 fake=tmp/'auto';fake.mkdir();env=dict(os.environ,TMPDIR=str(fake))
 r=subprocess.run([sys.executable,str(ROOT/'tools/jvm_verify.py'),str(jar),'dev/settledlands/model/BbModel#getName','--java',str(java)],env=env,capture_output=True,text=True);assert r.returncode==0 and 'DECLARED dev/settledlands/model/BbModel#getName' in r.stdout,(r.stdout,r.stderr)
 autos=list(fake.glob('jvmverify-*'));assert len(autos)==1;autostub=autos[0]
 for n in ('java','javax','jdk','sun','org/joml','com/zigythebird'):shutil.rmtree(autostub/n,ignore_errors=True)
 out=tmp/'out';out.mkdir();joml=tmp/'joml';build_joml248.build(java,joml)
 excluded={'dev/settledlands/model/BbModel.java','testing/Bridge249Scenarios.java','testing/Math249Scenario.java','testing/Fresh250Scenarios.java','testing/RealTnmFresh250Scenario.java'}
 merged=merge(ROOT/'tests/java249',ROOT/'tests/java250',ROOT/'tests/java249-realmodel',ROOT/'tests/java251')
 src=[p for name,p in merged.items() if name not in excluded]
 cp=':'.join(map(str,[jar,pal,autostub,joml]));subprocess.run([str(java),'-jar',str(ROOT/'tools/vendor/ecj-3.46.100.jar'),'-21','-encoding','UTF-8','-g:none','-nowarn','-cp',cp,'-d',str(out),*map(str,sorted(src))],check=True)
 return out,autostub,joml

def main():
 q=argparse.ArgumentParser();q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);q.add_argument('--pal',required=True,type=pathlib.Path);a=q.parse_args();java=a.java.resolve();jar=a.jar.resolve();pal=a.pal.resolve()
 assert hashlib.sha256(pal.read_bytes()).hexdigest()==PAL_SHA,'unexpected PAL binary'
 with tempfile.TemporaryDirectory(prefix='motion251-') as d:
  tmp=pathlib.Path(d);out,autostub,joml=make_fixture(java,jar,pal,tmp)
  cp=':'.join(map(str,[out,jar,pal,autostub,joml]))
  for mode in MODES:
   r=subprocess.run([str(java),'-Xverify:all','-cp',cp,'testing.Motion251Scenario',mode],capture_output=True,text=True)
   assert r.returncode==0 and 'PASS '+mode in r.stdout,(mode,r.stdout,r.stderr)
   print(r.stdout.strip(),flush=True)
  r=subprocess.run([str(java),'-Xverify:all','-cp',cp,'testing.RealModel249Scenario'],capture_output=True,text=True)
  assert r.returncode==0 and 'PASS real BbModel/BbAnimation' in r.stdout,(r.stdout,r.stderr)
  print(r.stdout.strip(),flush=True)
  for mode in ('active','absent'):
   r=subprocess.run([str(java),'-Xverify:all','-cp',cp,'testing.RealActor249Scenario',mode],capture_output=True,text=True)
   assert r.returncode==0 and 'PASS actual actor '+mode in r.stdout,(mode,r.stdout,r.stderr)
   print(r.stdout.strip(),flush=True)
 print('251 JVM scenarios PASS; controlled layer/synthetic Minecraft+EMF+TnM; NOT a game or visual test')
if __name__=='__main__':main()
