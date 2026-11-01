import dev.settledlands.npc.client.NpcWirePointsUi321;

/**
 * The order in which a press is read.
 *
 * Build 325 had the geometry right and the gesture wrong: everything within the forgiving grab
 * radius of a point counted as a click on that point, and a double click on a point removes it. So
 * a double click on the segment that leads into a point - the segment an author naturally hits -
 * either deleted the point or did nothing, and no point could be made there. The store and the hit
 * tests were green the whole time, because the mistake was in the precedence, not in the model.
 *
 * These cases pin the precedence down. They run headlessly against the shipped jar.
 */
public final class ClickOrder {

    private static int failures;

    private static void expect(String what, int want, boolean again, int button, boolean hasPoint,
            boolean onDot, boolean hasPort, boolean portFree, boolean hasWire) {
        int got = NpcWirePointsUi321.decide(again, button, hasPoint, onDot, hasPort, portFree,
                hasWire);
        if (got != want) {
            System.out.println("  " + what + ": expected " + name(want) + ", got " + name(got));
            failures++;
        }
    }

    private static void expectDouble(String what, boolean want, long last, long now, double dx,
            double dy, boolean dragged) {
        boolean got = NpcWirePointsUi321.doubleClick(last, now, dx, dy, dragged);
        if (got != want) {
            System.out.println("  " + what + ": expected " + want + ", got " + got);
            failures++;
        }
    }

    private static String name(int action) {
        return switch (action) {
            case NpcWirePointsUi321.ACT_PULL -> "pull";
            case NpcWirePointsUi321.ACT_REMOVE -> "remove";
            case NpcWirePointsUi321.ACT_ADD -> "add";
            case NpcWirePointsUi321.ACT_GRAB -> "grab";
            default -> "none";
        };
    }

    public static void main(String[] args) {
        // -- the build 325 bug: the wire wins over a point that is merely near the cursor --------
        expect("double click on a wire leading into a point",
                NpcWirePointsUi321.ACT_ADD, true, 0, true, false, false, false, true);
        expect("double click on the second segment of a run",
                NpcWirePointsUi321.ACT_ADD, true, 0, true, false, true, true, true);
        expect("double click on a wire with no point anywhere near",
                NpcWirePointsUi321.ACT_ADD, true, 0, false, false, false, false, true);
        expect("double click on a waiting wire",
                NpcWirePointsUi321.ACT_ADD, true, 0, false, false, false, false, true);

        // -- the dot itself is the only thing that means "remove" -------------------------------
        expect("double click on the dot, wire underneath",
                NpcWirePointsUi321.ACT_REMOVE, true, 0, true, true, true, true, true);
        expect("double click on a point pulled off its wire",
                NpcWirePointsUi321.ACT_REMOVE, true, 0, true, false, false, false, false);
        expect("right click on a point",
                NpcWirePointsUi321.ACT_REMOVE, false, 1, true, false, false, false, false);
        expect("right click on a port",
                NpcWirePointsUi321.ACT_REMOVE, false, 1, false, false, true, true, false);

        // -- nothing else swallows a click ------------------------------------------------------
        expect("right click on empty canvas", NpcWirePointsUi321.ACT_NONE,
                false, 1, false, false, false, false, false);
        expect("middle click on a point", NpcWirePointsUi321.ACT_NONE,
                false, 2, true, true, true, true, true);
        expect("double click on empty canvas", NpcWirePointsUi321.ACT_NONE,
                true, 0, false, false, false, false, false);
        expect("single press on empty canvas", NpcWirePointsUi321.ACT_NONE,
                false, 0, false, false, false, false, false);

        // -- a single press moves a point, and a wire is only pulled on purpose -----------------
        expect("press on the dot", NpcWirePointsUi321.ACT_GRAB,
                false, 0, true, true, true, false, true);
        expect("press on a port", NpcWirePointsUi321.ACT_PULL,
                false, 0, true, false, true, true, true);
        expect("press on a point pulled off its wire", NpcWirePointsUi321.ACT_GRAB,
                false, 0, true, false, false, false, false);
        expect("double click on a port does not start a pull",
                NpcWirePointsUi321.ACT_ADD, true, 0, false, false, true, true, true);


        // -- a drag between two presses makes them two gestures, not a double click -----------------
        expectDouble("a press right after a drag", false, 1000L, 1100L, 0.0, 0.0, true);
        expectDouble("two quick presses in one place", true, 1000L, 1100L, 0.0, 0.0, false);
        expectDouble("a press a second later", false, 1000L, 1400L, 0.0, 0.0, false);
        expectDouble("a press across the canvas", false, 1000L, 1100L, 40.0, 0.0, false);
        expectDouble("the very first press", false, 0L, 10L, 0.0, 0.0, false);

        if (failures > 0) {
            System.out.println("click order: " + failures + " wrong");
            System.exit(1);
        }
        System.out.println("click order: OK");
    }
}
