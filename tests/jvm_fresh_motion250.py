#!/usr/bin/env python3
"""Exercise actual 250 bridge/JAR with real JOML; EMF, Minecraft and TnM are fixtures."""
import argparse, pathlib, subprocess, sys, tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'));import build_joml248
BRIDGE='normal idle stale crouch ride standvehicle baby nested rotated repeat restrot absent unknown notworld wrongstate firstperson parcool held othermodel badrig duplicate nonplayer beforeError applyError nan shear renderer'.split()
FRESH='normal pool repeat slim reload two-players variants source-no-animation missing-marker missing-creator root-motion synthetic-gait unconnected-gait wrong-target paused cancelled exception no-animation wrong-model-id forced-vanilla skip-variate no-hook duplicate-hook unsupported-emf'.split()
def merge(*roots):
 out={}
 for root in roots:
  for p in root.rglob('*.java'):out[p.relative_to(root).as_posix()]=p
 return out

def main():
 q=argparse.ArgumentParser();q.add_argument('--java',type=pathlib.Path,required=True);q.add_argument('--jar',type=pathlib.Path,required=True);a=q.parse_args();java=a.java.resolve();jar=a.jar.resolve()
 with tempfile.TemporaryDirectory(prefix='fresh250-') as d:
  tmp=pathlib.Path(d);joml=tmp/'joml';out=tmp/'classes';out.mkdir();build_joml248.build(java,joml)
  src=merge(ROOT/'tests/java249',ROOT/'tests/java250');src.pop('testing/RealTnmFresh250Scenario.java')
  cp=':'.join(map(str,[jar,joml]));subprocess.run([str(java),'-jar',str(ROOT/'tools/vendor/ecj-3.46.100.jar'),'-21','-encoding','UTF-8','-g:none','-nowarn','-cp',cp,'-d',str(out),*map(str,sorted(src.values()))],check=True)
  cp=':'.join(map(str,[out,jar,joml]))
  def run(cls,mode=''):
   r=subprocess.run([str(java),'-Xverify:all','-cp',cp,cls,*([mode] if mode else [])],capture_output=True,text=True)
   assert r.returncode==0 and 'PASS ' in r.stdout,(cls,mode,r.stdout,r.stderr)
  for mode in BRIDGE:run('testing.Bridge249Scenarios',mode)
  for mode in FRESH:run('testing.Fresh250Scenarios',mode)
  run('testing.Math249Scenario')
 print(f'250 actual bridge: {len(BRIDGE)+len(FRESH)} scenarios + 1200 JOML poses PASS; synthetic FA/EMF/TnM/MC; NOT a game test',flush=True)
if __name__=='__main__':main()
