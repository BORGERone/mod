package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcWirePoints321;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Build 321: where the reroute points are, and what the mouse does with them.
 *
 * <p>This class does not draw cables - the screen's own routine does, one curve per segment, so a
 * bent cable looks exactly like a straight one: same thickness, same colour, same dashes, same
 * behaviour at any zoom. Here live the handle squares, the hit tests and the coordinate conversion;
 * the mixin calls them.</p>
 *
 * <p>Hit tests use the canvas' own cable test with the same port numbers the screen draws with:
 * port 0 is the ordinary output, port 1 the "false" output of a condition. A click that lands on a
 * node or on a port is not a wire, so nothing is added there.</p>
 */
public final class NpcWirePointsUi321 {

    /** Half the handle in screen pixels at zoom 1, and the smallest it may get. */
    public static final int HANDLE = 3;

    public static final int HANDLE_MIN = 3;

    private static final int COL_HANDLE = 0xFFC8C8C8;

    private static final int COL_HANDLE_HOT = 0xFFFFFFFF;

    private static final int COL_HANDLE_EDGE = 0xFF2A2A2A;

    /** The wire a bend belongs to. */
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
        return Math.max(HANDLE_MIN, (int) Math.round(HANDLE * canvas.zoom));
    }

    public static void drawHandles(NpcDialogueGraphScreen screen, GuiGraphics graphics,
            NpcDialogueGraph graph, double mouseX, double mouseY) {
        drawHandles(screen, graphics, graph, mouseX, mouseY, false);
    }

    /**
     * @param dropping true while a wire is being dragged: the point under the cursor is highlighted
     *                 as a place the wire can be released onto
     */
    public static void drawHandles(NpcDialogueGraphScreen screen, GuiGraphics graphics,
            NpcDialogueGraph graph, double mouseX, double mouseY, boolean dropping) {
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (canvas == null || graph == null) return;
        int half = handleHalf(canvas);
        for (NpcWirePoints321.Handle handle : NpcWirePoints321.handles(graph)) {
            double x = canvas.screenX(handle.x);
            double y = canvas.screenY(handle.y);
            if (x < canvas.left - 16 || x > canvas.right + 16) continue;
            if (y < canvas.top - 16 || y > canvas.bottom + 16) continue;
            boolean near = Math.abs(mouseX - x) <= half + 2 && Math.abs(mouseY - y) <= half + 2;
            int grow = near && dropping ? half : 0;
            int left = (int) Math.round(x) - half;
            int top = (int) Math.round(y) - half;
            graphics.fill(left - 1, top - 1, left + half * 2 + 1, top + half * 2 + 1,
                    COL_HANDLE_EDGE);
            graphics.fill(left - grow, top - grow, left + half * 2 + grow, top + half * 2 + grow,
                    near ? COL_HANDLE_HOT : COL_HANDLE);
        }
    }

    /** The bend under the cursor, or null. */
    public static NpcWirePoints321.Handle handleAt(NpcDialogueGraphScreen screen,
            NpcDialogueGraph graph, double mouseX, double mouseY) {
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (canvas == null || graph == null) return null;
        int half = handleHalf(canvas) + 2;
        for (NpcWirePoints321.Handle handle : NpcWirePoints321.handles(graph)) {
            if (Math.abs(mouseX - canvas.screenX(handle.x)) <= half
                    && Math.abs(mouseY - canvas.screenY(handle.y)) <= half) {
                return handle;
            }
        }
        return null;
    }

    /** The wire under the cursor, or null. */
    public static Wire wireAt(NpcDialogueGraphScreen screen, NpcDialogueGraph graph, double mouseX,
            double mouseY) {
        NpcGraphCanvas canvas = screen.settledlands$canvas();
        if (canvas == null || graph == null || graph.nodes == null) return null;
        if (!canvas.contains(mouseX, mouseY)) return null;
        if (canvas.hit(graph, mouseX, mouseY) >= 0) return null;
        if (canvas.hitPort(graph, mouseX, mouseY, true) >= 0) return null;
        if (canvas.hitPort(graph, mouseX, mouseY, false) >= 0) return null;

        for (Node node : graph.nodes.values()) {
            if (node.type == NpcDialogueGraph.IF && node.falseNext >= 0
                    && hit(canvas, graph, node, 1, node.falseNext, mouseX, mouseY)) {
                return new Wire(node, 1, node.falseNext);
            }
            if (node.next >= 0 && hit(canvas, graph, node, 0, node.next, mouseX, mouseY)) {
                return new Wire(node, 0, node.next);
            }
            for (int to : node.choices) {
                if (hit(canvas, graph, node, 0, to, mouseX, mouseY)) return new Wire(node, 0, to);
            }
            for (int to : node.randomBranches) {
                if (hit(canvas, graph, node, 0, to, mouseX, mouseY)) return new Wire(node, 0, to);
            }
            for (int to : node.triggers) {
                if (hit(canvas, graph, node, 0, to, mouseX, mouseY)) return new Wire(node, 0, to);
            }
        }
        return null;
    }

    private static boolean hit(NpcGraphCanvas canvas, NpcDialogueGraph graph, Node from,
            int fromPort, int toId, double mouseX, double mouseY) {
        Node to = graph.get(toId);
        if (to == null || to == from) return false;
        return canvas.hitCable(from, fromPort, to, 0, mouseX, mouseY);
    }

    /** Graph coordinates of a screen position, on the model's grid. */
    public static int graphX(NpcGraphCanvas canvas, double mouseX) {
        return NpcWirePoints321.snap((int) Math.round(canvas.worldX(mouseX)));
    }

    public static int graphY(NpcGraphCanvas canvas, double mouseY) {
        return NpcWirePoints321.snap((int) Math.round(canvas.worldY(mouseY)));
    }
}
