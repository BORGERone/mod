import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcGraphClipboard318;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless round trip for Build 318: copy a branch, paste it into another graph, compare.
 *
 * <p>Run by {@code tools/clipboard_check.py}; no Minecraft classes are touched because the graph
 * model and the clipboard format are plain Java.</p>
 */
public final class ClipboardRoundTrip {

    private static int failures = 0;

    public static void main(String[] args) {
        NpcDialogueGraph source = new NpcDialogueGraph();
        Node entry = source.add(NpcDialogueGraph.LINE, 0, 0);
        source.entry = entry.id;
        Node line = source.add(NpcDialogueGraph.LINE, 120, 40);
        line.text = "Привет, \"мир\"\nвторая строка\tтаб";
        line.action = "say:hello";
        line.relation = 2;
        Node button = source.add(NpcDialogueGraph.BUTTON, 240, 40);
        button.text = "Кнопка";
        Node check = source.add(NpcDialogueGraph.IF, 360, 40);
        check.varValue = 7;
        check.varCompare = 1;
        check.varOp = 2;
        check.damageCooldown = 120;
        check.moveSpeed = 1.25;
        check.triggers.add(button.id);
        check.triggers.add(entry.id);        // outside the copied set: must be dropped
        button.choices.add(check.id);
        line.next = button.id;
        check.falseNext = line.id;
        check.varInputNode = line.id;
        check.next = 999;                       // points outside the copied set
        check.variants.add(new dev.settledlands.npc.NpcProfile.Variant(3, "второй: текст"));

        List<Integer> ids = new ArrayList<>();
        ids.add(line.id);
        ids.add(button.id);
        ids.add(check.id);

        String text = NpcGraphClipboard318.copy(source, ids);
        check$(!text.isEmpty(), "copy produced nothing");
        check$(text.startsWith(NpcGraphClipboard318.HEADER), "missing header");
        check$(NpcGraphClipboard318.preview(text) == 3, "preview is not 3");
        check$(!text.contains("id=" + entry.id + " "), "the entry node was copied");

        NpcDialogueGraph target = new NpcDialogueGraph();
        Node targetEntry = target.add(NpcDialogueGraph.LINE, 0, 0);
        target.entry = targetEntry.id;

        List<Integer> added = NpcGraphClipboard318.paste(target, text);
        check$(added != null, "paste failed: " + NpcGraphClipboard318.lastError());
        if (added == null) {
            finish();
            return;
        }
        check$(added.size() == 3, "paste added " + added.size() + " nodes, not 3");
        check$(target.nodes.size() == 4, "target graph has " + target.nodes.size() + " nodes");

        Node copyLine = target.get(added.get(0));
        Node copyButton = target.get(added.get(1));
        Node copyCheck = target.get(added.get(2));
        check$(copyLine != null && copyButton != null && copyCheck != null, "pasted node missing");

        check$(copyLine.text.equals(line.text), "text did not survive: " + copyLine.text);
        check$(copyLine.action.equals(line.action), "action did not survive");
        check$(copyLine.relation == 2, "relation did not survive");
        check$(copyLine.x == line.x + NpcGraphClipboard318.PASTE_OFFSET, "x offset wrong");
        check$(copyLine.y == line.y + NpcGraphClipboard318.PASTE_OFFSET, "y offset wrong");

        check$(copyLine.next == copyButton.id, "next was not remapped: " + copyLine.next);
        check$(copyButton.choices.size() == 1 && copyButton.choices.get(0) == copyCheck.id,
                "choices were not remapped: " + copyButton.choices);
        check$(copyCheck.falseNext == copyLine.id, "falseNext was not remapped");
        check$(copyCheck.varInputNode == copyLine.id, "the blue wire was not remapped");
        check$(copyCheck.next == -1, "an external link was kept: " + copyCheck.next);
        check$(copyCheck.triggers.size() == 1 && copyCheck.triggers.get(0) == copyButton.id,
                "triggers were not remapped: " + copyCheck.triggers);
        check$(copyCheck.varValue == 7 && copyCheck.varCompare == 1 && copyCheck.varOp == 2,
                "variable fields did not survive");
        check$(copyCheck.damageCooldown == 120, "damage cooldown did not survive");
        check$(Math.abs(copyCheck.moveSpeed - 1.25) < 1e-9, "move speed did not survive");
        check$(copyCheck.variants.size() == 1
                && copyCheck.variants.get(0).minRelation == 3
                && copyCheck.variants.get(0).text.equals("второй: текст"),
                "variants did not survive");

        int[] box = NpcGraphClipboard318.bounds(text);
        check$(box != null && box[0] == Math.min(line.x, button.x) - 0,
                "bounds are wrong: " + java.util.Arrays.toString(box));
        NpcDialogueGraph centered = new NpcDialogueGraph();
        centered.add(NpcDialogueGraph.LINE, 0, 0);
        List<Integer> placed = NpcGraphClipboard318.pasteCentered(centered, text, 1000.0D, 500.0D);
        check$(placed != null && placed.size() == 3, "centered paste failed");
        if (placed != null && placed.size() == 3 && box != null) {
            int wantDx = (int) (Math.round((1000.0D - (box[0] + box[2]) / 2.0D) / 8.0D) * 8);
            int wantDy = (int) (Math.round((500.0D - (box[1] + box[3]) / 2.0D) / 8.0D) * 8);
            Node placedLine = centered.get(placed.get(0));
            check$(placedLine.x == line.x + wantDx, "centered x is " + placedLine.x
                    + ", want " + (line.x + wantDx));
            check$(placedLine.y == line.y + wantDy, "centered y is " + placedLine.y
                    + ", want " + (line.y + wantDy));
            int midX = (box[0] + box[2]) / 2 + wantDx;
            check$(Math.abs(midX - 1000) <= 8, "the block is not centred: " + midX);
        }

        check$(NpcGraphClipboard318.copy(source, List.of(entry.id)).isEmpty(),
                "the entry node alone was copied");
        check$(NpcGraphClipboard318.paste(target, "hello") == null, "garbage was accepted");
        check$(!NpcGraphClipboard318.lastError().isEmpty(), "garbage left no error");
        check$(NpcGraphClipboard318.preview("hello") == -1, "garbage preview is not -1");
        check$(target.nodes.size() == 4, "a failed paste changed the graph");

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
            System.out.println("clipboard round trip: OK");
            return;
        }
        System.out.println("clipboard round trip: " + failures + " failure(s)");
        System.exit(1);
    }
}
