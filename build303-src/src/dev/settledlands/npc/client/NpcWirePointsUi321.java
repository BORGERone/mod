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
 * <p>Every point has a small white port on its right, like the port on a card. A wire is pulled
 * out of that port: the point itself is grabbed to move it, the port is grabbed to aim it. Both
 * are drawn with the screen's own curve routine, so a waiting wire and the band that follows the
 * cursor look exactly like every other wire on the canvas.</p>
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

    /** The port of a point, in the same white as the ports on the cards. */
    private static final int COL_PORT = 0xFFE8E8E8;

    private static final int COL_AIM = 0xFFFFFFFF;

    /**
     * A wire under the cursor: either a real one (it has a target) or one that waits at a point
     * (it has none). {@code index} is where a new point goes in that wire's run, so the points of a
     * wire stay in the order they are clicked.
     */
    public static final class Wire {
        public final Node from;
        public final int fromPort;
        public final int toId;
        public final int endX;
        public final int endY;
        public final int index;

        /** Screen ends of the segment the cursor was nearest to: where the point will be put. */
        public final double sx1;
        public final double sy1;
        public final double sx2;
        public final double sy2;

        public Wire(Node from, int fromPort, int toId, int endX, int endY, int index) {
            this(from, fromPort, toId, endX, endY, index, Double.NaN, Double.NaN, Double.NaN,
                    Double.NaN);
        }

        public Wire(Node from, int fromPort, int toId, int endX, int endY, int index, double sx1,
                double sy1, double sx2, double sy2) {
            this.from = from;
            this.fromPort = fromPort;
            this.toId = toId;
            this.endX = endX;
            this.endY = endY;
            this.index = index;
            this.sx1 = sx1;
            this.sy1 = sy1;
            this.sx2 = sx2;
            this.sy2 = sy2;
        }

        /** A wire that stops at a point and carries no connection. */
        public boolean waiting() {
            return toId < 0;
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

    /**
     * The wire nearest the cursor, or null.
     *
     * <p>Nearest, not first, and along the route the cable is actually drawn on: a wire that runs
     * through points is nowhere near the straight line between its ports, so hit-testing the stock
     * curve would make the segments that lead to a point impossible to click.</p>
     *
     * <p>Every candidate carries the port number the screen itself draws that wire with - port 0
     * for {@code next}, {@code choices} and {@code triggers}, port 1 for the false branch of a
     * condition, and the index of the branch for a random node. A wire keyed with a port the screen
     * never draws is stored, drawn as a dot, and no cable ever passes through it: a point floating
     * on empty canvas.</p>
     */
    public static Wire wireAt(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            double mouseX, double mouseY) {
        return wireAt(screen == null ? null : screen.settledlands$canvas(), graph, mouseX, mouseY);
    }

    public static Wire wireAt(NpcGraphCanvas canvas, NpcDialogueGraph graph, double mouseX,
            double mouseY) {
        if (canvas == null || graph == null || graph.nodes == null) return null;
        if (!canvas.contains(mouseX, mouseY)) return null;
        if (canvas.hit(graph, mouseX, mouseY) >= 0) return null;
        if (canvas.hitPort(graph, mouseX, mouseY, true) >= 0) return null;
        if (canvas.hitPort(graph, mouseX, mouseY, false) >= 0) return null;

        double limit = Math.max(3.0, Math.min(6.5, 4.0 * canvas.zoom + 1.5));
        double[] best = { limit * limit };
        Wire found = null;
        for (Node node : graph.nodes.values()) {
            if (node.type == NpcDialogueGraph.IF && node.falseNext >= 0) {
                found = scan(canvas, graph, node, 1, node.falseNext, mouseX, mouseY, best, found);
            }
            if (node.next >= 0) {
                found = scan(canvas, graph, node, 0, node.next, mouseX, mouseY, best, found);
            }
            for (int to : node.choices) {
                found = scan(canvas, graph, node, 0, to, mouseX, mouseY, best, found);
            }
            for (int i = 0; i < node.randomBranches.size(); i++) {
                int to = node.randomBranches.get(i);
                if (to < 0) continue;
                // The screen draws branch i with port i, so the point has to be keyed with i.
                found = scan(canvas, graph, node, i, to, mouseX, mouseY, best, found);
            }
            for (int to : node.triggers) {
                found = scan(canvas, graph, node, 0, to, mouseX, mouseY, best, found);
            }
            // Wires that wait at a point have no link at all, so they are tested here.
            for (int[] hang : NpcWirePoints321.hangs(node)) {
                found = scanWaiting(canvas, node, hang, mouseX, mouseY, best, found);
            }
        }
        return found;
    }

    private static Wire scan(NpcGraphCanvas canvas, NpcDialogueGraph graph, Node from, int fromPort,
            int toId, double mouseX, double mouseY, double[] best, Wire found) {
        Node to = graph.get(toId);
        if (to == null || to == from) return found;
        return measure(canvas, from, fromPort, route(canvas, from, fromPort, to), toId, 0, 0,
                mouseX, mouseY, best, found);
    }

    private static Wire scanWaiting(NpcGraphCanvas canvas, Node node, int[] hang, double mouseX,
            double mouseY, double[] best, Wire found) {
        int size = NpcWirePoints321.routeSize(hang);
        if (size == 0) return found;
        double[] route = new double[(size + 1) * 2];
        route[0] = canvas.portScreenX(node, true);
        route[1] = canvas.portScreenY(node, true, hang[0]);
        int at = 2;
        for (int i = 0; i < size; i++) {
            route[at++] = canvas.screenX(NpcWirePoints321.routeX(hang, i));
            route[at++] = canvas.screenY(NpcWirePoints321.routeY(hang, i));
        }
        return measure(canvas, node, hang[0], route, -1, NpcWirePoints321.routeX(hang, size - 1),
                NpcWirePoints321.routeY(hang, size - 1), mouseX, mouseY, best, found);
    }

    private static Wire measure(NpcGraphCanvas canvas, Node from, int fromPort, double[] route,
            int toId, int endX, int endY, double mouseX, double mouseY, double[] best, Wire found) {
        int[] segment = new int[1];
        double[] ends = new double[4];
        double dist = nearestOnRoute(canvas, route, mouseX, mouseY, segment, ends);
        if (dist > best[0]) return found;
        best[0] = dist;
        return new Wire(from, fromPort, toId, endX, endY, segment[0], ends[0], ends[1], ends[2],
                ends[3]);
    }

    /**
     * The screen polyline one wire is drawn as: its port, every point of its route in order, and
     * the input port of the target - or, for a wire that waits, the port and the points it visits.
     */
    public static double[] route(NpcGraphCanvas canvas, Node from, int fromPort, Node to) {
        List<int[]> points = NpcWirePoints321.bends(from, fromPort, to == null ? -1 : to.id);
        double[] out = new double[(points.size() + 2) * 2];
        out[0] = canvas.portScreenX(from, true);
        out[1] = canvas.portScreenY(from, true, fromPort);
        int at = 2;
        for (int[] point : points) {
            out[at++] = canvas.screenX(point[0]);
            out[at++] = canvas.screenY(point[1]);
        }
        if (to != null) {
            out[at++] = canvas.portScreenX(to, false);
            out[at++] = canvas.portScreenY(to, false, 0);
        }
        return at == out.length ? out : java.util.Arrays.copyOf(out, at);
    }

    /**
     * Nearest point of a screen polyline to the cursor, and the segment it belongs to.
     *
     * @return the squared distance
     */
    public static double nearestOnRoute(NpcGraphCanvas canvas, double[] route, double mouseX,
            double mouseY, int[] segment, double[] ends) {
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 3 < route.length; i += 2) {
            double dist = segmentDistance(canvas, route[i], route[i + 1], route[i + 2],
                    route[i + 3], mouseX, mouseY);
            if (dist < best) {
                best = dist;
                segment[0] = i / 2;
                ends[0] = route[i];
                ends[1] = route[i + 1];
                ends[2] = route[i + 2];
                ends[3] = route[i + 3];
            }
        }
        return best;
    }

    /**
     * Graph coordinates for the point the author is making: the spot on the segment under the
     * cursor, not the cursor itself.
     *
     * <p>A point is put where the cable is, so it is never a dot beside the wire. The grid is
     * applied only when it does not pull the point off the cable; if it would, the exact spot is
     * kept, because "on the wire" matters more than "on the grid".</p>
     */
    public static int[] pointOn(NpcGraphCanvas canvas, Wire wire, double mouseX, double mouseY) {
        if (canvas == null || wire == null) return null;
        double[] near = nearestOnSegment(canvas, wire.sx1, wire.sy1, wire.sx2, wire.sy2, mouseX,
                mouseY);
        int gx = NpcWirePoints321.snap((int) Math.round(canvas.worldX(near[0])));
        int gy = NpcWirePoints321.snap((int) Math.round(canvas.worldY(near[1])));
        if (segmentDistance(canvas, wire.sx1, wire.sy1, wire.sx2, wire.sy2, canvas.screenX(gx),
                canvas.screenY(gy)) > 4.0) {
            gx = (int) Math.round(canvas.worldX(near[0]));
            gy = (int) Math.round(canvas.worldY(near[1]));
        }
        return new int[] { gx, gy };
    }

    /**
     * Distance from the cursor to one link, measured along its own points, and the segment it was
     * nearest to (which is where a new point would be inserted).
     */
    public static double linkDistance(NpcGraphCanvas canvas, NpcDialogueGraph graph, Node from,
            int fromPort, int toId, double mouseX, double mouseY, int[] segment) {
        Node to = graph.get(toId);
        if (to == null || to == from) return Double.MAX_VALUE;
        double[] ends = new double[4];
        return nearestOnRoute(canvas, route(canvas, from, fromPort, to), mouseX, mouseY, segment,
                ends);
    }

    /**
     * Squared distance from the cursor to one segment of a cable, along the curve the screen draws:
     * the same cubic {@code curve} uses, sampled in the same way, so what is clicked is what is
     * seen.
     */
    public static double segmentDistance(NpcGraphCanvas canvas, double x1, double y1, double x2,
            double y2, double mx, double my) {
        double[] near = nearestOnSegment(canvas, x1, y1, x2, y2, mx, my);
        double dx = mx - near[0];
        double dy = my - near[1];
        return dx * dx + dy * dy;
    }

    /** The nearest point of one drawn segment to the cursor, in screen space. */
    public static double[] nearestOnSegment(NpcGraphCanvas canvas, double x1, double y1, double x2,
            double y2, double mx, double my) {
        double bend = Math.max(25.0 * canvas.zoom, Math.abs(x2 - x1) * 0.45);
        double px = x1;
        double py = y1;
        double best = Double.MAX_VALUE;
        double bx = x1;
        double by = y1;
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
            double dist = ex * ex + ey * ey;
            if (dist < best) {
                best = dist;
                bx = px + dx * v;
                by = py + dy * v;
            }
            px = x;
            py = y;
        }
        return new double[] { bx, by };
    }

    // -- the port a wire is pulled out of -----------------------------------

    /** Half of the port square in pixels: the same shape as the ports on the cards, smaller. */
    public static int portHalf(NpcGraphCanvas canvas) {
        return Math.max(2, (int) Math.round(2.5 * canvas.zoom));
    }

    /** Screen x of the port of a point - just right of the dot, clear of the wire. */
    public static double portX(NpcGraphCanvas canvas, int pointX) {
        return canvas.screenX(pointX) + handleHalf(canvas) * 2 + portHalf(canvas) + 3;
    }

    public static double portY(NpcGraphCanvas canvas, int pointY) {
        return canvas.screenY(pointY);
    }

    /** The point whose port is under the cursor, or null. */
    public static int[] portAt(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            double mouseX, double mouseY) {
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (canvas == null || graph == null) return null;
        double radius = Math.max(9.0, 6.0 * canvas.zoom);
        int[] best = null;
        double bestSq = radius * radius;
        for (int[] point : NpcWirePoints321.points(graph)) {
            double dx = mouseX - portX(canvas, point[0]);
            double dy = mouseY - portY(canvas, point[1]);
            double distSq = dx * dx + dy * dy;
            if (distSq <= bestSq) {
                bestSq = distSq;
                best = point;
            }
        }
        return best;
    }

    /**
     * True when the cursor is really on the dot: the radius here is the dot plus a couple of
     * pixels, not the forgiving grab radius. Everything outside it is wire, not point, so a click
     * next to a point makes another point instead of deleting the one that is there.
     */
    public static boolean onDot(NpcDialogueGraphScreen screen, NpcDialogueGraph graph, int[] point,
            double mouseX, double mouseY) {
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (canvas == null || point == null) return false;
        double radius = Math.max(6.0, 3.5 * canvas.zoom + 3.0);
        double dx = mouseX - canvas.screenX(point[0]);
        double dy = mouseY - canvas.screenY(point[1]);
        return dx * dx + dy * dy <= radius * radius;
    }

    /** How soon a second press counts as a double click. */
    public static final long DOUBLE_MS = 350L;

    /** How far the second press may land from the first. */
    public static final double DOUBLE_SLOP = 6.0;

    /**
     * True when this press is the second one of a double click.
     *
     * <p>{@code dragged} is what makes it honest: if the press before this one turned into a drag -
     * the author moved a point or pulled a wire - then the gesture was over, and the next press is
     * a new gesture. Without that, moving a point and pressing it again a moment later landed as
     * "double click", which made a copy of the point instead of picking it up.</p>
     */
    public static boolean doubleClick(long lastClick, long now, double dx, double dy,
            boolean dragged) {
        if (dragged || lastClick <= 0L) return false;
        return now - lastClick <= DOUBLE_MS && Math.abs(dx) <= DOUBLE_SLOP
                && Math.abs(dy) <= DOUBLE_SLOP;
    }

    // -- gesture decision ----------------------------------------------------

    /** Nothing happens here; the click goes on to the screen's own handler. */
    public static final int ACT_NONE = 0;
    /** A wire starts being pulled out of the point's port. */
    public static final int ACT_PULL = 1;
    /** The point under the cursor goes away. */
    public static final int ACT_REMOVE = 2;
    /** A new point is put on the wire under the cursor. */
    public static final int ACT_ADD = 3;
    /** The point under the cursor starts being dragged. */
    public static final int ACT_GRAB = 4;

    /**
     * What a press on the graph means. All of the precedence lives here, in one place, so the
     * order can be checked without a game: the screen's mixin only carries the answer out.
     *
     * <p>The order is the whole point of build 326. A double click removes a point only when the
     * cursor is on the dot itself ({@code onDot}); anywhere else on a wire it makes a new point,
     * even when another point stands a few pixels away. Reading "near a point" as "on the point"
     * is what made the segment that leads into a point refuse to take one.
     *
     * @param again     second press of a double click
     * @param button    0 left, 1 right
     * @param hasPoint  a point inside the forgiving grab radius
     * @param onDot     the cursor is on that point's dot, not merely near it
     * @param hasPort   a point port under the cursor
     * @param portFree  that port belongs to the point itself, not being the dot's inner circle
     * @param hasWire   a wire (carrying or waiting) runs under the cursor
     */
    public static int decide(boolean again, int button, boolean hasPoint, boolean onDot,
            boolean hasPort, boolean portFree, boolean hasWire) {
        if (button == 1) return hasPoint || hasPort ? ACT_REMOVE : ACT_NONE;   // right click
        if (button != 0) return ACT_NONE;

        if (!again) {
            if (hasPort && portFree) return ACT_PULL;   // pull needs a deliberate single press
            if (hasPoint) return ACT_GRAB;
            return ACT_NONE;
        }
        if (onDot) return ACT_REMOVE;                   // only the dot itself means "remove"
        if (hasWire) return ACT_ADD;                    // the wire wins over a nearby point
        if (hasPoint) return ACT_REMOVE;
        return ACT_NONE;
    }

    /** True when the cursor is on the port of this point rather than on the dot itself. */
    public static boolean overPort(NpcDialogueGraphScreen screen, NpcDialogueGraph graph,
            int[] point, double mouseX, double mouseY) {
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (canvas == null || point == null) return false;
        double px = portX(canvas, point[0]);
        double py = portY(canvas, point[1]);
        double dotX = canvas.screenX(point[0]);
        double dotY = canvas.screenY(point[1]);
        double toPort = (mouseX - px) * (mouseX - px) + (mouseY - py) * (mouseY - py);
        double toDot = (mouseX - dotX) * (mouseX - dotX) + (mouseY - dotY) * (mouseY - dotY);
        double radius = Math.max(9.0, 6.0 * canvas.zoom);
        return toPort <= radius * radius && toPort < toDot;
    }

    // -- drawing ------------------------------------------------------------

    /**
     * The points themselves, with the port a wire is pulled out of.
     *
     * @param mouseX    cursor, for the hover highlight
     * @param aimTarget node the point being aimed at would go to, or -1
     */
    public static void drawPoints(NpcDialogueGraphScreen screen, GuiGraphics graphics,
            NpcDialogueGraph graph, double mouseX, double mouseY, boolean dragging,
            int aimTarget) {
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (canvas == null || graph == null) return;
        int half = handleHalf(canvas);
        int port = portHalf(canvas);
        double radius = hitRadius(canvas);
        double portRadius = Math.max(9.0, 6.0 * canvas.zoom);
        for (int[] point : NpcWirePoints321.points(graph)) {
            double x = canvas.screenX(point[0]);
            double y = canvas.screenY(point[1]);
            if (x < canvas.left - 24 || x > canvas.right + 24) continue;
            if (y < canvas.top - 24 || y > canvas.bottom + 24) continue;
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

            // The port: a small white square, the same shape the cards use for theirs.
            double px = portX(canvas, point[0]);
            double py = portY(canvas, point[1]);
            double pdx = mouseX - px;
            double pdy = mouseY - py;
            boolean hot = pdx * pdx + pdy * pdy <= portRadius * portRadius;
            int px0 = (int) Math.round(px) - port;
            int py0 = (int) Math.round(py) - port;
            graphics.fill(px0 - 1, py0 - 1, px0 + port * 2 + 1, py0 + port * 2 + 1,
                    COL_HANDLE_EDGE);
            graphics.fill(px0, py0, px0 + port * 2, py0 + port * 2,
                    hot ? COL_HANDLE_HOT : COL_PORT);
        }
        if (aimTarget >= 0) {
            Node target = graph.get(aimTarget);
            if (target != null) frame(canvas, graphics, target);
        }
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

    // -- coordinates --------------------------------------------------------

    /** Graph coordinates of a screen position, on the model's grid. */
    public static int graphX(NpcGraphCanvas canvas, double mouseX) {
        return NpcWirePoints321.snap((int) Math.round(canvas.worldX(mouseX)));
    }

    public static int graphY(NpcGraphCanvas canvas, double mouseY) {
        return NpcWirePoints321.snap((int) Math.round(canvas.worldY(mouseY)));
    }
}
