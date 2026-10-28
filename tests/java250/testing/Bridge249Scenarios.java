package testing;
import com.mojang.blaze3d.vertex.PoseStack;import dev.settledlands.model.*;import net.minecraft.client.Minecraft;import net.minecraft.client.model.HumanoidModel;import net.minecraft.client.player.AbstractClientPlayer;import net.minecraft.world.entity.*;import net.neoforged.fml.ModList;import org.joml.Matrix4f;import traben.entity_model_features.models.animation.state.EMFState;import strm.touchnmotion.compat.*;import strm.touchnmotion.interaction.DrawnEntities;import java.util.*;
public class Bridge249Scenarios {
 static final String[] V={"body","head","right_arm","left_arm","right_leg","left_leg"},R={"body","head","rightarm","leftarm","rightleg","leftleg"};
 static void check(boolean v,String s){if(!v)throw new AssertionError(s+" events="+Probe.events);}
 static boolean eq(Matrix4f a,Matrix4f b){return a!=null&&b!=null&&a.equals(b,.00038f);}
 static Matrix4f restV(int i){float x=0,y=0;if(i==2){x=-5;y=2;}if(i==3){x=5;y=2;}if(i==4){x=-1.9f;y=12;}if(i==5){x=1.9f;y=12;}return new Matrix4f().translate(x/16,y/16,0);}
 static Map<String,Matrix4f> rest(BbModel m){Map<String,Matrix4f> a=new HashMap<>();for(String n:m.parts.keySet())a.put(n,m.world(n));return a;}
 static void assertRetarget(BbModel m,Map<String,Matrix4f> rest){
  for(int i=0;i<V.length;i++){Matrix4f wanted=new Matrix4f(Probe.expected.get(V[i])).mul(restV(i).invert()).mul(rest.get(R[i]));check(eq(m.drawn.get(R[i]),wanted),"retarget "+R[i]+" got="+m.drawn.get(R[i])+" want="+wanted);}
  check(eq(m.drawn.get("bone"),new Matrix4f(m.drawn.get("head")).translate(0,-5.5f/16,0)),"head details remain attached");
  check(eq(m.drawn.get("rightarmitemlocator"),new Matrix4f(m.drawn.get("rightarm")).translate(-1f/16,10f/16,0)),"item locator follows hand");
 }
 public static void main(String[] args){String mode=args[0];AbstractClientPlayer p=new AbstractClientPlayer();EMFState.ENTITY=p;BbModel m=new BbModel();PoseStack s=new PoseStack();
  LivingEntity actor=mode.equals("nonplayer")?new LivingEntity():p;if(mode.equals("restrot")){m.getPart("waist").zRot=.4f;m.getPart("waist").saveRest();}Map<String,Matrix4f> before=rest(m);
  boolean good=Set.of("normal","idle","stale","crouch","ride","standvehicle","baby","nested","rotated","repeat","restrot").contains(mode);
  Probe.mutate=Set.of("normal","stale","crouch","ride","standvehicle","baby","nested","rotated","repeat","restrot").contains(mode);Probe.rotateBefore=mode.equals("rotated");
  if(mode.equals("idle")){m.swing=0;m.amount=0;m.headYaw=0;m.pitch=0;}
  if(mode.equals("stale")){m.getPart("bone").x=99;m.getPart("bone").zRot=1;m.getPart("waist").zRot=.7f;m.getPart("root").xScale=2;}
  if(mode.equals("absent"))ModList.LOADED=false;if(mode.equals("unknown"))ModList.VERSION="1.0.5";if(mode.equals("notworld"))DrawnEntities.IN_WORLD=false;
  if(mode.equals("wrongstate"))EMFState.ENTITY=new AbstractClientPlayer();if(mode.equals("firstperson"))EMFState.FIRST_PERSON=true;
  if(mode.equals("parcool"))ParCoolActivity.ACTIVE=true;if(mode.equals("held"))WholeBody.HELD=true;if(mode.equals("othermodel"))m.name="another_model";
  if(mode.equals("badrig"))m.parents.remove(m.getPart("rightarm"));if(mode.equals("duplicate"))m.parts.put("leftarm",m.getPart("rightarm"));
  if(mode.equals("beforeError"))Probe.failBefore=true;if(mode.equals("applyError"))Probe.failApply=true;if(mode.equals("nan"))Probe.badPose=true;
  if(mode.equals("shear"))Probe.override=parts->{parts.apply("body").xScale=2;parts.apply("head").yRot+=.7f;};
  if(mode.equals("renderer"))Minecraft.getInstance().dispatcher.absent=true;
  if(mode.equals("crouch")){var model=Minecraft.getInstance().dispatcher.renderer.getModel();model.crouching=true;model.rightArmPose=HumanoidModel.ArmPose.ITEM;model.leftArmPose=HumanoidModel.ArmPose.EMPTY;}
  if(mode.equals("ride")||mode.equals("standvehicle")){p.passenger=true;p.vehicle=new Entity();p.vehicle.riderSit=mode.equals("ride");}
  if(mode.equals("baby"))p.baby=true;
  if(mode.equals("nested")){AbstractClientPlayer other=new AbstractClientPlayer();BbModel second=new BbModel();m.nested=()->{EMFState.ENTITY=other;PlayerMotionBridge.render(second,other,1,.5f,s,null,15);check(s.depth()==3,"nested stack");EMFState.ENTITY=p;};}
  try{PlayerMotionBridge.render(m,actor,1,.5f,s,null,15);}catch(IllegalStateException e){throw new AssertionError("pose stack or original render failed",e);}check(s.depth()==1,"stack restored");
  if(good){check(Probe.nativeCount==0,"embedded clip skipped");check(Probe.events.containsAll(List.of("before","variant","prepare","vanilla","fresh","record","apply","draw")),"correct hooks");
   check(eq(m.drawnStack,new Matrix4f().translate(0,1.125f,0).rotateZ(mode.equals("rotated")?.4f:0)),"modelSpace applied");assertRetarget(m,before);
   if(mode.equals("idle"))for(String n:before.keySet())check(eq(m.drawn.get(n),before.get(n)),"exact Ivy rest "+n);
   if(mode.equals("normal")){check(Probe.events.equals(List.of("before","variant","prepare","vanilla","fresh","record","apply","draw")),"exact one-shot animation order");check(!eq(before.get("rightleg"),m.drawn.get("rightleg")),"walk moves leg");check(Math.abs(Probe.initial.get("right_arm").m30()+5f/16)<1e-6,"TnM sees vanilla right arm pivot");}
   if(mode.equals("crouch")){check(Probe.values[8]==1&&Probe.values[9]==1&&Probe.values[10]==0,"renderer pose forwarded");check(Math.abs(Probe.values[5]-.335f)<1e-6,"attack interpolation forwarded");}
   if(mode.equals("ride"))check(Probe.values[0]==0&&Probe.values[1]==0&&Probe.values[6]==1,"sitting suppresses walk");
   if(mode.equals("standvehicle"))check(Probe.values[0]==2&&Probe.values[1]==.75f&&Probe.values[6]==0,"standing passenger walks");
   if(mode.equals("baby"))check(Probe.values[0]==6&&Probe.values[7]==1,"baby phase");
   if(mode.equals("repeat")){Probe.events.clear();Probe.expected.clear();m.swing=0;m.amount=0;m.headYaw=0;m.pitch=0;Probe.mutate=false;PlayerMotionBridge.render(m,p,1,.5f,s,null,15);for(String n:before.keySet())check(eq(m.drawn.get(n),before.get(n)),"pool/rest isolation "+n);check(Probe.nativeCount==0&&s.depth()==1,"repeat no native");}
  }else{
   boolean neutral=Set.of("shear","nan").contains(mode);
   check(Probe.nativeCount==(neutral?0:1),"neutral pose or original fallback");
   check(!Probe.events.contains("vanilla")||neutral||mode.equals("applyError"),"only supported player animates vanilla");
   check(eq(m.drawnStack,new Matrix4f().translate(0,1,0)),"fallback stack");
   if(neutral){for(String n:before.keySet())check(eq(m.drawn.get(n),before.get(n)),"neutral rest "+n);}
   else check(Math.abs(m.getPart("head").xRot-.91f)<1e-6,"original animation fallback");
  }
  System.out.println("PASS "+mode);
 }
}
