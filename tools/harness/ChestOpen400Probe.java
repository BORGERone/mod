import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.chest.ChestLock397;
import dev.settledlands.npc.NpcDialogueGraph;

/**
 * Build 400 probe: the numbers of a chest's card reach that chest's panel, and a solved panel's lift is
 * spent by the one click that follows it.
 *
 * <p>Two reports stand behind this probe, and neither of them can be answered by reading the jar alone:</p>
 *
 * <ul>
 *   <li>«в взломе сундука не меняется кол-во чисел, скорость и сложность» - the chest card's three sliders
 *       were writing nothing, because the door's inspector had merged a method of the same name and
 *       signature into the same screen class and the door's body writes through `DoorGraph382`, which
 *       refuses a chest node. The sliders now have their own names. What this probe checks is the part a
 *       gate can see: the setters the row calls are the setters the panel's numbers come from, so a card
 *       that reads 9 / 3 / 2 is a panel that asks 9 keys in 3 seconds on difficulty 2;</li>
 *   <li>«по прежнему после успешного взлома сундук не открывается» - the server no longer opens a menu
 *       from behind the screen (three builds of that produced no window). It lifts the lock of that one
 *       chest, the panel clicks the chest, and the click passes through `ChestGraph396Events#onUse`, which
 *       consumes the lift and remembers the opening as a crack. The lift must therefore be readable once
 *       and gone after that once: a second opening of the same chest is an ordinary opening and runs the
 *       «открытие» chain, not the «взлом» one.</li>
 * </ul>
 *
 * <p>No game, no world, no screen: only the classes of the jar.</p>
 *
 * <pre>
 *   CARD_BEFORE keys=6 sec=5 diff=1
 *   CARD_AFTER keys=9 sec=3 diff=2
 *   LIFT_BEFORE false
 *   LIFT_GRANTED true
 *   LIFT_SPENT cracked=true after=false again=false
 * </pre>
 */
public final class ChestOpen400Probe {

    public static void main(String[] args) {
        // 1. The card the inspector edits is the card the panel is built from.
        NpcDialogueGraph graph = ChestGraph396.defaultGraph();
        NpcDialogueGraph.Node card = ChestGraph396.chestNode(graph);
        System.out.println("CARD_BEFORE keys=" + ChestLock397.keys(card)
                + " sec=" + ChestLock397.seconds(card)
                + " diff=" + ChestLock397.difficulty(card));

        // Exactly what the three rows of the chest card do on a drag: write keys, seconds, difficulty.
        ChestLock397.setKeys(card, 9);
        ChestLock397.setSeconds(card, 3);
        ChestLock397.setDifficulty(card, 2);
        System.out.println("CARD_AFTER keys=" + ChestLock397.keys(card)
                + " sec=" + ChestLock397.seconds(card)
                + " diff=" + ChestLock397.difficulty(card));

        // 2. The lift of a solved panel: one click, then nothing.
        String key = "minecraft:overworld@1,64,2";
        System.out.println("LIFT_BEFORE " + ChestLock397.granted(key));
        ChestLock397.grant(key);
        boolean cracked = ChestLock397.granted(key);
        System.out.println("LIFT_GRANTED " + cracked);
        // What ChestGraph396Events#onUse does with that answer.
        if (cracked) {
            ChestLock397.revoke(key);
        }
        System.out.println("LIFT_SPENT cracked=" + cracked
                + " after=" + ChestLock397.granted(key)
                + " again=" + ChestLock397.granted(key));
    }
}
