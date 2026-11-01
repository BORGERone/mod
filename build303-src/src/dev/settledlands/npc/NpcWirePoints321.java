package dev.settledlands.npc;

import dev.settledlands.npc.NpcDialogueGraph.Node;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Build 323: a reroute point is a hub on the canvas, not a decoration.
 *
 * <p>Build 321 made points purely visual and Build 322 let a wire fall onto one - straight into the
 * target the point happened to sit on, which is exactly the surprise: the wire was gone to a node
 * nobody asked for. Now a point has two kinds of wires:</p>
 *
 * <ul>
 *   <li><b>carrying wires</b> - wires that pass through the point. The stock connection is
 *       untouched; the cable is drawn through the point instead of going straight, and the point
 *       never changes where the wire goes.</li>
 *   <li><b>waiting wires</b> - wires whose end was dropped on the point. They carry no connection
 *       at all: the wire simply stops at the point, exactly as the author dropped it. Nothing is
 *       connected behind their back.</li>
 * </ul>
 *
 * <p>The continuation is a separate, deliberate step: the point is dragged onto a target node and
 * every wire at that point - carrying and waiting - goes there. Until then a waiting wire waits,
 * which is the whole point of a hub: the author decides the destination, not the node the point was
 * made on.</p>
 *
 * <p>Where the points live. A graph node has no spare field, so the records of every wire that
 * leaves a node are kept as text in one string field of that node, and the field is picked so that
 * the node's own type never reads it: {@code animBone} for every type except an animation node,
 * {@code mobilityGroupId} for an animation node. Both are saved with the graph, so the points
 * travel with it, through Ctrl+C/Ctrl+V, undo and a world reload. Nothing outside this class ever
 * reads those fields as points.</p>
 *
 * <p>The text. {@code SLP2} then one entry per wire. A carrying wire is
 * {@code p<port>t<target>:x,y x,y ...} - the bends of that one wire, in order. A waiting wire is
 * {@code h<port>@x,y} - one entry, because it has no target to write. Build 321/322 wrote
 * {@code SLP1} with carrying wires only, and that text is still read, so a graph made with the old
 * build keeps its bends. A field value without either marker is not ours and is never overwritten
 * and never parsed.</p>
 */
public final class NpcWirePoints321 {

    /** First token of the text this build writes. */
    public static final String MARK = "SLP2";

    /** First token of the text Build 321/322 wrote - still read. */
    public static final String MARK_OLD = "SLP1";

    /** Points are placed on the same eight-unit grid the canvas snaps nodes to. */
    public static final int GRID = 8;

    /** Parsed text of a node, kept until the field changes under it. */
    private static final Map<Node, Object[]> CACHE = new WeakHashMap<>();

    private NpcWirePoints321() {
    }

    /** A wire passing through a point. */
    public static final class Bend {
        public final Node from;
        public final int fromPort;
        public final int toId;
        public final int x;
        public final int y;

        Bend(Node from, int fromPort, int toId, int x, int y) {
            this.from = from;
            this.fromPort = fromPort;
            this.toId = toId;
            this.x = x;
            this.y = y;
        }
    }

    /** A wire whose end was dropped on a point and which goes nowhere yet. */
    public static final class Hang {
        public final Node from;
        public final int fromPort;
        public final int x;
        public final int y;

        Hang(Node from, int fromPort, int x, int y) {
            this.from = from;
            this.fromPort = fromPort;
            this.x = x;
            this.y = y;
        }
    }

    /** What one node's text holds. Bends are keyed {@code port:target}, hangs are {@code port,x,y}. */
    private static final class Data {
        final Map<String, List<int[]>> bends = new LinkedHashMap<>();
        final List<int[]> hangs = new ArrayList<>();

        boolean empty() {
            return bends.isEmpty() && hangs.isEmpty();
        }
    }

    // -- storage ------------------------------------------------------------

    /** The field a node keeps its wire records in. See the class comment. */
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

    private static boolean ours(String raw) {
        return raw != null
                && (raw.equals(MARK) || raw.equals(MARK_OLD)
                        || raw.startsWith(MARK + "|") || raw.startsWith(MARK_OLD + "|"));
    }

