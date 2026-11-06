import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.RadiusPass354;

/**
 * Build 394 probe: every node a door's create menu offers is run by someone, and a door's event node is
 * seen by the pass that fires it.
 *
 * <p>The author's report on build 393: «у меня к примеру теперь не работает нода в радиусе, проверь и
 * остальные ноды». Build 392 opened the whole palette of a block in a door's graph - and nothing ever
 * walked a door's graph except the card's hack, so the four event nodes («В радиусе», «Цель», «Время»,
 * «Таймер») could be placed on a door, wired, and never fired. The two questions this probe answers are
 * exactly the two that were never asked:</p>
 *
 * <ul>
 *   <li><b>does anything run this type</b> - for every row of the door's menu, the type is either a step of
 *       a chain ({@code BlockTrigger351#isStep}, run by {@code walk}) or an event node
 *       ({@code RadiusPass354#isEventType}, fired by the pass), and the graph's {@code allowed} agrees with
 *       the menu;</li>
 *   <li><b>does the pass see the door's graph</b> - {@code RadiusPass354#hasFiringEvent} is what a door's
 *       spot is built from, so a door whose «В радиусе» is wired must answer yes and one with nothing
 *       wired must answer no.</li>
 * </ul>
 *
 * <pre>
 *   PALETTE  rows=11 step=7 event=4 unknown=0     the menu offers nothing nobody runs
 *   ALLOWED  card=yes step=yes event=yes junk=no  allowed() is isStep || isEventType, both ways
 *   EVENT    radius=yes time=yes line=no card=no  the four event types, and only them
 *   DOOR     event=yes                            a wired door event node is what the pass fires
 *   UNWIRED  event=no                             a radius node with nowhere to go is not walked
 *   CHAIN    start=1                              what that node fires: its next, one «Реплика»
 *   FIRE     event=yes                            the event entry itself still runs it
 *   RING     off=no on=yes                        the visibility toggle is what draws a ring
 * </pre>
 */
public final class DoorEvent394Probe {

    public static void main(String[] args) {
        int rows = 0;
        int steps = 0;
        int events = 0;
        int unknown = 0;
        int[] palette = DoorGraph382.paletteRows();
        for (int i = 0; i < palette.length; i++) {
            int type = DoorGraph382.typeForRow(palette[i]);
            rows++;
            if (BlockTrigger351.isStep(type)) {
                steps++;
            } else if (RadiusPass354.isEventType(type)) {
                events++;
            } else {
                unknown++;
            }
        }
        System.out.println("PALETTE rows=" + rows + " step=" + steps + " event=" + events
                + " unknown=" + unknown);

        // allowed() is the promise of the menu, in both directions: what it offers is run, what it refuses
        // is not in the palette at all.
        System.out.println("ALLOWED card=" + yes(DoorGraph382.allowed(DoorGraph382.DOOR_TYPE))
                + " step=" + yes(BlockTrigger351.allowed(NpcDialogueGraph.LINE))
                + " event=" + yes(BlockTrigger351.allowed(NpcDialogueGraph.RADIUS))
                + " junk=" + yes(BlockTrigger351.allowed(9999)));

        // The four event types, and only them.
        System.out.println("EVENT radius=" + yes(RadiusPass354.isEventType(NpcDialogueGraph.RADIUS))
                + " time=" + yes(RadiusPass354.isEventType(NpcDialogueGraph.TIME))
                + " line=" + yes(RadiusPass354.isEventType(NpcDialogueGraph.LINE))
                + " card=" + yes(RadiusPass354.isEventType(DoorGraph382.DOOR_TYPE)));

        // A door's graph, wired the way the author wired his: «В радиусе» -> «Реплика».
        NpcDialogueGraph graph = new NpcDialogueGraph();
        NpcDialogueGraph.Node card = graph.add(DoorGraph382.DOOR_TYPE, 120, 120);
        NpcDialogueGraph.Node radius = graph.add(NpcDialogueGraph.RADIUS, 400, 120);
        NpcDialogueGraph.Node line = graph.add(NpcDialogueGraph.LINE, 400, 260);
        if (card == null || radius == null || line == null) {
            System.out.println("DOOR event=missing");
            System.out.println("UNWIRED event=missing");
            System.out.println("CHAIN start=missing");
            System.out.println("FIRE event=missing");
            System.out.println("RING off=missing on=missing");
            return;
        }
        line.text = "Дверь открыта";
        // Nothing wired: the pass must not walk a node that has nowhere to go.
        System.out.println("UNWIRED event=" + yes(RadiusPass354.hasFiringEvent(graph)));

        radius.next = line.id;
        System.out.println("DOOR event=" + yes(RadiusPass354.hasFiringEvent(graph)));
        System.out.println("CHAIN start=" + BlockTrigger351.start(null, graph, radius.next, null));
        System.out.println("FIRE event=" + yes(BlockTrigger351.fire(null, graph, radius, null)));

        // Build 367: the ring starts switched off in game (hideByDefault, applied to every node the
        // editor creates); the toggle is what brings it back.
        RadiusPass354.hideByDefault(radius);
        boolean off = RadiusPass354.hasVisibleRadius(graph);
        RadiusPass354.setVisible(radius, true);
        boolean on = RadiusPass354.hasVisibleRadius(graph);
        System.out.println("RING off=" + yes(off) + " on=" + yes(on));
    }

    private static String yes(boolean value) {
        return value ? "yes" : "no";
    }
}
