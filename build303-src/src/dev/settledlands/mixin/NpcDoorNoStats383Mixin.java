package dev.settledlands.mixin;

import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.BlockName359;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 383: a door's graph has no «Характеристики» card.
 *
 * <p>The same lesson as build 359, applied to the second kind of editor this mod opens. The stats card
 * is not a palette row: {@code NpcStatsGraphUi.ensure} puts it on every graph the editor opens, from
 * the screen's constructor, so a door's graph came up with the body's stats card floating next to the
 * door node - one of the things the author's screenshot of build 382 showed, and the reason the canvas
 * looked like a copy of a body's graph with our card dropped into it.</p>
 *
 * <p>Here the call is skipped for a door, and any stats card already in the graph - put there by
 * build 382, or carried in from a copied file - is dropped, together with the «Имя» card of the block
 * editor if one ever arrived. A door's canvas ends up with one node: the door. Both removals go through
 * the screen's own {@code changed()}, the way the stock call does, so the editor knows the graph it
 * opened is not the graph it will save.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcStatsGraphUi", remap = false)
public abstract class NpcDoorNoStats383Mixin {

    @Inject(method = "ensure", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$doorWithoutStats(NpcDialogueGraphScreen screen, CallbackInfo ci) {
        if (screen == null) return;
        if (!DoorGraph382.isDoorEditorId(screen.entityId())) return;
        ci.cancel();
        NpcDialogueGraph graph = screen.settledlands$graph();
        if (graph == null) return;
        boolean changed = BlockName359.dropType(graph, BlockName359.STATS_TYPE) > 0;
        if (BlockName359.dropType(graph, BlockName359.TYPE) > 0) changed = true;
        if (changed) screen.settledlands$changed();
    }
}
