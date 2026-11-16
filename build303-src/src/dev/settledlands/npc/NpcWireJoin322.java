package dev.settledlands.npc;

import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcWirePoints321.Bend;
import dev.settledlands.npc.NpcWirePoints321.Hang;
import java.util.List;

/**
 * Build 323: what a point does with the wires that meet it - it holds them, and it is given a
 * target once, deliberately.
 *
 * <p>A wire dropped on a point is never connected to anything behind the author's back. Two
 * separate steps, and only the second one writes a connection:</p>
 *
 * <ul>
 *   <li>{@link #attach} - a wire whose end was dropped on the point stops there. No connection is
 *       made, no link field is written: the wire waits, and the canvas shows it waiting.</li>
 *   <li>{@link #aim} - the point is dragged onto a target node and every wire at that point goes
 *       there: the waiting ones get their connection, the carrying ones are rerouted, and the
 *       point keeps its position and its shape.</li>
 * </ul>
 *
 * <p>Every connection is made and unmade through {@code NpcDialogueGraph#connect} and
 * {@code #disconnect}, so the editor's own rules apply: a target that cannot take the link (a
 * special node, a full choice list, the wire's own source) refuses, and the wire is put back where
 * it was rather than lost.</p>
 */
public final class NpcWireJoin322 {

    private NpcWireJoin322() {
    }

    /** True when the wire could stop at this point - used before the release is taken. */
    public static boolean canAttach(NpcDialogueGraph graph, int fromId, int fromPort, int x,
            int y) {
        if (graph == null || fromId < 0 || fromPort < 0) return false;
        if (!NpcWirePoints321.hasPoint(graph, x, y)) return false;
        Node from = graph.get(fromId);
        return from != null && from.special == NpcDialogueGraph.NORMAL;
    }

    /**
     * Ends the wire of {@code fromId} on the point at {@code x,y}.
     *
     * <p>The wire does not go anywhere: it waits. If the node had exactly one output and its end is
     * being carried over, that end is released first, so the wire really has moved instead of
     * staying behind on its old target. A node with several possible outputs keeps them: which of
     * them the author grabbed cannot be told from the port alone, and silently cutting one of them
     * would be worse than leaving the wire where it was.</p>
     *
     * @return true when the wire now waits at the point
     */
    public static boolean attach(NpcDialogueGraph graph, int fromId, int fromPort, int x, int y) {
        if (!canAttach(graph, fromId, fromPort, x, y)) return false;
        Node from = graph.get(fromId);
        int old = oldTarget(from, fromPort);
        if (old >= 0) {
            // The wire moves to the point, so its old end is let go. Leaving it connected while the
            // canvas shows the wire waiting at the point is how a wire ends up reaching a node
            // nobody picked - the author sees a wire that stops, the dialogue sees a link.
            if (alsoLinks(from, fromPort, old)) return false;
            graph.disconnect(fromId, old);
        }
        NpcWirePoints321.attach(from, fromPort, x, y);
        return true;
    }

    /** True when this point has a wire to aim. */
    public static boolean canAim(NpcDialogueGraph graph, int x, int y, int targetId) {
        if (graph == null || targetId < 0) return false;
        Node target = graph.get(targetId);
        if (target == null || target.special != NpcDialogueGraph.NORMAL) return false;
        return !NpcWirePoints321.bendsAt(graph, x, y).isEmpty()
                || !NpcWirePoints321.hangsAt(graph, x, y).isEmpty();
    }

    /**
     * Sends everything at the point to {@code targetId}.
     *
     * @return how many wires now reach the target; 0 means nothing changed and the caller must not
     *         report a connection
     */
    public static int aim(NpcDialogueGraph graph, int x, int y, int targetId) {
        if (!canAim(graph, x, y, targetId)) return 0;
        int done = 0;
        List<Bend> bends = NpcWirePoints321.bendsAt(graph, x, y);
        for (Bend bend : bends) {
            if (bend.from == null || bend.from.id == targetId) continue;
            if (bend.toId == targetId) {
                done++;                       // already where the point now points
                continue;
            }
            if (!NpcWirePoints321.linkExists(bend.from, bend.fromPort, bend.toId)) continue;
            graph.disconnect(bend.from.id, bend.toId);
            if (graph.connect(bend.from.id, targetId, bend.fromPort)) {
                NpcWirePoints321.retarget(bend.from, bend.fromPort, bend.toId, targetId);
                done++;
            } else {
                // Put the wire back exactly where it was.
                graph.connect(bend.from.id, bend.toId, bend.fromPort);
            }
        }
        List<Hang> hangs = NpcWirePoints321.hangsAt(graph, x, y);
        for (Hang hang : hangs) {
            if (hang.from == null || hang.from.id == targetId) continue;
            if (!graph.connect(hang.from.id, targetId, hang.fromPort)) continue;
            if (NpcWirePoints321.promote(hang.from, hang.fromPort, hang.endX(), hang.endY(),
                    targetId)) {
                done++;
            }
        }
        return done;
    }

    // -- which end a dropped wire was carrying -------------------------------

    /** The target the wire of this port has now, or -1. */
    private static int oldTarget(Node from, int fromPort) {
        if (fromPort == 1) return from.falseNext;
        if (from.type == NpcDialogueGraph.RANDOM) {
            return fromPort < from.randomBranches.size() ? from.randomBranches.get(fromPort) : -1;
        }
        return from.next;
    }

    /**
     * True when the same node reaches {@code target} through another output as well: releasing this
     * one would silently cut the other, so the drop is refused instead.
     */
    private static boolean alsoLinks(Node from, int fromPort, int target) {
        if (fromPort != 0 && from.next == target) return true;
        if (fromPort != 1 && from.falseNext == target) return true;
        if (from.varInputNode == target) return true;
        if (from.choices.contains(target)) return true;
        if (from.triggers.contains(target)) return true;
        for (int i = 0; i < from.randomBranches.size(); i++) {
            if (i != fromPort && from.randomBranches.get(i) == target) return true;
        }
        return false;
    }
}
