package strm.touchnmotion.footgrounding.compat;
import java.util.*; import java.util.function.Function; import net.minecraft.client.model.geom.ModelPart; import net.minecraft.client.player.AbstractClientPlayer; import com.mojang.blaze3d.vertex.PoseStack; import testing.Probe;
public class FootGrounding {
 public static void recordAnimated(UUID id,Function<String,ModelPart> p){Probe.events.add("record"); Probe.recorded=id; if(p.apply("right_leg")==null || p.apply("left_leg")==null)throw new AssertionError("legs");}
 public static void apply(UUID id,Function<String,ModelPart> p){}
 public static void modelPose(AbstractClientPlayer e,PoseStack stack){Probe.events.add("before");stack.translate(0,.125f,0);if(Probe.rotateBefore)stack.mulPose(new org.joml.Quaternionf().rotateZ(.4f));if(Probe.failBefore)throw new IllegalStateException("before failed");}
}
