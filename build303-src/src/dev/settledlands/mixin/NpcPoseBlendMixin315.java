package dev.settledlands.mixin;

import dev.settledlands.model.NpcPoseBlend315;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 315: every pose a clip writes is cross-faded.
 *
 * <p>{@code NpcMotionBridge256.Pose#set(ModelPart)} is the single place a clip pose reaches the
 * model - both {@code applyFullBodyCustomClip} and {@code applyPartialCustomMask} go through it.
 * The hook runs at the end of that write and hands the part to {@link NpcPoseBlend315}, which
 * blends the new pose with the one the part had a frame ago.</p>
 *
 * <p>The pose before the write is captured first, so the fade always starts from what the part
 * really had, even when nothing has written to it for a while - that case used to be applied whole
 * and showed as a one frame jump when a walk turned into idle.</p>
 *
 * <p>Only the values already written are touched, so a part that no clip drives is never changed
 * and nothing else in the model is affected.</p>
 */
@Mixin(targets = "dev.settledlands.model.NpcMotionBridge256$Pose", remap = false)
public abstract class NpcPoseBlendMixin315 {

    @Inject(method = "set(Lnet/minecraft/client/model/geom/ModelPart;)V",
            at = @At("HEAD"), remap = false)
    private void settledlands$capturePose(ModelPart part, CallbackInfo ci) {
        NpcPoseBlend315.capture(part);
    }

    @Inject(method = "set(Lnet/minecraft/client/model/geom/ModelPart;)V",
            at = @At("RETURN"), remap = false)
    private void settledlands$blendPose(ModelPart part, CallbackInfo ci) {
        NpcPoseBlend315.blend(part);
    }
}
