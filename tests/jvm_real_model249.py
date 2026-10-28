#!/usr/bin/env python3
"""Run real BbModel, BbAnimation and 249 bridge with synthetic Minecraft/TnM classes."""
import argparse,os,pathlib,shutil,subprocess,sys,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'));import build_joml248

def main():
 q=argparse.ArgumentParser();q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);a=q.parse_args();java=a.java.resolve();jar=a.jar.resolve()
 with tempfile.TemporaryDirectory(prefix='realmodel249-') as td:
  tmp=pathlib.Path(td);fake=tmp/'auto';fake.mkdir();env=dict(os.environ,TMPDIR=str(fake))
  r=subprocess.run([sys.executable,str(ROOT/'tools/jvm_verify.py'),str(jar),'dev/settledlands/model/BbModel#getName','--java',str(java)],env=env,capture_output=True,text=True);assert r.returncode==0 and 'DECLARED dev/settledlands/model/BbModel#getName' in r.stdout,(r.stdout,r.stderr)
  autos=list(fake.glob('jvmverify-*'));assert len(autos)==1;autostub=autos[0]
  for n in ('java','javax','jdk','sun','org/joml'):
   shutil.rmtree(autostub/n,ignore_errors=True)
  out=tmp/'out';out.mkdir();joml=tmp/'joml';build_joml248.build(java,joml)
  excluded={'dev/settledlands/model/BbModel.java','testing/Bridge249Scenarios.java','testing/Math249Scenario.java'}
  src=[p for p in (ROOT/'tests/java249').rglob('*.java') if p.relative_to(ROOT/'tests/java249').as_posix() not in excluded]+list((ROOT/'tests/java249-realmodel').rglob('*.java'))
  cp=':'.join(map(str,[jar,autostub,joml]));subprocess.run([str(java),'-jar',str(ROOT/'tools/vendor/ecj-3.46.100.jar'),'-21','-encoding','UTF-8','-g:none','-nowarn','-cp',cp,'-d',str(out),*map(str,sorted(src))],check=True)
  cp=':'.join(map(str,[out,jar,autostub,joml]));r=subprocess.run([str(java),'-Xverify:all','-cp',cp,'testing.RealModel249Scenario'],capture_output=True,text=True);assert r.returncode==0 and 'PASS real BbModel/BbAnimation' in r.stdout,(r.stdout,r.stderr);print(r.stdout.strip())
  for mode in ('active','absent'):
   r=subprocess.run([str(java),'-Xverify:all','-cp',cp,'testing.RealActor249Scenario',mode],capture_output=True,text=True);assert r.returncode==0 and 'PASS actual actor '+mode in r.stdout,(mode,r.stdout,r.stderr);print(r.stdout.strip())
  print('NOT Minecraft or a visual test')
if __name__=='__main__':main()
