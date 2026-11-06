import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.npc.NpcDialogueGraph;

/**
 * Build 396 probe: a chest's graph holds one card of this mod, and that card's two outputs are sockets,
 * not nodes.
 *
 * <p>The author's words: «Все прекрасно, теперь я хочу сделать такой-же граф для сундуков». «Такой же» is the
 * door's design, and build 392 fixed its shape for good: «я хотел чтобы ты создал не ноды, а УЗЛЫ на ноде
 * "дверь"». So the three things that must be true of the chest's card can be walked here without a game:</p>
 *
 * <ul>
 *   <li><b>the card</b> - a fresh chest graph holds exactly one node, that node is the chest card, and it is
 *       the entry the editor starts from;</li>
 *   <li><b>the two outputs</b> - the card has an execution socket (the round one, on the execution row) and a
 *       value socket (the blue diamond). The execution socket is the chain's start: an unwired chest has no
 *       chain at all, and a wired one starts the chain at the node it points to, not at the card and not one
 *       node later (build 393's rule);</li>
 *   <li><b>the card's memory</b> - how many times the chest was opened, and who opened it last. Both are
 *       written into fields the stock serialiser keeps for every node, which is build 384's lesson: a field a
 *       node type does not own is a field the save drops.</li>
 * </ul>
 *
 * <pre>
 *   TYPE own=1 stock=false      the chest card's type is this mod's own, outside the stock range
 *   CARD nodes=1 card=true entry=true
 *   CHAIN unwired=none wired=2 stop=2
 *   MEMORY count=3 opener=BORGER long=32
 *   STRIP dropped=2 left=2 entry=kept
 *   ID in=true back=true stable=true
 * </pre>
 */
public final class ChestGraph396Probe {

    public static void main(String[] args) {
        // 1. The card's type is this mod's own and cannot be mistaken for a stock node.
        boolean stock = ChestGraph396.CHEST_TYPE >= 0
                && ChestGraph396.CHEST_TYPE <= NpcDialogueGraph.CUTSCENE;
        System.out.println("TYPE own=" + (ChestGraph396.CHEST_TYPE == 50 ? 1 : 0) + " stock=" + stock);

        // 2. A fresh graph: one node, the card, and the editor opens on it.
        NpcDialogueGraph graph = ChestGraph396.defaultGraph();
        NpcDialogueGraph.Node card = ChestGraph396.chestNode(graph);
        boolean one = graph.nodes.size() == 1;
        boolean isCard = card != null && card.type == ChestGraph396.CHEST_TYPE;
        boolean entry = card != null && graph.entry == card.id;
        System.out.println("CARD nodes=" + graph.nodes.size() + " card=" + isCard + " entry=" + entry);
        if (!one) System.out.println("CARD-WARN the fresh graph holds " + graph.nodes.size() + " nodes");

        // 3. The two outputs are sockets of the card, not nodes: an unwired card has no chain, a wired one
        //    starts the chain at the node its execution socket points to - and only there.
        NpcDialogueGraph.Node start = ChestGraph396.chainStart(graph);
        NpcDialogueGraph.Node line = graph.add(NpcDialogueGraph.LINE, 320, 120);
        NpcDialogueGraph.Node other = graph.add(NpcDialogueGraph.LINE, 520, 120);
        int wiredId = line.id;
        card.next = wiredId;
        NpcDialogueGraph.Node wired = ChestGraph396.chainStart(graph);
        card.next = -1;
        NpcDialogueGraph.Node stop = ChestGraph396.chainStart(graph);
        System.out.println("CHAIN unwired=" + (start == null ? "none" : Integer.toString(start.id))
                + " wired=" + (wired == null ? "none" : Integer.toString(wired.id))
                + " stop=" + (stop == null ? "none" : Integer.toString(stop.id)));
        if (other == null || line == null) System.out.println("CHAIN-WARN the stock nodes could not be added");

        // 4. The card remembers the two facts the author reads on it.
        ChestGraph396.setCount(card, 0);
        ChestGraph396.setCount(card, ChestGraph396.count(card) + 1);
        ChestGraph396.setCount(card, ChestGraph396.count(card) + 1);
        ChestGraph396.setCount(card, ChestGraph396.count(card) + 1);
        ChestGraph396.setOpener(card, "   ");
        ChestGraph396.setOpener(card, " BORGER ");
        String kept = ChestGraph396.opener(card);
        ChestGraph396.setOpener(card, "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefghij");
        int length = ChestGraph396.opener(card).length();
        System.out.println("MEMORY count=" + ChestGraph396.count(card) + " opener=" + kept
                + " long=" + length);
        ChestGraph396.setOpener(card, "BORGER");

        // 5. A chest graph holds the chest and what the author wired: never the two cards the editor adds.
        NpcDialogueGraph.Node stats = graph.add(BlockNameType.STATS, 720, 120);
        NpcDialogueGraph.Node name = graph.add(BlockNameType.NAME, 720, 320);
        int dropped = ChestGraph396.stripCards(graph);
        boolean keptEntry = graph.entry >= 0 && graph.get(graph.entry) != null;
        System.out.println("STRIP dropped=" + dropped + " left=" + graph.nodes.size()
                + " entry=" + (keptEntry ? "kept" : "lost"));
        if (stats == null || name == null) System.out.println("STRIP-WARN the cards could not be added");

        // 6. The ids of the chests are their own range, and a key comes back as the chest it was made from.
        String key = "minecraft:overworld@12,64,-30";
        int first = ChestGraph396.idFor(key);
        int second = ChestGraph396.idFor(key);
        String back = ChestGraph396.keyOf(first);
        System.out.println("ID in=" + ChestGraph396.isChestEditorId(first)
                + " back=" + key.equals(back) + " stable=" + (first == second));
    }

    /** The two cards the editor adds to every graph it opens - {@code BlockName359}'s own numbers. */
    private static final class BlockNameType {
        static final int STATS = 16;
        static final int NAME = 18;

        private BlockNameType() {}
    }
}
