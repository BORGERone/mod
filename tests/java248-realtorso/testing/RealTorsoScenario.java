package testing;
import com.mojang.blaze3d.vertex.PoseStack;import dev.settledlands.model.*;import net.minecraft.client.player.AbstractClientPlayer;import org.joml.Matrix4f;import traben.entity_model_features.models.animation.state.EMFState;
public class RealTorsoScenario {
 static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg+" events="+Probe.events);}
 public static void main(String[] a){
  AbstractClientPlayer p=new AbstractClientPlayer();EMFState.ENTITY=p;BbModel model=new BbModel();
  Matrix4f body=new Matrix4f(model.world("body")),arm=new Matrix4f(model.world("rightarm"));PoseStack stack=new PoseStack();
  TouchMotionBridge.render(model,p,1,.5f,stack,null,15);
  check(Probe.expected.size()==6,"actual TnM controller completed");
  check(!Probe.expected.get("body").equals(body,.001f),"real torso algorithm changed body");
  check(!Probe.expected.get("right_arm").equals(arm,.001f),"real torso algorithm carried arm");
  for(String[] pair:new String[][]{{"body","body"},{"head","head"},{"rightarm","right_arm"},{"leftarm","left_arm"},{"rightleg","right_leg"},{"leftleg","left_leg"}})
   check(model.drawn.get(pair[0]).equals(Probe.expected.get(pair[1]),.001f),"bone "+pair[0]);
  check(model.drawn.get("rightarmitemlocator").equals(new Matrix4f(model.drawn.get("rightarm")).translate(0,10f/16,0),.001f),"held item");
  check(stack.depth()==1,"restored stack");System.out.println("PASS real TnM 1.0.4 torso: body, arms, item locator and all six bone matrices");
 }
}
