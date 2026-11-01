import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcWirePoints321;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.NpcWirePointsUi321;

/**
 * The point a double click makes must end up ON the cable that was clicked.
 *
 * <p>Two ways a dot ends up beside a wire instead of on it, both checked here:</p>
 *
 * <ul>
 *   <li><b>the wrong key</b> - the screen draws each link with a fixed port number (0 for
 *       {@code next}, {@code choices} and {@code triggers}, 1 for the false branch of a condition,
 *       the branch index for a random node). A point keyed with any other number is stored, drawn
 *       as a dot, and never has a cable through it, because no cable is ever looked up under that
 *       key. The test asserts that the port a click resolves to is the port the screen draws with,
 *       for every kind of link.</li>
 *   <li><b>the wrong place</b> - the point used to be put where the cursor was, which is up to the
 *       hit radius off the cable, and then snapped to the eight-unit grid, which pulls it further.
 *       The test clicks a few pixels off a cable and asserts the point lands on the cable, and that
 *       it stays on the cable after it is stored.</li>
 * </ul>
 */
public final class WireKeyTest {

    private static int failures;

    public static void main(String[] args) {
        // -- every kind of link, one cable at a time ------------------------------------------
        NpcGraphCanvas canvas = canvas();

        NpcDialogueGraph plain = new NpcDialogueGraph();
        Node plainFrom = plain.add(NpcDialogueGraph.LINE, 0, 0);
        Node plainTo = plain.add(NpcDialogueGraph.LINE, 400, 100);
        plainFrom.next = plainTo.id;
        checkWire(canvas, plain, plainFrom, 0, plainTo, "next");

        NpcDialogueGraph choice = new NpcDialogueGraph();
        Node choiceFrom = choice.add(NpcDialogueGraph.LINE, 0, 0);
        Node choiceTo = choice.add(NpcDialogueGraph.BUTTON, 400, 100);
        choiceFrom.choices.add(choiceTo.id);
        checkWire(canvas, choice, choiceFrom, 0, choiceTo, "choice");

        NpcDialogueGraph trigger = new NpcDialogueGraph();
        Node triggerFrom = trigger.add(NpcDialogueGraph.LINE, 0, 0);
        Node triggerTo = trigger.add(NpcDialogueGraph.TRIGGER, 400, 100);
        triggerFrom.triggers.add(triggerTo.id);
        checkWire(canvas, trigger, triggerFrom, 0, triggerTo, "trigger");

        NpcDialogueGraph branch = new NpcDialogueGraph();
        Node cond = branch.add(NpcDialogueGraph.IF, 0, 0);
        Node condTo = branch.add(NpcDialogueGraph.LINE, 400, 100);
        cond.falseNext = condTo.id;
        checkWire(canvas, branch, cond, 1, condTo, "false next");

        NpcDialogueGraph rolls = new NpcDialogueGraph();
        Node random = rolls.add(NpcDialogueGraph.RANDOM, 0, 0);
        Node rollTo = rolls.add(NpcDialogueGraph.LINE, 400, 100);
        random.randomMax = 3;
        random.randomBranches.add(-1);
        random.randomBranches.add(rollTo.id);
        // The screen draws branch i with port i, so a point on the second branch has to be keyed
        // with port 1. It used to be keyed with 0, and no cable was ever drawn through it.
        checkWire(canvas, rolls, random, 1, rollTo, "random branch 1");

        // -- keys the screen never draws are refused, so no dot can float on empty canvas ----
        NpcDialogueGraph keys = new NpcDialogueGraph();
        Node line = keys.add(NpcDialogueGraph.LINE, 0, 0);
        Node target = keys.add(NpcDialogueGraph.LINE, 400, 0);
        line.next = target.id;
        if (!NpcWirePoints321.drawn(line, 0, target.id)) {
            fail("a point on an ordinary wire is not considered drawn");
        }
        if (NpcWirePoints321.drawn(line, 1, target.id)) {
            fail("a point keyed to a port the screen never draws is considered drawn");
        }
        if (NpcWirePoints321.drawn(line, 0, 9999)) {
            fail("a point on a wire that does not exist is considered drawn");
        }
        if (NpcWirePoints321.drawn(random, 0, rollTo.id)) {
            // Branch 0 is empty here, so nothing is drawn for that port.
            fail("a point on an empty random branch is considered drawn");
        }
        if (!NpcWirePoints321.drawn(random, 1, rollTo.id)) {
            fail("a point on a drawn random branch is not considered drawn");
        }

        // -- a click a few pixels off the cable still makes a point ON the cable --------------
        NpcDialogueGraph clean = new NpcDialogueGraph();
        Node a = clean.add(NpcDialogueGraph.LINE, 0, 0);
        Node b = clean.add(NpcDialogueGraph.LINE, 400, 100);
        a.next = b.id;
        double x1 = canvas.portScreenX(a, true);
        double y1 = canvas.portScreenY(a, true, 0);
        double x2 = canvas.portScreenX(b, false);
        double y2 = canvas.portScreenY(b, false, 0);
        double[] on = sample(canvas, x1, y1, x2, y2, 0.5);
        NpcWirePointsUi321.Wire wire = NpcWirePointsUi321.wireAt(canvas, clean, on[0], on[1] - 3.0);
        if (wire == null) {
            fail("a click three pixels off a cable was not taken as that cable");
            finish();
            return;
        }
        if (wire.from != a || wire.toId != b.id || wire.fromPort != 0) {
            fail("the wrong wire was picked under the cursor");
        }
        int[] at = NpcWirePointsUi321.pointOn(canvas, wire, on[0], on[1] - 3.0);
        if (at == null) {
            fail("no place on the cable was found for the point");
            finish();
            return;
        }
        NpcWirePoints321.addBend(a, wire.fromPort, wire.toId, at[0], at[1], wire.index);
        if (NpcWirePoints321.bends(a, 0, b.id).size() != 1) {
            fail("the point was not stored on the wire");
        }
        if (!NpcWirePoints321.drawn(a, 0, b.id)) {
            fail("the cable is not drawn through the new point");
        }
        int[] stored = NpcWirePoints321.bends(a, 0, b.id).get(0);
        if (stored[0] != at[0] || stored[1] != at[1]) {
            fail("the point was moved off the cable after it was made");
        }
        int[] segment = new int[1];
        double after = NpcWirePointsUi321.linkDistance(canvas, clean, a, 0, b.id,
                canvas.screenX(stored[0]), canvas.screenY(stored[1]), segment);
        if (after > 1.0) {
            fail("the new point is not on its own cable: " + after);
        }
        // The point is on the cable as drawn, not merely near the straight line between the ports.
        double toCable = NpcWirePointsUi321.segmentDistance(canvas, x1, y1, x2, y2,
                canvas.screenX(stored[0]), canvas.screenY(stored[1]));
        if (toCable > 4.0) {
            fail("the point is " + Math.sqrt(toCable) + " px off the cable");
        }

        // -- a click on the first segment of a bent wire lands on that segment ---------------
        double[] first = sample(canvas, x1, y1, canvas.screenX(stored[0]),
                canvas.screenY(stored[1]), 0.5);
        NpcWirePointsUi321.Wire again = NpcWirePointsUi321.wireAt(canvas, clean, first[0],
                first[1] + 2.0);
        if (again == null) {
            fail("the first segment of a bent wire cannot be clicked");
        } else if (again.index != 0) {
            fail("a click on the first segment resolved to segment " + again.index);
        }

        // -- a click far from every cable makes nothing at all -------------------------------
        if (NpcWirePointsUi321.wireAt(canvas, clean, 1400, 1100) != null) {
            fail("a click far from every cable found a wire");
        }
        finish();
    }

