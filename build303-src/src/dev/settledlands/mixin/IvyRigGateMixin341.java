package dev.settledlands.mixin;

import dev.settledlands.model.BbModel;
import dev.settledlands.model.IvyRig341;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 341: lets the profession rig reach the Fresh Animations / Touch'n Motion retarget.
 *
 * <p>{@code PlayerMotionBridge.isIvy} decides whether a possessed player's body is retargeted from the
 * player model's pose, and it recognised only {@code ivy} and {@code ivy_oleander}. The profession
 * variant - the same rig with the profession bones, and the one every NPC with a profession uses - was
 * refused, so such a body kept its own clips and Touch'n Motion's motion had nothing to land on. The
 * gate is widened to every Ivy rig; the bridge's own rig validation still refuses anything without the
 * body, waist and limb bones.</p>
 */
@Mixin(targets = "dev.settledlands.model.PlayerMotionBridge", remap = false)
public abstract class IvyRigGateMixin341 {

    @Inject(method = "isIvy", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void settledlands$professionRig(BbModel model, CallbackInfoReturnable<Boolean> cir) {
        boolean accepted = Boolean.TRUE.equals(cir.getReturnValue());
        if (!accepted && IvyRig341.isIvyRig(model)) {
            cir.setReturnValue(Boolean.TRUE);
            accepted = true;
        }
        IvyRig341.note(model, accepted);
    }
}
