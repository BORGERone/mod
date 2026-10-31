package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphContextMenu;
import dev.settledlands.npc.client.PursuitGraphNode272;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 278: the pursuit card, the extra context-menu row and node creation.
 *
 * A pursuit node is stored as a TRIGGER node, so the stock drawNode paints it
 * as "Trigger #n" with the raw action text. Build 277 lost the drawNode hook,
 * which is why a freshly created pursuit node looked like a plain trigger node;
 * it is back here and PursuitGraphNode272 draws the card.
 *
 * This mixin does not intercept keystrokes or in-card clicks: editing happens in
 * the inspector panel (NpcPursuitInspectorMixin277).
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcPursuitGraphUiMixin272 {
    @Shadow private NpcDialogueGraph graph;
    @Shadow private NpcGraphContextMenu contextMenu;
    @Shadow private Node addAt(int type, double x, double y) { throw new AssertionError(); }
    @Shadow private void widgets() { throw new AssertionError(); }
    @Shadow private void changed() { throw new AssertionError(); }

    @Inject(method = "drawContextMenu(Lnet/minecraft/client/gui/GuiGraphics;II)V", at = @At("TAIL"), remap = false)
    private void settledlands$drawPursuitMenuRow(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        PursuitGraphNode272.drawMenuRow(contextMenu, graphics,
                ((NpcDialogueGraphScreen) (Object) this).settledlands$font(), mouseX, mouseY);
    }

    @Inject(method = "drawNode(Lnet/minecraft/client/gui/GuiGraphics;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$drawPursuitNode(GuiGraphics graphics, Node node, CallbackInfo ci) {
        if (PursuitGraphNode272.isPursuitNode(node)) {
            PursuitGraphNode272.drawNode((NpcDialogueGraphScreen) (Object) this, graphics, node);
            ci.cancel();
        }
    }

    /**
     * Build 289: the blue output of an "In Radius" node. The stock card is drawn first
     * (this hook does not cancel for check nodes), then the pin is painted on its right
     * edge in screen space, so it keeps its size at any zoom.
     */
    @Inject(method = "drawNode(Lnet/minecraft/client/gui/GuiGraphics;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("TAIL"), remap = false)
    private void settledlands$drawCheckValuePin(GuiGraphics graphics, Node node, CallbackInfo ci) {
        PursuitGraphNode272.drawCheckPin((NpcDialogueGraphScreen) (Object) this, graphics, node);
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$createPursuitNode(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || !PursuitGraphNode272.isOpenForCreation(contextMenu)) return;
        int row = PursuitGraphNode272.rowAt(contextMenu, mouseX, mouseY);
        String action = PursuitGraphNode272.actionForMenuRow(row);
        if (action == null) return;

        PursuitGraphNode272.MenuPoint point = PursuitGraphNode272.capture(contextMenu, row);
        contextMenu.close();
        Node node = addAt(NpcDialogueGraph.TRIGGER, point.x(), point.y());
        if (node != null) {
            node.action = action;
            if (point.wireFrom() >= 0 && graph != null)
                graph.connect(point.wireFrom(), node.id, point.wirePort());
            widgets();
            changed();
        }
        cir.setReturnValue(true);
    }
}