    /** Clicks along a cable and checks the port and the key the screen draws it with. */
    private static void checkWire(NpcGraphCanvas canvas, NpcDialogueGraph graph, Node from, int port,
            Node to, String what) {
        double x1 = canvas.portScreenX(from, true);
        double y1 = canvas.portScreenY(from, true, port);
        double x2 = canvas.portScreenX(to, false);
        double y2 = canvas.portScreenY(to, false, 0);
        boolean found = false;
        for (double t : new double[] { 0.3, 0.5, 0.7 }) {
            double[] on = sample(canvas, x1, y1, x2, y2, t);
            NpcWirePointsUi321.Wire wire = NpcWirePointsUi321.wireAt(canvas, graph, on[0], on[1]);
            if (wire == null) continue;
            found = true;
            if (wire.from != from || wire.toId != to.id) {
                fail(what + ": the click found another wire");
            }
            if (wire.fromPort != port) {
                fail(what + ": the click resolved to port " + wire.fromPort + " instead of " + port);
            }
            if (!NpcWirePoints321.drawn(from, wire.fromPort, to.id)) {
                fail(what + ": the point would be stored under a port no cable is drawn with");
            }
            int[] at = NpcWirePointsUi321.pointOn(canvas, wire, on[0], on[1]);
            if (at == null) {
                fail(what + ": no place on the cable was found");
                continue;
            }
            NpcWirePoints321.addBend(from, wire.fromPort, to.id, at[0], at[1], wire.index);
            java.util.List<int[]> of = NpcWirePoints321.bends(from, wire.fromPort, to.id);
            if (of.size() != 1) {
                fail(what + ": the point was not stored");
            }
            int[] stored = of.get(0);
            double off = NpcWirePointsUi321.linkDistance(canvas, graph, from, wire.fromPort, to.id,
                    canvas.screenX(stored[0]), canvas.screenY(stored[1]), new int[1]);
            if (off > 1.0) {
                fail(what + ": the point is not on the cable it was made on: " + off);
            }
            break;
        }
        if (!found) {
            fail(what + ": the cable could not be found by clicking on it");
        }
    }

    /** The cubic the screen draws, sampled exactly as the hit test samples it. */
    private static double[] sample(NpcGraphCanvas canvas, double x1, double y1, double x2,
            double y2, double t) {
        double bend = Math.max(25.0 * canvas.zoom, Math.abs(x2 - x1) * 0.45);
        double u = 1 - t;
        return new double[] {
            u * u * u * x1 + 3 * u * u * t * (x1 + bend) + 3 * u * t * t * (x2 - bend)
                    + t * t * t * x2,
            u * u * u * y1 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y2
        };
    }

    private static NpcGraphCanvas canvas() {
        NpcGraphCanvas canvas = new NpcGraphCanvas();
        canvas.bounds(0, 0, 1600, 1200);
        canvas.zoom = 1.0;
        canvas.panX = 0;
        canvas.panY = 0;
        return canvas;
    }

    private static void fail(String message) {
        failures++;
        System.out.println("  - " + message);
    }

    private static void finish() {
        if (failures > 0) {
            System.out.println("wire keys: " + failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("wire keys: OK");
    }
}
