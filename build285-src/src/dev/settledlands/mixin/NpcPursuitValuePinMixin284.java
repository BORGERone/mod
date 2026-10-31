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
 * Build 284: the two value pins of the pursuit node.
 *
 * <p>A blueprint node has an execution pair and a data pair. The execution pair is
 * the stock port dots; the data pair is the target value, and it now runs both
 * ways:</p>
 * <ul>
 *   <li>blue diamond on the right - the target this node pursues, wire it to any
 *       node that should receive the value;</li>
 *   <li>blue diamond on the left - the target this node takes in, wire it to
 *       another pursuit node and this one follows that node's target;</li>
 *   <li>the wires are one smooth blue cable each, and the rubber band follows the
 *       cursor while a pin is dragged.</li>
 * </ul>
 *
 * <p>Both wires are stored in the node's own action (see PursuitGraphNode272), so
 * they are saved and loaded with the graph and need no new field. The value a
 * wired-in target resolves to is written into the action on every graph change, so
 * the server always receives the current text.</p>
 *
 * <p>Everything is reached through the accessors Build 272 already publishes, so
 * this mixin shadows no member of the screen and cannot go stale if a field is
 * renamed.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcPursuitValuePinMixin284 {

    /** Node whose value output is being dragged, or -1. */
    private static int settledlands$dragOut = -1;

    /** Node whose value input is being dragged, or -1. */
    private static int settledlands$dragIn = -1;

    private static NpcDialogueGraphScreen settledlands$screen(Object self) {
        return (NpcDialogueGraphScreen) self;
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$grabValuePin(double mouseX, double mouseY, int button,
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
        if (PursuitGraphNode272.hitTargetPin(canvas, node, mouseX, mouseY)) {
            settledlands$dragOut = id;
            cir.setReturnValue(true);
        } else if (PursuitGraphNode272.hitTargetInPin(canvas, node, mouseX, mouseY)) {
            settledlands$dragIn = id;
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$dropValuePin(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        int out = settledlands$dragOut;
        int in = settledlands$dragIn;
        if (out < 0 && in < 0) return;
        settledlands$dragOut = -1;
        settledlands$dragIn = -1;
        cir.setReturnValue(true);

        NpcDialogueGraphScreen screen = settledlands$screen(this);
        NpcDialogueGraph graph = screen.settledlands$graph();
        if (graph == null) return;
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        int id = canvas == null ? -1 : canvas.hit(graph, mouseX, mouseY);
        Node over = id >= 0 ? graph.get(id) : null;

        if (out >= 0) {
            Node source = graph.get(out);
            if (PursuitGraphNode272.isPursuitNode(source)) {
                Node consumer = over != null && over.id != out ? over : null;
                PursuitGraphNode272.toggleConsumer(source, consumer == null ? -1 : consumer.id);
            }
        }
        if (in >= 0) {
            Node receiver = graph.get(in);
            if (PursuitGraphNode272.isPursuitNode(receiver)) {
                // Only a pursuit node carries a target value; anything else means "unwire".
                boolean valid = over != null && over.id != in
                        && PursuitGraphNode272.isPursuitNode(over);
                PursuitGraphNode272.setSource(receiver, valid ? over.id : -1);
            }
        }
        screen.settledlands$changed();
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"), remap = false)
    private void settledlands$drawValueWires(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        PursuitGraphNode272.drawValueWires(settledlands$screen(this), graphics, mouseX, mouseY,
                settledlands$dragOut, settledlands$dragIn);
    }

    /**
     * Any change to the graph re-materialises every wired-in target, so what is
     * saved is what the cards show.
     */
    @Inject(method = "changed()V", at = @At("HEAD"), remap = false)
    private void settledlands$refreshValueLinks(CallbackInfo ci) {
        PursuitGraphNode272.refreshDependents(settledlands$screen(this).settledlands$graph());
    }
}
