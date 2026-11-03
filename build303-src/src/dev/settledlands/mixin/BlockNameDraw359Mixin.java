package dev.settledlands.mixin;

import dev.settledlands.npc.BlockName359;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.client.BlockNameCard359;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 359: the block's «Имя» card on the canvas.
 *
 * <p>{@code drawNode} is the screen's single drawing point for a node, and it knows the node types of
 * the stock editor only - a type it has no branch for gets the title of «Случайность» and no text at
 * all, which is the one thing a name card must not say. So a name node of a block graph is taken out
 * of that method and painted by {@link BlockNameCard359} instead, in the same box and at the same
 * place the canvas hit-tests a node with, so selecting and dragging it stay the stock ones.</p>
 *
 * <p>It is a block graph only: a body's graph never holds this node, and if one ever arrived with it
 * (an older save, a hand-edited file) it would draw as an ordinary unknown node rather than as a
 * block's name.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class BlockNameDraw359Mixin {

    @Inject(method = "drawNode(Lnet/minecraft/client/gui/GuiGraphics;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$drawBlockNameCard(GuiGraphics graphics, Node node, CallbackInfo ci) {
        if (node == null || node.type != BlockName359.TYPE) return;
        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        if (!BlockTrigger351.isBlockEditorId(screen.entityId())) return;
        ci.cancel();
        BlockNameCard359.draw(screen, graphics, node);
    }
}
