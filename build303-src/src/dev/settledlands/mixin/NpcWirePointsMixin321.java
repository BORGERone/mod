package dev.settledlands.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcWireJoin322;
import dev.settledlands.npc.NpcWirePoints321;
import dev.settledlands.npc.client.NpcDialogueGraphScreen;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.NpcWirePointsUi321;
import dev.settledlands.npc.client.PursuitGraphNode272;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
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
 * told. Wires without bends fall through to the original call untouched.</p>
 *
 * <p>Wires that wait at a point have no target, so no stock cable exists for them: the screen only
 * draws links. They are drawn here with the same {@code curve} the screen uses, into a buffer of
 * their own, in the same white - the same call, the same width, the same dashes, so a waiting wire
 * and the band that follows the cursor while a wire is pulled out of a point look exactly like the
 * rest of the wiring. Nothing here re-implements the look of a cable.</p>
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

    /** True while a wire is being pulled out of a point's port, and where that point is. */
    private boolean settledlands$pull;

    private int settledlands$pullX;

    private int settledlands$pullY;

    private long settledlands$lastClick;

    private double settledlands$lastX;

    private double settledlands$lastY;

    /** Set by any drag: the press before the next one was then a drag, not a click. */
    private boolean settledlands$gestureMoved;

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
        NpcGraphCanvas canvas = settledlands$canvas();
        if (graph == null || canvas == null) return;
        settledlands$drawWires(graphics, graph, canvas, mouseX, mouseY);
        NpcWirePointsUi321.drawPoints(screen, graphics, graph, mouseX, mouseY,
                settledlands$pointDrag || settledlands$pull, settledlands$aimNode);
    }

    /**
     * The wires the screen cannot draw: a waiting wire has no target, so no link exists to draw.
     * Each one goes through {@code curve} - the screen's own routine - into a buffer of its own, so
     * the result is indistinguishable from a stock cable. While a wire is being pulled out of a
     * point, the band from that point's port to the cursor is drawn here too, in the same way.
     */
    private void settledlands$drawWires(GuiGraphics graphics, NpcDialogueGraph graph,
            NpcGraphCanvas canvas, double mouseX, double mouseY) {
        if (graph.nodes == null) return;
        boolean any = settledlands$pull;
        if (!any) {
            for (Node node : graph.nodes.values()) {
                if (!NpcWirePoints321.hangs(node).isEmpty()) {
                    any = true;
                    break;
                }
            }
        }
        if (!any) return;

        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();
        int color = PursuitGraphNode272.COL_CABLE_WHITE;
        int quads = 0;

        for (Node node : graph.nodes.values()) {
            List<int[]> hangs = NpcWirePoints321.hangs(node);
            if (hangs.isEmpty()) continue;
            double x = canvas.portScreenX(node, true);
            for (int[] hang : hangs) {
                int size = NpcWirePoints321.routeSize(hang);
                double y = canvas.portScreenY(node, true, hang[0]);
                for (int i = 0; i < size; i++) {
                    double qx = canvas.screenX(NpcWirePoints321.routeX(hang, i));
                    double qy = canvas.screenY(NpcWirePoints321.routeY(hang, i));
                    if (Math.max(x, qx) >= canvas.left && Math.min(x, qx) <= canvas.right
                            && Math.max(y, qy) >= canvas.top && Math.min(y, qy) <= canvas.bottom) {
                        // One curve per segment, exactly as a carrying wire is drawn through its
                        // points, so a run of points looks the same either way.
                        quads += curve(buffer, matrix, x, y, qx, qy, color, false);
                    }
                    x = qx;
                    y = qy;
                }
                x = canvas.portScreenX(node, true);
            }
        }

        if (settledlands$pull) {
            quads += curve(buffer, matrix,
                    NpcWirePointsUi321.portX(canvas, settledlands$pullX),
                    NpcWirePointsUi321.portY(canvas, settledlands$pullY),
                    mouseX, mouseY, color, false);
        }

        if (quads > 0) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } else {
            buffer.build();
        }
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
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
        int[] port = NpcWirePointsUi321.portAt(screen, graph, mouseX, mouseY);
        boolean onDot = NpcWirePointsUi321.onDot(screen, graph, point, mouseX, mouseY);
        boolean portFree = port != null
                && (point == null || (point[0] == port[0] && point[1] == port[1]
                        && NpcWirePointsUi321.overPort(screen, graph, port, mouseX, mouseY)));
        // The wire is only looked up when the answer can use it, so a plain drag costs nothing.
        NpcWirePointsUi321.Wire wire = again && !onDot && button == 0
                ? NpcWirePointsUi321.wireAt(screen, graph, mouseX, mouseY)
                : null;

        switch (NpcWirePointsUi321.decide(again, button, point != null, onDot, port != null,
                portFree, wire != null)) {
            case NpcWirePointsUi321.ACT_REMOVE -> {
                int[] hit = point != null ? point : port;
                if (hit == null) return;
                settledlands$pointDrag = false;
                settledlands$pointMoved = false;
                settledlands$remove(graph, hit);
                cir.setReturnValue(true);
            }
            case NpcWirePointsUi321.ACT_PULL -> {
                settledlands$pull = true;
                settledlands$pullX = port[0];
                settledlands$pullY = port[1];
                settledlands$aimNode = -1;
                cir.setReturnValue(true);
            }
            case NpcWirePointsUi321.ACT_ADD -> {
                settledlands$pointDrag = false;
                settledlands$pointMoved = false;
                // The point goes where the cable is, not where the cursor is: a dot beside the
                // wire is exactly what this build removes.
                int[] at = NpcWirePointsUi321.pointOn(canvas, wire, mouseX, mouseY);
                if (at == null) return;
                remember();
                int px = at[0];
                int py = at[1];
                if (wire.waiting()) {
                    // One more point in the run this wire visits, where it was clicked, so
                    // node - point - point - point is possible.
                    NpcWirePoints321.addRoutePoint(wire.from, wire.fromPort, wire.endX, wire.endY,
                            px, py, wire.index);
                } else {
                    NpcWirePoints321.addBend(wire.from, wire.fromPort, wire.toId, px, py,
                            wire.index);
                }
                changed();
                status = tr("gui.settledlands.graph.point_added");
                cir.setReturnValue(true);
            }
            case NpcWirePointsUi321.ACT_GRAB -> {
                settledlands$pointDrag = true;
                settledlands$pointMoved = false;
                settledlands$pointX = point[0];
                settledlands$pointY = point[1];
                settledlands$pointStartX = point[0];
                settledlands$pointStartY = point[1];
                settledlands$aimNode = -1;
                cir.setReturnValue(true);
            }
            default -> {
            }
        }
    }

    @Inject(method = "mouseDragged(DDIDD)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$dragPoint(double mouseX, double mouseY, int button, double dragX,
            double dragY, CallbackInfoReturnable<Boolean> cir) {
        settledlands$gestureMoved = true;
        if (settledlands$pull) {
            NpcDialogueGraph graph = settledlands$graph();
            NpcGraphCanvas canvas = settledlands$canvas();
            settledlands$aimNode = graph == null || canvas == null ? -1
                    : canvas.hit(graph, mouseX, mouseY);
            cir.setReturnValue(true);
            return;
        }
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
        if (settledlands$pull) {
            int x = settledlands$pullX;
            int y = settledlands$pullY;
            settledlands$pull = false;
            settledlands$aimNode = -1;
            NpcDialogueGraph graph = settledlands$graph();
            NpcGraphCanvas canvas = settledlands$canvas();
            if (button == 0 && graph != null && canvas != null) {
                int target = canvas.hit(graph, mouseX, mouseY);
                if (target >= 0) {
                    remember();
                    if (NpcWireJoin322.aim(graph, x, y, target) > 0) {
                        changed();
                        status = tr("gui.settledlands.graph.connected");
                    }
                }
            }
            cir.setReturnValue(true);
            return;
        }
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
        settledlands$gestureMoved = false;
        if (attached) {
            changed();
            status = tr("gui.settledlands.graph.point_waiting");
        } else {
            // Nothing was connected and nothing may be: the stock release would take the wire to
            // whatever node card happens to lie under the cursor, which is not what the author
            // dropped it on.
            status = tr("gui.settledlands.graph.point_refused");
        }
        cir.setReturnValue(true);
    }

    // -- helpers ------------------------------------------------------------

    private void settledlands$remove(NpcDialogueGraph graph, int[] point) {
        remember();
        if (NpcWirePoints321.removePoint(graph, point[0], point[1])) {
            changed();
            status = tr("gui.settledlands.graph.point_removed");
        }
    }

    /**
     * True when this click lands where the previous one did, soon enough to be a double click, and
     * the press before it did not turn into a drag. Moving a point and pressing it again is two
     * gestures, not a double click - reading it as one is what made a moved point come back as a
     * copy.
     */
    private boolean settledlands$doubleClick(double mouseX, double mouseY) {
        long now = System.currentTimeMillis();
        boolean again = NpcWirePointsUi321.doubleClick(settledlands$lastClick, now,
                mouseX - settledlands$lastX, mouseY - settledlands$lastY,
                settledlands$gestureMoved);
        settledlands$gestureMoved = false;
        settledlands$lastClick = again ? 0L : now;
        settledlands$lastX = mouseX;
        settledlands$lastY = mouseY;
        return again;
    }
}
