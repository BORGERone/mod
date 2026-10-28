#!/usr/bin/env python3
"""Calibrate 249 bridge tests: each mutant must compile and trip a scenario assertion."""
import argparse,pathlib,subprocess,sys,tempfile,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'));import build_joml248,gen_api249,patch_player_motion249 as patch
MUTANTS=[
 ('unloaded','absent',[('if (mods == null || !mods.isLoaded("touch_n_motion")) return false;','if (mods == null) return false;'),('if (!"1.0.4".equals(version)) {','if (false) {')]),
 ('version','unknown',[('if (!"1.0.4".equals(version)) {','if (false) {')]),
 ('name','othermodel',[('return name.equals("ivy") || name.equals("ivy_oleander");','return true;')]),
 ('state-identity','wrongstate',[('if (state == null || stateEntity.invoke(state) != entity) return;','if (state == null) return;')]),
 ('first-person','firstperson',[('if (Boolean.TRUE.equals(firstPersonHand.invoke(state))) return;','')]),
 ('in-world','notworld',[('if (!Boolean.TRUE.equals(inWorld.invoke(null, entity))) return;','')]),
 ('parcool','parcool',[('if (Boolean.TRUE.equals(parcool.invoke(null, entity)) || Boolean.TRUE.equals(held.invoke(null, entity))) return;','if (Boolean.TRUE.equals(held.invoke(null, entity))) return;')]),
 ('whole-body','held',[('if (Boolean.TRUE.equals(parcool.invoke(null, entity)) || Boolean.TRUE.equals(held.invoke(null, entity))) return;','if (Boolean.TRUE.equals(parcool.invoke(null, entity))) return;')]),
 ('broken-rig','badrig',[('if (scope == null || disabled || !validRig(model, parents)) return;','if (scope == null || disabled) return;')]),
 ('duplicate-bones','duplicate',[('if (part == null || seen.put(part, Boolean.TRUE) != null || !beneath(part, model.getRootPart(), parents)) return false;','if (part == null || !beneath(part, model.getRootPart(), parents)) return false;')]),
 ('modelspace','normal',[('modelBefore.invoke(null, entity, stack, partial, NO_MODEL_CALLBACK);','')]),
 ('record-feet','normal',[('recordAnimated.invoke(null, uuid, parts);','')]),
 ('tnm-apply','normal',[('applyAll.invoke(null, uuid, parts, true, true);','')]),
 ('double-apply','normal',[('applyAll.invoke(null, uuid, parts, true, true);','applyAll.invoke(null, uuid, parts, true, true); applyAll.invoke(null, uuid, parts, true, true);')]),
 ('pose-stack-frame','normal',[('            stack.pushPose();\n            scope.pushed = true;','            scope.pushed = true;')]),
 ('pose-stack-finally','normal',[('if (scope.pushed) stack.popPose();','')]),
 ('native-runs-on-player','normal',[('if (!applied) {','if (true) {')]),
 ('native-fallback-missing','absent',[('model.setupAnimRaw(talking, alive, state1, state2, flag, animation, tick,\n                               limbSwing, limbAmount, age, headYaw, headPitch, partial);','')]),
 ('stale-pose','stale',[('model.getRootPart().getAllParts().forEach(ModelPart::resetPose);','')]),
 ('vanilla-preparation','normal',[('vanilla.prepareMobModel(player, swing, amount, partial);','')]),
 ('vanilla-pose','normal',[('vanilla.setupAnim(player, swing, amount, age, Mth.wrapDegrees(headYaw), headPitch);','')]),
 ('arm-pose','crouch',[('vanilla.rightArmPose = current.rightArmPose;','vanilla.rightArmPose = current.leftArmPose;')]),
 ('crouch','crouch',[('vanilla.crouching = current.crouching;','vanilla.crouching = false;')]),
 ('attack-phase','crouch',[('vanilla.attackTime = player.getAttackAnim(partial);','vanilla.attackTime = 0f;')]),
 ('riding','ride',[('vanilla.riding = shouldSit;','vanilla.riding = false;')]),
 ('baby-phase','baby',[('swing = limbSwing * (vanilla.young ? 3f : 1f);','swing = limbSwing;')]),
 ('allow-shear','shear',[('return matrix.equals(pose.matrix(), MATRIX_EPS) ? pose : null;','return pose;')]),
 ('wrong-pivot','normal',[('.mul(new Matrix4f(virtualRest.get(part)).invert())','')]),
 ('wrong-order','restrot',[( '.mul(new Matrix4f(virtualRest.get(part)).invert())\n                    .mul(realRest.get(real));', '.mul(realRest.get(real))\n                    .mul(new Matrix4f(virtualRest.get(part)).invert());')]),
 ('parent-transform','normal',[('Matrix4f parent = world(parents.get(real), parents, updates);','Matrix4f parent = new Matrix4f();')]),
 ('swap-arms','normal',[('"right_arm", "left_arm", "right_leg"','"left_arm", "right_arm", "right_leg"')]),
 ('gimbal','@math', [('''Matrix4f rotation = new Matrix4f().rotation(q);
            float cosine = (float)Math.hypot(rotation.m00(), rotation.m01());
            float angleY = (float)Math.atan2(-rotation.m02(), cosine);
            float angleX, angleZ;
            if (cosine > 1e-6f) {
                angleX = (float)Math.atan2(rotation.m12(), rotation.m22());
                angleZ = (float)Math.atan2(rotation.m01(), rotation.m00());
            } else {
                angleX = (float)Math.atan2(-rotation.m21(), rotation.m11());
                angleZ = 0f;
            }''','''Vector3f angles = new Matrix4f().rotation(q).getEulerAnglesZYX(new Vector3f());
            float angleX=angles.x, angleY=angles.y, angleZ=angles.z;''')]),
]

