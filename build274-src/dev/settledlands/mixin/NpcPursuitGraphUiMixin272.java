package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphCanvas;
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
 * Build 273: custom pursuit node card with inline dropdown + field + stop toggle.
 *
 * Changes against Build 272 (each one was a launch crash):
 *  - the shadow is {@code node()} - the screen has no {@code selected()} method,
 *    it keeps the selected node id in a private int field {@code selected} and
 *    exposes {@code node() == graph.get(selected)};
 *  - the {@code charTyped(CI)V} injector is gone: the screen never declares
 *    charTyped (Screen declares {@code charTyped(char,int)Z} and inherited
 *    methods cannot be injected into), so it failed to resolve. Typing into the
 *    node field needs {@code NpcDialogueGraphScreen#charTyped(char,int)boolean}
 *    to exist first - see README-Build273-RU.md.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcPursuitGraphUiMixin272 {
    @Shadow private NpcDialogueGraph graph;
    @Shadow private NpcGraphContextMenu contextMenu;
    @Shadow private Node addAt(int type, double x, double y) { throw new AssertionError(); }
    @Shadow private void widgets() { throw new AssertionError(); }
    @Shadow private void changed() { throw new AssertionError(); }
    @Shadow private Node node() { throw new AssertionError(); }

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

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$createOrEditPursuitNode(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        NpcDialogueGraphScreen self = (NpcDialogueGraphScreen) (Object) this;

        NpcGraphCanvas canvas = self.settledlands$canvas();
        if (canvas != null && canvas.contains(mouseX, mouseY)) {
            Node hit = graph.get(canvas.hit(graph, mouseX, mouseY));
            if (hit != null && PursuitGraphNode272.isPursuitNode(hit)) {
                if (PursuitGraphNode272.mouseClicked(self, mouseX, mouseY, button, hit)) {
                    cir.setReturnValue(true);
                    cir.cancel();
                    return;
                }
            }
        }

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
        // setReturnValue alone does not stop the original method - it also needs cancel()
        cir.setReturnValue(true);
        cir.cancel();
    }

    @Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$pursuitKeyPressed(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        Node sel = node();
        if (sel != null && PursuitGraphNode272.isPursuitNode(sel)) {
            if (PursuitGraphNode272.keyPressed((NpcDialogueGraphScreen) (Object) this, keyCode, scanCode, modifiers, sel)) {
                cir.setReturnValue(true);
                cir.cancel();
            }
        }
    }
}
