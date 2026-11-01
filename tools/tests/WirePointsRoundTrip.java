import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcWirePoints321;

/**
 * Headless test for Build 321: a reroute point must bend a cable and nothing else.
 *
 * <p>The graph model and the point store are plain Java, so this runs without the game. It checks
 * the stored text, the round trip through a second node built from that text, the grid, the field
 * choice for an animation node, and - most importantly - that a point never touches the connection:
 * after adding, moving and deleting bends the wire is still the same wire.</p>
 */
public final class WirePointsRoundTrip {

    private static int failures = 0;

    public static void main(String[] args) {
        NpcDialogueGraph graph = new NpcDialogueGraph();
        Node a = graph.add(NpcDialogueGraph.LINE, 0, 0);
        Node b = graph.add(NpcDialogueGraph.LINE, 200, 0);
        a.next = b.id;

        // -- add, and the text that is stored --------------------------------------
        NpcWirePoints321.add(a, 0, b.id, 112, 48);
        check$(a.animBone != null && a.animBone.startsWith(NpcWirePoints321.MARK),
                "the field does not hold the marker: " + a.animBone);
        check$(a.animBone.equals("SLP1|p0t" + b.id + ":112,48"),
                "unexpected text: " + a.animBone);
        check$(NpcWirePoints321.has(a, 0, b.id), "the point is not found");
        check$(a.next == b.id, "adding a point changed the connection");

        // -- a second bend, then the round trip through a node built from the text --
        NpcWirePoints321.add(a, 0, b.id, 208, 96);
        check$(NpcWirePoints321.points(a, 0, b.id).size() == 2, "the second point is missing");
        String stored = a.animBone;

        Node twin = graph.add(NpcDialogueGraph.LINE, 400, 0);
        twin.animBone = stored;
        var again = NpcWirePoints321.points(twin, 0, b.id);
        check$(again.size() == 2, "the text did not parse back to two points");
        if (again.size() == 2) {
            check$(again.get(0)[0] == 112 && again.get(0)[1] == 48, "first point changed");
            check$(again.get(1)[0] == 208 && again.get(1)[1] == 96, "second point changed");
        }

        // -- the grid ---------------------------------------------------------------
        NpcWirePoints321.moveTo(a, 0, b.id, 0, 113, 51);
        int[] moved = NpcWirePoints321.points(a, 0, b.id).get(0);
        check$(moved[0] % NpcWirePoints321.GRID == 0 && moved[1] % NpcWirePoints321.GRID == 0,
                "the point is off the grid: " + moved[0] + "," + moved[1]);
        check$(moved[0] == 112 && moved[1] == 48, "113,51 should snap to 112,48");

        // -- delete, and the field is given back ------------------------------------
        NpcWirePoints321.removeAt(a, 0, b.id, 1);
        check$(NpcWirePoints321.points(a, 0, b.id).size() == 1, "delete left the point");
        NpcWirePoints321.removeAt(a, 0, b.id, 0);
        check$(!NpcWirePoints321.has(a, 0, b.id), "the last point survived");
        check$(a.animBone == null || a.animBone.isEmpty(),
                "the field was not given back: " + a.animBone);
        check$(a.next == b.id, "deleting points changed the connection");

        // -- an animation node keeps its bones and uses the other field -------------
        Node anim = graph.add(NpcDialogueGraph.ANIMATION, 0, 200);
        anim.animBone = "right_arm";
        NpcWirePoints321.add(anim, 0, b.id, 64, 64);
        check$("right_arm".equals(anim.animBone), "the bone of an animation node was overwritten");
        check$(anim.mobilityGroupId != null && anim.mobilityGroupId.startsWith("SLP1"),
                "an animation node did not fall back to the other field");

        // -- foreign text is never destroyed ---------------------------------------
        Node busy = graph.add(NpcDialogueGraph.LINE, 0, 400);
        busy.animBone = "somebody else";
        NpcWirePoints321.add(busy, 0, b.id, 32, 32);
        check$("somebody else".equals(busy.animBone), "foreign text in the field was overwritten");
        check$(NpcWirePoints321.has(busy, 0, b.id),
                "the point was lost even though it could not be saved");

        // -- handles ---------------------------------------------------------------
        // The twin was given the text of the two bends, so it has two of its own; plus one on the
        // animation node and one on the node whose field was busy.
        check$(NpcWirePoints321.handles(graph).size() == 4,
                "expected four handles, got " + NpcWirePoints321.handles(graph).size());

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
