package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.TrialCard446;
import dev.settledlands.trial.TrialGraph446;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 447: expands canvas hit/visibility/framing dimensions for the modular
 * {@link TrialGraph446#TRIAL_TYPE} hub card ({@code 256x176}).
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcStatsGraphUi", remap = false)
public abstract class TrialCanvasSize447Mixin {

    @Inject(method = "width(Ldev/settledlands/npc/NpcDialogueGraph$Node;)I",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$trialHubWidth447(NpcDialogueGraph.Node node,
            CallbackInfoReturnable<Integer> cir) {
        if (TrialGraph446.isTrialNode(node)) {
            cir.setReturnValue(Integer.valueOf(TrialCard446.CARD_W));
        }
    }

    @Inject(method = "height(Ldev/settledlands/npc/NpcDialogueGraph$Node;)I",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$trialHubHeight447(NpcDialogueGraph.Node node,
            CallbackInfoReturnable<Integer> cir) {
        if (TrialGraph446.isTrialNode(node)) {
            cir.setReturnValue(Integer.valueOf(TrialCard446.CARD_H));
        }
    }
}
