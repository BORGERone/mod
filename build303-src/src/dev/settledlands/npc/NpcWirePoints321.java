package dev.settledlands.npc;

import dev.settledlands.npc.NpcDialogueGraph.Node;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Build 321: reroute points on a cable, the way a blueprint lets a wire be tidied up.
 *
 * <p>A point is a purely visual bend in one wire: the cable is drawn through it instead of going
 * straight from the output port to the input port. Nothing about the connection changes - the
 * source still points at the same target, the server reads the same fields, and the dialogue runs
 * exactly as before. The only thing a point does is make a graph readable when ten wires cross each
 * other.</p>
 *
 * <p>Where the points live. A graph node has no spare field, so the points of every wire that
 * leaves a node are kept as text in one string field of that node, and the field is picked so that
 * the node's own type never reads it: {@code animBone} for every type except an animation node,
 * {@code mobilityGroupId} for an animation node. Both are saved with the graph, so the points travel
 * with it, through Ctrl+C/Ctrl+V, undo, and a world reload. The text starts with
 * {@link #MARK}; a field value without the marker is never treated as points and never overwritten,
 * so nothing that was already in the field can be damaged by this.</p>
 *
 * <p>A wire is identified by the node it leaves, the port it leaves, and the node it arrives at -
 * the same triple the canvas uses to hit-test a cable. Two links from one port to the same node
 * draw on top of each other anyway, so sharing the bend between them is invisible.</p>
 */
public final class NpcWirePoints321 {

    /** First token of the stored text. Anything else in the field is not ours. */
    public static final String MARK = "SLP1";

    /** Points are placed on the same eight-unit grid the canvas snaps nodes to. */
    public static final int GRID = 8;

    /** Nodes whose cable the field of the moment holds. Only these are ever parsed. */
    private static final Map<Node, Object[]> CACHE = new WeakHashMap<>();

    private NpcWirePoints321() {
    }

    /** One bend, for drawing and for the hit test. */
    public static final class Handle {
        public final Node from;
        public final int fromPort;
        public final int toId;
        public final int index;
        public final int x;
        public final int y;

        Handle(Node from, int fromPort, int toId, int index, int x, int y) {
            this.from = from;
            this.fromPort = fromPort;
            this.toId = toId;
            this.index = index;
            this.x = x;
            this.y = y;
        }
    }

    // -- storage ------------------------------------------------------------

    /** The field a node keeps its cable bends in. See the class comment. */
    private static String field(Node node) {
        return node.type == NpcDialogueGraph.ANIMATION ? node.mobilityGroupId : node.animBone;
    }

    private static void field(Node node, String value) {
        if (node.type == NpcDialogueGraph.ANIMATION) {
            node.mobilityGroupId = value;
        } else {
            node.animBone = value;
        }
        CACHE.remove(node);
    }

    private static String key(int fromPort, int toId) {
        return fromPort + ":" + toId;
    }

    /** Wire key to its points, in the order they bend the cable. */
    private static Map<String, List<int[]>> read(Node node) {
        String raw = field(node);
        Object[] cached = CACHE.get(node);
        if (cached != null && raw.equals(cached[0])) {
            @SuppressWarnings("unchecked")
            Map<String, List<int[]>> hit = (Map<String, List<int[]>>) cached[1];
            return hit;
        }
        Map<String, List<int[]>> out = new LinkedHashMap<>();
        if (raw != null && raw.startsWith(MARK + "|")) {
            for (String entry : raw.split("\\|")) {
                int colon = entry.indexOf(':');
                if (colon < 0) continue;
                String head = entry.substring(0, colon);
                if (!head.startsWith("p") || head.indexOf('t') < 0) continue;
                int t = head.indexOf('t');
                int port = number(head.substring(1, t), 0);
                int to = number(head.substring(t + 1), -1);
                if (to < 0) continue;
                List<int[]> points = new ArrayList<>();
                for (String pair : entry.substring(colon + 1).trim().split(" ")) {
                    int comma = pair.indexOf(',');
                    if (comma < 0) continue;
                    points.add(new int[] { number(pair.substring(0, comma), 0),
                            number(pair.substring(comma + 1), 0) });
                }
                if (!points.isEmpty()) out.put(key(port, to), points);
            }
        }
        CACHE.put(node, new Object[] { raw, out });
        return out;
    }

    private static void write(Node node, Map<String, List<int[]>> all) {
        StringBuilder out = new StringBuilder(MARK);
        for (Map.Entry<String, List<int[]>> entry : all.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            int colon = entry.getKey().indexOf(':');
            int port = number(entry.getKey().substring(0, colon), 0);
            int to = number(entry.getKey().substring(colon + 1), -1);
            if (to < 0) continue;
            out.append('|').append('p').append(port).append('t').append(to).append(':');
            boolean first = true;
            for (int[] point : entry.getValue()) {
                if (!first) out.append(' ');
                out.append(point[0]).append(',').append(point[1]);
                first = false;
            }
        }
        // Nothing left to store: give the field back, but only if it was ours to begin with.
        if (out.length() == MARK.length()) {
            String current = field(node);
            if (current != null && current.startsWith(MARK)) field(node, "");
            return;
        }
        String current = field(node);
        if (current != null && !current.isEmpty() && !current.startsWith(MARK)) {
            // Somebody else's text is in the field; do not destroy it. The bends stay for this
            // session in the cache and are simply not saved.
            CACHE.put(node, new Object[] { current, all });
            return;
        }
        field(node, out.toString());
        CACHE.put(node, new Object[] { out.toString(), all });
    }

    private static int number(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    // -- public model -------------------------------------------------------

    public static boolean has(Node from, int fromPort, int toId) {
        return from != null && !read(from).getOrDefault(key(fromPort, toId), List.of()).isEmpty();
    }

    public static List<int[]> points(Node from, int fromPort, int toId) {
        if (from == null) return List.of();
        return read(from).getOrDefault(key(fromPort, toId), List.of());
    }

    /** Adds a bend at the end of the wire's list, on the eight-unit grid. */
    public static void add(Node from, int fromPort, int toId, int x, int y) {
        if (from == null || toId < 0) return;
        Map<String, List<int[]>> all = read(from);
        all.computeIfAbsent(key(fromPort, toId), unused -> new ArrayList<>())
                .add(new int[] { snap(x), snap(y) });
        write(from, all);
    }

    public static void moveTo(Node from, int fromPort, int toId, int index, int x, int y) {
        if (from == null) return;
        Map<String, List<int[]>> all = read(from);
        List<int[]> points = all.get(key(fromPort, toId));
        if (points == null || index < 0 || index >= points.size()) return;
        points.set(index, new int[] { snap(x), snap(y) });
        write(from, all);
    }

    public static void removeAt(Node from, int fromPort, int toId, int index) {
        if (from == null) return;
        Map<String, List<int[]>> all = read(from);
        List<int[]> points = all.get(key(fromPort, toId));
        if (points == null || index < 0 || index >= points.size()) return;
        points.remove(index);
        if (points.isEmpty()) all.remove(key(fromPort, toId));
        write(from, all);
    }

    /** Every bend in the graph, for the handles the editor draws. */
    public static List<Handle> handles(NpcDialogueGraph graph) {
        List<Handle> out = new ArrayList<>();
        if (graph == null || graph.nodes == null) return out;
        for (Node node : graph.nodes.values()) {
            for (Map.Entry<String, List<int[]>> entry : read(node).entrySet()) {
                int colon = entry.getKey().indexOf(':');
                int port = number(entry.getKey().substring(0, colon), 0);
                int to = number(entry.getKey().substring(colon + 1), -1);
                List<int[]> points = entry.getValue();
                for (int i = 0; i < points.size(); i++) {
                    out.add(new Handle(node, port, to, i, points.get(i)[0], points.get(i)[1]));
                }
            }
        }
        return out;
    }

    public static int snap(int value) {
        return Math.round((float) value / GRID) * GRID;
    }
}
