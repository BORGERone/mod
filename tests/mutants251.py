#!/usr/bin/env python3
"""Compile deliberately broken 251 bridges and require behavioral tests to reject them.

Actual patched BbModel, PAL and JOML; Minecraft, EMF, TnM and animation layers
are test doubles. This calibrates tests, it does not replace a game test.
"""
import argparse,hashlib,pathlib,subprocess,sys,tempfile,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'tools'),str(ROOT/'tests')]
import gen_api251,patch_player_motion251 as patch
from jvm_motion251 import make_fixture,PAL_SHA

# (description, edited source, negative-control scenario, exact replacements)
MUTANTS=[
 ('crawl-missing-pitch','B','dry-full',[('stack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(amount * target));','')]),
 ('crawl-missing-offset','B','dry-full',[('if (player.isVisuallySwimming()) stack.translate(0.0f, -1.0f, 0.3f);','')]),
 ('crawl-no-transition','B','dry-half',[('rotationDegrees(amount * target)','rotationDegrees(target)')]),
 ('water-pitch-ignored','B','swim-water',[('float target = fluid ? -90.0f - player.getViewXRot(partial) : -90.0f;','float target = -90.0f;')]),
 ('fluid-type-ignored','B','swim-fluid',[('player.isInWater() || player.isInFluidType((type, height) -> player.canSwimInFluidType(type))','player.isInWater()')]),
 ('view-pitch-not-interpolated','B','swim-pitch-transition',[('player.getViewXRot(partial)','player.getXRot()')]),
 ('elytra-ignored','B','elytra',[('!player.isFallFlying() && amount > 0.0f','amount > 0.0f')]),
 ('no-root-rollback','B','fallback',[('''        rollbackRoot(scope);
        scope.combat = null;
        scope.combatReady = false;''','''        scope.combat = null;
        scope.combatReady = false;''')]),
 ('unsupported-pal-version','B','hns-unknown',[('!"1.1.6+mc.1.21.1".equals(pal)','false')]),
 ('competing-addon-ignored','B','hns-addon',[('mods.isLoaded("emf_compat_hackers_and_slashers")','false')]),
 ('wrong-layer-id','H','hns-active',[('"hackersandslashers", "attack_layer"','"hackersandslashers", "unknown_layer"')]),
 ('unrelated-pal-layer','H','hns-inactive',[('attack == null || !attack.isActive() || processor','attack == null || processor')]),
 ('missing-processor','H','hns-active',[('processor.handleAnimations(manager.getTickDelta(), false);','')]),
 ('wrong-processor-delta','H','hns-active',[('processor.handleAnimations(manager.getTickDelta(), false);','processor.handleAnimations(0f, false);')]),
 ('duplicate-overlay-before-fresh','B','hns-active',[('if (Boolean.TRUE.equals(freshAnimate.invoke(null, vanilla, player))) {','if (scope.combat != null) combatParts.invoke(null, scope.combat, vanilla);\n                    if (Boolean.TRUE.equals(freshAnimate.invoke(null, vanilla, player))) {')]),
 ('skip-body-scale','H','hns-active',[('stack.scale(body.getScaleX(), body.getScaleY(), body.getScaleZ());','')]),
 ('skip-body-position','H','hns-active',[('stack.translate(-body.getPosX() / 16f, body.getPosY() / 16f + .75f, body.getPosZ() / 16f);','stack.translate(0f, .75f, 0f);')]),
 ('body-yaw-wrong-sign','H','hns-active',[('body.setRotY(-body.getRotY());','body.setRotY(body.getRotY());')]),
 ('skip-body-rotation','H','hns-active',[('RenderUtil.rotateMatrixAroundBone(stack, body);','')]),
 ('omit-head','H','hns-active',[('apply(frame.manager, model.head, "head");','')]),
 ('omit-torso','H','hns-active',[('apply(frame.manager, model.body, "torso");','')]),
 ('omit-right-arm','H','hns-active',[('apply(frame.manager, model.rightArm, "right_arm");','')]),
 ('omit-left-arm','H','hns-active',[('apply(frame.manager, model.leftArm, "left_arm");','')]),
 ('omit-right-leg','H','hns-active',[('apply(frame.manager, model.rightLeg, "right_leg");','')]),
 ('omit-left-leg','H','hns-active',[('apply(frame.manager, model.leftLeg, "left_leg");','')]),
 ('swap-item-channels','H','hns-active',[('left ? "left_item" : "right_item"','left ? "right_item" : "left_item"')]),
 ('wrong-item-x','H','hns-active',[('bone.getPosX() / 16f, -bone.getPosY() / 16f','-bone.getPosX() / 16f, -bone.getPosY() / 16f')]),
 ('wrong-item-y','H','hns-active',[('bone.getPosX() / 16f, -bone.getPosY() / 16f','bone.getPosX() / 16f, bone.getPosY() / 16f')]),
 ('wrong-item-roll','H','hns-active',[('rotationZ(-bone.getRotY())','rotationZ(bone.getRotY())')]),
 ('wrong-item-yaw','H','hns-active',[('rotationY(-bone.getRotZ())','rotationY(bone.getRotZ())')]),
 ('wrong-item-pitch','H','hns-active',[('rotationX(-bone.getRotX())','rotationX(bone.getRotX())')]),
 ('missing-item-scale','H','hns-active',[('stack.scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());','')]),
 ('zero-body-accepted','H','hns-bad-body',[('''                || Math.abs(bone.getScaleX()) < 1e-8f || Math.abs(bone.getScaleY()) < 1e-8f || Math.abs(bone.getScaleZ()) < 1e-8f)''',''')''')]),
 ('nan-item-accepted','H','hns-bad-item',[('!Float.isFinite(bone.getPosX()) || ','')]),
 ('combat-failure-disables-fresh','B','fail-parts',[('''        combatDisabled = true;
        combatWarn(''','''        combatDisabled = true;
        disabled = true;
        combatWarn(''')]),
 ('item-matrix-only-rollback','B','hns-item-throw',[
   ('''        stack.pushPose();
        boolean guarded = true;''','''        Matrix4f savedPose = new Matrix4f(stack.last().pose());
        org.joml.Matrix3f savedNormal = new org.joml.Matrix3f(stack.last().normal());
        boolean guarded = false;'''),
   ('''                stack.popPose();
                guarded = false;
                disableCombat(error);''','''                stack.last().pose().set(savedPose);
                stack.last().normal().set(savedNormal);
                guarded = false;
                disableCombat(error);''')]),
 ('item-guard-leaked','B','hns-active',[('if (guarded) stack.popPose();','if (guarded) { /* leaked frame */ }')]),
]

