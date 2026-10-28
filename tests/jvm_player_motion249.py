#!/usr/bin/env python3
"""Actual 249 bridge on JVM 21/JOML; vanilla, NeoForge and TnM here are test doubles."""
import argparse,pathlib,shutil,subprocess,sys,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'));import build_joml248
SCENARIOS=('normal','idle','stale','crouch','ride','standvehicle','baby','nested','rotated','repeat','absent','unknown','notworld','wrongstate','firstperson','parcool','held','othermodel','badrig','duplicate','nonplayer','beforeError','applyError','nan','shear','renderer')
def main():
 q=argparse.ArgumentParser();q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);a=q.parse_args()
 with tempfile.TemporaryDirectory(prefix='jvm249-') as td:
  work=pathlib.Path(td);joml=work/'joml';out=work/'classes';out.mkdir();build_joml248.build(a.java.resolve(),joml)
  sources=sorted((ROOT/'tests/java249').rglob('*.java'));cp=str(joml)+':'+str(a.jar.resolve())
  subprocess.run([str(a.java),'-jar',str(ROOT/'tools/vendor/ecj-3.46.100.jar'),'-21','-encoding','UTF-8','-g:none','-nowarn','-cp',cp,'-d',str(out),*map(str,sources)],check=True)
  classpath=':'.join(map(str,[out,a.jar.resolve(),joml]))
  for mode in SCENARIOS:
   r=subprocess.run([str(a.java),'-Xverify:all','-cp',classpath,'testing.Bridge249Scenarios',mode],capture_output=True,text=True)
   assert r.returncode==0 and 'PASS '+mode in r.stdout,(mode,r.stdout,r.stderr)
  r=subprocess.run([str(a.java),'-Xverify:all','-cp',classpath,'testing.Math249Scenario'],capture_output=True,text=True);assert r.returncode==0 and 'PASS 1200' in r.stdout,(r.stdout,r.stderr);print(r.stdout.strip())
  missing=work/'missing';shutil.copytree(out,missing);(missing/'strm/touchnmotion/TouchNMotionHook.class').unlink();classpath=':'.join(map(str,[missing,a.jar.resolve(),joml]));r=subprocess.run([str(a.java),'-Xverify:all','-cp',classpath,'testing.Bridge249Scenarios','missingclasses'],capture_output=True,text=True);assert r.returncode==0 and 'PASS missingclasses' in r.stdout,(r.stdout,r.stderr)
  print(f'249 bridge: {len(SCENARIOS)+1} scenarios + 1200 poses PASS; synthetic game/TnM, NOT a game test')
if __name__=='__main__':main()
