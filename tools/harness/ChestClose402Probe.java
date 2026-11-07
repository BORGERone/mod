import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.npc.NpcDialogueGraph;

/**
 * Build 402 probe: on the author's own card, the close of a chest follows «открытие» and nothing else.
 *
 * <p>The report, with his screenshot: «Сундук» → «открытие» → «Реплика #1», and «взлом» empty. «Я бы хотел
 * чтобы реплика проигрывалась не после взлома, а уже после открытия инвентаря сундука и его закрытия, как было
 * до этого апдейта».</p>
 *
 * <p>This probe builds exactly that card and asks the two questions the two sockets answer:</p>
 *
 * <ul>
 *   <li>{@code closeChainStart} - what a close runs. On his card it must be the node under «открытие», and it
 *       must be the same answer whether the chest was opened by hand or cracked: a cracked chest is not a
 *       third case;</li>
 *   <li>{@code hackChainStart} - what runs before the opening. On his card it must be <b>nothing</b>: with
 *       «взлом» empty there is no line before the chest, and the build-401 fallback that pulled «открытие»
 *       forward is gone.</li>
 * </ul>
 *
 * <pre>
 *   AUTHOR next=1 falseNext=-1 close=1 hack=none
 *   BOTH next=1 falseNext=2 close=1 hack=2
 *   EMPTY close=none hack=none
 * </pre>
 */
public final class ChestClose402Probe {

    public static void main(String[] args) {
        // 1. The author's card: «открытие» → «Реплика #1», «взлом» empty.
        NpcDialogueGraph author = ChestGraph396.defaultGraph();
        NpcDialogueGraph.Node card = ChestGraph396.chestNode(author);
        NpcDialogueGraph.Node line = author.add(NpcDialogueGraph.LINE, 320, 120);
        card.next = line.id;             // «открытие»
        card.falseNext = -1;             // «взлом» - not wired
        System.out.println("AUTHOR next=" + card.next + " falseNext=" + card.falseNext
                + " close=" + id(ChestGraph396.closeChainStart(author))
                + " hack=" + id(ChestGraph396.hackChainStart(author)));

        // 2. Both sockets wired: they are two moments, and they are two different nodes.
        NpcDialogueGraph both = ChestGraph396.defaultGraph();
        NpcDialogueGraph.Node card2 = ChestGraph396.chestNode(both);
        NpcDialogueGraph.Node opened = both.add(NpcDialogueGraph.LINE, 320, 120);
        NpcDialogueGraph.Node locked = both.add(NpcDialogueGraph.LINE, 320, 320);
        card2.next = opened.id;
        card2.falseNext = locked.id;
        System.out.println("BOTH next=" + card2.next + " falseNext=" + card2.falseNext
                + " close=" + id(ChestGraph396.closeChainStart(both))
                + " hack=" + id(ChestGraph396.hackChainStart(both)));

        // 3. Nothing wired at all: both moments are silent, and neither borrows the other's answer.
        NpcDialogueGraph empty = ChestGraph396.defaultGraph();
        NpcDialogueGraph.Node card3 = ChestGraph396.chestNode(empty);
        card3.next = -1;
        card3.falseNext = -1;
        System.out.println("EMPTY close=" + id(ChestGraph396.closeChainStart(empty))
                + " hack=" + id(ChestGraph396.hackChainStart(empty)));
    }

    private static String id(NpcDialogueGraph.Node node) {
        return node == null ? "none" : Integer.toString(node.id);
    }
}
