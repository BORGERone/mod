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
 * Build 325: a point is a hub on the canvas, and a wire may run through a run of them.
 *
 * <p>A wire either carries a connection or it waits. A point sits on a wire either way, and a wire
 * may pass through any number of points in a row - node, point, point, node - so the canvas can be
 * tidied without adding nodes to the dialogue.</p>
 *
 * <ul>
 *   <li><b>carrying wires</b> - wires that pass through points. The stock connection is untouched;
 *       the cable is drawn through the points instead of going straight, and a point never changes
 *       where the wire goes.</li>
 *   <li><b>waiting wires</b> - wires whose end was dropped on a point. They carry no connection at
 *       all: the wire stops at the point, exactly as the author dropped it. Nothing is connected
 *       behind their back. Points may be added along a waiting wire as well, in any number.</li>
 * </ul>
 *
 * <p>The continuation is a separate, deliberate step: a wire is pulled out of a point's port and
 * dropped on a node, and every wire at that point - carrying and waiting - goes there. Until then a
 * waiting wire waits, which is the whole point of a hub: the author decides the destination, not the
 * node the point was made on.</p>
 *
 * <p>Where the points live. A graph node has no spare field, so the records of every wire that
 * leaves a node are kept as text in one string field of that node, and the field is picked so that
 * the node's own type never reads it: {@code animBone} for every type except an animation node,
 * {@code mobilityGroupId} for an animation node. Both are saved with the graph, so the points
 * travel with it, through Ctrl+C/Ctrl+V, undo and a world reload. Nothing outside this class ever
 * reads those fields as points.</p>
 *
 * <p>The text. {@code SLP2} then one entry per wire. A carrying wire is
 * {@code p<port>t<target>:x,y x,y ...} - the points of that one wire, in the order the cable visits
 * them. A waiting wire is {@code h<port>@x,y x,y ...} - the same run of points, ending at the point
 * the wire stops at. Build 321/322 wrote {@code SLP1} with carrying wires only, and Build 323/324
 * wrote waiting wires with a single point; both are still read, so an older graph keeps its points.
 * A field value without a marker is not ours: it is never overwritten and never parsed.</p>
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

    /** A wire passing through points. */
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

    /**
     * A wire that stops at a point: the points it visits, in order, the last one being where it
     * stops.
     */
    public static final class Hang {
        public final Node from;
        public final int fromPort;
        public final List<int[]> route;

        Hang(Node from, int fromPort, List<int[]> route) {
            this.from = from;
            this.fromPort = fromPort;
            this.route = route;
        }

        /** Where the wire stops. */
        public int endX() {
            return route.isEmpty() ? 0 : route.get(route.size() - 1)[0];
        }

        public int endY() {
            return route.isEmpty() ? 0 : route.get(route.size() - 1)[1];
        }
    }

    /**
     * What one node's text holds. Bends are keyed {@code port:target}; a waiting wire is a raw
     * array {@code {port, x1, y1, x2, y2, ...}} of the points it visits, in order.
     */
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
                    List<int[]> route = new ArrayList<>();
                    for (String pair : entry.substring(at + 1).trim().split(" ")) {
                        int comma = pair.indexOf(',');
                        if (comma < 0) continue;
                        route.add(new int[] { number(pair.substring(0, comma), 0),
                                number(pair.substring(comma + 1), 0) });
                    }
                    if (route.isEmpty()) continue;
                    int[] flat = new int[1 + route.size() * 2];
                    flat[0] = port;
                    for (int i = 0; i < route.size(); i++) {
                        flat[1 + i * 2] = route.get(i)[0];
                        flat[2 + i * 2] = route.get(i)[1];
                    }
                    data.hangs.add(flat);
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
            out.append('|').append('h').append(hang[0]).append('@');
            for (int i = 1; i + 1 < hang.length; i += 2) {
                if (i > 1) out.append(' ');
                out.append(hang[i]).append(',').append(hang[i + 1]);
            }
        }
        return out.toString();
    }

    private static void write(Node node, Data data) {
        String next = format(data);
        String current = field(node);
        if (next.isEmpty()) {
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

    /** Points of one carrying wire, in order. Read only - this is the drawing path. */
    public static List<int[]> bends(Node from, int fromPort, int toId) {
        if (from == null) return List.of();
        return read(from).bends.getOrDefault(key(fromPort, toId), List.of());
    }

    /** Waiting wires of a node as {@code {port, x1, y1, ...}}, in order. Drawing path. */
    public static List<int[]> hangs(Node from) {
        if (from == null) return List.of();
        return read(from).hangs;
    }

    /** Number of points a raw waiting wire visits. */
    public static int routeSize(int[] hang) {
        return (hang.length - 1) / 2;
    }

    public static int routeX(int[] hang, int index) {
        return hang[1 + index * 2];
    }

    public static int routeY(int[] hang, int index) {
        return hang[2 + index * 2];
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
            for (int[] point : hang.route) {
                if (seen.add(pack(point[0], point[1]))) out.add(new int[] { point[0], point[1] });
            }
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

    /** Every point of every carrying wire. */
    public static List<Bend> bendsAt(NpcDialogueGraph graph, int x, int y) {
        List<Bend> bends = new ArrayList<>();
        collect(graph, bends, new ArrayList<>(), false);
        List<Bend> out = new ArrayList<>();
        for (Bend bend : bends) {
            if (bend.x == x && bend.y == y) out.add(bend);
        }
        return out;
    }

    /** Every waiting wire that stops at this point. */
    public static List<Hang> hangsAt(NpcDialogueGraph graph, int x, int y) {
        List<Hang> hangs = new ArrayList<>();
        collect(graph, new ArrayList<>(), hangs, false);
        List<Hang> out = new ArrayList<>();
        for (Hang hang : hangs) {
            if (hang.endX() == x && hang.endY() == y) out.add(hang);
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
                List<int[]> route = new ArrayList<>();
                for (int i = 0; i + 1 < hang.length; i += 2) {
                    route.add(new int[] { hang[1 + i], hang[2 + i] });
                }
                if (!route.isEmpty()) hangs.add(new Hang(node, hang[0], route));
            }
            if (changed) write(node, data);
        }
    }

    private static long pack(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    // -- editing ------------------------------------------------------------

    /** Puts a point on a carrying wire at the end of its run. */
    public static void addBend(Node from, int fromPort, int toId, int x, int y) {
        addBend(from, fromPort, toId, x, y, Integer.MAX_VALUE);
    }

    /**
     * Puts a point on a carrying wire at {@code index} of its run, so a wire can be made to pass
     * through points in the order they were clicked.
     */
    public static void addBend(Node from, int fromPort, int toId, int x, int y, int index) {
        if (from == null || toId < 0) return;
        Data data = read(from);
        List<int[]> points = data.bends.computeIfAbsent(key(fromPort, toId),
                unused -> new ArrayList<>());
        int sx = snap(x);
        int sy = snap(y);
        // Two dots on the same wire a few pixels apart look like one dot and cannot be told apart
        // by a click, so a second point is never made next to an existing one.
        for (int[] point : points) {
            if (Math.abs(point[0] - sx) <= GRID && Math.abs(point[1] - sy) <= GRID) return;
        }
        points.add(Math.max(0, Math.min(index, points.size())), new int[] { sx, sy });
        write(from, data);
    }

    /**
     * Adds a point along a waiting wire: the wire visits one more point before it stops.
     *
     * @param endX  the point the wire stops at now - identifies the waiting wire
     * @param index where in the run the new point goes
     */
    public static boolean addRoutePoint(Node from, int fromPort, int endX, int endY, int x, int y,
            int index) {
        if (from == null) return false;
        int sx = snap(x);
        int sy = snap(y);
        Data data = read(from);
        for (int i = data.hangs.size() - 1; i >= 0; i--) {
            int[] hang = data.hangs.get(i);
            if (hang[0] != fromPort) continue;
            int size = routeSize(hang);
            if (size == 0) continue;
            if (hang[1 + (size - 1) * 2] != endX || hang[2 + (size - 1) * 2] != endY) continue;
            for (int k = 0; k < size; k++) {
                if (Math.abs(hang[1 + k * 2] - sx) <= GRID
                        && Math.abs(hang[2 + k * 2] - sy) <= GRID) {
                    return false;
                }
            }
            int at = Math.max(0, Math.min(index, size));
            int[] grown = new int[hang.length + 2];
            System.arraycopy(hang, 0, grown, 0, 1 + at * 2);
            grown[1 + at * 2] = sx;
            grown[2 + at * 2] = sy;
            System.arraycopy(hang, 1 + at * 2, grown, 3 + at * 2, hang.length - 1 - at * 2);
            data.hangs.set(i, grown);
            write(from, data);
            return true;
        }
        return false;
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
            int size = routeSize(hang);
            if (hang[0] == fromPort && size > 0
                    && hang[1 + (size - 1) * 2] == sx && hang[2 + (size - 1) * 2] == sy) {
                return;
            }
        }
        // The wire cannot also be routed through that very spot: it ends here now.
        List<String> emptied = new ArrayList<>();
        for (Map.Entry<String, List<int[]>> entry : data.bends.entrySet()) {
            if (keyPort(entry.getKey()) != fromPort) continue;
            List<int[]> points = entry.getValue();
            points.removeIf(point -> point[0] == sx && point[1] == sy);
            if (points.isEmpty()) emptied.add(entry.getKey());
        }
        for (String key : emptied) data.bends.remove(key);
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
                for (int i = 1; i + 1 < hang.length; i += 2) {
                    if (hang[i] == x && hang[i + 1] == y) {
                        hang[i] = tx;
                        hang[i + 1] = ty;
                        changed = true;
                    }
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
                int size = routeSize(hang);
                int kept = size;
                List<int[]> route = new ArrayList<>();
                for (int k = 0; k < size; k++) {
                    int px = hang[1 + k * 2];
                    int py = hang[2 + k * 2];
                    if (px == x && py == y) {
                        kept--;
                        continue;
                    }
                    route.add(new int[] { px, py });
                }
                if (kept == size) continue;
                changed = true;
                if (route.isEmpty()) {
                    data.hangs.remove(i);
                    continue;
                }
                int[] smaller = new int[1 + route.size() * 2];
                smaller[0] = hang[0];
                for (int k = 0; k < route.size(); k++) {
                    smaller[1 + k * 2] = route.get(k)[0];
                    smaller[2 + k * 2] = route.get(k)[1];
                }
                data.hangs.set(i, smaller);
            }
            if (changed) {
                write(node, data);
                any = true;
            }
        }
        return any;
    }

    /**
     * Turns one waiting wire into a real one to {@code toId}, keeping the points it passes through:
     * from that moment the wire carries the connection instead of stopping at the point.
     */
    public static boolean promote(Node from, int fromPort, int x, int y, int toId) {
        if (from == null || toId < 0) return false;
        Data data = read(from);
        for (int i = data.hangs.size() - 1; i >= 0; i--) {
            int[] hang = data.hangs.get(i);
            int size = routeSize(hang);
            if (hang[0] != fromPort || size == 0) continue;
            if (hang[1 + (size - 1) * 2] != x || hang[2 + (size - 1) * 2] != y) continue;
            data.hangs.remove(i);
            List<int[]> points = data.bends.computeIfAbsent(key(fromPort, toId),
                    unused -> new ArrayList<>());
            for (int k = 0; k < size; k++) {
                int px = hang[1 + k * 2];
                int py = hang[2 + k * 2];
                if (!contains(points, px, py)) points.add(new int[] { px, py });
            }
            write(from, data);
            return true;
        }
        return false;
    }

    private static boolean contains(List<int[]> points, int x, int y) {
        for (int[] point : points) {
            if (point[0] == x && point[1] == y) return true;
        }
        return false;
    }

    /**
     * Rewrites the target of one carrying wire, keeping the whole route where it is: every point of
     * the old wire moves to the new one, so the cable keeps its shape.
     */
    public static boolean retarget(Node from, int fromPort, int oldTarget, int newTarget) {
        if (from == null || oldTarget < 0 || newTarget < 0 || oldTarget == newTarget) return false;
        Data data = read(from);
        List<int[]> points = data.bends.remove(key(fromPort, oldTarget));
        if (points == null) return false;
        List<int[]> target = data.bends.computeIfAbsent(key(fromPort, newTarget),
                unused -> new ArrayList<>());
        for (int[] point : points) {
            if (!contains(target, point[0], point[1])) target.add(point);
        }
        write(from, data);
        return true;
    }

    public static int snap(int value) {
        return Math.round((float) value / GRID) * GRID;
    }
}
