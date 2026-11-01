package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcWirePoints321;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Build 323: where the points are, what the mouse does with them, and what a waiting wire looks
 * like.
 *
 * <p>This class draws no cables: the screen's own routine draws them, one curve per segment, so a
 * wire that passes through a point looks exactly like a straight one - same thickness, same colour,
 * same dashes, same behaviour at any zoom. Here live the point handles, the hit tests, the stubs of
 * wires that are waiting at a point, and the band that shows where a dragged point will be aimed.</p>
 *
 * <p>Hit tests are deliberately forgiving. A point is drawn six pixels across at zoom one and the
 * canvas snaps it to the eight-unit grid, so the drawn dot is rarely exactly under the cursor; a
 * small tolerance turns "tried to grab the point" into "made another point next to it", which is
 * how copies appear. The radius therefore never drops below {@link #HIT_MIN} pixels and grows with
 * the zoom.</p>
 */
public final class NpcWirePointsUi321 {

    /** Half the handle in screen pixels at zoom 1, and the smallest it may get. */
    public static final int HALF = 3;

    /** Half of the smallest hit area, in pixels. Much larger than the dot on purpose. */
    public static final int HIT_MIN = 11;

    private static final int COL_HANDLE = 0xFFC8C8C8;

    private static final int COL_HANDLE_HOT = 0xFFFFFFFF;

    private static final int COL_HANDLE_EDGE = 0xFF2A2A2A;

    /** A waiting wire is an ordinary cable that has not found its end yet. */
    private static final int COL_WAITING = PursuitGraphNode272.COL_CABLE_WHITE;

    private static final int COL_AIM = 0xFFFFFFFF;

    /** The wire a point sits on or the wire under the cursor. */
    public static final class Wire {
        public final Node from;
        public final int fromPort;
        public final int toId;

        public Wire(Node from, int fromPort, int toId) {
            this.from = from;
            this.fromPort = fromPort;
            this.toId = toId;
        }
    }

    private NpcWirePointsUi321() {
    }

    /** Half the handle in pixels: it follows the zoom, but never shrinks below the minimum. */
    public static int handleHalf(NpcGraphCanvas canvas) {
        return Math.max(HALF, (int) Math.round(HALF * canvas.zoom));
    }

    /** How far from a point a click may land and still be taken as that point. */
    public static double hitRadius(NpcGraphCanvas canvas) {
        return Math.max(HIT_MIN, 7.0 * canvas.zoom);
    }

    // -- hit tests ----------------------------------------------------------

    /** The nearest point to the cursor, or null when the click is nowhere near one. */
    public static int[] pointAt(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            double mouseX, double mouseY) {
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (canvas == null || graph == null) return null;
        double radius = hitRadius(canvas);
        int[] best = null;
        double bestSq = radius * radius;
        for (int[] point : NpcWirePoints321.points(graph)) {
            double dx = mouseX - canvas.screenX(point[0]);
            double dy = mouseY - canvas.screenY(point[1]);
            double distSq = dx * dx + dy * dy;
            if (distSq <= bestSq) {
                bestSq = distSq;
                best = point;
            }
        }
        return best;
    }

    /** The wire nearest the cursor, or null. Nearest, not first: wires cross. */
    public static Wire wireAt(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            double mouseX, double mouseY) {
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (canvas == null || graph == null || graph.nodes == null) return null;
        if (!canvas.contains(mouseX, mouseY)) return null;
        if (canvas.hit(graph, mouseX, mouseY) >= 0) return null;
        if (canvas.hitPort(graph, mouseX, mouseY, true) >= 0) return null;
        if (canvas.hitPort(graph, mouseX, mouseY, false) >= 0) return null;

        double limit = Math.max(3.0, Math.min(6.5, 4.0 * canvas.zoom + 1.5));
        double bestSq = limit * limit;
        Wire best = null;
        for (Node node : graph.nodes.values()) {
            if (node.type == NpcDialogueGraph.IF && node.falseNext >= 0) {
                double d = distance(canvas, graph, node, 1, node.falseNext, mouseX, mouseY);
                if (d <= bestSq) {
                    bestSq = d;
                    best = new Wire(node, 1, node.falseNext);
                }
            }
            if (node.next >= 0) {
                double d = distance(canvas, graph, node, 0, node.next, mouseX, mouseY);
                if (d <= bestSq) {
                    bestSq = d;
                    best = new Wire(node, 0, node.next);
                }
            }
            for (int to : node.choices) {
                double d = distance(canvas, graph, node, 0, to, mouseX, mouseY);
                if (d <= bestSq) {
                    bestSq = d;
                    best = new Wire(node, 0, to);
                }
            }
            for (int to : node.randomBranches) {
                if (to < 0) continue;
                double d = distance(canvas, graph, node, 0, to, mouseX, mouseY);
                if (d <= bestSq) {
                    bestSq = d;
                    best = new Wire(node, 0, to);
                }
            }
            for (int to : node.triggers) {
                double d = distance(canvas, graph, node, 0, to, mouseX, mouseY);
                if (d <= bestSq) {
                    bestSq = d;
                    best = new Wire(node, 0, to);
                }
            }
        }
        return best;
    }

    /** Squared distance from the cursor to the curve the screen would draw for this wire. */
    private static double distance(NpcGraphCanvas canvas, NpcDialogueGraph graph, Node from,
            int fromPort, int toId, double mx, double my) {
        Node to = graph.get(toId);
        if (to == null || to == from) return Double.MAX_VALUE;
        double x1 = canvas.portScreenX(from, true);
        double y1 = canvas.portScreenY(from, true, fromPort);
        double x2 = canvas.portScreenX(to, false);
        double y2 = canvas.portScreenY(to, false, 0);
        double bend = Math.max(25.0 * canvas.zoom, Math.abs(x2 - x1) * 0.45);
        double px = x1;
        double py = y1;
        double best = Double.MAX_VALUE;
        for (int i = 1; i <= 20; i++) {
            double t = i / 20.0;
            double u = 1 - t;
            double x = u * u * u * x1 + 3 * u * u * t * (x1 + bend) + 3 * u * t * t * (x2 - bend)
                    + t * t * t * x2;
            double y = u * u * u * y1 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y2;
            double dx = x - px;
            double dy = y - py;
            double len = dx * dx + dy * dy;
            double v = len == 0 ? 0 : Math.max(0, Math.min(1, ((mx - px) * dx + (my - py) * dy)
                    / len));
            double ex = mx - (px + dx * v);
            double ey = my - (py + dy * v);
            best = Math.min(best, ex * ex + ey * ey);
            px = x;
            py = y;
        }
        return best;
    }

    // -- drawing ------------------------------------------------------------

    /**
     * The wires that wait at a point: they have no target, so no stock cable is drawn for them and
     * they are drawn here, from the port they left to the point they stop at.
     */
    public static void drawWaiting(NpcDialogueGraphScreen screen, GuiGraphics graphics,
            NpcDialogueGraph graph) {
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (canvas == null || graph == null || graph.nodes == null) return;
        for (Node node : graph.nodes.values()) {
            List<int[]> hangs = NpcWirePoints321.hangs(node);
            if (hangs.isEmpty()) continue;
            double x1 = canvas.portScreenX(node, true);
            for (int[] hang : hangs) {
                double y1 = canvas.portScreenY(node, true, hang[0]);
                double x2 = canvas.screenX(hang[1]);
                double y2 = canvas.screenY(hang[2]);
                if (Math.max(x1, x2) < canvas.left || Math.min(x1, x2) > canvas.right) continue;
                if (Math.max(y1, y2) < canvas.top || Math.min(y1, y2) > canvas.bottom) continue;
                dashed(graphics, x1, y1, x2, y2, COL_WAITING);
            }
        }
    }

    /**
     * The points themselves.
     *
     * @param mouseX    cursor, for the hover highlight
     * @param aimTarget node the point being dragged would be aimed at, or -1
     */
    public static void drawPoints(NpcDialogueGraphScreen screen, GuiGraphics graphics,
            NpcDialogueGraph graph, double mouseX, double mouseY, boolean dragging,
            int aimTarget) {
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (canvas == null || graph == null) return;
        int half = handleHalf(canvas);
        double radius = hitRadius(canvas);
        for (int[] point : NpcWirePoints321.points(graph)) {
            double x = canvas.screenX(point[0]);
            double y = canvas.screenY(point[1]);
            if (x < canvas.left - 16 || x > canvas.right + 16) continue;
            if (y < canvas.top - 16 || y > canvas.bottom + 16) continue;
            double dx = mouseX - x;
            double dy = mouseY - y;
            boolean near = dx * dx + dy * dy <= radius * radius;
            int grow = dragging && near ? half : 0;
            int left = (int) Math.round(x) - half;
            int top = (int) Math.round(y) - half;
            graphics.fill(left - 1 - grow, top - 1 - grow, left + half * 2 + 1 + grow,
                    top + half * 2 + 1 + grow, COL_HANDLE_EDGE);
            graphics.fill(left - grow, top - grow, left + half * 2 + grow, top + half * 2 + grow,
                    near || dragging ? COL_HANDLE_HOT : COL_HANDLE);
        }
        if (dragging && aimTarget >= 0) {
            Node target = graph.get(aimTarget);
            if (target != null) frame(canvas, graphics, target);
        }
    }

    /** While a point is dragged towards a node: the band, so it is clear what the drop will do. */
    public static void drawAimBand(NpcDialogueGraphScreen screen, GuiGraphics graphics,
            int pointX, int pointY, double mouseX, double mouseY) {
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (canvas == null) return;
        dashed(graphics, canvas.screenX(pointX), canvas.screenY(pointY), mouseX, mouseY, COL_AIM);
    }

    private static void frame(NpcGraphCanvas canvas, GuiGraphics graphics, Node node) {
        int x1 = (int) Math.round(canvas.screenX(node.x)) - 2;
        int y1 = (int) Math.round(canvas.screenY(node.y)) - 2;
        int x2 = (int) Math.round(canvas.screenX(node.x + NpcGraphCanvas.NODE_W)) + 2;
        int y2 = (int) Math.round(canvas.screenY(node.y + NpcGraphCanvas.NODE_H)) + 2;
        graphics.fill(x1, y1, x2, y1 + 1, COL_AIM);
        graphics.fill(x1, y2 - 1, x2, y2, COL_AIM);
        graphics.fill(x1, y1, x1 + 1, y2, COL_AIM);
        graphics.fill(x2 - 1, y1, x2, y2, COL_AIM);
    }

    /**
     * A dashed straight line, drawn with plain fills.
     *
     * <p>A waiting wire has no end, so the screen's own cable routine cannot draw it - that routine
     * needs two ports. Two pixels every five keeps it clearly a wire without a quad per pixel.</p>
     */
    private static void dashed(GuiGraphics graphics, double x1, double y1, double x2, double y2,
            int color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 0.5) return;
        int steps = (int) Math.ceil(length / 5.0);
        for (int i = 0; i <= steps; i += 2) {
            double t = Math.min(1.0, i * 5.0 / length);
            int x = (int) Math.round(x1 + dx * t);
            int y = (int) Math.round(y1 + dy * t);
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }

    // -- coordinates --------------------------------------------------------

    /** Graph coordinates of a screen position, on the model's grid. */
    public static int graphX(NpcGraphCanvas canvas, double mouseX) {
        return NpcWirePoints321.snap((int) Math.round(canvas.worldX(mouseX)));
    }

    public static int graphY(NpcGraphCanvas canvas, double mouseY) {
        return NpcWirePoints321.snap((int) Math.round(canvas.worldY(mouseY)));
    }
}
