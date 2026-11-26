package dev.settledlands.mixin;

import dev.settledlands.npc.NpcNet;
import dev.settledlands.npc.client.TrialGraphClient446;
import dev.settledlands.trial.TrialGraph446;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 446: a config view whose id belongs to a trial spawner opens the graph editor.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcConfigScreen", remap = false)
public abstract class NpcTrialOpen446Mixin {

    @Inject(method = "open", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$trialOpen446(NpcNet.CfgView view, CallbackInfo ci) {
        if (view == null || !TrialGraph446.isTrialEditorId(view.entityId())) return;
        ci.cancel();
        TrialGraphClient446.openEditor(view);
    }
}
