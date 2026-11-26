package dev.settledlands.mixin;

import dev.settledlands.npc.NpcProfile;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcStatsGraphUi;
import dev.settledlands.trial.TrialGraph446;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 446: suppresses the Autonomous AI panel on trial spawner graphs while forwarding clicks.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcAutoAiGraphUi255", remap = false)
public abstract class NpcTrialNoAi446Mixin {

    @Inject(method = "ensure", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$trialWithoutAi446(NpcDialogueGraphScreen screen,
            NpcProfile profile, CallbackInfo ci) {
        if (screen != null && TrialGraph446.isTrialEditorId(screen.entityId())) {
            ci.cancel();
        }
    }

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$trialWithoutAiDraw446(NpcDialogueGraphScreen screen,
            GuiGraphics graphics, CallbackInfo ci) {
        if (screen != null && TrialGraph446.isTrialEditorId(screen.entityId())) {
            ci.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$trialWithoutAiClick446(NpcDialogueGraphScreen screen,
            double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (screen == null || !TrialGraph446.isTrialEditorId(screen.entityId())) return;
        cir.setReturnValue(NpcStatsGraphUi.mouseClicked(screen, mouseX, mouseY, button));
    }
}
