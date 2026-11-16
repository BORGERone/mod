package dev.settledlands.mixin;

import dev.settledlands.npc.BlockName359;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 359: a block has no «Характеристики» card, and gets its «Имя» card in the same breath.
 *
 * <p>The stats card is not a palette row and it is not added by the editor's own code either: it is
 * put on every graph by {@code NpcStatsGraphUi.ensure}, called from the screen's constructor. That is
 * why removing it for a block means taking over that call rather than editing a table - this is the
 * only place the card is created for a graph the player just opened.</p>
 *
 * <p>Here the call is not only skipped: a stats card that is already in the block's graph - it was
 * put there by an older build, or by a copy of a body's graph - is dropped, so a block's canvas ends up
 * with a name card and nothing else. Both changes go through the screen's own {@code changed()}, the
 * way the stock call does, so the editor knows the graph it opened is not the graph it will save.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcStatsGraphUi", remap = false)
public abstract class BlockNoStats359Mixin {

    @Inject(method = "ensure", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$blockNameInsteadOfStats(NpcDialogueGraphScreen screen, CallbackInfo ci) {
        if (screen == null) return;
        if (!BlockTrigger351.isBlockEditorId(screen.entityId())) return;
        ci.cancel();
        NpcDialogueGraph graph = screen.settledlands$graph();
        if (graph == null) return;
        boolean changed = BlockName359.dropType(graph, BlockName359.STATS_TYPE) > 0;
        if (BlockName359.ensure(graph)) changed = true;
        if (changed) screen.settledlands$changed();
    }
}
