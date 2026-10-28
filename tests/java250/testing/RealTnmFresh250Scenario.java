package testing;
import com.mojang.blaze3d.vertex.PoseStack;import dev.settledlands.model.*;import net.minecraft.client.player.AbstractClientPlayer;import traben.entity_model_features.models.animation.state.EMFState;
/** Actual external TnM 1.0.4 hook with controlled providers and synthetic EMF/MC. NOT a game test. */
public final class RealTnmFresh250Scenario {
 public static void main(String[] a){AbstractClientPlayer p=new AbstractClientPlayer();EMFState.ENTITY=p;BbModel m=new BbModel();PoseStack stack=new PoseStack();Probe.mutate=true;
  var state=EMFState.state();state.setLimbAngle(3);state.setLimbDistance(4);state.setHeadYaw(5);state.setHeadPitch(6);long count=traben.entity_model_features.EMFManager.getInstance().entityRenderCount;
  PlayerMotionBridge.render(m,p,1,.5f,stack,null,15);
  if(Probe.nativeCount!=0||Probe250.packRuns!=1||!Probe.events.containsAll(java.util.List.of("fresh","record","apply","draw"))||stack.depth()!=1)throw new AssertionError("actual hook absent or repeated: "+Probe.events);
  if(Probe.events.indexOf("fresh")>Probe.events.indexOf("record")||Probe.events.indexOf("record")>Probe.events.indexOf("apply"))throw new AssertionError("pack/hook order "+Probe.events);
  if(java.util.Collections.frequency(Probe.events,"apply")!=1)throw new AssertionError("double TnM "+Probe.events);
  if(Math.abs(Probe.initial.get("right_leg").getUnnormalizedRotation(new org.joml.Quaternionf()).x)<.1f)throw new AssertionError("TnM did not observe the pack's gait");
  if(state.limbAngle()!=3||state.limbDistance()!=4||state.headYaw()!=5||state.headPitch()!=6||traben.entity_model_features.EMFManager.getInstance().entityRenderCount!=count)throw new AssertionError("caller EMF state/counter leaked");
  boolean actualEmf=Boolean.getBoolean("actualEmf");
  String rootSource=String.valueOf(traben.entity_model_features.models.parts.EMFModelPartRoot.class.getProtectionDomain().getCodeSource().getLocation());
  if(actualEmf && !java.nio.file.Path.of(java.net.URI.create(rootSource)).toAbsolutePath().equals(java.nio.file.Path.of(System.getProperty("actualEmfPath")).toAbsolutePath()))throw new AssertionError("EMF root not from supplied external JAR: "+rootSource);
  System.out.println("PASS actual TnM 1.0.4 hook receives synthetic pack pose exactly once; " + (actualEmf ? "actual EMF 3.3.12 root" : "synthetic EMF") + "/controlled providers; NOT a game test");
 }
}
