package testing;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.model.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;
import org.joml.Matrix4f;
import traben.entity_model_features.models.animation.state.EMFState;
import strm.touchnmotion.compat.*;
import strm.touchnmotion.interaction.DrawnEntities;
import java.util.*;

public class BridgeScenarios {
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label+"; events="+Probe.events);}
 static boolean eq(Matrix4f a,Matrix4f b){return a!=null&&b!=null&&a.equals(b,.0003f);}
 static void assertExpected(BbModel m){
  for(String[] pair:new String[][]{{"body","body"},{"head","head"},{"rightarm","right_arm"},{"leftarm","left_arm"},{"rightleg","right_leg"},{"leftleg","left_leg"}})
   check(eq(m.drawn.get(pair[0]),Probe.expected.get(pair[1])),"world transform "+pair[0]+" actual="+m.drawn.get(pair[0])+" expected="+Probe.expected.get(pair[1]));
  Matrix4f local=new Matrix4f().translate(0,10f/16,0);
  check(eq(m.drawn.get("rightarmitemlocator"),new Matrix4f(m.drawn.get("rightarm")).mul(local)),"item locator follows animated arm");
 }
 public static void main(String[] args){
  String mode=args[0]; AbstractClientPlayer player=new AbstractClientPlayer();EMFState.ENTITY=player; BbModel m=new BbModel();PoseStack s=new PoseStack();
  LivingEntity actor=mode.equals("nonplayer")?new LivingEntity():player;
  Probe.mutate=mode.equals("normal")||mode.equals("nested")||mode.equals("renderError")||mode.equals("rotated")||mode.equals("uniform");
  Probe.rotateBefore=mode.equals("rotated");
  if(mode.equals("absent"))ModList.LOADED=false;
  if(mode.equals("unknown"))ModList.VERSION="1.0.5";
  if(mode.equals("notworld"))DrawnEntities.IN_WORLD=false;
  if(mode.equals("wrongstate"))EMFState.ENTITY=new AbstractClientPlayer();
  if(mode.equals("firstperson"))EMFState.FIRST_PERSON=true;
  if(mode.equals("parcool"))ParCoolActivity.ACTIVE=true;
  if(mode.equals("held"))WholeBody.HELD=true;
  if(mode.equals("othermodel"))m.name="someone_else";
  if(mode.equals("badrig"))m.parents.remove(m.getPart("rightarm"));
  if(mode.equals("duplicate"))m.parts.put("leftarm",m.getPart("rightarm"));
  if(mode.equals("uniform")){m.getPart("body").xScale=1.3f;m.getPart("body").yScale=1.3f;m.getPart("body").zScale=1.3f;}
  if(mode.equals("shear")){m.getPart("body").xScale=2;m.getPart("waist").zRot=.5f;}
  if(mode.equals("afterShear"))Probe.override=parts->{parts.apply("body").xScale=2;parts.apply("head").yRot+=.7f;};
  if(mode.equals("beforeError")||mode.equals("beforeErrorTwice"))Probe.failBefore=true;
  if(mode.equals("applyError"))Probe.failApply=true;
  if(mode.equals("nan"))Probe.badPose=true;
  if(mode.equals("renderError"))Probe.failRender=true;
  Map<String,Matrix4f> original=new HashMap<>();for(String n:m.parts.keySet())original.put(n,m.world(n));
  if(mode.equals("nested")){
   AbstractClientPlayer other=new AbstractClientPlayer();BbModel second=new BbModel();
   m.nested=()->{EMFState.ENTITY=other;TouchMotionBridge.render(second,other,1,.5f,s,null,15);check(s.depth()==3,"nested return restores parent pose");EMFState.ENTITY=player;};
  }
  boolean thrown=false;
  try {TouchMotionBridge.render(m,actor,1,.5f,s,null,15);}catch(IllegalStateException e){thrown=true;}
  if(mode.equals("renderError")){check(thrown,"exception propagated");check(s.depth()==2,"baseline original renderer leaves its pose on error");System.out.println("PASS "+mode);return;}
  check(!thrown,"no exception");check(s.depth()==1,"pose stack restored");
  if(mode.equals("normal")||mode.equals("nested")||mode.equals("rotated")||mode.equals("uniform")){
   check(Probe.events.containsAll(List.of("before","native","record","apply","draw")),"complete order");
   if(mode.equals("normal"))check(Probe.events.equals(List.of("before","native","record","apply","draw")),"exact order");
   Matrix4f expectedStack=new Matrix4f().translate(0,1.125f,0);
   if(mode.equals("rotated")){
    org.joml.Quaternionf q=new org.joml.Quaternionf().rotateZ(.4f);expectedStack.rotate(q);
    check(Probe.drawNormal.equals(new org.joml.Matrix3f().rotate(q),.0003f),"transformed normals while drawing");
    check(s.last().normal().equals(new org.joml.Matrix3f(),.0003f),"normal matrix restored");
   }
   check(eq(m.drawnStack,expectedStack),"TnM frame applied");assertExpected(m);
   for(String[] pair:new String[][]{{"body","body"},{"head","head"},{"rightarm","right_arm"},{"leftarm","left_arm"},{"rightleg","right_leg"},{"leftleg","left_leg"}})
    check(eq(original.get(pair[0]),Probe.initial.get(pair[1])),"TnM input is original model-space bone "+pair[0]);
   check(!eq(original.get("rightarm"),m.drawn.get("rightarm")),"arm visibly changed");
   check(Probe.recorded.equals(player.getUUID()),"right player's UUID");
  } else {
   for(String n:m.parts.keySet())check(eq(original.get(n),m.drawn.get(n)),"original pose retained: "+n);
   if(mode.equals("idle"))check(eq(m.drawnStack,new Matrix4f().translate(0,1.125f,0)),"modelSpace retained even without part changes");
   else check(eq(m.drawnStack,new Matrix4f().translate(0,1,0)),"fallback stack retained");
   if(mode.equals("nan")||mode.equals("applyError")||mode.equals("afterShear"))check(Probe.events.contains("apply"),"apply was attempted");
   if(mode.equals("shear"))check(Probe.events.contains("before")&&!Probe.events.contains("record"),"unsafe shear rejected before apply");
   if(mode.equals("absent")||mode.equals("unknown")||mode.equals("notworld")||mode.equals("wrongstate")||mode.equals("firstperson")||mode.equals("parcool")||mode.equals("held")||mode.equals("othermodel")||mode.equals("badrig")||mode.equals("nonplayer")||mode.equals("duplicate")||mode.equals("missingclasses"))
    check(!Probe.events.contains("before"),"guard prevents TnM calls");
   if(mode.equals("beforeErrorTwice")){
    Probe.events.clear();Probe.failBefore=false;BbModel next=new BbModel();TouchMotionBridge.render(next,player,1,.5f,s,null,15);
    check(Probe.events.equals(List.of("native","draw")),"failed optional bridge stays disabled");check(s.depth()==1,"second stack restored");
   }
  }
  System.out.println("PASS "+mode);
 }
}
