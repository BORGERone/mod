package testing;
import java.io.*;import java.util.*;import java.util.zip.*;
import com.zigythebird.playeranimcore.animation.Animation;import com.zigythebird.playeranimcore.loading.PlayerAnimatorLoader;import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractModifier;import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.player.AbstractClientPlayer;import net.minecraft.world.entity.Entity;import traben.entity_model_features.models.animation.state.EMFState;import dev.settledlands.model.BbModel;import com.mojang.blaze3d.vertex.*;import net.neoforged.fml.ModList;
public class ActualHnsMods251 {
 static void ok(boolean b,String s){if(!b)throw new AssertionError(s+" events="+Probe.events);}
 static String sourceJar;
 static AbstractModifier modifier(String name,AbstractClientPlayer p)throws Exception {Class<?> c=Class.forName("net.dndats.api.animations.HnSPlayerAttackController$"+name);String actual=String.valueOf(c.getProtectionDomain().getCodeSource().getLocation());ok(java.nio.file.Path.of(java.net.URI.create(actual)).toAbsolutePath().equals(java.nio.file.Path.of(sourceJar).toAbsolutePath()),"modifier not from user HnS JAR");var ctor=c.getDeclaredConstructor(AbstractClientPlayer.class);ctor.setAccessible(true);return (AbstractModifier)ctor.newInstance(p);}
 public static void main(String[] args)throws Exception{
  sourceJar=args[0];String omit=System.getProperty("omitModifier","");ModList.HNS=true;ModList.PAL=true;Animation animation;
  try(ZipFile z=new ZipFile(args[0])){var e=z.stream().filter(x->x.getName().contains("player_animations/hnsattack_")&&x.getName().endsWith(".json")).findFirst().orElseThrow();try(Reader r=new InputStreamReader(z.getInputStream(e))){animation=PlayerAnimatorLoader.GSON.fromJson(r,Animation.class);}}
  RealHns251Scenario.Animated p=new RealHns251Scenario.Animated();p.passenger=true;p.vehicle=new Entity();p.tickCount=123;p.controller.addModifierLast(modifier("BodyAlignmentModifier",p));if(!omit.equals("riding"))p.controller.addModifierLast(modifier("RidingLegBlocker",p));if(!omit.equals("reach"))p.controller.addModifierLast(modifier("WeaponReachModifier",p));if(!omit.equals("camera"))p.controller.addModifierLast(modifier("CameraHeightSyncModifier",p));
  p.controller.triggerAnimation(animation,Math.max(0,animation.length()*.35f-.5f));BbModel model=RealModel249Scenario.make();EMFState.ENTITY=p;PoseStack s=new PoseStack();model.renderActor(p,1,.5f,s,t->new VertexConsumer(){},15);
  ok(s.depth()==1,"stack leak");PlayerAnimBone leg=p.controller.get3DTransform(new PlayerAnimBone("right_leg"));PlayerAnimBone right=p.controller.get3DTransform(new PlayerAnimBone("right_item"));PlayerAnimBone left=p.controller.get3DTransform(new PlayerAnimBone("left_item"));
  System.out.println("leg="+leg.getRotX()+","+leg.getRotY()+","+leg.getRotZ()+" item scale="+right.getScaleY()+","+left.getScaleY());
  ok(Math.abs(leg.getRotX()+1.4137167f)<.0001f,"riding modifier not active");ok(Math.abs(right.getScaleY()-left.getScaleY())>.2f,"weapon modifier not active");
  ok(Probe251.items.get("right").equals(Motion251Scenario.actualPalItem(model,p,false,false),.0006f),"actual modified right item differs from PAL mixin");ok(Probe251.items.get("left").equals(Motion251Scenario.actualPalItem(model,p,true,false),.0006f),"actual modified left item differs from PAL mixin");
  var state=net.dndats.hackersandslashers.impl.combat.mechanics.combo.ComboManager.getState(p);ok(state.lastCameraOffsetUpdateTick==123,"camera state not updated");
  RealHns251Scenario.Animated q=new RealHns251Scenario.Animated();q.yBodyRotO=10;q.yBodyRot=20;q.yHeadRotO=q.yHeadRot=60;if(!omit.equals("alignment"))q.controller.addModifierLast(modifier("BodyAlignmentModifier",q));q.controller.triggerAnimation(animation,Math.max(0,animation.length()*.35f-.5f));
  BbModel qModel=RealModel249Scenario.make();EMFState.ENTITY=q;PoseStack qStack=new PoseStack();qModel.renderActor(q,1,.5f,qStack,t->new VertexConsumer(){},15);ok(qStack.depth()==1,"alignment stack");
  RealHns251Scenario.Animated baseline=new RealHns251Scenario.Animated();baseline.yBodyRotO=10;baseline.yBodyRot=20;baseline.yHeadRotO=baseline.yHeadRot=60;baseline.controller.triggerAnimation(animation,Math.max(0,animation.length()*.35f-.5f));
  BbModel bModel=RealModel249Scenario.make();EMFState.ENTITY=baseline;PoseStack bStack=new PoseStack();bModel.renderActor(baseline,1,.5f,bStack,t->new VertexConsumer(){},15);ok(bStack.depth()==1,"baseline stack");
  PlayerAnimBone aligned=q.controller.get3DTransform(new PlayerAnimBone("right_leg")),unmodified=baseline.controller.get3DTransform(new PlayerAnimBone("right_leg"));ok(Math.abs((unmodified.getRotY()-aligned.getRotY())-(float)Math.toRadians(45))<.0001f,"body alignment yaw");
  ok(!Motion251Scenario.partWorld(qModel,"rightleg").equals(Motion251Scenario.partWorld(bModel,"rightleg"),.0006f),"modified leg did not reach actor");
  System.out.println("PASS actual HnS modifiers BodyAlignment, RidingLegBlocker, WeaponReach and CameraHeightSync with genuine PAL/actor; synthetic game/combat state; NOT a game test");
 }
}
