package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcWater317;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 317: runs {@link NpcWater317} once per server tick.
 *
 * <p>{@code GuideNpc#customServerAiStep} is the one method every server tick goes through: the
 * escort branch, the scripted-move branch and the ordinary AI branch all start there, and the
 * method returns early in several of them, so the water tick belongs at its head and nowhere
 * later.</p>
 */
@Mixin(value = GuideNpc.class, remap = false)
public abstract class NpcWaterMixin317 {

    @Inject(method = "customServerAiStep()V", at = @At("HEAD"), remap = false)
    private void settledlands$waterTick(CallbackInfo ci) {
        NpcWater317.tick((GuideNpc) (Object) this);
    }
}
