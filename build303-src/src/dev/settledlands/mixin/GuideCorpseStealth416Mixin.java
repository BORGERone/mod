package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcHnsStealth416;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 416: {@code GuideCorpseNpc} overrides {@code customServerAiStep()} as a no-op, so its own
 * {@code tick()} clears any Hackers 'n Slashers sight/suspicion entries on player corpses and coffins.
 */
@Mixin(targets = "dev.settledlands.npc.GuideCorpseNpc", remap = false)
public abstract class GuideCorpseStealth416Mixin {

    @Inject(method = "tick()V", at = @At("HEAD"), require = 0, remap = false)
    private void settledlands$clearCorpseSightOnTick(CallbackInfo info) {
        NpcHnsStealth416.clearBlindEntitySight((GuideNpc) (Object) this);
    }
}
