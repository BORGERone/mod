package testing;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.model.BbModel;
import dev.settledlands.model.TouchMotionBridge;
import net.minecraft.client.player.AbstractClientPlayer;
import org.joml.Matrix4f;
import strm.touchnmotion.footgrounding.compat.TerrainBalance;
import traben.entity_model_features.models.animation.state.EMFState;

/** End-to-end bridge with actual TnM hook/ModelSpace/FootGrounding/FootStep;
 * world APIs, state clock, terrain and other TnM features are controlled doubles. */
public class RealFootScenarios {
 static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg+" events="+Probe.events);}
 static void near(float a,float b,String msg){check(Math.abs(a-b)<.002f,msg+" "+a+" "+b);}
 static void matrix(Matrix4f a,Matrix4f b,String msg){float[] x=a.get(new float[16]),y=b.get(new float[16]);for(int i=0;i<16;i++)near(x[i],y[i],msg+" ["+i+"]");}
 static Matrix4f fromPose(float[] p){return new Matrix4f().translation(p[3]/16,p[4]/16,p[5]/16).rotate(new org.joml.Quaternionf().rotationZYX(p[2],p[1],p[0])).scale(1,p[6],1);}
 public static void main(String[] args){
  AbstractClientPlayer actor=new AbstractClientPlayer();EMFState.ENTITY=actor;BbModel model=new BbModel();model.name="ivy_oleander";
  PoseStack stack=new PoseStack();Matrix4f baseline=new Matrix4f(stack.matrix());
  TouchMotionBridge.render(model,actor,1,.5f,stack,null,15);
  check(Probe.expected.size()==6,"actual hook completed");check(TerrainBalance.calls==1,"real grounding applied");
  check(TerrainBalance.right!=null&&TerrainBalance.left!=null&&TerrainBalance.right.length==7,"real recorded poses");
  Matrix4f right=Probe.expected.get("right_leg"),left=Probe.expected.get("left_leg");
  check(!right.equals(fromPose(TerrainBalance.right),.001f),"actual right foot offset changed pose");
  check(!left.equals(fromPose(TerrainBalance.left),.001f),"actual left foot offset changed pose");
  for(String[] pair:new String[][]{{"body","body"},{"head","head"},{"rightarm","right_arm"},{"leftarm","left_arm"},{"rightleg","right_leg"},{"leftleg","left_leg"}})matrix(model.drawn.get(pair[0]),Probe.expected.get(pair[1]),"real foot world "+pair[0]);
  check(stack.depth()==1,"stack depth");matrix(stack.matrix(),baseline,"stack rollback");
  check(Math.abs(model.drawnStack.m31()-1.125f)<.001f,"real modelPose lower 2/16");
  System.out.println("PASS real TnM 1.0.4 foot grounding: recorded animated pose, nonzero feet, model pose and six bone matrices");
 }
}