    private static String key(int fromPort, int toId) {
        return fromPort + ":" + toId;
    }

    private static int keyPort(String key) {
        int colon = key.indexOf(':');
        return colon < 0 ? 0 : number(key.substring(0, colon), 0);
    }

    private static int keyTarget(String key) {
        int colon = key.indexOf(':');
        return colon < 0 ? -1 : number(key.substring(colon + 1), -1);
    }

    private static Data read(Node node) {
        String raw = field(node);
        Object[] cached = CACHE.get(node);
        if (cached != null && Objects.equals(raw, cached[0])) {
            return (Data) cached[1];
        }
        Data data = new Data();
        // Both markers are read, so a graph made with Build 321/322 keeps its bends.
        String body = null;
        if (raw != null && raw.startsWith(MARK + "|")) {
            body = raw.substring(MARK.length() + 1);
        } else if (raw != null && raw.startsWith(MARK_OLD + "|")) {
            body = raw.substring(MARK_OLD.length() + 1);
        }
        if (body != null) {
            for (String entry : body.split("\\|")) {
                if (entry.isEmpty()) continue;
                if (entry.startsWith("h")) {
                    int at = entry.indexOf('@');
                    if (at < 0) continue;
                    int port = number(entry.substring(1, at), 0);
                    int comma = entry.indexOf(',', at);
                    if (comma < 0) continue;
                    data.hangs.add(new int[] { port, number(entry.substring(at + 1, comma), 0),
                            number(entry.substring(comma + 1), 0) });
                    continue;
                }
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
                if (!points.isEmpty()) data.bends.put(key(port, to), points);
            }
        }
        CACHE.put(node, new Object[] { raw, data });
        return data;
    }

    private static String format(Data data) {
        if (data.empty()) return "";
        StringBuilder out = new StringBuilder(MARK);
        for (Map.Entry<String, List<int[]>> entry : data.bends.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            int to = keyTarget(entry.getKey());
            if (to < 0) continue;
            out.append('|').append('p').append(keyPort(entry.getKey())).append('t').append(to)
                    .append(':');
            boolean first = true;
            for (int[] point : entry.getValue()) {
                if (!first) out.append(' ');
                out.append(point[0]).append(',').append(point[1]);
                first = false;
            }
        }
        for (int[] hang : data.hangs) {
            out.append('|').append('h').append(hang[0]).append('@')
                    .append(hang[1]).append(',').append(hang[2]);
        }
        return out.toString();
    }

    private static void write(Node node, Data data) {
        String next = format(data);
        String current = field(node);
        if (next.isEmpty()) {
            // Nothing left to store: give the field back, but only if it was ours to begin with.
            if (ours(current)) field(node, "");
            CACHE.put(node, new Object[] { field(node), data });
            return;
        }
        if (current != null && !current.isEmpty() && !ours(current)) {
            // Somebody else's text is in the field. Do not destroy it: the records stay for this
            // session and are simply not saved.
            CACHE.put(node, new Object[] { current, data });
            return;
        }
        field(node, next);
        CACHE.put(node, new Object[] { next, data });
    }

