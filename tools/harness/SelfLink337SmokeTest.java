import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcRandomLine336;
import dev.settledlands.npc.NpcSelfLink337;
import dev.settledlands.npc.client.NpcRandomLineUi337;

/**
 * Runs build 337 on real graph nodes: what the plain graph does with a link from a node to itself
 * (it keeps it - that is the endless dialogue), what the guard answers, that one refusal is reported
 * once, and that the inspector row names the state of the node.
 */
public class SelfLink337SmokeTest {

    static int failures = 0;

    static void check(String what, boolean ok) {
        if (ok) {
            System.out.println("ok    " + what);
        } else {
            failures++;
            System.out.println("FAIL  " + what);
        }
    }

    public static void main(String[] args) {
        NpcDialogueGraph graph = new NpcDialogueGraph();
        NpcDialogueGraph.Node line = graph.add(NpcDialogueGraph.LINE, 0, 0);
        NpcDialogueGraph.Node other = graph.add(NpcDialogueGraph.LINE, 200, 0);
        line.text = "first\nsecond\nthird";

        check("the plain graph keeps a node wired to itself",
                line.next < 0 && graph.connect(line.id, line.id) && line.next == line.id);
        graph.disconnect(line.id, line.id);
        check("and it can be undone again", line.next < 0);

        check("a node and itself is a self-link", NpcSelfLink337.isSelfLink(line.id, line.id));
        check("two different nodes are not", !NpcSelfLink337.isSelfLink(line.id, other.id));
        check("no id at all is not", !NpcSelfLink337.isSelfLink(-1, -1));

        check("nothing is reported before a refusal", !NpcSelfLink337.consumeRefusal());
        NpcSelfLink337.refuse();
        check("a refusal is reported once", NpcSelfLink337.consumeRefusal()
                && !NpcSelfLink337.consumeRefusal());

        check("the row is off by default",
                NpcRandomLineUi337.label(line).getString().equals(NpcRandomLine336.OFF_KEY));
        NpcRandomLine336.enable(line, 2);
        check("and names the roll once it is on",
                NpcRandomLineUi337.label(line).getString().equals(NpcRandomLine336.ON_KEY));
        check("the roll still decides the phrase", "second".equals(NpcRandomLine336.pick(line)));
        check("a node that is not a line has no row",
                NpcRandomLineUi337.label(graph.add(NpcDialogueGraph.TRIGGER, 0, 200)).getString()
                        .equals(NpcRandomLine336.OFF_KEY));

        if (failures == 0) {
            System.out.println("ALL OK");
        } else {
            System.out.println("FAILURES: " + failures);
            System.exit(1);
        }
    }
}
