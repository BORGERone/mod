package dev.settledlands.mixin;

import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.npc.BlockName359;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 396: a chest's graph has no «Характеристики» card.
 *
 * <p>The same rule the blocks got in 359 and the doors in 383. The stats card is not a palette row:
 * {@code NpcStatsGraphUi.ensure} puts it on every graph the editor opens, from the screen's constructor, so
 * a chest's graph would come up with a body's stats card floating next to the chest - and the author's
 * build 383 words about the door apply to a chest word for word: the canvas must hold the chest and what he
 * wires to it, nothing else.</p>
 *
 * <p>Any stats card or «Имя» card already in the graph (a copied file, a graph written by another editor) is
 * dropped too, through the screen's own {@code changed()} so the editor knows the graph it opened is not the
 * graph it will save.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcStatsGraphUi", remap = false)
public abstract class NpcChestNoStats396Mixin {

    @Inject(method = "ensure", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$chestWithoutStats(NpcDialogueGraphScreen screen, CallbackInfo ci) {
        if (screen == null) return;
        if (!ChestGraph396.isChestEditorId(screen.entityId())) return;
        ci.cancel();
        NpcDialogueGraph graph = screen.settledlands$graph();
        if (graph == null) return;
        boolean changed = BlockName359.dropType(graph, BlockName359.STATS_TYPE) > 0;
        if (BlockName359.dropType(graph, BlockName359.TYPE) > 0) changed = true;
        if (changed) screen.settledlands$changed();
    }
}
