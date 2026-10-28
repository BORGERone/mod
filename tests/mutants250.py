#!/usr/bin/env python3
"""Calibrate critical 250 Fresh/EMF failure paths; all mutations must be caught."""
import argparse,pathlib,subprocess,sys,tempfile,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'tools'),str(ROOT/'tests')]
import build_joml248,gen_api250,patch_player_motion250 as patch
from jvm_fresh_motion250 import merge
# file (bridge B or helper F), scenario, exact edit
MUTANTS=[
 ('fake-gait','F','synthetic-gait',[('''return expression != null && expression.contains("limb_speed") && expression.contains("var.run")
                && expression.contains("var.walk") && expression.contains("cos(");''','''return true;''')]),
 ('missing-creator','F','missing-creator',[('''marker == null || marker.expression == null || !"1".equals(marker.expression.trim())
                || ''','')]),
 ('missing-movement','F','missing-marker',[('''|| !movementExpression(rightMovement) || !movementExpression(leftMovement)''','')]),
 ('unconnected-gait','F','unconnected-gait',[('''if (rightLeg == null || rightLeg.expression == null || !rightLeg.expression.contains("var.mvmnt_rlegrx")
                || leftLeg == null || leftLeg.expression == null || !leftLeg.expression.contains("var.mvmnt_llegrx")) return false;''','')]),
 ('wrong-target','F','wrong-target',[('''line == null || line.partToApplyTo != parts[i]''','''line == null''')]),
 ('ignore-root','F','root-motion',[('''run.valid && unchangedRoot(rootBefore, root)''','''run.valid''')]),
 ('cancelled-hook','F','cancelled',[('''run.valid = !isCancelledByHook && hasFreshRig''','''run.valid = hasFreshRig'''),('''run.valid &= !wasCancelledByHook && context.error() == null;''','''run.valid &= context.error() == null;''')]),
 ('handler-error','F','exception',[('''run.valid &= !wasCancelledByHook && context.error() == null;''','''run.valid &= !wasCancelledByHook;''')]),
 ('render-count-leak','F','normal',[('''manager.entityRenderCount = oldRenderCount;''','')]),
 ('state-input-leak','F','normal',[('''state.setLimbAngle(oldLimbAngle);''','')]),
 ('phase-leak','F','normal',[('''EMFState.isLayerPhase = oldLayer;''','')]),
 ('duplicate-tnm','F','duplicate-hook',[('''return installed && hookCount("strm.touchnmotion.TouchNMotionHook") == 1;''','''return installed;''')]),
 ('never-run-emf','F','normal',[('''root.animate(); // respects EMF pause conditions and invokes TnM's hook''','')]),
 ('apply-twice','F','normal',[('''root.animate(); // respects EMF pause conditions and invokes TnM's hook''','''root.animate(); manager.entityRenderCount = PRIVATE_COUNTS.getAndIncrement(); root.animate();''')]),
 ('global-variant-write','F','variants',[('''            dst.setVariantStateTo(suffix);''','''            dst.setVariantStateTo(suffix); traben.entity_model_features.EMFManager.getInstance().lastModelSuffixOfEntity.put(id,suffix);''')]),
 ('restore-conflicting-clips','B','missing-marker',[('''if (freshPath && !disabled) {''','''if (false) {''')]),
 ('stale-shared-model','F','source-no-animation',[('''|| src == dst || src.modelName == null''','''|| src == dst || !src.hasAnimation() || src.modelName == null''')]),
 ('no-reload-invalidation','B','reload',[('''candidate.anchor == current && candidate.slim == slim''','''candidate.slim == slim''')]),
 ('no-vanilla-pose','B','normal-bridge',[('''vanilla.setupAnim(player, swing, amount, age, Mth.wrapDegrees(headYaw), headPitch);''','')]),
 ('wrong-pivot','B','normal-bridge',[('''.mul(new Matrix4f(virtualRest.get(part)).invert())''','')]),
 ('parent-transform','B','normal-bridge',[('''Matrix4f parent = world(parents.get(real), parents, updates);''','''Matrix4f parent = new Matrix4f();''')]),
]

def main():
 q=argparse.ArgumentParser();q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);a=q.parse_args();java=a.java.resolve();jar=a.jar.resolve()
 originals={'B':(ROOT/'src/build250/dev/settledlands/model/PlayerMotionBridge.java').read_text(),'F':(ROOT/'src/build250/dev/settledlands/model/FreshEmfAccess.java').read_text()}
 with tempfile.TemporaryDirectory(prefix='mutants250-') as d:
  tmp=pathlib.Path(d);joml=tmp/'joml';api=tmp/'api';fixture=tmp/'fixture';fixture.mkdir();build_joml248.build(java,joml);gen_api250.main(api)
  # Extend only the compile silhouette for the explicit global-write mutant.
  manager=api/'traben/entity_model_features/EMFManager.java';s=manager.read_text().replace('public long entityRenderCount;', 'public long entityRenderCount; public java.util.Map<java.util.UUID,Integer> lastModelSuffixOfEntity;');manager.write_text(s)
  common=[str(java),'-jar',str(ROOT/'tools/vendor/ecj-3.46.100.jar'),'-21','-encoding','UTF-8','-g:none','-nowarn']
  src=merge(ROOT/'tests/java249',ROOT/'tests/java250');src.pop('testing/RealTnmFresh250Scenario.java')
  subprocess.run(common+['-cp',str(joml)+':'+str(jar),'-d',str(fixture),*map(str,sorted(src.values()))],check=True)
  def run(jarfile,mode):
   cls='testing.Bridge249Scenarios' if mode in ('normal-bridge',) else 'testing.Fresh250Scenarios'
   mode='normal' if mode=='normal-bridge' else mode
   return subprocess.run([str(java),'-Xverify:all','-cp',':'.join(map(str,[fixture,jarfile,joml])),cls,mode],capture_output=True,text=True)
  modes=sorted({mode for _,_,mode,_ in MUTANTS})
  for mode in modes:
   r=run(jar,mode);assert r.returncode==0 and 'PASS ' in r.stdout,('baseline',mode,r.stdout,r.stderr)
  for i,(label,which,mode,edits) in enumerate(MUTANTS):
   text=dict(originals)
   for old,new in edits:assert text[which].count(old)==1,(label,old,text[which].count(old));text[which]=text[which].replace(old,new,1)
   work=tmp/str(i);srcdir=work/'src/dev/settledlands/model';srcdir.mkdir(parents=True);out=work/'classes';out.mkdir()
   b=srcdir/'PlayerMotionBridge.java';b.write_text(text['B']);f=srcdir/'FreshEmfAccess.java';f.write_text(text['F'])
   r=subprocess.run(common+['-cp',str(joml),'-d',str(out),*map(str,sorted(api.rglob('*.java'))),str(b),str(f)],capture_output=True,text=True)
   assert r.returncode==0,('compile',label,r.stdout,r.stderr)
   mutant=work/'mutant.jar'
   with zipfile.ZipFile(jar) as base,zipfile.ZipFile(mutant,'w') as dst:
    for info in base.infolist():dst.writestr(info,(out/info.filename).read_bytes() if info.filename in patch.ADDED else base.read(info.filename))
   r=run(mutant,mode);assert r.returncode!=0 and 'AssertionError' in r.stderr,('escaped or unrelated',label,mode,r.stdout,r.stderr)
   print('DETECTED',label,flush=True)
 print('250 behavioral mutants: PASS',len(MUTANTS),'(compiled and rejected; synthetic APIs)',flush=True)
if __name__=='__main__':main()
