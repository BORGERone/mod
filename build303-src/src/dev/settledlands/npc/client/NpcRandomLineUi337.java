package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.NpcRandomLine336;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Build 337: the "случайная реплика" toggle, as the inspector's own on/off row.
 *
 * <p>Build 336 painted a check box over the card. This build drops that and uses what every other
 * node's inspector already does for a yes/no property - a full-width button whose label is the state,
 * flipping on click - because that is the control the screen already knows how to scroll, clip and
 * undo. Nothing is drawn by hand any more: the row is a real widget, so it takes the click, the scroll
 * and the rebuild exactly like the stock rows next to it.</p>
 *
 * <p>The label carries the roll, so the author reads "строка 2 из 5" without opening anything, and it
 * is rebuilt after every flip.</p>
 */
public final class NpcRandomLineUi337 {

    private NpcRandomLineUi337() {
    }

    /** The button label for this node's state. */
    public static Component label(Node node) {
        if (node == null || !NpcRandomLine336.random(node)) {
            return Component.translatable(NpcRandomLine336.OFF_KEY);
        }
        List<String> lines = NpcRandomLine336.phrases(node.text);
        int count = Math.max(1, lines.size());
        int rolled = NpcRandomLine336.index(node, count) + 1;
        return Component.translatable(NpcRandomLine336.ON_KEY, rolled, count);
    }
}
