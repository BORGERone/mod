package testing;

import java.util.*;
import java.lang.reflect.*;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import com.mojang.blaze3d.vertex.*;
import com.zigythebird.playeranim.accessors.IAnimatedPlayer;
import com.zigythebird.playeranim.animation.PlayerAnimManager;
import com.zigythebird.playeranimcore.animation.AnimationProcessor;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.neoforged.fml.ModList;
import traben.entity_model_features.models.animation.state.EMFState;
import dev.settledlands.model.BbModel;
import dev.settledlands.model.PlayerMotionBridge;

/**
 * Build 253 motion & held-item scenario runner:
 * exercises all 21 crawl/FA/TnM/PAL scenarios from 251 plus explicit verification
 * that blocking shields (player+PAL, player idle, and non-player LivingEntity NPC)
 * NO LONGER apply the legacy Build 179/180 +40deg X tilt / (0, 0.15, -0.05) offset,
 * while reproducing that exact +40deg tilt on Build 252.
 */
public class Motion253Scenario {
 static final float EPS=.0006f;
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m+" events="+Probe.events+" bones="+Probe251.bones);}
 static void matrix(Matrix4f got,Matrix4f want,String label){ok(got!=null&&got.equals(want,EPS),label+"\nactual="+got+"\nexpected="+want);}
 static final class Sample implements IAnimation {
  boolean active;
  public boolean isActive(){return active;}
  public PlayerAnimBone get3DTransform(PlayerAnimBone b){
   String name=b.getName(); Probe251.bones.add(name);Probe.events.add("pal-"+name);
   if(Probe251.throwOnArm&&name.equals("right_arm"))throw new IllegalStateException("controlled PAL error");
   switch(name){
    case "body" -> {b.setPosX(1.2f);b.setPosY(.8f);b.setPosZ(-.4f);b.setRotX(.11f);b.setRotY(-.13f);b.setRotZ(.17f);b.setScaleX(Probe251.invalidBody?0:Probe251.uniformBody?1:1.1f);b.setScaleY(Probe251.uniformBody?1:.9f);b.setScaleZ(Probe251.uniformBody?1:1.2f);}
    case "torso" -> {b.setRotX(b.getRotX()+.22f);b.setRotY(b.getRotY()-.15f);}
    case "head" -> b.setRotZ(b.getRotZ()+.08f);
    case "right_arm" -> {b.setRotX(b.getRotX()-.7f);b.setRotY(b.getRotY()+.35f);b.setRotZ(b.getRotZ()-.18f);}
    case "left_arm" -> {b.setRotX(b.getRotX()+.3f);b.setRotZ(b.getRotZ()+.1f);}
    case "right_leg" -> b.setRotX(b.getRotX()+.2f);
    case "left_leg" -> b.setRotX(b.getRotX()-.2f);
    case "right_item" -> {if(Probe251.armScaleFailure){PoseStack.CURRENT.forceTrustedForTest();Probe251.throwOnNextNonuniformScale=true;}b.setPosX(Probe251.invalidItem?Float.NaN:2);b.setPosY(3);b.setPosZ(4);b.setRotX(.2f);b.setRotY(-.4f);b.setRotZ(.6f);b.setScaleX(.8f);b.setScaleY(.9f);b.setScaleZ(1.1f);}
    case "left_item" -> {b.setPosX(-3);b.setPosY(4);b.setPosZ(-2);b.setRotX(-.3f);b.setRotY(.5f);b.setRotZ(-.7f);b.setScaleX(1.2f);b.setScaleY(.7f);b.setScaleZ(.95f);}
   }
   return b;
  }
 }
 static final class Animated extends AbstractClientPlayer implements IAnimatedPlayer {
  final Sample sample=new Sample();final PlayerAnimManager manager=new PlayerAnimManager(this);
  final AnimationProcessor processor=new AnimationProcessor(){public void handleAnimations(float tickDelta,boolean first){ok(!first,"third person");Probe251.processorCalls++;Probe251.processorDelta=tickDelta;Probe.events.add("processor");}};
  Animated(){manager.addAnimLayer(10,sample);manager.setTickDelta(.375f);}
  public PlayerAnimManager playerAnimLib$getAnimManager(){return manager;}
  public AnimationProcessor playerAnimLib$getAnimProcessor(){return processor;}
  public IAnimation playerAnimLib$getAnimation(ResourceLocation id){return id.toString().equals("hackersandslashers:attack_layer")?sample:null;}
 }
 static void draw(BbModel model,LivingEntity entity){
  EMFState.ENTITY=entity instanceof AbstractClientPlayer p?p:null;
  PoseStack stack=new PoseStack();MultiBufferSource b=type->new VertexConsumer(){};
  model.renderActor(entity,1,.5f,stack,b,15);ok(stack.depth()==1,"actor pose-stack leak");ok(Probe251.rootAtDraw!=null,"geometry not drawn");
 }
 static void noScopes()throws Exception {
  Field f=PlayerMotionBridge.class.getDeclaredField("SCOPES");f.setAccessible(true);
  ok(((ThreadLocal<?>)f.get(null)).get()==null,"thread-local scope leaked");
 }
 static void exceptionalDraw(BbModel model,Animated p)throws Exception{
  EMFState.ENTITY=p;PoseStack stack=new PoseStack();Probe251.throwOnDraw=true;
  try {model.renderActor(p,1,.5f,stack,t->new VertexConsumer(){},15);throw new AssertionError("draw should throw");}
  catch(IllegalStateException expected){ok(expected.getMessage().contains("controlled draw failure"),"wrong draw exception");}
  Probe251.throwOnDraw=false;
  ok(stack.depth()==2,"bridge left extra poses after draw exception");
  matrix(stack.matrix(),new Matrix4f().rotateY((float)Math.PI),"draw exception unwinds bridge root frames");
  ok(stack.last().transformNormal(1,2,3,new Vector3f()).equals(new Matrix3f().rotateY((float)Math.PI).transform(new Vector3f(1,2,3)),EPS),"draw exception normal flag");
  stack.popPose();matrix(stack.matrix(),new Matrix4f(),"original caller frame");noScopes();
  draw(model,p);matrix(Probe251.rootAtDraw,combatRoot(true),"next render after exception");
 }
 static Matrix4f root(float swim,float pitch,boolean fluid,boolean visual,boolean tnm){
  Matrix4f m=new Matrix4f().rotateY((float)Math.PI);
  if(swim>0){float target=fluid?-90f-pitch:-90f;m.rotate(new Quaternionf().rotationX((float)Math.toRadians(swim*target)));if(visual)m.translate(0,-1,.3f);}
  m.scale(-1,-1,1).translate(0,-1.501f,0);if(tnm)m.translate(0,.125f,0);return m;
 }
 static Matrix4f combatRoot() {return combatRoot(false);}
 static Matrix4f combatRoot(boolean swimming) {
  Matrix4f m=new Matrix4f().rotateY((float)Math.PI);
  if(swimming)m.rotate(new Quaternionf().rotationX((float)-Math.PI/2)).translate(0,-1,.3f);
  return m.scale(1.1f,.9f,1.2f)
   .translate(-1.2f/16f,.8f/16f+.75f,-.4f/16f).rotateZYX(.17f,.13f,-.11f).translate(0,-.75f,0)
   .scale(-1,-1,1).translate(0,-1.501f,0).translate(0,.125f,0);
 }
 static Matrix4f partWorld(BbModel model,String name)throws Exception{
  Field pf=BbModel.class.getDeclaredField("partParents");pf.setAccessible(true);@SuppressWarnings("unchecked") IdentityHashMap<ModelPart,ModelPart> parents=(IdentityHashMap<ModelPart,ModelPart>)pf.get(model);
  Deque<ModelPart> path=new ArrayDeque<>();ModelPart q=model.getPart(name);while(q!=null){path.addFirst(q);q=parents.get(q);}
  Matrix4f m=new Matrix4f();for(ModelPart part:path)m.translate(part.x/16f,part.y/16f,part.z/16f).rotate(new Quaternionf().rotationZYX(part.zRot,part.yRot,part.xRot)).scale(part.xScale,part.yScale,part.zScale);return m;
 }
 static Matrix4f itemExpected(BbModel model,boolean left,boolean legacyTilt,boolean position,boolean pose)throws Exception{
  Field pf=BbModel.class.getDeclaredField("partParents");pf.setAccessible(true);@SuppressWarnings("unchecked") IdentityHashMap<ModelPart,ModelPart> parents=(IdentityHashMap<ModelPart,ModelPart>)pf.get(model);
  ModelPart part=model.getPart(left?"leftarm":"rightarm");Deque<ModelPart> path=new ArrayDeque<>();while(part!=null){path.addFirst(part);part=parents.get(part);}
  Matrix4f m=new Matrix4f(Probe251.rootAtDraw);for(ModelPart q:path)m.translate(q.x/16f,q.y/16f,q.z/16f).rotate(new Quaternionf().rotationZYX(q.zRot,q.yRot,q.xRot)).scale(q.xScale,q.yScale,q.zScale);
  if(position){if(left)m.translate(-3/16f,-4/16f,-2/16f);else m.translate(2/16f,-3/16f,4/16f);}
  m.rotateX((float)Math.toRadians(-90)).rotateY((float)Math.PI);
  if(legacyTilt)m.rotateX((float)Math.toRadians(40)).translate(0,.15f,-.05f);
  if(pose){if(left)m.rotateZ(-.5f).rotateY(.7f).rotateX(.3f).scale(1.2f,.7f,.95f);
  else m.rotateZ(.4f).rotateY(-.6f).rotateX(-.2f).scale(.8f,.9f,1.1f);}
  return m;
 }
 static Matrix4f actualPalItem(BbModel model,AbstractClientPlayer player,boolean left,boolean legacyTilt)throws Exception{
  PoseStack reference=new PoseStack();reference.last().pose().set(new Matrix4f(Probe251.rootAtDraw).mul(partWorld(model,left?"leftarm":"rightarm")));
  Class<?> mixin=Class.forName("com.zigythebird.playeranim.mixin.ItemInHandLayerMixin");Object instance=mixin.getDeclaredConstructor().newInstance();
  Class<?> arm=net.minecraft.world.entity.HumanoidArm.class;
  Class<?>[] args={net.minecraft.world.entity.LivingEntity.class,ItemStack.class,net.minecraft.world.item.ItemDisplayContext.class,arm,PoseStack.class,MultiBufferSource.class,int.class,org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class};
  Method loc=mixin.getDeclaredMethod("changeItemLocation",args),rot=mixin.getDeclaredMethod("changeItemRotationAndScale",args);loc.setAccessible(true);rot.setAccessible(true);
  Object hand=left?net.minecraft.world.entity.HumanoidArm.LEFT:net.minecraft.world.entity.HumanoidArm.RIGHT;
  Object[] vals={player,left?player.getOffhandItem():player.getMainHandItem(),left?net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND:net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,hand,reference,null,15,null};
  loc.invoke(instance,vals);reference.mulPose(new Quaternionf().rotationX((float)-Math.PI/2));reference.mulPose(new Quaternionf().rotationY((float)Math.PI));
  if(legacyTilt){reference.mulPose(new Quaternionf().rotationX((float)Math.toRadians(40)));reference.translate(0,.15f,-.05f);}
  rot.invoke(instance,vals);return new Matrix4f(reference.matrix());
 }
 public static void main(String[] args)throws Exception{
  String mode=args[0];BbModel model=RealModel249Scenario.make();Animated p=new Animated();
  if(mode.equals("npc-shield-blocking")){
   LivingEntity npc=new LivingEntity();npc.blocking=true;npc.main=new ItemStack();npc.main.use=UseAnim.BLOCK;npc.off=new ItemStack();npc.off.use=UseAnim.BLOCK;
   draw(model,npc);
   matrix(Probe251.items.get("right"),itemExpected(model,false,false,false,false),"npc right shield without +40 tilt");
   matrix(Probe251.items.get("left"),itemExpected(model,true,false,false,false),"npc left shield without +40 tilt");
   ok(!Probe251.items.get("right").equals(itemExpected(model,false,true,false,false),EPS),"npc right shield still has +40 tilt");
   ok(!Probe251.items.get("left").equals(itemExpected(model,true,true,false,false),EPS),"npc left shield still has +40 tilt");
   noScopes();System.out.println("PASS npc-shield-blocking; actual BbModel without legacy shield tilt");return;
  }
  if(mode.equals("player-idle-shield-blocking")){
   p.blocking=true;p.main=new ItemStack();p.main.use=UseAnim.BLOCK;p.off=new ItemStack();p.off.use=UseAnim.BLOCK;
   draw(model,p);
   matrix(Probe251.items.get("right"),itemExpected(model,false,false,false,false),"idle player right shield without +40 tilt");
   matrix(Probe251.items.get("left"),itemExpected(model,true,false,false,false),"idle player left shield without +40 tilt");
   ok(!Probe251.items.get("right").equals(itemExpected(model,false,true,false,false),EPS),"idle player right shield still has +40 tilt");
   ok(!Probe251.items.get("left").equals(itemExpected(model,true,true,false,false),EPS),"idle player left shield still has +40 tilt");
   noScopes();System.out.println("PASS player-idle-shield-blocking; actual BbModel/bridge without legacy shield tilt");return;
  }
  if(mode.equals("legacy-blocking-252")){
   ModList.HNS=true;ModList.PAL=true;p.sample.active=true;p.blocking=true;
   p.main=new ItemStack();p.main.use=UseAnim.BLOCK;p.off=new ItemStack();p.off.use=UseAnim.BLOCK;
   draw(model,p);
   matrix(Probe251.items.get("right"),itemExpected(model,false,true,true,true),"252 legacy right shield +40 tilt");
   matrix(Probe251.items.get("left"),itemExpected(model,true,true,true,true),"252 legacy left shield +40 tilt");
   noScopes();System.out.println("CONFIRMED build 252 legacy shield tilt +40deg");return;
  }
  if(mode.startsWith("hns")||mode.equals("fail-parts")||mode.startsWith("nested")||mode.equals("render-error")){ModList.HNS=true;ModList.PAL=true;}
  switch(mode){
   case "dry-full" -> {p.swim=1;p.visualSwimming=true;}
   case "dry-half" -> {p.swim=.5f;p.visualSwimming=true;}
   case "swim-water" -> {p.swim=.75f;p.pitch=23;p.xRotO=23;p.inWater=true;p.visualSwimming=true;}
   case "swim-fluid" -> {p.swim=.75f;p.pitch=-17;p.xRotO=-17;p.inFluid=true;p.visualSwimming=true;}
   case "swim-transition" -> {p.swim=.25f;p.visualSwimming=false;}
   case "swim-pitch-transition" -> {p.swim=.75f;p.pitch=30;p.xRotO=-10;p.inWater=true;p.visualSwimming=true;}
   case "elytra" -> {p.swim=1;p.visualSwimming=true;p.fallFlying=true;}
   case "fallback" -> {p.swim=1;p.visualSwimming=true;Probe250.missingMarker=true;}
   case "bad-world" -> {p.swim=1;p.visualSwimming=true;strm.touchnmotion.interaction.DrawnEntities.IN_WORLD=false;}
   case "hns-unknown" -> {p.sample.active=true;ModList.PAL_VERSION="1.2.0";}
   case "hns-addon" -> {p.sample.active=true;ModList.ADDON=true;}
   case "hns-active", "hns-blocking", "hns-bad-item", "hns-item-throw" -> {p.sample.active=true;p.main=new ItemStack();p.off=new ItemStack();if(mode.equals("hns-blocking")){p.blocking=true;p.main.use=UseAnim.BLOCK;p.off.use=UseAnim.BLOCK;}if(mode.equals("hns-bad-item"))Probe251.invalidItem=true;if(mode.equals("hns-item-throw")){Probe251.uniformBody=true;Probe251.armScaleFailure=true;}}
   case "hns-bad-body" -> {p.swim=1;p.visualSwimming=true;p.sample.active=true;Probe251.invalidBody=true;}
   case "before-error" -> {p.swim=1;p.visualSwimming=true;Probe.failBefore=true;}
   case "hns-inactive" -> {p.sample.active=false;p.main=new ItemStack();p.manager.addAnimLayer(20,new IAnimation(){public boolean isActive(){return true;}public PlayerAnimBone get3DTransform(PlayerAnimBone b){return b;}});}
   case "fail-parts" -> {p.swim=1;p.visualSwimming=true;p.sample.active=true;Probe251.throwOnArm=true;}
   case "nested-draw", "nested-setup" -> {p.sample.active=true;p.main=new ItemStack();p.off=new ItemStack();}
   case "render-error" -> {p.swim=1;p.visualSwimming=true;p.sample.active=true;}
   default -> throw new IllegalArgumentException(mode);
  }
  if(mode.equals("render-error")){exceptionalDraw(model,p);noScopes();System.out.println("PASS render-error; actual BbModel/bridge, synthetic renderer");return;}
  if(mode.equals("nested-setup")){
   Animated inner=new Animated();BbModel innerModel=RealModel249Scenario.make();inner.swim=.5f;inner.visualSwimming=true;
   Probe.override=unused->{Probe.override=null;var outer=EMFState.ENTITY;draw(innerModel,inner);EMFState.ENTITY=outer;};
  }
  if(mode.equals("nested-draw")){
   Animated inner=new Animated();BbModel innerModel=RealModel249Scenario.make();inner.swim=.5f;inner.visualSwimming=true;
   Probe251.onDraw=stack->{var outer=EMFState.ENTITY;int depth=stack.depth();Matrix4f saved=new Matrix4f(stack.matrix());
    EMFState.ENTITY=inner;innerModel.renderActor(inner,1,.5f,stack,t->new VertexConsumer(){},15);EMFState.ENTITY=outer;
    ok(stack.depth()==depth,"nested frame stack leak");matrix(stack.matrix(),saved,"outer stack after nested draw");};
  }
  Map<String,Matrix4f> baseline=new HashMap<>();
  if(mode.equals("hns-active")||mode.equals("hns-blocking")){
   p.sample.active=false;draw(model,p);for(String n:List.of("body","head","rightarm","leftarm","rightleg","leftleg"))baseline.put(n,partWorld(model,n));
   p.sample.active=true;Probe.events.clear();Probe251.bones.clear();Probe251.items.clear();Probe251.rootAtDraw=null;
  }
  draw(model,p);
  switch(mode){
   case "dry-full" -> matrix(Probe251.rootAtDraw,root(1,0,false,true,true),"full dry crawl");
   case "dry-half" -> matrix(Probe251.rootAtDraw,root(.5f,0,false,true,true),"partial dry crawl");
   case "swim-water" -> matrix(Probe251.rootAtDraw,root(.75f,23,true,true,true),"water pitch");
   case "swim-fluid" -> matrix(Probe251.rootAtDraw,root(.75f,-17,true,true,true),"NeoForge fluid pitch");
   case "swim-transition" -> matrix(Probe251.rootAtDraw,root(.25f,0,false,false,true),"swim exit interpolation");
   case "swim-pitch-transition" -> matrix(Probe251.rootAtDraw,root(.75f,10,true,true,true),"interpolated swim view pitch");
   case "elytra" -> matrix(Probe251.rootAtDraw,root(0,0,false,false,true),"fall-flying guard");
   case "fallback", "bad-world", "before-error" -> matrix(Probe251.rootAtDraw,root(0,0,false,false,false),"fallback restored old actor root");
   case "hns-unknown", "hns-addon", "hns-inactive" -> {matrix(Probe251.rootAtDraw,root(0,0,false,false,true),"inactive/unsupported HnS has no effect");ok(Probe251.processorCalls==0&&Probe251.bones.isEmpty(),"unexpected PAL work");}
   case "hns-active", "hns-blocking" -> {
    matrix(Probe251.rootAtDraw,combatRoot(),"PAL body root and pivot");
    Vector3f normal=new Matrix3f().rotateY((float)Math.PI).scale(1/1.1f,1/.9f,1/1.2f).rotateZYX(.17f,.13f,-.11f).scale(-1,-1,1).transform(new Vector3f(1,2,3)).normalize();
    ok(Probe251.normalAtDraw.equals(normal,EPS),"PAL normal transform");
    for(String name:baseline.keySet())ok(!partWorld(model,name).equals(baseline.get(name),EPS),"PAL did not change part "+name);
    ok(Probe251.processorCalls==1&&Probe251.processorDelta==.375f,"processor once with PAL delta");
    ok(Probe251.bones.containsAll(List.of("body","torso","head","right_arm","left_arm","right_leg","left_leg","right_item","left_item")),"missing PAL channels");
    ok(Probe.events.indexOf("processor")<Probe.events.indexOf("before")&&Probe.events.indexOf("fresh")<Probe.events.indexOf("pal-right_arm")&&Probe.events.indexOf("pal-right_arm")<Probe.events.indexOf("actual-draw"),"wrong stage ordering");
    matrix(Probe251.items.get("right"),itemExpected(model,false,false,true,true),"right item PAL transforms without shield tilt");
    matrix(Probe251.items.get("left"),itemExpected(model,true,false,true,true),"left item PAL transforms without shield tilt");
    matrix(Probe251.items.get("right"),actualPalItem(model,p,false,false),"right vs actual PAL item mixin");
    matrix(Probe251.items.get("left"),actualPalItem(model,p,true,false),"left vs actual PAL item mixin");
    if(mode.equals("hns-blocking")){
     ok(!Probe251.items.get("right").equals(itemExpected(model,false,true,true,true),EPS),"right blocking shield still has legacy +40deg tilt");
     ok(!Probe251.items.get("left").equals(itemExpected(model,true,true,true,true),EPS),"left blocking shield still has legacy +40deg tilt");
    }
    ok(Math.abs(model.getPart("rightarm").xRot)>.01,"attack arm did not move");
   }
   case "nested-draw", "nested-setup" -> {
    matrix(Probe251.rootAtDraw,combatRoot(),"nested outer root");
    ok(Probe251.processorCalls==1&&Probe251.bones.contains("right_item")&&Probe251.bones.contains("left_item"),"nested scope lost outer attack");
    matrix(Probe251.items.get("right"),itemExpected(model,false,false,true,true),"nested right item");
    matrix(Probe251.items.get("left"),itemExpected(model,true,false,true,true),"nested left item");
   }
   case "hns-bad-body" -> {
    matrix(Probe251.rootAtDraw,root(1,0,false,true,true),"invalid PAL body must retain crawl/FA");
    ok(Probe251.processorCalls==1 && !Probe251.bones.contains("right_arm"),"invalid PAL body not disabled");
   }
   case "hns-item-throw" -> {
    ok(Probe251.items.size()==2&&Probe251.bones.contains("right_item")&&!Probe251.bones.contains("left_item"),"failed item must disable attack bridge");
    matrix(Probe251.items.get("right"),itemExpected(model,false,false,true,false),"failed pose restores item transform");
    matrix(Probe251.items.get("left"),itemExpected(model,true,false,false,false),"other hand after item failure");
    ok(Math.abs(Probe251.itemNormals.get("right").length()-(float)Math.sqrt(14))<EPS,"private normal flag not restored after item failure");
    ok(Probe251.itemNormals.get("left").isFinite(),"other hand normal invalid");
   }
   case "hns-bad-item" -> {
    ok(Probe251.items.size()==2&&Probe251.items.values().stream().allMatch(v->v.isFinite()),"invalid item polluted pose");
    ok(Probe251.processorCalls==1&&Probe251.bones.contains("right_item")&&!Probe251.bones.contains("left_item"),"invalid item did not disable PAL");
   }
   case "fail-parts" -> {
    matrix(Probe251.rootAtDraw,root(0,0,false,false,false),"failed PAL frame rolled back");
    Vector3f normal=new Matrix3f().rotateY((float)Math.PI).scale(-1,-1,1).transform(new Vector3f(1,2,3));
    ok(Probe251.normalAtDraw.equals(normal,EPS),"normal flag not restored on rollback");
    ok(Math.abs(model.getPart("head").xRot)>1,"original clip fallback on PAL failure");
    Probe251.throwOnArm=false;Probe251.rootAtDraw=null;Probe.events.clear();draw(model,p);
    matrix(Probe251.rootAtDraw,root(1,0,false,true,true),"next frame retains FA/TnM and crawl after PAL error");ok(Probe251.processorCalls==1,"failed PAL should stay disabled");
   }
  }
  noScopes();System.out.println("PASS "+mode+"; actual BbModel/bridge/PAL, controlled animation & synthetic Minecraft/EMF/TnM");
 }
}
