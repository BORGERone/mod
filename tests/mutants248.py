#!/usr/bin/env python3
"""Calibrate behavior tests with intentionally broken bridge bytecode.
Each mutant must compile, load and trip a scenario assertion. Test doubles are
not a substitute for Minecraft; these are negative controls, not game tests.
"""
import argparse,collections,pathlib,subprocess,sys,tempfile,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'));import build_joml248,gen_api248,patch_touchmotion248 as patch

# Each edit is deliberately small and targets one contract. The original bridge
# is never modified; ECJ compiles each mutant in an isolated temporary directory.
MUTANTS=[
 ('unloaded','absent', [('if (mods == null || !mods.isLoaded("touch_n_motion")) return false;','if (mods == null) return false;'),('if (!"1.0.4".equals(version)) {','if (false) {')]),
 ('version','unknown', [('if (!"1.0.4".equals(version)) {','if (false) {')]),
 ('name','othermodel', [('return name.equals("ivy") || name.equals("ivy_oleander");','return true;')]),
 ('state-identity','wrongstate', [('if (state == null || stateEntity.invoke(state) != entity) return;','if (state == null) return;')]),
 ('first-person','firstperson', [('if (Boolean.TRUE.equals(firstPersonHand.invoke(state))) return;','')]),
 ('in-world','notworld', [('if (!Boolean.TRUE.equals(inWorld.invoke(null, entity))) return;','')]),
 ('parcool','parcool', [('if (Boolean.TRUE.equals(parcool.invoke(null, entity)) || Boolean.TRUE.equals(held.invoke(null, entity))) return;','if (Boolean.TRUE.equals(held.invoke(null, entity))) return;')]),
 ('whole-body','held', [('if (Boolean.TRUE.equals(parcool.invoke(null, entity)) || Boolean.TRUE.equals(held.invoke(null, entity))) return;','if (Boolean.TRUE.equals(parcool.invoke(null, entity))) return;')]),
 ('broken-rig','badrig', [('if (scope == null || disabled || !validRig(model, parents)) return;','if (scope == null || disabled) return;')]),
 ('duplicate-bones','duplicate', [('if (part == null || seen.put(part, Boolean.TRUE) != null || !beneath(part, model.getRootPart(), parents)) return false;','if (part == null || !beneath(part, model.getRootPart(), parents)) return false;')]),
 ('modelspace','normal', [('modelBefore.invoke(null, entity, stack, partial, NO_MODEL_CALLBACK);','')]),
 ('record-feet','normal', [('recordAnimated.invoke(null, uuid, parts);','')]),
 ('tnm-apply','normal', [('applyAll.invoke(null, uuid, parts, true, true);','')]),
 ('pose-stack-frame','normal', [('            stack.pushPose();\n            scope.pushed = true;','            scope.pushed = true;')]),
 ('pose-stack-finally','normal', [('if (scope.pushed) stack.popPose();','')]),
 ('cancel-on-unrepresentable','afterShear', [('if (!rig.commit(parents)) cancel(scope);','rig.commit(parents);')]),
 ('partial-commit','afterShear', [('updates.put(real, result);','result.set(real); updates.put(real, result);')]),
 ('allow-shear','shear', [('return matrix.equals(pose.matrix(), MATRIX_EPS) ? pose : null;','return pose;')]),
 ('local-not-global','normal', [('pose.set(part);','new Pose(real).set(part);')]),
 ('parent-transform','normal', [('Matrix4f parent = world(parents.get(real), parents, updates);','Matrix4f parent = new Matrix4f();')]),
 ('swap-arms','normal', [('"right_arm", "left_arm", "right_leg"','"left_arm", "right_arm", "right_leg"')]),
 ('discard-scales','uniform', [('.rotate(new Quaternionf().rotationZYX(rz, ry, rx)).scale(sx, sy, sz);','.rotate(new Quaternionf().rotationZYX(rz, ry, rx)).scale(1f, 1f, 1f);')]),
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

def run(java,classpath,mode):
 command=[str(java),'-Xverify:all','-cp',classpath]
 command += ['testing.MathPropertyScenario'] if mode=='@math' else ['testing.BridgeScenarios',mode]
 return subprocess.run(command,capture_output=True,text=True)

def main():
 q=argparse.ArgumentParser();q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);a=q.parse_args();java=a.java.resolve();jar=a.jar.resolve()
 original=(ROOT/'src/build248/dev/settledlands/model/TouchMotionBridge.java').read_text()
 compiler=ROOT/'tools/vendor/ecj-3.46.100.jar'
 with tempfile.TemporaryDirectory(prefix='mutants-248-') as td:
  tmp=pathlib.Path(td);joml=tmp/'joml';api=tmp/'api';fixture=tmp/'fixture';fixture.mkdir();build_joml248.build(java,joml);gen_api248.main(api)
  common=[str(java),'-jar',str(compiler),'-21','-encoding','UTF-8','-g:none','-nowarn']
  source=sorted((ROOT/'tests/java248').rglob('*.java'))
  subprocess.run(common+['-cp',str(joml)+':'+str(jar),'-d',str(fixture),*map(str,source)],check=True)
  basecp=':'.join(map(str,[fixture,jar,joml]));modes=sorted({mode for _,mode,_ in MUTANTS})
  for mode in modes:
   r=run(java,basecp,mode);assert r.returncode==0 and 'PASS ' in r.stdout,('fixture baseline',mode,r.stdout,r.stderr)
  for index,(label,mode,edits) in enumerate(MUTANTS):
   text=original
   for old,new in edits:
    assert text.count(old)==1,(label,old,text.count(old));text=text.replace(old,new)
   work=tmp/str(index);src=work/'src/dev/settledlands/model/TouchMotionBridge.java';src.parent.mkdir(parents=True);src.write_text(text);out=work/'classes';out.mkdir()
   r=subprocess.run(common+['-cp',str(joml),'-d',str(out),*map(str,sorted(api.rglob('*.java'))),str(src)],capture_output=True,text=True)
   assert r.returncode==0,('mutant did not compile',label,r.stdout,r.stderr)
   mutant=work/'mutant.jar'
   with zipfile.ZipFile(jar) as base,zipfile.ZipFile(mutant,'w') as dst:
    for info in base.infolist():
     data=(out/info.filename).read_bytes() if info.filename in patch.ADDED else base.read(info.filename)
     dst.writestr(info,data)
   r=run(java,':'.join(map(str,[fixture,mutant,joml])),mode)
   assert r.returncode!=0 and 'AssertionError' in r.stderr,('mutant escaped or unrelated failure',label,mode,r.stdout,r.stderr)
   print('DETECTED',label,flush=True)
  print('248 behavioral mutants: PASS',len(MUTANTS),'(all compiled, loaded and rejected by calibrated assertions; synthetic API)',flush=True)
if __name__=='__main__':main()
