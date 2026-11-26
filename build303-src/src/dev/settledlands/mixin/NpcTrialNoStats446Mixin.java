package dev.settledlands.mixin;

import dev.settledlands.npc.BlockName359;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.trial.TrialGraph446;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 446: keeps NPC stats/name cards off a trial spawner's graph canvas.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcStatsGraphUi", remap = false)
public abstract class NpcTrialNoStats446Mixin {

    @Inject(method = "ensure", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$trialWithoutStats446(NpcDialogueGraphScreen screen,
            CallbackInfo ci) {
        if (screen == null || !TrialGraph446.isTrialEditorId(screen.entityId())) return;
        ci.cancel();
        NpcDialogueGraph graph = screen.settledlands$graph();
        if (graph == null) return;
        boolean changed = BlockName359.dropType(graph, BlockName359.STATS_TYPE) > 0;
        if (BlockName359.dropType(graph, BlockName359.TYPE) > 0) changed = true;
        if (changed) screen.settledlands$changed();
    }
}
