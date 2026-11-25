package dev.settledlands.mixin;

import dev.settledlands.chest.LootPool436;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.LootPoolCard436;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 436: renders the «Пул предметов» card on the chest graph canvas.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class LootPoolGraphUiMixin436 {

    @Inject(method = "drawNode(Lnet/minecraft/client/gui/GuiGraphics;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$drawLootPoolCard436(GuiGraphics graphics, NpcDialogueGraph.Node node,
            CallbackInfo ci) {
        if (!LootPool436.isLootPoolNode(node)) return;
        LootPoolCard436.draw((NpcDialogueGraphScreen) (Object) this, graphics, node);
        ci.cancel();
    }
}
