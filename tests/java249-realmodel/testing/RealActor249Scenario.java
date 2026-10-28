package testing;
import dev.settledlands.model.BbModel;import com.mojang.blaze3d.vertex.*;import net.minecraft.client.player.AbstractClientPlayer;import net.minecraft.client.renderer.MultiBufferSource;import net.neoforged.fml.ModList;import traben.entity_model_features.models.animation.state.EMFState;
/** Real patched BbModel.renderActor + real animation/bridge bytecode; synthetic dependencies. */
public class RealActor249Scenario {
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m+" events="+Probe.events);}
 public static void main(String[] args){String mode=args[0];BbModel model=RealModel249Scenario.make();AbstractClientPlayer player=new AbstractClientPlayer();EMFState.ENTITY=player;PoseStack pose=new PoseStack();MultiBufferSource buffers=type->new VertexConsumer(){};
  if(mode.equals("absent"))ModList.LOADED=false;
  model.renderActor(player,1,.5f,pose,buffers,15);
  ok(pose.depth()==1,"actor stack restored");ok(Probe.events.contains("actual-draw"),"actual ModelPart.render called");
  if(mode.equals("active")){ok(Probe.events.containsAll(java.util.List.of("before","vanilla","record","apply")),"real actor used bridge");ok(Math.abs(model.getPart("head").xRot)<.0001,"active actor skipped native idle");ok(Math.abs(model.getPart("rightleg").xRot)>.01,"actor forwarded walking");}
  else {ok(!Probe.events.contains("before")&&!Probe.events.contains("vanilla"),"absent mod cannot apply bridge");ok(Math.abs(model.getPart("head").xRot)>1,"fallback actor applied native idle");}
  System.out.println("PASS actual actor "+mode+"; synthetic game/renderer/TnM");
 }
}