    private static int number(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    // -- links --------------------------------------------------------------

    /**
     * True when the node still has a link to {@code toId} on {@code fromPort}.
     *
     * <p>This is what makes a carrying wire real: a bend whose wire was rewired or cut belongs to no
     * cable any more, and is dropped by {@link #points} instead of hanging around invisibly.</p>
     */
    public static boolean linkExists(Node node, int fromPort, int toId) {
        if (node == null || toId < 0) return false;
        if (fromPort == 1) return node.falseNext == toId;
        return node.next == toId || node.varInputNode == toId
                || node.choices.contains(toId) || node.triggers.contains(toId)
                || node.randomBranches.contains(toId);
    }

    // -- reading what is drawn ----------------------------------------------

    /** Bends of one wire, in order. Read only - this is the drawing path. */
    public static List<int[]> bends(Node from, int fromPort, int toId) {
        if (from == null) return List.of();
        return read(from).bends.getOrDefault(key(fromPort, toId), List.of());
    }

    /** Waiting wires of a node: {@code {port, x, y}}, for the stub the editor draws. */
    public static List<int[]> hangs(Node from) {
        if (from == null) return List.of();
        return read(from).hangs;
    }

    /** Every point of the graph, in the order the nodes were written. Stale bends are dropped. */
    public static List<int[]> points(NpcDialogueGraph graph) {
        List<Bend> bends = new ArrayList<>();
        List<Hang> hangs = new ArrayList<>();
        collect(graph, bends, hangs, true);
        Set<Long> seen = new LinkedHashSet<>();
        List<int[]> out = new ArrayList<>();
        for (Bend bend : bends) {
            if (seen.add(pack(bend.x, bend.y))) out.add(new int[] { bend.x, bend.y });
        }
        for (Hang hang : hangs) {
            if (seen.add(pack(hang.x, hang.y))) out.add(new int[] { hang.x, hang.y });
        }
        return out;
    }

    /** True when a point is already within {@code tolerance} graph units of this place. */
    public static boolean pointWithin(NpcDialogueGraph graph, int x, int y, int tolerance) {
        for (int[] point : points(graph)) {
            if (Math.abs(point[0] - x) <= tolerance && Math.abs(point[1] - y) <= tolerance) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasPoint(NpcDialogueGraph graph, int x, int y) {
        for (int[] point : points(graph)) {
            if (point[0] == x && point[1] == y) return true;
        }
        return false;
    }

    /** Every carrying wire of the point. */
    public static List<Bend> bendsAt(NpcDialogueGraph graph, int x, int y) {
        List<Bend> bends = new ArrayList<>();
        collect(graph, bends, new ArrayList<>(), false);
        List<Bend> out = new ArrayList<>();
        for (Bend bend : bends) {
            if (bend.x == x && bend.y == y) out.add(bend);
        }
        return out;
    }

    /** Every waiting wire of the point. */
    public static List<Hang> hangsAt(NpcDialogueGraph graph, int x, int y) {
        List<Hang> hangs = new ArrayList<>();
        collect(graph, new ArrayList<>(), hangs, false);
        List<Hang> out = new ArrayList<>();
        for (Hang hang : hangs) {
            if (hang.x == x && hang.y == y) out.add(hang);
        }
        return out;
    }

    /**
     * Walks every node once.
     *
     * @param prune drop carrying wires whose connection no longer exists, so a point never outlives
     *              the cable it was made on
     */
    private static void collect(NpcDialogueGraph graph, List<Bend> bends, List<Hang> hangs,
            boolean prune) {
        if (graph == null || graph.nodes == null) return;
        for (Node node : graph.nodes.values()) {
            Data data = read(node);
            boolean changed = false;
            List<String> dead = new ArrayList<>();
            for (Map.Entry<String, List<int[]>> entry : data.bends.entrySet()) {
                int port = keyPort(entry.getKey());
                int to = keyTarget(entry.getKey());
                if (prune && !linkExists(node, port, to)) {
                    dead.add(entry.getKey());
                    continue;
                }
                for (int[] point : entry.getValue()) {
                    bends.add(new Bend(node, port, to, point[0], point[1]));
                }
            }
            if (!dead.isEmpty()) {
                for (String key : dead) data.bends.remove(key);
                changed = true;
            }
            for (int[] hang : data.hangs) {
                hangs.add(new Hang(node, hang[0], hang[1], hang[2]));
            }
            if (changed) write(node, data);
        }
    }

    private static long pack(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    // -- editing ------------------------------------------------------------

    /** Puts a point on a wire: a bend, with no change to the connection. */
    public static void addBend(Node from, int fromPort, int toId, int x, int y) {
        if (from == null || toId < 0) return;
        Data data = read(from);
        List<int[]> points = data.bends.computeIfAbsent(key(fromPort, toId),
                unused -> new ArrayList<>());
        int sx = snap(x);
        int sy = snap(y);
        for (int[] point : points) {
            if (point[0] == sx && point[1] == sy) return;
        }
        points.add(new int[] { sx, sy });
        write(from, data);
    }

    /**
     * Ends one wire of a node on a point. Nothing is connected: the wire waits at the point until
     * the point is given a target.
     */
    public static void attach(Node from, int fromPort, int x, int y) {
        if (from == null) return;
        Data data = read(from);
        int sx = snap(x);
        int sy = snap(y);
        for (int[] hang : data.hangs) {
            if (hang[0] == fromPort && hang[1] == sx && hang[2] == sy) return;
        }
        data.hangs.add(new int[] { fromPort, sx, sy });
        write(from, data);
    }

    /** Moves every record of the point: all carrying and all waiting wires follow it. */
    public static boolean movePoint(NpcDialogueGraph graph, int x, int y, int nx, int ny) {
        if (graph == null || graph.nodes == null) return false;
        int tx = snap(nx);
        int ty = snap(ny);
        if (tx == x && ty == y) return false;
        boolean any = false;
        for (Node node : graph.nodes.values()) {
            Data data = read(node);
            boolean changed = false;
            for (List<int[]> points : data.bends.values()) {
                for (int[] point : points) {
                    if (point[0] == x && point[1] == y) {
                        point[0] = tx;
                        point[1] = ty;
                        changed = true;
                    }
                }
            }
            for (int[] hang : data.hangs) {
                if (hang[1] == x && hang[2] == y) {
                    hang[1] = tx;
                    hang[2] = ty;
                    changed = true;
                }
            }
            if (changed) {
                write(node, data);
                any = true;
            }
        }
        return any;
    }

    /** Removes the point: the carrying wires go straight again, the waiting wires are let go. */
    public static boolean removePoint(NpcDialogueGraph graph, int x, int y) {
        if (graph == null || graph.nodes == null) return false;
        boolean any = false;
        for (Node node : graph.nodes.values()) {
            Data data = read(node);
            boolean changed = false;
            List<String> dead = new ArrayList<>();
            for (Map.Entry<String, List<int[]>> entry : data.bends.entrySet()) {
                List<int[]> points = entry.getValue();
                for (int i = points.size() - 1; i >= 0; i--) {
                    if (points.get(i)[0] == x && points.get(i)[1] == y) {
                        points.remove(i);
                        changed = true;
                    }
                }
                if (points.isEmpty()) dead.add(entry.getKey());
            }
            for (String key : dead) data.bends.remove(key);
            for (int i = data.hangs.size() - 1; i >= 0; i--) {
                int[] hang = data.hangs.get(i);
                if (hang[1] == x && hang[2] == y) {
                    data.hangs.remove(i);
                    changed = true;
                }
            }
            if (changed) {
                write(node, data);
                any = true;
            }
        }
        return any;
    }

    /**
     * Turns one waiting wire into a real one to {@code toId}. Used when a point is given a target:
     * from that moment the wire carries the connection instead of stopping at the point.
     */
    public static boolean promote(Node from, int fromPort, int x, int y, int toId) {
        if (from == null || toId < 0) return false;
        Data data = read(from);
        boolean found = false;
        for (int i = data.hangs.size() - 1; i >= 0; i--) {
            int[] hang = data.hangs.get(i);
            if (hang[0] == fromPort && hang[1] == x && hang[2] == y) {
                data.hangs.remove(i);
                found = true;
            }
        }
        if (!found) return false;
        data.bends.computeIfAbsent(key(fromPort, toId), unused -> new ArrayList<>())
                .add(new int[] { x, y });
        write(from, data);
        return true;
    }

    /**
     * Rewrites the target of one carrying wire, keeping the whole route where it is: every bend of
     * the old wire moves to the new one, so the cable keeps its shape.
     */
    public static boolean retarget(Node from, int fromPort, int oldTarget, int newTarget) {
        if (from == null || oldTarget < 0 || newTarget < 0 || oldTarget == newTarget) return false;
        Data data = read(from);
        List<int[]> points = data.bends.remove(key(fromPort, oldTarget));
        if (points == null) return false;
        data.bends.computeIfAbsent(key(fromPort, newTarget), unused -> new ArrayList<>())
                .addAll(points);
        write(from, data);
        return true;
    }

    public static int snap(int value) {
        return Math.round((float) value / GRID) * GRID;
    }
}
