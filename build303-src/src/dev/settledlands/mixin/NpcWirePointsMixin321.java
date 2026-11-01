package dev.settledlands.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcWirePoints321;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.NpcWirePointsUi321;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 321: reroute points on a cable - add, drag, delete.
 *
 * <p>Drawing. The stock cable is one call to {@code cable} per wire, which computes both port
 * positions and hands them to {@code curve}. The redirect takes that call and, when the wire has
 * bends, replaces it with one {@code curve} per segment: output port, each bend in order, input
 * port. Same routine, same colour, same dashes, so the result is a stock cable that goes where it is
 * told. Wires without bends fall through to the original call untouched.</p>
 *
 * <p>Mouse. A double click on a cable adds a bend there; dragging a bend moves it; a double click on
 * a bend deletes it. All three are taken before the screen's own click handling, so a bend never
 * starts a node drag, and every finished gesture calls the screen's {@code changed()} - the edit is
 * saved the same way every other edit is.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcWirePointsMixin321 {

    /** How close two clicks have to be in time and place to count as a double click. */
    private static final long DOUBLE_MS = 350L;

    private static final double DOUBLE_SLOP = 4.0D;

    /** The bend being dragged, or null. */
    private NpcWirePoints321.Handle settledlands$drag;

    private long settledlands$lastClick;

    private double settledlands$lastX;

    private double settledlands$lastY;

    @Shadow
    private int cable(BufferBuilder buffer, Matrix4f matrix, Node from, int fromPort, Node to,
            int toPort, int color, boolean dashed) {
        throw new AssertionError();
    }

    @Shadow
    private int curve(BufferBuilder buffer, Matrix4f matrix, double x1, double y1, double x2,
            double y2, int color, boolean dashed) {
        throw new AssertionError();
    }

    @Shadow
    private void changed() {
        throw new AssertionError();
    }

    @Shadow
    public abstract NpcDialogueGraph settledlands$graph();

    @Shadow
    public abstract NpcGraphCanvas settledlands$canvas();

    @Shadow
    public abstract boolean settledlands$contextOpen();

    @Redirect(method = "wires(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/client/NpcDialogueGraphScreen;cable(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lorg/joml/Matrix4f;Ldev/settledlands/npc/NpcDialogueGraph$Node;ILdev/settledlands/npc/NpcDialogueGraph$Node;IIZ)I"),
            require = 1, remap = false)
    private int settledlands$cableThroughPoints(NpcDialogueGraphScreen self, BufferBuilder buffer,
            Matrix4f matrix, Node from, int fromPort, Node to, int toPort, int color,
            boolean dashed) {
        List<int[]> points = NpcWirePoints321.points(from, fromPort, to.id);
        if (points.isEmpty()) {
            return cable(buffer, matrix, from, fromPort, to, toPort, color, dashed);
        }
        NpcGraphCanvas canvas = settledlands$canvas();
        if (canvas == null) {
            return cable(buffer, matrix, from, fromPort, to, toPort, color, dashed);
        }
        double x = canvas.portScreenX(from, true);
        double y = canvas.portScreenY(from, true, fromPort);
        int drawn = 0;
        for (int[] point : points) {
            double px = canvas.screenX(point[0]);
            double py = canvas.screenY(point[1]);
            drawn += curve(buffer, matrix, x, y, px, py, color, dashed);
            x = px;
            y = py;
        }
        drawn += curve(buffer, matrix, x, y, canvas.portScreenX(to, false),
                canvas.portScreenY(to, false, toPort), color, dashed);
        return drawn;
    }

    @Inject(method = "wires(Lnet/minecraft/client/gui/GuiGraphics;II)V", at = @At("TAIL"),
            remap = false)
    private void settledlands$drawHandles(GuiGraphics graphics, int mouseX, int mouseY,
            CallbackInfo ci) {
        NpcWirePointsUi321.drawHandles((NpcDialogueGraphScreen) (Object) this, graphics,
                settledlands$graph(), mouseX, mouseY);
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$pointClick(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        if (settledlands$contextOpen()) return;
        NpcDialogueGraph graph = settledlands$graph();
        NpcGraphCanvas canvas = settledlands$canvas();
        if (graph == null || canvas == null) return;

        boolean again = settledlands$doubleClick(mouseX, mouseY);
        NpcWirePoints321.Handle handle = NpcWirePointsUi321.handleAt(screen, graph, mouseX, mouseY);
        if (handle != null) {
            if (again) {
                NpcWirePoints321.removeAt(handle.from, handle.fromPort, handle.toId, handle.index);
            } else {
                settledlands$drag = handle;
            }
            changed();
            cir.setReturnValue(true);
            return;
        }

        NpcWirePointsUi321.Wire wire = NpcWirePointsUi321.wireAt(screen, graph, mouseX, mouseY);
        if (wire == null || !again) return;
        NpcWirePoints321.add(wire.from, wire.fromPort, wire.toId,
                NpcWirePointsUi321.graphX(canvas, mouseX), NpcWirePointsUi321.graphY(canvas, mouseY));
        changed();
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged(DDIDD)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$dragPoint(double mouseX, double mouseY, int button, double dragX,
            double dragY, CallbackInfoReturnable<Boolean> cir) {
        if (settledlands$drag == null) return;
        NpcGraphCanvas canvas = settledlands$canvas();
        if (canvas == null) return;
        NpcWirePoints321.moveTo(settledlands$drag.from, settledlands$drag.fromPort,
                settledlands$drag.toId, settledlands$drag.index,
                NpcWirePointsUi321.graphX(canvas, mouseX), NpcWirePointsUi321.graphY(canvas, mouseY));
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$releasePoint(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (settledlands$drag == null) return;
        settledlands$drag = null;
        changed();
        cir.setReturnValue(true);
    }

    /** True when this click lands where the previous one did, soon enough to be a double click. */
    private boolean settledlands$doubleClick(double mouseX, double mouseY) {
        long now = System.currentTimeMillis();
        boolean again = now - settledlands$lastClick <= DOUBLE_MS
                && Math.abs(mouseX - settledlands$lastX) <= DOUBLE_SLOP
                && Math.abs(mouseY - settledlands$lastY) <= DOUBLE_SLOP;
        settledlands$lastClick = again ? 0L : now;
        settledlands$lastX = mouseX;
        settledlands$lastY = mouseY;
        return again;
    }
}
