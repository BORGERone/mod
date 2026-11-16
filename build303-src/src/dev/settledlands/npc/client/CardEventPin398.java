package dev.settledlands.npc.client;

import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import net.minecraft.network.chat.Component;

/**
 * Build 398: the second execution socket of a card - «взлом» on the chest, «открытие» on the door.
 *
 * <p>The author's words: «ТАк-же добавь ноде главной у сундука узел \"взлом\" а главной ноде двери узел
 * \"открытие\"». Each card had exactly one of the two events, and the two events are not the same event:</p>
 *
 * <ul>
 *   <li>the <b>chest</b> had «открытие» (the chain runs when the chest is closed again) and had no socket for
 *       the moment it was <b>cracked</b> - a cracked chest is a different thing from a chest somebody unlocked
 *       and opened;</li>
 *   <li>the <b>door</b> had «взлом» (the chain runs when the door is hacked) and had no socket for a door that
 *       was simply <b>opened</b>.</li>
 * </ul>
 *
 * <p>So both cards get the missing one, and it is a socket of the card in exactly the sense of build 392 -
 * «не ноды, а УЗЛЫ на ноде»: a round amber dot two rows under the first one, on the same right edge, with its
 * own label in the strip the card keeps free for exactly this.</p>
 *
 * <p><b>Where the second chain is kept.</b> On {@code Node#falseNext} - the second output every node already
 * has, the one an «Условие If» uses for its "no" branch. It is written and read by the stock serialiser for
 * <b>every</b> node type (the lesson of build 384: a field a node type does not own is a field the save
 * drops), so a card's second chain survives a save without a single new field and without touching the store.
 * The card's own type is 40/50, and nothing in the stock interpreter walks {@code falseNext} of anything that
 * is not an IF node - so the field can only mean what this class says it means.</p>
 *
 * <p>Everything else here is geometry: where the socket is drawn, what it is called, and what it holds. The
 * dragging, the dropping and the cutting are in {@code CardEventPin398Mixin} - the stock screen gives port
 * index 1 to an IF node alone, and a card is not an IF node, so this pin needs its own three handlers.</p>
 */
public final class CardEventPin398 {

    /** The row of the first execution socket, where the canvas draws every node's output port. */
    public static final int FIRST_ROW = NpcGraphCanvas.NODE_H / 2;

    /** The row of the second one: two rows under it, clear of the blue diamond at {@code NODE_H - 8}. */
    public static final int SECOND_ROW = FIRST_ROW + 18;

    /** How close the cursor has to be to grab the socket, in card units before the zoom. */
    private static final double HIT = 7.0D;

    private CardEventPin398() {}

    /** True for the two cards of this mod - the only nodes with a second execution socket. */
    public static boolean isCard(Node node) {
        return DoorGraph382.isDoorNode(node) || ChestGraph396.isChestNode(node);
    }

    /** The node this card's second socket points at, or -1. */
    public static int secondChain(Node card) {
        return card == null ? -1 : card.falseNext;
    }

    /**
     * Points the second socket at a node, or clears it with -1.
     *
     * <p>Only a card is ever written: {@code falseNext} is an «Условие If» node's "no" branch, and a helper that
     * happily cleared it on any node would be a way for this build to break every conditional chain in the mod.
     * The probe of this build asks exactly this question and answered it once already.</p>
     */
    public static void setSecondChain(Node card, int target) {
        if (!isCard(card)) return;
        card.falseNext = target;
    }

    /**
     * The name of the socket: «взлом» on a chest, «открытие» on a door.
     *
     * <p>A chest's first socket is «открытие» and its second is «взлом»; a door's first is «взлом» and its
     * second is «открытие». The two cards keep the same order of rows, and it is the meaning that differs -
     * so the label is read from the card's own kind and never assumed.</p>
     */
    public static String labelKey(Node card) {
        return ChestGraph396.isChestNode(card)
                ? "gui.settledlands.chest.pin_hack" : "gui.settledlands.door.pin_open";
    }

    /** The label, in the language the client runs in. */
    public static String label(Node card) {
        try {
            return Component.translatable(labelKey(card)).getString();
        } catch (Throwable failure) {
            return labelKey(card);
        }
    }

    public static double pinScreenX(NpcGraphCanvas canvas, Node card) {
        return canvas == null || card == null ? 0.0D : canvas.portScreenX(card, true);
    }

    public static double pinScreenY(NpcGraphCanvas canvas, Node card) {
        return canvas == null || card == null ? 0.0D : canvas.screenY(card.y + SECOND_ROW);
    }

    /** The card whose second socket is under the cursor, or null. */
    public static Node cardAt(NpcDialogueGraph graph, NpcGraphCanvas canvas, double mouseX, double mouseY) {
        if (graph == null || canvas == null || graph.nodes == null) return null;
        double limit = Math.max(HIT * 0.65D, HIT * canvas.zoom);
        for (Node node : graph.nodes.values()) {
            if (!isCard(node)) continue;
            double dx = mouseX - pinScreenX(canvas, node);
            double dy = mouseY - pinScreenY(canvas, node);
            if (dx * dx + dy * dy <= limit * limit) return node;
        }
        return null;
    }

    /** The card whose second wire is under the cursor, or null - what a middle click cuts. */
    public static Node wireAt(NpcDialogueGraph graph, NpcGraphCanvas canvas, double mouseX, double mouseY) {
        if (graph == null || canvas == null || graph.nodes == null) return null;
        for (Node card : graph.nodes.values()) {
            if (!isCard(card)) continue;
            int target = secondChain(card);
            if (target < 0) continue;
            Node to = graph.get(target);
            if (to == null || to == card) continue;
            if (PursuitGraphNode272.hitValueWire(canvas, pinScreenX(canvas, card),
                    pinScreenY(canvas, card), canvas.portScreenX(to, false),
                    canvas.portScreenY(to, false, 0), mouseX, mouseY)) {
                return card;
            }
        }
        return null;
    }

    /** True when this card has a second wire to draw. */
    public static boolean hasWire(NpcDialogueGraph graph, Node card) {
        if (graph == null || card == null) return false;
        int target = secondChain(card);
        if (target < 0) return false;
        Node to = graph.get(target);
        return to != null && to != card;
    }

    /** The amber of a signal, the colour a pulse has had in this mod since build 386. */
    public static final int AMBER = -3177856;

    /** The colours the wire pass uses for the second socket's cable: the same amber, a shade deeper. */
    public static final int COL_AMBER_WIRE = -4678666;
}
