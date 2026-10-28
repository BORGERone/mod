#!/usr/bin/env python3
"""Exercise the *actual* installed TnM 1.0.4 hook/ModelSpace bytecode on JVM 21.

Most game APIs and individual feature providers are test doubles. This is NOT a
Minecraft, NeoForge, IK, network, or visual test. The GPL TnM jar is an external
input and is never repackaged in the candidate/backup.
"""
import argparse,collections,hashlib,pathlib,struct,subprocess,sys,tempfile,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tools'))
import build_joml248,classedit,decode
from patch_corpse_pose237 import constant_ref
SHA='3754909bf0d12445b18a60de315619734c1229bcc621f88f315ccf076701ab56'
HOOK='strm/touchnmotion/TouchNMotionHook';SPACE='strm/touchnmotion/ModelSpace'
MANUAL={
 'strm/touchnmotion/compat/ParCoolActivity','strm/touchnmotion/compat/WholeBody',
 'strm/touchnmotion/footgrounding/compat/FootGrounding','strm/touchnmotion/interaction/DrawnEntities',
 'strm/touchnmotion/compat/FirstPersonHead','strm/touchnmotion/compat/FirstPersonHead$Neck',
 'strm/touchnmotion/compat/ShadowPass','strm/touchnmotion/platform/Platform',
 HOOK,SPACE,'traben/entity_model_features/models/animation/state/EMFState',
}

def types(desc):
 def one(i):
  c=desc[i]
  if c=='[':
   name,j=one(i+1);return name+'[]',j
  if c=='L':
   j=desc.index(';',i);return desc[i+1:j].replace('/','.').replace('$','.'),j+1
  return {'Z':'boolean','F':'float','I':'int','D':'double','J':'long','V':'void'}[c],i+1
 assert desc[0]=='(';i=1;out=[]
 while desc[i]!=')':
  a,i=one(i);out.append(a)
 r,j=one(i+1);assert j==len(desc);return out,r

def referenced(z):
 refs=collections.defaultdict(set)
 for path,names in [(HOOK,{'applyAll','applyAllParts'}),(SPACE,{'before','unturned','frame'})]:
  k=classedit.Klass(z.read(path+'.class'))
  for m in k.methods:
   if m['name'] not in names:continue
   for _,ins in decode.walk(classedit.code_parts(k,m)['code']):
    if ins[0]!=0xb8:continue
    owner,name,desc=constant_ref(k,struct.unpack('>H',ins[1:3])[0])
    if owner.startswith('strm/touchnmotion/') and owner not in MANUAL:refs[owner].add((name,desc))
 return refs

def generate(root,z,real_foot=False,real_torso=False):
 refs=referenced(z)
 if real_torso:
  refs.pop('strm/touchnmotion/torso/TorsoLean')
  refs['strm/touchnmotion/blockuse/aeronautics/CockpitControls'].add(('active','(Ljava/util/UUID;)Z'))
  refs['strm/touchnmotion/wallhand/WallSqueeze'].add(('crouchOffset','(Ljava/util/UUID;F)Lorg/joml/Vector3f;'))
 for owner,methods in sorted(refs.items()):
  package,cls=owner.rsplit('/',1);assert '$' not in cls,owner
  lines=['package '+package.replace('/','.')+';','public class '+cls+' {']
  for name,desc in sorted(methods):
   args,ret=types(desc);params=', '.join(t+' a'+str(i) for i,t in enumerate(args))
   body=''
   if owner=='strm/touchnmotion/motion/PoseInertia' and name=='apply':
    body='''testing.Probe.events.add("apply"); if(!a0.equals(testing.Probe.recorded))throw new AssertionError("UUID");
    for(String n:new String[]{"body","head","right_arm","left_arm","right_leg","left_leg"}){net.minecraft.client.model.geom.ModelPart a=(net.minecraft.client.model.geom.ModelPart)a1.apply(n);testing.Probe.initial.put(n,new org.joml.Matrix4f().translation(a.x/16,a.y/16,a.z/16).rotate(new org.joml.Quaternionf().rotationZYX(a.zRot,a.yRot,a.xRot)).scale(a.xScale,a.yScale,a.zScale));}
    net.minecraft.client.model.geom.ModelPart b=(net.minecraft.client.model.geom.ModelPart)a1.apply("body"),r=(net.minecraft.client.model.geom.ModelPart)a1.apply("right_arm"),l=(net.minecraft.client.model.geom.ModelPart)a1.apply("left_leg");
    if(testing.Probe.mutate){b.zRot+=.21f;b.x+=1.1f;r.xRot-=.35f;r.y+=.8f;l.xRot+=.17f;}
    if(testing.Probe.override!=null)testing.Probe.override.accept(a1);
    if(testing.Probe.badPose)b.x=Float.NaN;if(testing.Probe.failApply)throw new IllegalStateException("apply failed");'''
    if real_foot:body=body.replace('if(!a0.equals(testing.Probe.recorded))throw new AssertionError("UUID");','')
    if real_torso:body+='testing.SeedTorso.state(a0);'
   if owner=='strm/touchnmotion/interaction/InteractionRuntime' and name=='finishHands':
    body='''for(String n:new String[]{"body","head","right_arm","left_arm","right_leg","left_leg"}){net.minecraft.client.model.geom.ModelPart p=(net.minecraft.client.model.geom.ModelPart)a1.apply(n);testing.Probe.expected.put(n,new org.joml.Matrix4f().translation(p.x/16,p.y/16,p.z/16).rotate(new org.joml.Quaternionf().rotationZYX(p.zRot,p.yRot,p.xRot)).scale(p.xScale,p.yScale,p.zScale));}'''
   if ret=='boolean':body+='return false;'
   elif ret=='float':body+='return 0f;'
   elif ret=='void':pass
   elif ret=='java.util.Map':body+='return java.util.Map.of();'
   elif ret=='org.joml.Vector3f':body+='return new org.joml.Vector3f();'
   else:body+='return null;'
   lines.append('public static '+ret+' '+name+'('+params+') {'+body+'}')
  lines.append('}');out=root/(owner+'.java');out.parent.mkdir(parents=True,exist_ok=True);out.write_text('\n'.join(lines)+'\n')
 return len(refs)

