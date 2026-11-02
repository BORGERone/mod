import dev.settledlands.npc.NpcCommonDialogue335;
import dev.settledlands.npc.NpcDialogueGraph;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Runs the merge of build 335 on real graph objects: the NPC's own graph, the common one, and the
 * result a conversation actually runs on. No Minecraft world is needed - this is pure topology.
 */
public class CommonDialogueSmokeTest {

    static int failures = 0;

    static void check(String what, boolean ok) {
        if (ok) {
            System.out.println("ok    " + what);
        } else {
            failures++;
            System.out.println("FAIL  " + what);
        }
    }

    public static void main(String[] args) throws Exception {
        Method roots = NpcCommonDialogue335.class.getDeclaredMethod("roots", NpcDialogueGraph.class);
        Method merge = NpcCommonDialogue335.class.getDeclaredMethod(
                "merge", NpcDialogueGraph.class, NpcDialogueGraph.class, List.class);
        roots.setAccessible(true);
        merge.setAccessible(true);

        // The NPC: intro line with one answer of its own.
        NpcDialogueGraph own = new NpcDialogueGraph();
        NpcDialogueGraph.Node intro = own.add(NpcDialogueGraph.LINE, 0, 0);
        intro.text = "Hello, traveller.";
        NpcDialogueGraph.Node ownButton = own.add(NpcDialogueGraph.BUTTON, 40, 0);
        ownButton.text = "Who are you?";
        own.connect(intro.id, ownButton.id);
        own.entry = intro.id;

        // The common graph: a root button, the line behind it, and a button inside that chain.
        NpcDialogueGraph common = new NpcDialogueGraph();
        NpcDialogueGraph.Node root = common.add(NpcDialogueGraph.BUTTON, 0, 0);
        root.text = "Common question";
        NpcDialogueGraph.Node answer = common.add(NpcDialogueGraph.LINE, 40, 0);
        answer.text = "Common answer";
        NpcDialogueGraph.Node inner = common.add(NpcDialogueGraph.BUTTON, 80, 0);
        inner.text = "Go deeper";
        common.connect(root.id, answer.id);
        common.connect(answer.id, inner.id);
        common.entry = root.id;

        @SuppressWarnings("unchecked")
        List<Integer> found = (List<Integer>) roots.invoke(null, common);
        check("roots = the button nothing points at", found.size() == 1 && found.get(0) == root.id);

        NpcDialogueGraph merged = (NpcDialogueGraph) merge.invoke(null, own, common, found);
        check("merge produced a graph", merged != null);
        check("own graph untouched", own.nodes.size() == 2 && intro.choices.size() == 1);
        check("common graph untouched", common.nodes.size() == 3 && answer.choices.size() == 1
                && common.get(root.id).next == answer.id && common.get(answer.id).choices.get(0) == inner.id);

        NpcDialogueGraph.Node start = merged.get(intro.id);
        check("start line kept its text", "Hello, traveller.".equals(start.text));
        check("start line kept its own answer", start.choices.contains(ownButton.id));
        check("start line carries the common button", start.choices.size() == 2);

        Integer commonButton = start.choices.get(1);
        NpcDialogueGraph.Node commonCopy = merged.get(commonButton);
        check("common button copied", commonCopy != null
                && commonCopy.type == NpcDialogueGraph.BUTTON
                && "Common question".equals(commonCopy.text));
        check("fresh id", !common.nodes.containsKey(commonButton) && !own.nodes.containsKey(commonButton));

        NpcDialogueGraph.Node lineCopy = merged.get(commonCopy.next);
        check("chain continues to the common line", lineCopy != null
                && lineCopy.type == NpcDialogueGraph.LINE
                && "Common answer".equals(lineCopy.text));
        check("chain keeps its inner button", lineCopy.choices.size() == 1);
        NpcDialogueGraph.Node innerCopy = merged.get(lineCopy.choices.get(0));
        check("inner button copied", innerCopy != null && "Go deeper".equals(innerCopy.text));
        check("end of the common chain leads back to the NPC", innerCopy.next == intro.id);

        // Second shape: the entry is an IF that hands over to the opening line.
        NpcDialogueGraph own2 = new NpcDialogueGraph();
        NpcDialogueGraph.Node gate = own2.add(NpcDialogueGraph.IF, 0, 0);
        NpcDialogueGraph.Node line2 = own2.add(NpcDialogueGraph.LINE, 40, 0);
        line2.text = "Second shape";
        own2.connect(gate.id, line2.id);
        own2.entry = gate.id;
        NpcDialogueGraph merged2 = (NpcDialogueGraph) merge.invoke(null, own2, common, found);
        check("IF entry still resolves", merged2 != null && merged2.get(line2.id).choices.size() == 1);
        check("buttons sit on the first line, not on the IF",
                merged2.get(gate.id).choices.isEmpty());

        // A common graph with no free button changes nothing.
        NpcDialogueGraph noRoots = new NpcDialogueGraph();
        NpcDialogueGraph.Node onlyLine = noRoots.add(NpcDialogueGraph.LINE, 0, 0);
        noRoots.entry = onlyLine.id;
        @SuppressWarnings("unchecked")
        List<Integer> empty = (List<Integer>) roots.invoke(null, noRoots);
        check("no root buttons", empty.isEmpty());
        check("no merge without roots", merge.invoke(null, own, noRoots, empty) == null);

        System.out.println(failures == 0 ? "ALL OK" : ("FAILURES: " + failures));
        if (failures != 0) System.exit(1);
    }
}
