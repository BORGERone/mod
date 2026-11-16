package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcRadiusZone320;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcRadiusZoneUi320;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 320: the visibility toggle of a "В радиусе" node.
 *
 * <p>The box is painted after the card (TAIL of {@code drawNode}), so it sits on top of it, and the
 * click is taken before the screen decides it is a node drag (HEAD of {@code mouseClicked}) - the
 * control is above the card, outside the area the stock hit test uses, but taking the click first
 * is still what keeps a toggle from also starting a drag.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcRadiusZoneUiMixin320 {

    @Shadow
    private NpcDialogueGraph graph;

    @Shadow
    private void changed() {
        throw new AssertionError();
    }

    @Inject(method = "drawNode(Lnet/minecraft/client/gui/GuiGraphics;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("TAIL"), remap = false)
    private void settledlands$drawZoneToggle(GuiGraphics graphics, Node node, CallbackInfo ci) {
        NpcRadiusZoneUi320.draw((NpcDialogueGraphScreen) (Object) this, graphics, node);
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$clickZoneToggle(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        if (screen.settledlands$contextOpen()) return;
        Node hit = NpcRadiusZoneUi320.at(screen, graph, mouseX, mouseY);
        if (hit == null) return;
        NpcRadiusZone320.setVisible(hit, !NpcRadiusZone320.visible(hit));
        changed();
        cir.setReturnValue(true);
    }
}
