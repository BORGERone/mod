package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphContextMenu;
import dev.settledlands.npc.client.PursuitGraphNodes271;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcPursuitGraphUiMixin271 {
    @Shadow private NpcDialogueGraph graph;
    @Shadow private NpcGraphContextMenu contextMenu;
    @Shadow private Node addAt(int type, double x, double y) { throw new AssertionError(); }
    @Shadow private void widgets() { throw new AssertionError(); }
    @Shadow private void changed() { throw new AssertionError(); }

    @Inject(method = "drawContextMenu(Lnet/minecraft/client/gui/GuiGraphics;II)V", at = @At("TAIL"), remap = false)
    private void settledlands$drawPursuitMenuRows(GuiGraphics graphics, int mouseX, int mouseY, CallbackInfo ci) {
        PursuitGraphNodes271.drawExtraRows(contextMenu, graphics,
                ((NpcDialogueGraphScreen) (Object) this).settledlands$font(), mouseX, mouseY);
    }

    @Inject(method = "drawNode(Lnet/minecraft/client/gui/GuiGraphics;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("TAIL"), remap = false)
    private void settledlands$labelPursuitNode(GuiGraphics graphics, Node node, CallbackInfo ci) {
        PursuitGraphNodes271.drawNodeTitle((NpcDialogueGraphScreen) (Object) this, graphics, node);
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$createPursuitNode(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (button != 0 || !PursuitGraphNodes271.isOpenForCreation(contextMenu)) return;
        int row = PursuitGraphNodes271.rowAt(contextMenu, mouseX, mouseY);
        String action = PursuitGraphNodes271.actionForMenuRow(row);
        if (action == null) return;

        PursuitGraphNodes271.MenuPoint point = PursuitGraphNodes271.capture(contextMenu, row);
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
