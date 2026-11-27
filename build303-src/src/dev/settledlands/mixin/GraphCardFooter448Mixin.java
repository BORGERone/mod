package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.VarDataNode453;
import dev.settledlands.npc.client.GraphCardDesc448;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphCanvas;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 448: attaches compact bottom role/description footers to every node card drawn via
 * {@code NpcStatsGraphUi.draw}, expands visibility culling to cover the footer, and allows
 * selecting/dragging cards by clicking their footer area.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcStatsGraphUi", remap = false)
public abstract class GraphCardFooter448Mixin {

    @Inject(method = "draw(Ldev/settledlands/npc/client/NpcDialogueGraphScreen;Lnet/minecraft/client/gui/GuiGraphics;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("RETURN"), remap = false)
    private static void settledlands$drawNodeFooter448(NpcDialogueGraphScreen screen,
            GuiGraphics graphics, NpcDialogueGraph.Node node, CallbackInfo ci) {
        // Build 453: node descriptions moved cleanly into the bottom-right inspector footer.
    }

    @Inject(method = "visible(Ldev/settledlands/npc/client/NpcGraphCanvas;Ldev/settledlands/npc/NpcDialogueGraph$Node;)Z",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void settledlands$visibleWithFooter448(NpcGraphCanvas canvas,
            NpcDialogueGraph.Node node, CallbackInfoReturnable<Boolean> cir) {
        // Build 453: no extra footer height below canvas cards.
    }

    @Inject(method = "hit(Ldev/settledlands/npc/client/NpcGraphCanvas;Ldev/settledlands/npc/NpcDialogueGraph;DD)I",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void settledlands$hitWithFooter448(NpcGraphCanvas canvas,
            NpcDialogueGraph graph, double mouseX, double mouseY,
            CallbackInfoReturnable<Integer> cir) {
        // Build 453: canvas card hitbox covers the card itself.
    }

    @Inject(method = "hitPort(Ldev/settledlands/npc/client/NpcGraphCanvas;Ldev/settledlands/npc/NpcDialogueGraph;DDZ)I",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void settledlands$disableWhitePortsOnVarData456(NpcGraphCanvas canvas,
            NpcDialogueGraph graph, double mouseX, double mouseY, boolean outgoing,
            CallbackInfoReturnable<Integer> cir) {
        Integer hitId = cir.getReturnValue();
        if (hitId == null || hitId.intValue() < 0 || graph == null) return;
        if (VarDataNode453.isVarDataNode(graph.get(hitId.intValue()))) {
            cir.setReturnValue(-1);
        }
    }
}
