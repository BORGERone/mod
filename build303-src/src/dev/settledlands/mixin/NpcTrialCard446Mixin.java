package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.TrialCard446;
import dev.settledlands.trial.TrialGraph446;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 446: renders the {@link TrialGraph446#TRIAL_TYPE} ({@code 60}) card on the graph canvas.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcTrialCard446Mixin {

    @Inject(method = "drawNode(Lnet/minecraft/client/gui/GuiGraphics;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$drawTrialCard446(GuiGraphics graphics, NpcDialogueGraph.Node node,
            CallbackInfo ci) {
        if (node == null || graphics == null || !TrialGraph446.isTrialNode(node)) return;
        ci.cancel();
        TrialCard446.draw((NpcDialogueGraphScreen) (Object) this, graphics, node);
    }
}
