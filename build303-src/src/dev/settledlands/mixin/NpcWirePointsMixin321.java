package dev.settledlands.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcWireJoin322;
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
 * Build 323: the point as a hub - made, grabbed, moved, aimed and removed.
 *
 * <p>Drawing. The stock cable is one call to {@code cable} per wire, which computes both port
 * positions and hands them to {@code curve}. The redirect takes that call and, when the wire has
 * bends, replaces it with one {@code curve} per segment: output port, each bend in order, input
 * port. Same routine, same colour, same dashes, so the result is a stock cable that goes where it is
 * told. Wires without bends fall through to the original call untouched. Wires that wait at a point
 * have no target, so no stock cable exists for them; they are drawn as a plain line from the port to
 * the point, in the same white as the rest of the wiring.</p>
 *
 * <p>Mouse. Every gesture here is finished inside this class, before the screen's own handling, and
 * every finished gesture calls {@code remember()} before it changes anything and {@code changed()}
 * after - so one Ctrl+Z takes back one gesture, whether that was making a point, moving it, aiming
 * it or removing it.</p>
 *
 * <ul>
 *   <li>double click on a cable - put a point on it;</li>
 *   <li>press on a point - grab it; drag it anywhere to move it, drag it onto a node to aim it,
 *       drop it back and nothing happens;</li>
 *   <li>double click or right click on a point - remove it;</li>
 *   <li>release a dragged wire on a point - the wire stops there and waits, it does not connect
 *       anywhere behind the author's back.</li>
 * </ul>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcWirePointsMixin321 {

    /** How close two clicks have to be in time and place to count as a double click. */
    private static final long DOUBLE_MS = 350L;

    private static final double DOUBLE_SLOP = 4.0D;

    /** True while a point is held, and where it is now / where it was picked up. */
    private boolean settledlands$pointDrag;

    private int settledlands$pointX;

    private int settledlands$pointY;

    private int settledlands$pointStartX;

    private int settledlands$pointStartY;

    /** True once the held point has actually moved; a press alone changes nothing. */
    private boolean settledlands$pointMoved;

    /** Node the held point would be aimed at, or -1. */
    private int settledlands$aimNode = -1;

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
    private void remember() {
        throw new AssertionError();
    }

    @Shadow
    private String status;

    @Shadow
    private String tr(String key) {
        throw new AssertionError();
    }

    /** Node a wire is being dragged from, or -1. Written by the stock screen. */
    @Shadow
    private int linkFrom;

    @Shadow
    private int linkFromPort;

    @Shadow
    public abstract NpcDialogueGraph settledlands$graph();

    @Shadow
    public abstract NpcGraphCanvas settledlands$canvas();

    @Shadow
    public abstract boolean settledlands$contextOpen();

    // -- drawing ------------------------------------------------------------

    @Redirect(method = "wires(Lnet/minecraft/client/gui/GuiGraphics;II)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/client/NpcDialogueGraphScreen;cable(Lcom/mojang/blaze3d/vertex/BufferBuilder;Lorg/joml/Matrix4f;Ldev/settledlands/npc/NpcDialogueGraph$Node;ILdev/settledlands/npc/NpcDialogueGraph$Node;IIZ)I"),
            require = 1, remap = false)
    private int settledlands$cableThroughPoints(NpcDialogueGraphScreen self, BufferBuilder buffer,
            Matrix4f matrix, Node from, int fromPort, Node to, int toPort, int color,
            boolean dashed) {
        List<int[]> points = NpcWirePoints321.bends(from, fromPort, to.id);
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
    private void settledlands$drawPoints(GuiGraphics graphics, int mouseX, int mouseY,
            CallbackInfo ci) {
        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        NpcDialogueGraph graph = settledlands$graph();
        if (graph == null) return;
        NpcWirePointsUi321.drawWaiting(screen, graphics, graph);
        if (settledlands$pointDrag) {
            NpcWirePointsUi321.drawAimBand(screen, graphics, settledlands$pointX,
                    settledlands$pointY, mouseX, mouseY);
        }
        NpcWirePointsUi321.drawPoints(screen, graphics, graph, mouseX, mouseY,
                settledlands$pointDrag, settledlands$aimNode);
    }

    // -- mouse --------------------------------------------------------------

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$pointClick(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (settledlands$contextOpen()) return;
        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        NpcDialogueGraph graph = settledlands$graph();
        NpcGraphCanvas canvas = settledlands$canvas();
        if (graph == null || canvas == null) return;
        if (linkFrom >= 0) return;                 // a wire is being dragged, not a point

        boolean again = settledlands$doubleClick(mouseX, mouseY);
        int[] point = NpcWirePointsUi321.pointAt(screen, graph, mouseX, mouseY);

        if (button == 1) {                         // right click removes a point outright
            if (point == null) return;
            settledlands$remove(graph, point);
            cir.setReturnValue(true);
            return;
        }
        if (button != 0) return;
        if (point != null) {
            if (again) {
                settledlands$remove(graph, point);
            } else {
                settledlands$pointDrag = true;
                settledlands$pointMoved = false;
                settledlands$pointX = point[0];
                settledlands$pointY = point[1];
                settledlands$pointStartX = point[0];
                settledlands$pointStartY = point[1];
                settledlands$aimNode = -1;
            }
            cir.setReturnValue(true);
            return;
        }

        NpcWirePointsUi321.Wire wire = NpcWirePointsUi321.wireAt(screen, graph, mouseX, mouseY);
        if (wire == null || !again) return;
        int px = NpcWirePointsUi321.graphX(canvas, mouseX);
        int py = NpcWirePointsUi321.graphY(canvas, mouseY);
        // A second dot next to the first is what a missed grab looks like, so a point is never
        // made on top of an existing one.
        if (NpcWirePoints321.pointWithin(graph, px, py, NpcWirePoints321.GRID * 2)) {
            cir.setReturnValue(true);
            return;
        }
        remember();
        NpcWirePoints321.addBend(wire.from, wire.fromPort, wire.toId, px, py);
        changed();
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged(DDIDD)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$dragPoint(double mouseX, double mouseY, int button, double dragX,
            double dragY, CallbackInfoReturnable<Boolean> cir) {
        if (!settledlands$pointDrag) return;
        NpcDialogueGraph graph = settledlands$graph();
        NpcGraphCanvas canvas = settledlands$canvas();
        if (graph == null || canvas == null) return;
        settledlands$aimNode = canvas.hit(graph, mouseX, mouseY);
        int nx = NpcWirePointsUi321.graphX(canvas, mouseX);
        int ny = NpcWirePointsUi321.graphY(canvas, mouseY);
        if (nx != settledlands$pointX || ny != settledlands$pointY) {
            if (!settledlands$pointMoved) {
                remember();
                settledlands$pointMoved = true;
            }
            NpcWirePoints321.movePoint(graph, settledlands$pointX, settledlands$pointY, nx, ny);
            settledlands$pointX = nx;
            settledlands$pointY = ny;
            changed();
        }
        cir.setReturnValue(true);
    }

    /**
     * The end of a gesture. A held point is put down: on a node it is aimed there, anywhere else it
     * simply stays where it was moved. A wire that was being dragged and is released on a point stops
     * at the point and waits - it is never connected to something the author did not pick, and the
     * stock "make a node out of this wire" menu never opens over a point.
     */
    @Inject(method = "mouseReleased(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$releasePoint(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (settledlands$pointDrag) {
            boolean moved = settledlands$pointMoved;
            int x = settledlands$pointX;
            int y = settledlands$pointY;
            int startX = settledlands$pointStartX;
            int startY = settledlands$pointStartY;
            settledlands$pointDrag = false;
            settledlands$pointMoved = false;
            settledlands$aimNode = -1;
            NpcDialogueGraph graph = settledlands$graph();
            NpcGraphCanvas canvas = settledlands$canvas();
            if (moved && button == 0 && graph != null && canvas != null) {
                int target = canvas.hit(graph, mouseX, mouseY);
                if (target >= 0) {
                    // Aiming is not moving: the point goes back where it was picked up and the
                    // wires at it are sent to the node it was dropped on.
                    NpcWirePoints321.movePoint(graph, x, y, startX, startY);
                    remember();
                    if (NpcWireJoin322.aim(graph, startX, startY, target) > 0) {
                        changed();
                        status = tr("gui.settledlands.graph.connected");
                    }
                }
            }
            cir.setReturnValue(true);
            return;
        }
        if (button != 0 || linkFrom < 0) return;
        NpcDialogueGraphScreen screen = (NpcDialogueGraphScreen) (Object) this;
        NpcDialogueGraph graph = settledlands$graph();
        if (graph == null) return;
        int[] point = NpcWirePointsUi321.pointAt(screen, graph, mouseX, mouseY);
        if (point == null) return;
        remember();
        boolean attached = NpcWireJoin322.attach(graph, linkFrom, linkFromPort, point[0],
                point[1]);
        linkFrom = -1;
        if (attached) changed();
        cir.setReturnValue(true);
    }

    // -- helpers ------------------------------------------------------------

    private void settledlands$remove(NpcDialogueGraph graph, int[] point) {
        remember();
        if (NpcWirePoints321.removePoint(graph, point[0], point[1])) {
            changed();
            status = tr("gui.settledlands.graph.deleted");
        }
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
