package dev.settledlands.npc;

/**
 * Build 337: a node cannot be wired to itself.
 *
 * <p>A node that points at itself is a dialogue that never ends: the line is shown, the player asks
 * for the next one, the next one is the same node, and so on. It is also one drag away - releasing a
 * wire on the node it started from looks exactly like releasing it next to that node, and the graph
 * keeps the link without a word. So the link is refused where every wire of the editor is made:
 * {@code NpcDialogueGraph#connect(from, to, port)} - next, false-branch, variable input, answers and
 * triggers all pass through it.</p>
 *
 * <p>Refusing is not enough on its own: the editor prints a generic "these two cannot be linked"
 * line, which reads like a wrong kind of node rather than a wrong target. {@link #consumeRefusal()}
 * lets the screen replace it with the one sentence that says what actually happened. The flag is a
 * {@link ThreadLocal} because the same class answers on both sides: whoever asks gets the answer for
 * the link it just tried.</p>
 */
public final class NpcSelfLink337 {

    /** Shown by the editor when a node is dropped onto itself. */
    public static final String SELF_KEY = "gui.settledlands.graph.self_link";

    private static final ThreadLocal<Boolean> REFUSED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private NpcSelfLink337() {
    }

    /** True when this pair of ids is one node and itself. */
    public static boolean isSelfLink(int from, int to) {
        return from >= 0 && from == to;
    }

    /** Notes that the last {@code connect} call was refused for this reason. */
    public static void refuse() {
        REFUSED.set(Boolean.TRUE);
    }

    /** True once, for the link that was just refused; false otherwise. */
    public static boolean consumeRefusal() {
        if (!Boolean.TRUE.equals(REFUSED.get())) return false;
        REFUSED.remove();
        return true;
    }
}
