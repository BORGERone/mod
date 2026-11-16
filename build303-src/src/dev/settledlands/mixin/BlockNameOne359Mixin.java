package dev.settledlands.mixin;

import dev.settledlands.npc.BlockName359;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcStatsGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 359: a block has one name, so it has one name card.
 *
 * <p>A body's «Характеристики» card is already a one-off - the editor refuses a second one in
 * {@code NpcStatsGraph#add}, and the same method is what the duplicate key and the palette go
 * through. The name card is given the same rule here: a second one is refused, so a copy of it
 * (Ctrl+D, a pasted graph, a hand-edited file) can never leave a block with two names that disagree
 * about what the dialogue should call it.</p>
 */
@Mixin(NpcStatsGraph.class)
public abstract class BlockNameOne359Mixin {

    @Inject(method = "add", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$singleNameCard(NpcDialogueGraph graph, int type, int x, int y,
            CallbackInfoReturnable<Node> cir) {
        if (type != BlockName359.TYPE) return;
        if (BlockName359.find(graph) != null) cir.setReturnValue(null);
    }
}