def run(java,cp,mode):return subprocess.run([str(java),'-Xverify:all','-cp',cp,*(['testing.Math249Scenario'] if mode=='@math' else ['testing.Bridge249Scenarios',mode])],capture_output=True,text=True)
def main():
 q=argparse.ArgumentParser();q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);a=q.parse_args();java=a.java.resolve();jar=a.jar.resolve();original=(ROOT/'src/build249/dev/settledlands/model/PlayerMotionBridge.java').read_text()
 with tempfile.TemporaryDirectory(prefix='mutants249-') as td:
  tmp=pathlib.Path(td);joml=tmp/'joml';api=tmp/'api';fixture=tmp/'fixture';fixture.mkdir();build_joml248.build(java,joml);gen_api249.main(api)
  common=[str(java),'-jar',str(ROOT/'tools/vendor/ecj-3.46.100.jar'),'-21','-encoding','UTF-8','-g:none','-nowarn']
  subprocess.run(common+['-cp',str(joml)+':'+str(jar),'-d',str(fixture),*map(str,sorted((ROOT/'tests/java249').rglob('*.java')))],check=True)
  modes=sorted({mode for _,mode,_ in MUTANTS});cp=':'.join(map(str,[fixture,jar,joml]))
  for mode in modes:
   r=run(java,cp,mode);assert r.returncode==0 and 'PASS ' in r.stdout,('baseline',mode,r.stdout,r.stderr)
  for i,(label,mode,edits) in enumerate(MUTANTS):
   text=original
   for old,new in edits:assert text.count(old)==1,(label,old,text.count(old));text=text.replace(old,new)
   work=tmp/str(i);src=work/'src/dev/settledlands/model/PlayerMotionBridge.java';src.parent.mkdir(parents=True);src.write_text(text);out=work/'classes';out.mkdir()
   r=subprocess.run(common+['-cp',str(joml),'-d',str(out),*map(str,sorted(api.rglob('*.java'))),str(src)],capture_output=True,text=True);assert r.returncode==0,('compile',label,r.stdout,r.stderr)
   mutant=work/'mutant.jar'
   with zipfile.ZipFile(jar) as base,zipfile.ZipFile(mutant,'w') as dst:
    for info in base.infolist():dst.writestr(info,(out/info.filename).read_bytes() if info.filename in patch.ADDED else base.read(info.filename))
   r=run(java,':'.join(map(str,[fixture,mutant,joml])),mode);assert r.returncode!=0 and 'AssertionError' in r.stderr,('escaped or unrelated',label,mode,r.stdout,r.stderr)
   print('DETECTED',label,flush=True)
 print('249 behavioral mutants: PASS',len(MUTANTS),'(all compiled, loaded, rejected; synthetic API)',flush=True)
if __name__=='__main__':main()