def main():
 q=argparse.ArgumentParser();q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);q.add_argument('--pal',required=True,type=pathlib.Path);a=q.parse_args();java=a.java.resolve();jar=a.jar.resolve();pal=a.pal.resolve()
 assert hashlib.sha256(pal.read_bytes()).hexdigest()==PAL_SHA,'unexpected PAL binary'
 originals={'B':(ROOT/'src/build251/dev/settledlands/model/PlayerMotionBridge.java').read_text(),'H':(ROOT/'src/build251/dev/settledlands/model/HackSlashAccess.java').read_text(),'F':(ROOT/'src/build251/dev/settledlands/model/FreshEmfAccess.java').read_text()}
 with tempfile.TemporaryDirectory(prefix='mutants251-') as d:
  tmp=pathlib.Path(d);fixture,auto,joml=make_fixture(java,jar,pal,tmp);api=tmp/'api';gen_api251.main(api)
  common=[str(java),'-jar',str(ROOT/'tools/vendor/ecj-3.46.100.jar'),'-21','-encoding','UTF-8','-g:none','-nowarn']
  def run(j,mode):
   cp=':'.join(map(str,[fixture,j,pal,auto,joml]));return subprocess.run([str(java),'-Xverify:all','-cp',cp,'testing.Motion251Scenario',mode],capture_output=True,text=True)
  for mode in sorted({mode for _,_,mode,_ in MUTANTS}):
   r=run(jar,mode);assert r.returncode==0 and 'PASS '+mode in r.stdout,('baseline',mode,r.stdout,r.stderr)
  for i,(label,which,mode,edits) in enumerate(MUTANTS):
   text=dict(originals)
   for old,new in edits:
    assert text[which].count(old)==1,(label,old,text[which].count(old));text[which]=text[which].replace(old,new,1)
   work=tmp/str(i);src=work/'src/dev/settledlands/model';src.mkdir(parents=True);out=work/'classes';out.mkdir()
   sources=[]
   for key,name in [('B','PlayerMotionBridge'),('H','HackSlashAccess'),('F','FreshEmfAccess')]:
    p=src/(name+'.java');p.write_text(text[key]);sources.append(p)
   r=subprocess.run(common+['-cp',str(joml),'-d',str(out),*map(str,sorted(api.rglob('*.java'))),*map(str,sources)],capture_output=True,text=True)
   assert r.returncode==0,('compile',label,r.stdout,r.stderr)
   # Compile only the render bridge here; preserve the separately compiled
   # login-sync helper from the candidate instead of looking for it in `out`.
   mutant=work/'mutant.jar';replacements=patch.BRIDGE_CLASSES|{patch.HACK+'.class',patch.HACK+'$Frame.class'}
   with zipfile.ZipFile(jar) as base,zipfile.ZipFile(mutant,'w') as dst:
    for info in base.infolist():dst.writestr(info,(out/info.filename).read_bytes() if info.filename in replacements else base.read(info.filename))
   r=run(mutant,mode);assert r.returncode!=0 and 'AssertionError' in r.stderr,('escaped or unrelated',label,mode,r.stdout,r.stderr)
   print('DETECTED',label,flush=True)
 print('251 behavioral mutants: PASS',len(MUTANTS),'(actual BbModel/PAL, synthetic APIs; not a game test)',flush=True)
if __name__=='__main__':main()
