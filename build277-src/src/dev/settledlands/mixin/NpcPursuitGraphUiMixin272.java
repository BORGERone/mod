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
 * Build 277: creating a pursuit node and the extra context-menu row.
 *
 * The card is painted by PursuitGraphNode272, the inspector by
 * NpcPursuitInspectorMixin277. This mixin only adds the "+ Pursuit" row to the
 * create menu and turns a click on it into a node. It no longer intercepts
 * keystrokes or in-card clicks: editing happens in the inspector panel.
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
