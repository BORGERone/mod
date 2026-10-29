#!/usr/bin/env python3
"""Recalibrate all 250 FA/EMF mutants against actual 251 source/bytecode.
Minecraft, FA, EMF and TnM are test doubles; not an in-game test.
"""
import argparse,pathlib,subprocess,sys,tempfile,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'tools'),str(ROOT/'tests')]
import build_joml248,gen_api251,patch_player_motion251 as patch,patch_player_motion250 as p250
from jvm_fresh_motion250 import merge
from mutants250 import MUTANTS

def main():
 q=argparse.ArgumentParser();q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);a=q.parse_args();java=a.java.resolve();jar=a.jar.resolve()
 originals={'B':(ROOT/'src/build251/dev/settledlands/model/PlayerMotionBridge.java').read_text(),'H':(ROOT/'src/build251/dev/settledlands/model/HackSlashAccess.java').read_text(),'F':(ROOT/'src/build251/dev/settledlands/model/FreshEmfAccess.java').read_text()}
 with tempfile.TemporaryDirectory(prefix='fresh-mutants251-') as d:
  tmp=pathlib.Path(d);joml=tmp/'joml';api=tmp/'api';fixture=tmp/'fixture';fixture.mkdir();build_joml248.build(java,joml);gen_api251.main(api)
  manager=api/'traben/entity_model_features/EMFManager.java';s=manager.read_text().replace('public long entityRenderCount;', 'public long entityRenderCount; public java.util.Map<java.util.UUID,Integer> lastModelSuffixOfEntity;');manager.write_text(s)
  common=[str(java),'-jar',str(ROOT/'tools/vendor/ecj-3.46.100.jar'),'-21','-encoding','UTF-8','-g:none','-nowarn']
  src=merge(ROOT/'tests/java249',ROOT/'tests/java250');src.pop('testing/RealTnmFresh250Scenario.java')
  subprocess.run(common+['-cp',str(joml)+':'+str(jar),'-d',str(fixture),*map(str,sorted(src.values()))],check=True)
  def run(j,mode):
   cls='testing.Bridge249Scenarios' if mode=='normal-bridge' else 'testing.Fresh250Scenarios';mode='normal' if mode=='normal-bridge' else mode
   return subprocess.run([str(java),'-Xverify:all','-cp',':'.join(map(str,[fixture,j,joml])),cls,mode],capture_output=True,text=True)
  for mode in sorted({mode for _,_,mode,_ in MUTANTS}):
   r=run(jar,mode);assert r.returncode==0 and 'PASS ' in r.stdout,('baseline',mode,r.stdout,r.stderr)
  # Only render classes are recompiled; keep the candidate's login helper.
  replacements=patch.BRIDGE_CLASSES|{patch.HACK+'.class',patch.HACK+'$Frame.class',p250.FRESH+'.class',p250.FRESH+'$Run.class'}
  for i,(label,which,mode,edits) in enumerate(MUTANTS):
   text=dict(originals)
   for old,new in edits:
    assert text[which].count(old)==1,(label,old,text[which].count(old));text[which]=text[which].replace(old,new,1)
   work=tmp/str(i);srcdir=work/'src/dev/settledlands/model';srcdir.mkdir(parents=True);out=work/'classes';out.mkdir();sources=[]
   for key,name in [('B','PlayerMotionBridge'),('H','HackSlashAccess'),('F','FreshEmfAccess')]:
    p=srcdir/(name+'.java');p.write_text(text[key]);sources.append(p)
   r=subprocess.run(common+['-cp',str(joml),'-d',str(out),*map(str,sorted(api.rglob('*.java'))),*map(str,sources)],capture_output=True,text=True)
   assert r.returncode==0,('compile',label,r.stdout,r.stderr)
   mutant=work/'mutant.jar'
   with zipfile.ZipFile(jar) as base,zipfile.ZipFile(mutant,'w') as dst:
    for info in base.infolist():dst.writestr(info,(out/info.filename).read_bytes() if info.filename in replacements else base.read(info.filename))
   r=run(mutant,mode);assert r.returncode!=0 and 'AssertionError' in r.stderr,('escaped or unrelated',label,mode,r.stdout,r.stderr)
   print('DETECTED inherited',label,flush=True)
 print('250 FA behavioral mutants against 251: PASS',len(MUTANTS),'(synthetic APIs; not a game test)',flush=True)
if __name__=='__main__':main()
