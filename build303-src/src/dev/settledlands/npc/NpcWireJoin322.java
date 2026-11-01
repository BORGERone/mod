package dev.settledlands.npc;

import dev.settledlands.npc.NpcDialogueGraph.Node;

/**
 * Build 322: dropping a dragged wire on a reroute point joins it there.
 *
 * <p>Build 321 made points purely visual, and dropping a wire on one did nothing: the release was
 * treated as a release over empty canvas and the screen offered its "make a node out of this wire"
 * menu instead. That is not what a point is for. Now a wire released on a point is routed through
 * it: the connection the wire was being dragged to make is made, and the bend is added so the new
 * wire passes through the point and meets the wire the point already sits on.</p>
 *
 * <p>What that means in practice: the point becomes a junction. Drag from any output onto a point
 * that lies on the wire {@code B -> C} and the dragged node now also goes to {@code C}, with its
 * cable bending through the point - two cables in, one out, drawn exactly like the rest.</p>
 *
 * <p>Every guard the rest of the editor has still applies: the connection goes through
 * {@code NpcDialogueGraph#connect}, so a target that cannot be connected (itself, a duplicate, a
 * full slot) is refused and nothing is added - the original release path then runs as usual. A wire
 * may never be joined to a point of its own source, which would be a loop.</p>
 */
public final class NpcWireJoin322 {

    private NpcWireJoin322() {
    }

    /** True when the drop would do something; used to decide whether to take the release. */
    public static boolean canJoin(NpcDialogueGraph graph, int fromId, int fromPort,
            NpcWirePoints321.Handle point) {
        if (graph == null || point == null || fromId < 0) return false;
        if (point.toId == fromId) return false;          // a wire to itself is a loop
        Node from = graph.get(fromId);
        Node target = graph.get(point.toId);
        return from != null && target != null;
    }

    /**
     * Joins the dragged wire to the point.
     *
     * @return true when the connection was made and the bend added
     */
    public static boolean join(NpcDialogueGraph graph, int fromId, int fromPort,
            NpcWirePoints321.Handle point) {
        if (!canJoin(graph, fromId, fromPort, point)) return false;
        if (!graph.connect(fromId, point.toId, fromPort)) return false;
        Node from = graph.get(fromId);
        if (from == null) return false;
        NpcWirePoints321.add(from, fromPort, point.toId, point.x, point.y);
        return true;
    }
}