MANUAL_SRC={
'strm/touchnmotion/compat/FirstPersonHead.java':'''package strm.touchnmotion.compat; import java.util.*;import net.minecraft.client.model.geom.ModelPart; public class FirstPersonHead { public static boolean active(UUID id){return false;} public static void apply(Neck n,ModelPart p,Collection<?> all){} public static class Neck { public static Neck of(ModelPart b,ModelPart h){return null;} } }''',
'strm/touchnmotion/compat/ShadowPass.java':'''package strm.touchnmotion.compat; public class ShadowPass { public static boolean drawing(){return false;} public static org.joml.Matrix4f view(){return null;} }''',
'strm/touchnmotion/platform/Platform.java':'''package strm.touchnmotion.platform; public class Platform { public static net.minecraft.world.phys.Vec3 cameraPosition(){return null;} public static float partialTick(boolean b){return 0;} }''',
'net/minecraft/world/phys/Vec3.java':'''package net.minecraft.world.phys; public class Vec3 {}''',
'net/minecraft/world/entity/animal/horse/AbstractHorse.java':'''package net.minecraft.world.entity.animal.horse; public class AbstractHorse extends net.minecraft.world.entity.LivingEntity {}''',
'strm/emfcompat/core/ik/IKFrame.java':'''package strm.emfcompat.core.ik; public class IKFrame { public static IKFrame capture(org.joml.Matrix4f p,net.minecraft.world.phys.Vec3 c){return new IKFrame();} }''',
'org/slf4j/Logger.java':'''package org.slf4j; public interface Logger { default void warn(String msg,Throwable t){} }''',
'org/slf4j/LoggerFactory.java':'''package org.slf4j; public class LoggerFactory { static final Logger L=new Logger(){}; public static Logger getLogger(String name){return L;} }''',
'net/minecraft/client/model/HumanoidModel.java':'''package net.minecraft.client.model; import net.minecraft.client.model.geom.ModelPart; public class HumanoidModel<T> { public ModelPart head,hat,body,rightArm,leftArm,rightLeg,leftLeg; }''',
'net/minecraft/client/player/LocalPlayer.java':'''package net.minecraft.client.player; public class LocalPlayer extends AbstractClientPlayer {}''',
'net/minecraft/client/Minecraft.java':'''package net.minecraft.client; public class Minecraft { public net.minecraft.client.player.LocalPlayer player; public static Minecraft getInstance(){return new Minecraft();} }''',
'strm/touchnmotion/footgrounding/compat/HorseFootGrounding.java':'''package strm.touchnmotion.footgrounding.compat; import net.minecraft.world.entity.animal.horse.AbstractHorse;import com.mojang.blaze3d.vertex.PoseStack;public class HorseFootGrounding { public static boolean handles(Object o){return false;}public static void modelPose(AbstractHorse h,PoseStack s,float p){}public static void apply(java.util.UUID id,java.util.function.Function<String,net.minecraft.client.model.geom.ModelPart> p){} }''',
'strm/emfcompat/core/PoseSnapshot.java':'''package strm.emfcompat.core; public class PoseSnapshot { public static void copy(net.minecraft.client.model.geom.ModelPart a,net.minecraft.client.model.geom.ModelPart b){} }''',
'strm/touchnmotion/cape/CapeFollow.java':'''package strm.touchnmotion.cape; public class CapeFollow {}''',
'org/joml/placeholder.txt':'',
'traben/entity_model_features/models/animation/state/EMFBipedPose.java':'''package traben.entity_model_features.models.animation.state; public class EMFBipedPose {}''',
'traben/entity_model_features/models/parts/EMFModelPartRoot.java':'''package traben.entity_model_features.models.parts; public class EMFModelPartRoot { public boolean isMainModel; public java.util.Map getAllVanillaPartsByNameEMF(){return java.util.Map.of();} }''',
'traben/entity_model_features/EMFAnimationApi.java':'''package traben.entity_model_features; import traben.entity_model_features.models.animation.state.*;import traben.entity_model_features.models.parts.EMFModelPartRoot; public class EMFAnimationApi { public static boolean registerAnimationHook(EMFAnimationHook h){return true;} public static class EMFAnimationHook { public static class AnimationContext { public EMFEntityRenderState activeState(){return null;} public EMFModelPartRoot animatingModelRoot(){return null;} } } }''',
}

