package testing;
import com.mojang.blaze3d.vertex.PoseStack;import dev.settledlands.model.*;import net.minecraft.client.player.AbstractClientPlayer;import org.joml.Matrix4f;import traben.entity_model_features.models.animation.state.EMFState;
public class RealTorsoScenario {
 static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg+" events="+Probe.events);}
 public static void main(String[] a){
  AbstractClientPlayer p=new AbstractClientPlayer();EMFState.ENTITY=p;BbModel model=new BbModel();
  java.util.Map<String,Matrix4f> rest=Bridge249Scenarios.rest(model);PoseStack stack=new PoseStack();
  PlayerMotionBridge.render(model,p,1,.5f,stack,null,15);
  check(Probe.expected.size()==6,"actual TnM controller completed");
  check(!Probe.expected.get("body").equals(Probe.initial.get("body"),.001f),"real torso algorithm changed body");
  check(!Probe.expected.get("right_arm").equals(Probe.initial.get("right_arm"),.001f),"real torso algorithm carried arm");
  Bridge249Scenarios.assertRetarget(model,rest);
  check(stack.depth()==1,"restored stack");System.out.println("PASS real TnM 1.0.4 torso: body, arms, item locator and all six bone matrices");
 }
}
