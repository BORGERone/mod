package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.PursuitGraphNode272;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 283: the value output of the pursuit node.
 *
 * <p>A blueprint node has two outputs - execution and a value. The execution
 * output of a pursuit node is the stock "next" port; the value output is the
 * diamond drawn by PursuitGraphNode272 under it, and this mixin is what makes it
 * work:</p>
 * <ul>
 *   <li>pressing it starts a wire instead of dragging the card;</li>
 *   <li>releasing over another node links that node, releasing over empty space
 *       unlinks every node of this pursuit node;</li>
 *   <li>the wires are drawn dashed in the node colour, and the rubber band
 *       follows the cursor while dragging.</li>
 * </ul>
 *
 * <p>The link is stored in the node's own action (see PursuitGraphNode272), so it
 * is saved and loaded with the graph like any other part of the node and needs no
 * new field. The value itself is the target text: the same text {@code %pursuit%}
 * resolves to in every node of this NPC's graph.</p>
 *
 * <p>Everything is reached through the accessors Build 272 already publishes, so
 * this mixin shadows no member of the screen and cannot go stale if a field is
 * renamed.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcPursuitTargetPinMixin283 {

    /** Node whose value pin is being dragged, or -1. */
    private static int settledlands$pinDragFrom = -1;

    private static NpcDialogueGraphScreen settledlands$screen(Object self) {
        return (NpcDialogueGraphScreen) self;
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$grabTargetPin(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        NpcDialogueGraphScreen screen = settledlands$screen(this);
        if (screen.settledlands$contextOpen()) return;
        NpcDialogueGraph graph = screen.settledlands$graph();
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (graph == null || canvas == null) return;
        int id = canvas.hit(graph, mouseX, mouseY);
        if (id < 0) return;
        Node node = graph.get(id);
        if (!PursuitGraphNode272.isPursuitNode(node)) return;
        if (!PursuitGraphNode272.hitTargetPin(canvas, node, mouseX, mouseY)) return;
        settledlands$pinDragFrom = id;
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$dropTargetPin(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        int from = settledlands$pinDragFrom;
        if (from < 0) return;
        settledlands$pinDragFrom = -1;
        cir.setReturnValue(true);
        NpcDialogueGraphScreen screen = settledlands$screen(this);
        NpcDialogueGraph graph = screen.settledlands$graph();
        if (graph == null) return;
        Node source = graph.get(from);
        if (!PursuitGraphNode272.isPursuitNode(source)) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        int id = canvas == null ? -1 : canvas.hit(graph, mouseX, mouseY);
        Node consumer = id >= 0 && id != from ? graph.get(id) : null;
        PursuitGraphNode272.toggleConsumer(source, consumer == null ? -1 : consumer.id);
        screen.settledlands$changed();
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"), remap = false)
    private void settledlands$drawTargetWires(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        PursuitGraphNode272.drawTargetWires(settledlands$screen(this), graphics, mouseX, mouseY,
                settledlands$pinDragFrom);
    }
}
