import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcRandomLine336;

import java.util.List;

/**
 * Runs the roll of build 336 on real graph nodes: what counts as a phrase, what the node says for
 * a given roll, and what the toggle does. No world and no screen - this is the node's own decision.
 */
public class RandomLineSmokeTest {

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
        line.text = "first\nsecond\n\nthird\r\n";

        List<String> phrases = NpcRandomLine336.phrases(line.text);
        check("blank and CRLF lines are not phrases",
                phrases.size() == 3 && phrases.get(0).equals("first") && phrases.get(2).equals("third"));

        check("toggle is off by default", !NpcRandomLine336.random(line));
        check("nothing is picked while it is off", NpcRandomLine336.pick(line) == null);

        int rolled = NpcRandomLine336.reroll(line);
        check("the dice stay inside the phrases", rolled >= 1 && rolled <= 3);
        check("the roll is written into the node", line.varValue == rolled);

        line.varValue = 2;
        check("roll 2 says the second phrase", "second".equals(NpcRandomLine336.pick(line)));

        line.varValue = 3;
        check("roll 3 says the third phrase", "third".equals(NpcRandomLine336.pick(line)));

        line.varValue = 9;
        check("a roll past the end is clamped, not lost", "third".equals(NpcRandomLine336.pick(line)));

        line.varValue = 0;
        check("0 is off", NpcRandomLine336.pick(line) == null);

        line.varValue = 2;
        check("toggling off returns false and clears the roll",
                !NpcRandomLine336.toggle(line) && line.varValue == 0);
        check("toggling on rolls again", NpcRandomLine336.toggle(line) && line.varValue >= 1);

        NpcDialogueGraph.Node travel = graph.add(NpcDialogueGraph.LINE, 10, 0);
        travel.special = NpcDialogueGraph.TRAVEL;
        travel.text = "a\nb";
        check("a travel line is not touched", !NpcRandomLine336.isLine(travel) && NpcRandomLine336.toggle(travel) == false);

        NpcDialogueGraph.Node button = graph.add(NpcDialogueGraph.BUTTON, 20, 0);
        button.text = "a\nb";
        check("a button is not a line", !NpcRandomLine336.isLine(button));

        NpcDialogueGraph.Node single = graph.add(NpcDialogueGraph.LINE, 30, 0);
        single.text = "only one";
        single.varValue = 1;
        check("a single phrase is just the text", NpcRandomLine336.pick(single) == null);

        // The fields the toggle borrows must travel with the graph.
        line.varValue = 2;
        NpcDialogueGraph.Node copy = line.copy();
        check("copy carries the roll", copy.varValue == 2);
        NpcDialogueGraph restored = NpcDialogueGraph.load(graph.save());
        NpcDialogueGraph.Node back = restored.get(line.id);
        check("save/load carries the roll", back != null && back.varValue == 2);

        System.out.println(failures == 0 ? "ALL OK" : ("FAILURES: " + failures));
        if (failures != 0) System.exit(1);
    }
}
