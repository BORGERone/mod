package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.TrialCard446;
import dev.settledlands.trial.TrialGraph446;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 447: anchors right-edge output socket screen X at {@link TrialCard446#CARD_W}
 * for {@link TrialGraph446#TRIAL_TYPE} hub cards.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcGraphCanvas", remap = false)
public abstract class TrialGraphCanvas447Mixin {

    @Inject(method = "portScreenX(Ldev/settledlands/npc/NpcDialogueGraph$Node;Z)D",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$trialHubOutPortX447(NpcDialogueGraph.Node node, boolean out,
            CallbackInfoReturnable<Double> cir) {
        if (out && TrialGraph446.isTrialNode(node)) {
            NpcGraphCanvas canvas = (NpcGraphCanvas) (Object) this;
            cir.setReturnValue(Double.valueOf(canvas.screenX(node.x + TrialCard446.CARD_W)));
        }
    }
}
