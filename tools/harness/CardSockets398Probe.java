import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.chest.ChestLock397;
import dev.settledlands.door.DoorGraph382;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.client.CardEventPin398;

/**
 * Build 398 probe: the second socket of a card, and the two things that must never go wrong with it.
 *
 * <p>The author asked for one more socket on each card - «добавь ноде главной у сундука узел "взлом" а главной
 * ноде двери узел "открытие"». A socket is only worth having if the chain behind it is the <b>other</b> chain:
 * the first socket of a card is {@code Node#next}, and the whole feature collapses into a bug the moment the
 * second one writes the same field - the author would wire «взлом», and «открытие» would move with it.</p>
 *
 * <ul>
 *   <li><b>the two chains are two fields</b> - the second socket writes {@code Node#falseNext} and leaves
 *       {@code next} exactly where it was, on a chest card and on a door card alike;</li>
 *   <li><b>the socket is only a card's</b> - a «Реплика» or a «Триггер» in the same graph is not a card, and
 *       nothing in this class touches one;</li>
 *   <li><b>the names</b> - the chest's second socket is «взлом» and the door's is «открытие», read from the
 *       card's own kind and never assumed;</li>
 *   <li><b>each runtime reads its own field</b> - {@code ChestGraph396#hackChainStart} and
 *       {@code DoorGraph382#openChainStart} follow the second socket, while {@code chainStart} still follows the
 *       first, so a cracked chest and a hand-opened door run what the author wired to them;</li>
 *   <li><b>the fallback</b> - a graph of build 396/397 has only the first socket wired, so a cracked chest must
 *       still find a chain;</li>
 *   <li><b>the crack</b> - {@code ChestLock397#crack}/{@code #cracked} opens one chest, for the player whose
 *       panel was solved, and no other chest and no other player (build 401: a «Реплика» is read at the
 *       player's pace, so the lift of build 400 could be gone before the chain was over).</li>
 * </ul>
 *
 * <pre>
 *   SECOND fields=next:true falseNext:true apart=true
 *   FIRST next=1 falseNext=2
 *   FALLBACK wired=1
 *   DOOR second=2 first=1 apart=true
 *   NAMES chest=gui.settledlands.chest.pin_hack door=gui.settledlands.door.pin_open
 *   STOCK untouched=true
 *   CRACK mine=true other=false player=false
 * </pre>
 */
public final class CardSockets398Probe {

    public static void main(String[] args) {
        // 1. A chest card: the first socket points at one node and the second at another, in two fields.
        NpcDialogueGraph chestGraph = ChestGraph396.defaultGraph();
        NpcDialogueGraph.Node chest = ChestGraph396.chestNode(chestGraph);
        NpcDialogueGraph.Node first = chestGraph.add(NpcDialogueGraph.LINE, 320, 120);
        NpcDialogueGraph.Node second = chestGraph.add(NpcDialogueGraph.LINE, 320, 320);
        chest.next = first.id;
        CardEventPin398.setSecondChain(chest, second.id);
        boolean isCard = CardEventPin398.isCard(chest);
        boolean apart = chest.next == first.id && CardEventPin398.secondChain(chest) == second.id
                && chest.next != CardEventPin398.secondChain(chest);
        System.out.println("SECOND fields=next:" + isCard + " falseNext:"
                + CardEventPin398.hasWire(chestGraph, chest) + " apart=" + apart);

        // 2. Both chains still start where they were put: the ordinary one from next, the second from falseNext.
        NpcDialogueGraph.Node firstStart = ChestGraph396.chainStart(chestGraph);
        NpcDialogueGraph.Node secondStart = ChestGraph396.hackChainStart(chestGraph);
        System.out.println("FIRST next=" + (firstStart == null ? "none" : firstStart.id)
                + " falseNext=" + (secondStart == null ? "none" : secondStart.id));

        // 3. A graph of build 396/397: only the first socket is wired, and the crack still finds a chain.
        NpcDialogueGraph old = ChestGraph396.defaultGraph();
        NpcDialogueGraph.Node oldChest = ChestGraph396.chestNode(old);
        NpcDialogueGraph.Node only = old.add(NpcDialogueGraph.LINE, 320, 120);
        oldChest.next = only.id;
        oldChest.falseNext = -1;
        NpcDialogueGraph.Node second404 = ChestGraph396.hackChainStart(old);
        boolean fallback = second404 == null && ChestGraph396.chainStart(old) != null;
        System.out.println("FALLBACK wired=" + (fallback
                ? ChestGraph396.chainStart(old).id : "none"));

        // 4. The same on a door card: two sockets, two fields, two chains.
        NpcDialogueGraph doorGraph = DoorGraph382.defaultGraph(false);
        NpcDialogueGraph.Node door = DoorGraph382.doorNode(doorGraph);
        NpcDialogueGraph.Node hack = doorGraph.add(NpcDialogueGraph.LINE, 320, 120);
        NpcDialogueGraph.Node opened = doorGraph.add(NpcDialogueGraph.LINE, 320, 320);
        door.next = hack.id;
        CardEventPin398.setSecondChain(door, opened.id);
        NpcDialogueGraph.Node hackStart = DoorGraph382.chainStart(doorGraph);
        NpcDialogueGraph.Node openStart = DoorGraph382.openChainStart(doorGraph);
        System.out.println("DOOR second=" + (openStart == null ? "none" : openStart.id)
                + " first=" + (hackStart == null ? "none" : hackStart.id)
                + " apart=" + (hackStart != null && openStart != null
                        && hackStart.id != openStart.id));

        // 5. The names of the second sockets, each from its own card.
        System.out.println("NAMES chest=" + CardEventPin398.labelKey(chest)
                + " door=" + CardEventPin398.labelKey(door));

        // 6. A stock node is not a card: the setters leave it alone.
        NpcDialogueGraph.Node line = chestGraph.add(NpcDialogueGraph.LINE, 520, 120);
        CardEventPin398.setSecondChain(line, second.id);
        boolean untouched = line != null && line.falseNext == -1 && !CardEventPin398.isCard(line)
                && CardEventPin398.secondChain(line) == -1;
        System.out.println("STOCK untouched=" + untouched);

        // 7. The crack of a solved panel: this chest, this player, and no other of either.
        String key = ChestGraph396.keyOf(ChestGraph396.idFor("minecraft:overworld@5,70,5"));
        ChestLock397.crack("BORGER", key);
        System.out.println("CRACK mine=" + ChestLock397.cracked("BORGER", key)
                + " other=" + ChestLock397.cracked("BORGER", key + "x")
                + " player=" + ChestLock397.cracked("Steve", key));
    }
}
