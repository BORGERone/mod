package dev.settledlands.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Build 336: a "Реплика" node can pick one of its phrases at random.
 *
 * <p>The author flips a toggle on the node, writes several phrases separated by a newline, and the
 * node shows one of them: the dice are thrown once, when the toggle is flipped, and the result is
 * kept in the node itself - so from then on this node always says that phrase. It is a decision
 * about the node, not something re-rolled per conversation, which is what makes a written line
 * predictable while still being chosen at random.</p>
 *
 * <p>Where the flag and the result live: a graph node has no free field of its own, and adding one
 * means touching save, load, copy, the clipboard and the editor at once. The roll is kept in
 * {@code Node#varValue} - a saved integer that only the variable and the IF node's own branches
 * read, and whose default is 0. On a line node 0 therefore means "off" and 1..n means "on, and this
 * is the phrase"; one field carries both the flag and the result, it survives save, load, copy, undo
 * and the clipboard, and it reaches the server with the rest of the graph. {@link #FLAG_NOTE} keeps
 * that explained next to the code that depends on it.</p>
 *
 * <p>Blank lines are not phrases: they are dropped before the dice are thrown, so a stray empty line
 * cannot become a silent reply.</p>
 */
public final class NpcRandomLine336 {

    /** The saved node field this build borrows, and what it means on a line node. */
    public static final String FLAG_NOTE =
            "Node#varValue on a line node = 0 off, 1..n the rolled phrase";

    /** The flag that means "this node rolls for its phrase". See the class comment. */
    public static final String OFF_KEY = "gui.settledlands.graph.rand_line_off";

    /** Shown when the toggle is on: "{rolled} of {count}". */
    public static final String ON_KEY = "gui.settledlands.graph.rand_line_on";

    private NpcRandomLine336() {
    }

    /** True for the node this build is about: a plain "Реплика", not a travel/arrival line. */
    public static boolean isLine(NpcDialogueGraph.Node node) {
        return node != null
                && node.type == NpcDialogueGraph.LINE
                && node.special == NpcDialogueGraph.NORMAL;
    }

    public static boolean random(NpcDialogueGraph.Node node) {
        return isLine(node) && roll(node) > 0;
    }

    /** The phrase the node settled on, 1-based; 0 means the toggle is off. */
    public static int roll(NpcDialogueGraph.Node node) {
        return node == null ? 0 : node.varValue;
    }

    private static void setRoll(NpcDialogueGraph.Node node, int rolled) {
        node.varValue = Math.max(0, rolled);
    }

    /**
     * The phrases of the node, in order. Phrase boundaries are newlines; lines that are empty once
     * trimmed are not phrases.
     */
    public static List<String> phrases(String text) {
        List<String> out = new ArrayList<>(4);
        if (text == null || text.isEmpty()) return out;
        int start = 0;
        int length = text.length();
        for (int i = 0; i <= length; i++) {
            if (i == length || text.charAt(i) == '\n') {
                int end = i;
                if (end > start && text.charAt(end - 1) == '\r') end--;
                String line = text.substring(start, end);
                if (!line.isBlank()) out.add(line);
                start = i + 1;
            }
        }
        return out;
    }

    public static int count(NpcDialogueGraph.Node node) {
        return node == null ? 0 : phrases(node.text).size();
    }

    /** Index into {@link #phrases} for the stored roll, clamped into range. */
    public static int index(NpcDialogueGraph.Node node, int count) {
        if (count <= 0) return -1;
        int stored = roll(node);
        int idx = stored <= 0 ? 0 : stored - 1;
        return Math.max(0, Math.min(count - 1, idx));
    }

    /**
     * The phrase this node has to say, or {@code null} when that is not for this build to decide -
     * the toggle is off, or there is only one phrase to say.
     */
    public static String pick(NpcDialogueGraph.Node node) {
        if (!random(node)) return null;
        List<String> lines = phrases(node.text);
        if (lines.size() < 2) return null;
        int idx = index(node, lines.size());
        return idx < 0 ? null : lines.get(idx);
    }

    /** Throws the dice for this node and stores the result. Returns the rolled number. */
    public static int reroll(NpcDialogueGraph.Node node) {
        if (node == null) return 0;
        int count = Math.max(1, count(node));
        int rolled = 1 + ThreadLocalRandom.current().nextInt(count);
        setRoll(node, rolled);
        return rolled;
    }

    /** Flips the toggle. Turning it on throws the dice for the text as it is right now. */
    public static boolean toggle(NpcDialogueGraph.Node node) {
        if (!isLine(node)) return false;
        if (roll(node) > 0) {
            setRoll(node, 0);
            return false;
        }
        reroll(node);
        return true;
    }

    /** Turns the toggle on without asking, for tests and for restoring a node from data. */
    public static void enable(NpcDialogueGraph.Node node, int rolled) {
        if (!isLine(node)) return;
        setRoll(node, Math.max(1, rolled));
    }
}
