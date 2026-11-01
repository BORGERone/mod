package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcRadiusZone320;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 320: draws the zone of a "В радиусе" node whose visibility toggle is on.
 *
 * <p>Hooked at the head of {@code GuideNpc#customServerAiStep} - the same entry point Build 317
 * uses for the water tick, because every server tick of every guide passes through it. The work is
 * skipped on seven ticks out of eight and immediately when no player is close enough to see the
 * ring.</p>
 */
@Mixin(value = GuideNpc.class, remap = false)
public abstract class NpcRadiusZoneMixin320 {

    @Inject(method = "customServerAiStep()V", at = @At("HEAD"), remap = false)
    private void settledlands$radiusZone(CallbackInfo ci) {
        NpcRadiusZone320.tick((GuideNpc) (Object) this);
    }
}
