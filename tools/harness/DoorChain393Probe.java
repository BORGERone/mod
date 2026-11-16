import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcDialogueGraph;

/**
 * Build 393 probe: the node wired to the door's output socket is a step of the chain, not the one before it.
 *
 * <p>The author hacked a door whose card was wired straight to a «Реплика» - and nothing happened:
 * «ожидал, что после взлома (и после отпускания всех кнопок) начнется диалог, но ничего не произошло». The
 * name of the hacker was on the card, so the hack ran; the chain did not. Build 392 handed the wired node
 * to {@code BlockTrigger351#fire}, the entry the event nodes use - it runs a node's action and walks on from
 * that node's {@code next}, so the «Реплика» itself was skipped. {@code BlockTrigger351#start} walks from the
 * node and says how many nodes it stepped over, which is exactly what a test can see.</p>
 *
 * <pre>
 *   SINGLE   steps=1                    a lone «Реплика» is one step: it is run, not skipped
 *   CHAIN    steps=3                    реплика -> триггер -> переменная, all three stepped
 *   NOTHING  steps=0                    no node, no steps - a door with nothing wired runs nothing
 *   SOCKET   start=3 socket=line        what a hack runs: that node first, then its chain
 *   EVENT    fire=after                 fire() keeps the event semantics: it walks on from next only
 * </pre>
 */
public final class DoorChain393Probe {

    public static void main(String[] args) {
        NpcDialogueGraph graph = new NpcDialogueGraph();
        NpcDialogueGraph.Node door = graph.add(DoorGraph382.DOOR_TYPE, 120, 120);
        NpcDialogueGraph.Node line = graph.add(NpcDialogueGraph.LINE, 400, 120);
        NpcDialogueGraph.Node trigger = graph.add(NpcDialogueGraph.TRIGGER, 400, 260);
        NpcDialogueGraph.Node variable = graph.add(NpcDialogueGraph.VARIABLE, 400, 400);
        if (door == null || line == null || trigger == null || variable == null) {
            System.out.println("SINGLE steps=missing");
            return;
        }
        line.text = "Новая реплика";
        line.next = trigger.id;
        trigger.next = variable.id;

        // One «Реплика» on its own: the node itself must be stepped over, or nothing can ever be said.
        NpcDialogueGraph alone = new NpcDialogueGraph();
        NpcDialogueGraph.Node only = alone.add(NpcDialogueGraph.LINE, 100, 100);
        System.out.println("SINGLE steps=" + (only == null ? "missing"
                : BlockTrigger351.start(null, alone, only.id, null)));

        // The whole chain, node for node.
        System.out.println("CHAIN steps=" + BlockTrigger351.start(null, graph, line.id, null));

        // A door with nothing wired.
        System.out.println("NOTHING steps="
                + BlockTrigger351.start(null, graph, DoorGraph382.chainStart(graph) == null ? -1 : 999, null));

        // What a hack does: the socket target is that first step.
        door.next = line.id;
        NpcDialogueGraph.Node socket = DoorGraph382.chainStart(graph);
        int steps = socket == null ? -1 : BlockTrigger351.start(null, graph, socket.id, null);
        System.out.println("SOCKET start=" + steps + " socket=" + (socket == line ? "line" : String.valueOf(socket)));

        // fire() is untouched: it still runs an event node's action and walks on from next only.
        System.out.println("EVENT fire=" + (BlockTrigger351.fire(null, graph, trigger, null) ? "after" : "none"));
    }
}
