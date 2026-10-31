package dev.settledlands.mixin;

import dev.settledlands.model.NpcLocoDriver283;
import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Build 283: the Fresh Animations / Touch'n Motion NPC path gets a locomotion
 * input that follows the real speed instead of the mob's tick-stepped one.
 *
 * <p>Both reads in {@code renderFreshNpc} (the limb phase and the blend weight)
 * are redirected to {@link NpcLocoDriver283}, whose values feed the pose, the
 * vanilla setup and Touch'n Motion's own animator through the same two
 * arguments they always fed. When a state was never sampled the vanilla value is
 * used, so the redirect can never invent motion.</p>
 */
@Mixin(targets = "dev.settledlands.model.NpcMotionBridge256", remap = false)
public abstract class NpcLocoSmoothMixin283 {

    @Redirect(method = "renderFreshNpc",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/WalkAnimationState;position(F)F"),
            remap = false)
    private static float settledlands$framePhase(WalkAnimationState state, float partialTick) {
        float phase = NpcLocoDriver283.position(state);
        return Float.isNaN(phase) ? state.position(partialTick) : phase;
    }

    @Redirect(method = "renderFreshNpc",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/WalkAnimationState;speed(F)F"),
            remap = false)
    private static float settledlands$frameAmount(WalkAnimationState state, float partialTick) {
        float amount = NpcLocoDriver283.speed(state);
        return Float.isNaN(amount) ? state.speed(partialTick) : amount;
    }
}
