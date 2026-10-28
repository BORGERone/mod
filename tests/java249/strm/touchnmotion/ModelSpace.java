package strm.touchnmotion;
import net.minecraft.world.entity.LivingEntity; import com.mojang.blaze3d.vertex.PoseStack; import java.util.function.Consumer; import testing.Probe;
public class ModelSpace { public static void before(LivingEntity e,PoseStack stack,float partial,Consumer<?> callback){Probe.events.add("before"); stack.translate(0,.125f,0);if(Probe.rotateBefore)stack.mulPose(new org.joml.Quaternionf().rotateZ(.4f)); if(Probe.failBefore)throw new IllegalStateException("before failed");} }
