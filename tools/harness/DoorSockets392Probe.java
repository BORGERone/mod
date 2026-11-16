import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.NpcDialogueGraph;

/**
 * Build 392 probe: the fold that turns the two nodes of builds 389-391 into the sockets of the card.
 *
 * <p>The author asked for sockets, not nodes: «я хотел чтобы ты создал не ноды, а УЗЛЫ на ноде "дверь"».
 * A graph he already built still holds the two nodes, and the fold has to give him his own graph back -
 * his chain, his readers, his card - with the two cards gone. That is rewiring, and rewiring is the one
 * thing a screenshot can never check: a wire dropped in silence looks exactly like a wire that was never
 * there. So the fold is asked here, on the real classes, without a screen or a world:</p>
 *
 * <pre>
 *   FRESH      nodes=1 entry=door chainStart=none          a fresh door is one card and nothing else
 *   ALLOWED    door=true pulse=false target=false          the palette of a door cannot make the old nodes
 *   BEFORE     nodes=5 doorTo=pulse reader=target          the shape builds 389-391 left behind
 *   AFTER      nodes=3 doorTo=line reader=door entry=door  the card keeps the chain and the reader
 *   OPENER     card=Steve                                  the name the blue target held moved onto the card
 *   CHAIN      start=line                                  the hack starts where the card's socket points
 *   AGAIN      folded=false nodes=3                        a second open folds nothing and moves nothing
 *   KEPT       x=120 y=120 readerText=hello                the author's node text is not touched
 * </pre>
 */
public final class DoorSockets392Probe {

    /** The two types of builds 389-391: the probe may not name them, the fold recognises the numbers. */
    private static final int PULSE = 41;
    private static final int TARGET = 42;

    public static void main(String[] args) {
        fresh();
        allowed();
        fold();
        System.out.println("DONE");
    }

    /** A door nobody has touched yet: one card, «Начало» on it, no chain. */
    private static void fresh() {
        NpcDialogueGraph graph = DoorGraph382.defaultGraph(true);
        NpcDialogueGraph.Node door = DoorGraph382.doorNode(graph);
        String start = DoorGraph382.chainStart(graph) == null ? "none"
                : String.valueOf(DoorGraph382.chainStart(graph).id);
        System.out.println("FRESH nodes=" + graph.nodes.size()
                + " entry=" + (door != null && graph.entry == door.id ? "door" : "other")
                + " chainStart=" + start
                + " keys=" + DoorGraph382.keys(door)
                + " speed=" + DoorGraph382.speed(door)
                + " sealed=" + DoorGraph382.sealed(door));
    }

    /** What a door's palette may create: the card is its own, the two old nodes are refusals now. */
    private static void allowed() {
        System.out.println("ALLOWED door=" + DoorGraph382.allowed(DoorGraph382.DOOR_TYPE)
                + " pulse=" + DoorGraph382.allowed(PULSE)
                + " target=" + DoorGraph382.allowed(TARGET));
    }

    /** A graph with the two nodes wired the way build 391 left it, then folded. */
    private static void fold() {
        NpcDialogueGraph graph = new NpcDialogueGraph();
        NpcDialogueGraph.Node door = graph.add(DoorGraph382.DOOR_TYPE, 120, 120);
        NpcDialogueGraph.Node pulse = graph.add(PULSE, 400, 120);
        NpcDialogueGraph.Node target = graph.add(TARGET, 680, 120);
        NpcDialogueGraph.Node line = graph.add(NpcDialogueGraph.LINE, 400, 300);
        NpcDialogueGraph.Node trigger = graph.add(NpcDialogueGraph.TRIGGER, 100, 300);
        if (door == null || pulse == null || target == null || line == null || trigger == null) {
            System.out.println("FOLD nodes=missing");
            return;
        }
        DoorGraph382.setKeys(door, 7);
        door.x = 120;
        door.y = 120;
        line.text = "hello";
        // card -> pulse -> line, and a «Триггер» reading the blue target.
        door.next = pulse.id;
        pulse.next = line.id;
        graph.entry = pulse.id;
        trigger.varInputNode = target.id;
        target.text = "Steve";

        System.out.println("BEFORE nodes=" + graph.nodes.size()
                + " doorTo=" + (door.next == pulse.id ? "pulse" : String.valueOf(door.next))
                + " reader=" + (trigger.varInputNode == target.id ? "target"
                        : String.valueOf(trigger.varInputNode)));

        boolean folded = DoorGraph382.foldStarter(graph);
        NpcDialogueGraph.Node start = DoorGraph382.chainStart(graph);
        System.out.println("AFTER nodes=" + graph.nodes.size()
                + " doorTo=" + (door.next == line.id ? "line" : String.valueOf(door.next))
                + " reader=" + (trigger.varInputNode == door.id ? "door"
                        : String.valueOf(trigger.varInputNode))
                + " entry=" + (graph.entry == door.id ? "door" : String.valueOf(graph.entry))
                + " folded=" + folded);
        System.out.println("OPENER card=" + DoorGraph382.opener(door));
        System.out.println("CHAIN start=" + (start == line ? "line" : String.valueOf(start)));

        boolean again = DoorGraph382.foldStarter(graph);
        System.out.println("AGAIN folded=" + again + " nodes=" + graph.nodes.size());
        System.out.println("KEPT x=" + door.x + " y=" + door.y + " readerText=" + line.text);
    }
}
