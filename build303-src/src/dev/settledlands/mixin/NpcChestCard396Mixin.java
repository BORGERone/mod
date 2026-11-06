package dev.settledlands.mixin;

import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.ChestCard396;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 396: the card of a chest node is drawn by this mod, and only by this mod.
 *
 * <p>The pink-box lesson of build 359/382, applied to the chest: the stock painter has no branch for a node
 * type it does not know, so it would fall through to the topic rows of a «Реплика» - a pink box, a made-up
 * title and the relation and points lines - and draw them under our text. The stock painter is cancelled
 * for this one type and {@link ChestCard396} paints the card in full, in the box the canvas hit-tests a node
 * with, so selecting, dragging, multi-select and the wires stay the stock ones.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcChestCard396Mixin {

    @Inject(method = "drawNode(Lnet/minecraft/client/gui/GuiGraphics;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$chestCard(GuiGraphics graphics, NpcDialogueGraph.Node node,
            CallbackInfo ci) {
        if (node == null || graphics == null) return;
        if (!ChestGraph396.isChestGraphNode(node)) return;
        ci.cancel();
        ChestCard396.draw((NpcDialogueGraphScreen) (Object) this, graphics, node);
    }
}