def main():
 MANUAL_SRC.pop('net/minecraft/client/model/HumanoidModel.java')
 MANUAL_SRC.pop('net/minecraft/client/Minecraft.java')
 q=argparse.ArgumentParser();q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);q.add_argument('--tnm',required=True,type=pathlib.Path);modes=q.add_mutually_exclusive_group();modes.add_argument('--real-foot',action='store_true');modes.add_argument('--real-torso',action='store_true');a=q.parse_args()
 assert hashlib.sha256(a.tnm.read_bytes()).hexdigest()==SHA,'not the indexed TnM 1.0.4 binary'
 with tempfile.TemporaryDirectory(prefix='real-tnm248-') as tmp:
  root=pathlib.Path(tmp);sources=root/'gen';out=root/'classes';out.mkdir();joml=root/'joml';build_joml248.build(a.java.resolve(),joml)
  with zipfile.ZipFile(a.tnm) as z:
   assert z.testzip() is None;count=generate(sources,z,a.real_foot,a.real_torso)
  for n,s in MANUAL_SRC.items():
   if not n.endswith('.java'):continue
   p=sources/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s+'\n')
  excluded={'strm/touchnmotion/TouchNMotionHook.java'}
  if not a.real_torso:excluded.add('strm/touchnmotion/ModelSpace.java')
  if a.real_foot:excluded.add('strm/touchnmotion/footgrounding/compat/FootGrounding.java')
  tests=[p for p in (ROOT/'tests/java249').rglob('*.java') if p.as_posix().split('/tests/java249/',1)[1] not in excluded]
  if a.real_foot:tests+=list((ROOT/'tests/java249-realfoot').rglob('*.java'))
  if a.real_torso:tests+=list((ROOT/'tests/java249-realtorso').rglob('*.java'))
  cp=str(joml)+':'+str(a.jar.resolve())
  cmd=[str(a.java),'-jar',str(ROOT/'tools/vendor/ecj-3.46.100.jar'),'-21','-encoding','UTF-8','-g:none','-nowarn','-cp',cp,'-d',str(out),*map(str,sorted(tests+list(sources.rglob('*.java'))))]
  subprocess.run(cmd,check=True)
  classpath=':'.join(map(str,[out,a.jar.resolve(),joml,a.tnm.resolve()]))
  if a.real_foot:
   r=subprocess.run([str(a.java),'-Xverify:all','-cp',classpath,'testing.RealFootScenarios'],capture_output=True,text=True)
   assert r.returncode==0 and 'PASS real TnM 1.0.4 foot grounding' in r.stdout,(r.stdout,r.stderr)
   print(r.stdout.strip());print('Real foot mode uses a controlled clock/state and synthetic terrain/world; NOT a game test')
  elif a.real_torso:
   r=subprocess.run([str(a.java),'-Xverify:all','-cp',classpath,'testing.RealTorsoScenario'],capture_output=True,text=True)
   assert r.returncode==0 and 'PASS real TnM 1.0.4 torso' in r.stdout,(r.stdout,r.stderr)
   print(r.stdout.strip());print('Real torso mode uses a manually injected lean and synthetic model space/world; NOT a game test')
  else:
   for scenario in ('normal','idle','stale','crouch','ride','standvehicle','baby','nested','rotated','repeat','absent','unknown','notworld','wrongstate','firstperson','parcool','held','othermodel','badrig','duplicate','nonplayer','beforeError','applyError','nan','shear','renderer'):
    r=subprocess.run([str(a.java),'-Xverify:all','-cp',classpath,'testing.Bridge249Scenarios',scenario],capture_output=True,text=True)
    assert r.returncode==0 and ('PASS '+scenario) in r.stdout,(scenario,r.stdout,r.stderr)
   print('Actual TnM 1.0.4 ModelSpace + private applyAll/applyAllParts executed with',count,'synthetic feature providers: PASS 26; NOT a game test')
if __name__=='__main__':main()
