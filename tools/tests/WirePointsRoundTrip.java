import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcWireJoin322;
import dev.settledlands.npc.NpcWirePoints321;
import java.util.List;

/**
 * Headless test for Build 323: a point holds wires, and only an aim connects them.
 *
 * <p>The graph model and the point store are plain Java, so this runs without the game. It checks
 * the stored text and the round trip through it, the grid, the field choice for an animation node,
 * the refusal to overwrite foreign text, the old {@code SLP1} text still being read - and, above
 * all, the two halves of the new behaviour: a wire dropped on a point waits there and connects to
 * nothing, and the point only sends its wires somewhere when it is aimed at a node.</p>
 */
public final class WirePointsRoundTrip {

    private static int failures = 0;

    public static void main(String[] args) {
        NpcDialogueGraph graph = new NpcDialogueGraph();
        Node a = graph.add(NpcDialogueGraph.LINE, 0, 0);
        Node b = graph.add(NpcDialogueGraph.LINE, 200, 0);
        Node c = graph.add(NpcDialogueGraph.LINE, 400, 0);
        Node d = graph.add(NpcDialogueGraph.LINE, 0, 200);
        a.next = b.id;

        // -- a point on a wire is a bend, and the wire is untouched -------------------
        NpcWirePoints321.addBend(a, 0, b.id, 112, 48);
        check$(a.animBone != null && a.animBone.startsWith(NpcWirePoints321.MARK),
                "the field does not hold the marker: " + a.animBone);
        check$(a.animBone.equals("SLP2|p0t" + b.id + ":112,48"),
                "unexpected text: " + a.animBone);
        check$(a.next == b.id, "adding a point changed the connection");
        check$(NpcWirePoints321.points(graph).size() == 1, "the point is not on the canvas");
        check$(NpcWirePoints321.hasPoint(graph, 112, 48), "the point is not found by position");
        NpcWirePoints321.addBend(a, 0, b.id, 113, 49);
        check$(NpcWirePoints321.bends(a, 0, b.id).size() == 1,
                "a point was added again on top of itself");
        check$(NpcWirePoints321.pointWithin(graph, 116, 52, 16),
                "a point four units away is not treated as the same point");
        check$(!NpcWirePoints321.pointWithin(graph, 400, 400, 16),
                "a point was seen where there is none");

        // -- a wire dropped on the point waits, it does not connect anywhere ---------
        check$(NpcWireJoin322.canAttach(graph, d.id, 0, 112, 48), "the drop was refused");
        check$(NpcWireJoin322.attach(graph, d.id, 0, 112, 48), "the attach failed");
        check$(d.next == -1, "the dropped wire was connected to a node behind our back");
        check$(NpcWirePoints321.hangs(d).size() == 1, "the wire is not waiting at the point");
        check$(d.animBone != null && d.animBone.equals("SLP2|h0@112,48"),
                "unexpected waiting text: " + d.animBone);
        check$(NpcWirePoints321.points(graph).size() == 1,
                "the waiting wire created a second point");
        check$(a.next == b.id, "attaching a wire changed the wire under the point");
        check$(!NpcWireJoin322.canAttach(graph, d.id, 0, 400, 400),
                "a wire was attached to a place with no point");

        // -- aiming the point connects everything at it ------------------------------
        int aimed = NpcWireJoin322.aim(graph, 112, 48, c.id);
        check$(aimed == 2, "aiming did not reach both wires: " + aimed);
        check$(a.next == c.id, "the wire through the point was not rerouted");
        check$(d.next == c.id, "the waiting wire was not connected by the aim");
        check$(NpcWirePoints321.hangs(d).isEmpty(), "the wire is still waiting after the aim");
        check$(NpcWirePoints321.bends(a, 0, c.id).size() == 1
                        && NpcWirePoints321.bends(a, 0, c.id).get(0)[0] == 112,
                "the carrying wire lost its bend");
        check$(NpcWirePoints321.bends(d, 0, c.id).size() == 1,
                "the aimed wire does not bend through the point");
        check$(c.next == -1, "aiming changed the target node");

        // -- moving the point moves every wire of it --------------------------------
        check$(NpcWirePoints321.movePoint(graph, 112, 48, 200, 96), "the point did not move");
        check$(NpcWirePoints321.bends(a, 0, c.id).get(0)[0] == 200
                        && NpcWirePoints321.bends(a, 0, c.id).get(0)[1] == 96,
                "the carrying wire stayed behind");
        check$(NpcWirePoints321.bends(d, 0, c.id).get(0)[0] == 200,
                "the aimed wire stayed behind");
        check$(NpcWirePoints321.points(graph).size() == 1,
                "moving the point left a copy behind");

        // -- removing the point leaves the connections alone ------------------------
        check$(NpcWirePoints321.removePoint(graph, 200, 96), "the point was not removed");
        check$(a.next == c.id && d.next == c.id, "removing the point changed a connection");
        check$(NpcWirePoints321.bends(a, 0, c.id).isEmpty(), "the bend survived the removal");
        check$(NpcWirePoints321.points(graph).isEmpty(), "the point survived the removal");

        // -- the end a wire was carrying is released when it stops at a point --------
        Node e = graph.add(NpcDialogueGraph.LINE, 200, 200);
        Node f = graph.add(NpcDialogueGraph.LINE, 400, 200);
        e.next = f.id;
        NpcWirePoints321.addBend(f, 0, b.id, 64, 64);
        f.next = b.id;
        check$(NpcWireJoin322.attach(graph, e.id, 0, 64, 64), "the wire did not stop at the point");
        check$(e.next == -1, "the wire kept its old target as well as the point");
        check$(NpcWirePoints321.hangs(e).size() == 1, "the wire does not wait at the point");
        check$(NpcWireJoin322.aim(graph, 64, 64, b.id) == 2,
                "aiming did not reach the wire that was already there");

        // -- a bend whose wire is gone is dropped, not drawn -------------------------
        Node lone = graph.add(NpcDialogueGraph.LINE, -400, 0);
        Node gone = graph.add(NpcDialogueGraph.LINE, -200, 0);
        lone.next = gone.id;
        NpcWirePoints321.addBend(lone, 0, gone.id, 32, 32);
        check$(NpcWirePoints321.hasPoint(graph, 32, 32), "the point was not made");
        lone.next = -1;
        check$(!NpcWirePoints321.hasPoint(graph, 32, 32),
                "a bend outlived the wire it was made on");
        check$(NpcWirePoints321.bends(lone, 0, gone.id).isEmpty(),
                "the stale bend is still in the text: " + lone.animBone);

        // -- old text from Build 321 is still read, and is rewritten on the next edit -
        Node legacy = graph.add(NpcDialogueGraph.LINE, 0, 400);
        Node legacyTo = graph.add(NpcDialogueGraph.LINE, 200, 400);
        legacy.next = legacyTo.id;
        legacy.animBone = "SLP1|p0t" + legacyTo.id + ":152,64";
        check$(NpcWirePoints321.hasPoint(graph, 152, 64),
                "the text written by Build 321 is not read any more");
        NpcWirePoints321.movePoint(graph, 152, 64, 160, 64);
        check$(legacy.animBone != null && legacy.animBone.startsWith(NpcWirePoints321.MARK),
                "the old text was not rewritten in the new format: " + legacy.animBone);

        // -- an animation node keeps its bones and uses the other field -------------
        Node anim = graph.add(NpcDialogueGraph.ANIMATION, 0, 600);
        anim.animBone = "right_arm";
        NpcWirePoints321.addBend(anim, 0, b.id, 64, 96);
        check$("right_arm".equals(anim.animBone), "the bone of an animation node was overwritten");
        check$(anim.mobilityGroupId != null && anim.mobilityGroupId.startsWith(NpcWirePoints321.MARK),
                "an animation node did not fall back to the other field");

        // -- foreign text is never destroyed ---------------------------------------
        Node busy = graph.add(NpcDialogueGraph.LINE, 0, 800);
        busy.animBone = "somebody else";
        busy.next = b.id;
        NpcWirePoints321.addBend(busy, 0, b.id, 40, 40);
        check$("somebody else".equals(busy.animBone), "foreign text in the field was overwritten");
        check$(NpcWirePoints321.hasPoint(graph, 40, 40),
                "the point was lost even though it could not be saved");

        // -- a point with one wire, aimed at its own node: only the wire is skipped --
        NpcDialogueGraph loop = new NpcDialogueGraph();
        Node one = loop.add(NpcDialogueGraph.LINE, 0, 0);
        Node two = loop.add(NpcDialogueGraph.LINE, 200, 0);
        Node three = loop.add(NpcDialogueGraph.LINE, 400, 0);
        one.next = two.id;
        NpcWirePoints321.addBend(one, 0, two.id, 96, 32);
        NpcWireJoin322.attach(loop, three.id, 0, 96, 32);
        check$(NpcWireJoin322.aim(loop, 96, 32, three.id) == 1,
                "aiming a point at one of its own wires should leave that wire alone");
        check$(one.next == three.id, "the carrying wire was not sent to the target");
        check$(three.next == -1, "a node was wired to itself");

        // -- a run of points: node - point - point - point - node -------------------
        NpcDialogueGraph chain = new NpcDialogueGraph();
        Node from = chain.add(NpcDialogueGraph.LINE, 0, 0);
        Node into = chain.add(NpcDialogueGraph.LINE, 400, 0);
        from.next = into.id;
        NpcWirePoints321.addBend(from, 0, into.id, 96, 40, 0);
        NpcWirePoints321.addBend(from, 0, into.id, 296, 40, 1);
        List<int[]> run = NpcWirePoints321.bends(from, 0, into.id);
        check$(run.size() == 2, "two points on one wire did not land");
        check$(run.get(0)[0] == 96 && run.get(1)[0] == 296, "the points lost their order");
        // Clicking the first segment puts the new point before the second one.
        NpcWirePoints321.addBend(from, 0, into.id, 200, 40, 1);
        run = NpcWirePoints321.bends(from, 0, into.id);
        check$(run.size() == 3 && run.get(1)[0] == 200 && run.get(2)[0] == 296,
                "a point was not inserted where the wire was clicked");
        check$(from.next == into.id, "a run of points changed the connection");

        // -- a run of points on a waiting wire -------------------------------------
        NpcDialogueGraph waits = new NpcDialogueGraph();
        Node source = waits.add(NpcDialogueGraph.LINE, 0, 0);
        waits.add(NpcDialogueGraph.LINE, 400, 0);
        NpcWirePoints321.attach(source, 0, 0, 64);
        check$(NpcWirePoints321.hangsAt(waits, 0, 64).size() == 1,
                "the waiting wire is not found at its point");
        check$(NpcWirePoints321.addRoutePoint(source, 0, 0, 64, -64, 64, 0),
                "a point on a waiting wire was refused");
        int[] raw = NpcWirePoints321.hangs(source).get(0);
        check$(NpcWirePoints321.routeSize(raw) == 2, "the run of the waiting wire is wrong");
        check$(NpcWirePoints321.routeX(raw, 0) == -64 && NpcWirePoints321.routeY(raw, 0) == 64,
                "the new point is not where it was clicked");
        check$(NpcWirePoints321.routeX(raw, 1) == 0 && NpcWirePoints321.routeY(raw, 1) == 64,
                "the point the wire stops at moved");
        check$(source.animBone != null && source.animBone.equals("SLP2|h0@-64,64 0,64"),
                "unexpected text for a run of waiting points: " + source.animBone);
        // Both points of the run are points on the canvas.
        check$(NpcWirePoints321.hasPoint(waits, -64, 64) && NpcWirePoints321.hasPoint(waits, 0, 64),
                "the points of a waiting run are not on the canvas");
        // Moving the inner one moves only it.
        NpcWirePoints321.movePoint(waits, -64, 64, -72, 56);
        raw = NpcWirePoints321.hangs(source).get(0);
        check$(NpcWirePoints321.routeX(raw, 0) == -72
                        && NpcWirePoints321.routeX(raw, 1) == 0,
                "moving one point of a run moved the wrong one");

        // Aiming the point the wire stops at turns the whole run into a real wire.
        Node destination = waits.add(NpcDialogueGraph.LINE, 800, 0);
        check$(NpcWireJoin322.aim(waits, 0, 64, destination.id) == 1, "the waiting wire was not aimed");
        check$(source.next == destination.id, "aiming did not connect the waiting wire");
        List<int[]> promoted = NpcWirePoints321.bends(source, 0, destination.id);
        check$(promoted.size() == 2, "the run was lost when the wire became real: " + promoted.size());
        check$(NpcWirePoints321.hangs(source).isEmpty(), "the waiting wire is still waiting");

        // Removing one point of a run keeps the others; removing the last one lets the wire go.
        NpcDialogueGraph cut = new NpcDialogueGraph();
        Node tail = cut.add(NpcDialogueGraph.LINE, 0, 0);
        NpcWirePoints321.attach(tail, 0, 40, 40);
        NpcWirePoints321.addRoutePoint(tail, 0, 40, 40, -40, 40, 0);
        NpcWirePoints321.removePoint(cut, -40, 40);
        raw = NpcWirePoints321.hangs(tail).get(0);
        check$(NpcWirePoints321.routeSize(raw) == 1, "an inner point was not removed from the run");
        NpcWirePoints321.removePoint(cut, 40, 40);
        check$(NpcWirePoints321.hangs(tail).isEmpty(), "the waiting wire outlived its last point");

        finish();
    }

    private static void check$(boolean ok, String message) {
        if (!ok) {
            failures++;
            System.out.println("  - " + message);
        }
    }

    private static void finish() {
        if (failures == 0) {
            System.out.println("wire points: OK");
            return;
        }
        System.out.println("wire points: " + failures + " failure(s)");
        System.exit(1);
    }
}
