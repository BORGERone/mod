#!/usr/bin/env python3
"""Real Java 21 + JOML pose and guard checks; Minecraft/TnM are test doubles."""
import argparse,pathlib,shutil,subprocess,sys,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'));import build_joml248
SCENARIOS=('normal','idle','absent','unknown','notworld','wrongstate','firstperson','parcool','held','othermodel','badrig','duplicate','nonplayer','shear','afterShear','uniform','beforeError','beforeErrorTwice','applyError','nan','nested','renderError','rotated')

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--java',required=True,type=pathlib.Path);ap.add_argument('--jar',required=True,type=pathlib.Path);a=ap.parse_args();java=a.java.resolve();jar=a.jar.resolve()
 with tempfile.TemporaryDirectory(prefix='jvm-248-') as tmp:
  work=pathlib.Path(tmp);joml=work/'joml';classes=work/'classes';classes.mkdir();build_joml248.build(java,joml)
  sources=sorted((ROOT/'tests/java248').rglob('*.java'))
  subprocess.run([str(java),'-jar',str(ROOT/'tools/vendor/ecj-3.46.100.jar'),'-21','-encoding','UTF-8','-g:none','-nowarn','-cp',str(joml)+':'+str(jar),'-d',str(classes),*map(str,sources)],check=True)
  cp=':'.join(map(str,[classes,jar,joml]))
  for scenario in SCENARIOS:
   r=subprocess.run([str(java),'-Xverify:all','-cp',cp,'testing.BridgeScenarios',scenario],capture_output=True,text=True)
   assert r.returncode==0 and 'PASS '+scenario in r.stdout,(scenario,r.stdout,r.stderr)
  r=subprocess.run([str(java),'-Xverify:all','-cp',cp,'testing.MathPropertyScenario'],capture_output=True,text=True)
  assert r.returncode==0 and 'PASS 1200' in r.stdout,(r.stdout,r.stderr)
  missing=work/'missing';shutil.copytree(classes,missing);(missing/'strm/touchnmotion/TouchNMotionHook.class').unlink()
  cp=':'.join(map(str,[missing,jar,joml]));r=subprocess.run([str(java),'-Xverify:all','-cp',cp,'testing.BridgeScenarios','missingclasses'],capture_output=True,text=True)
  assert r.returncode==0 and 'PASS missingclasses' in r.stdout,(r.stdout,r.stderr)
  print('Java 21: 24 guard/rollback/normal scenarios + 1200 deterministic real-JOML poses PASS; synthetic game API, NOT Minecraft')
if __name__=='__main__':main()
