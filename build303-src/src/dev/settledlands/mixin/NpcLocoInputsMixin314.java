package dev.settledlands.mixin;

import dev.settledlands.model.NpcLocoDriver283;
import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 314: the smoothed locomotion reaches every reader, not just the bridge.
 *
 * <p>Build 283 replaced the two reads the bridge itself does - {@code position(partialTick)} and
 * {@code speed(partialTick)} inside {@code renderFreshNpc} - with values that follow the distance
 * the NPC actually covered. Everything else that asks the same state the same question still got
 * the raw tick-stepped numbers, and with Touch'n Motion installed that is the animator: its blend
 * between idle, walk and run is the weight it reads from exactly these methods. Stepped input
 * gives it stepped blends, which is the jerky walk with hard transitions.</p>
 *
 * <p>So the state itself answers with the smoothed values. Only states the driver has actually
 * sampled are affected - {@link NpcLocoDriver283#position} returns {@code NaN} for everything
 * else, and then the vanilla value is returned untouched, so no other entity and no other mod
 * sees a single different number.</p>
 *
 * <p>Nothing is written and nothing is synced: three reads, one map lookup each.</p>
 */
@Mixin(targets = "net.minecraft.world.entity.WalkAnimationState", remap = false)
public abstract class NpcLocoInputsMixin314 {

    @Inject(method = "position(F)F", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$position(float partialTick, CallbackInfoReturnable<Float> cir) {
        float phase = NpcLocoDriver283.position((WalkAnimationState) (Object) this);
        if (!Float.isNaN(phase)) cir.setReturnValue(phase);
    }

    @Inject(method = "speed(F)F", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$speed(float partialTick, CallbackInfoReturnable<Float> cir) {
        float amount = NpcLocoDriver283.speed((WalkAnimationState) (Object) this);
        if (!Float.isNaN(amount)) cir.setReturnValue(amount);
    }

    @Inject(method = "speed()F", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$speedNow(CallbackInfoReturnable<Float> cir) {
        float amount = NpcLocoDriver283.speed((WalkAnimationState) (Object) this);
        if (!Float.isNaN(amount)) cir.setReturnValue(amount);
    }
}
