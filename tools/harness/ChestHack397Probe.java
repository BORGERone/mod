import dev.settledlands.chest.ChestGraph396;
import dev.settledlands.chest.ChestLock397;
import dev.settledlands.npc.NpcDialogueGraph;

/**
 * Build 397 probe: the chest's lock is a setting, and it lives on the chest's card.
 *
 * <p>The author's report was one line - «в графе сундука я не могу настраивать взлом» - so the question this
 * probe answers is exactly whether the four numbers of the lock panel can be written and read back on the
 * card, through the stock node fields a save keeps (build 384's lesson: a field a node does not own is a
 * field the save drops). No game, no world - only the classes from the jar.</p>
 *
 * <ul>
 *   <li><b>the defaults</b> - a fresh chest graph already carries the door's numbers, so the inspector's
 *       sliders start where the panel does and the first save stores real values instead of zeroes;</li>
 *   <li><b>the four setters</b> - keys, seconds, difficulty and the lock, written on the card and read back
 *       through the same four readers the server uses to build the panel;</li>
 *   <li><b>the ranges</b> - the setters clamp, so no hand-edited or mis-dragged value can reach the panel:
 *       10 keys is the ceiling, 1 second the floor, difficulty 1..3;</li>
 *   <li><b>the raw zeroes</b> - a card written by build 396 has three zeroes where the numbers are now, and
 *       reading it must give the defaults rather than a four-key, zero-second panel;</li>
 *   <li><b>the stock nodes</b> - a «Реплика» in a chest's graph is not a card, so none of the four setters
 *       may touch it.</li>
 * </ul>
 *
 * <pre>
 *   DEFAULTS keys=6 sec=5 diff=1 locked=false state=gui.settledlands.chest.state_open
 *   SET keys=4 sec=20 diff=3 locked=true state=gui.settledlands.chest.locked
 *   RANGE keys=10 sec=1 diff=1
 *   RAW keys=6 sec=5 diff=1
 *   STOCK untouched=true
 *   NOTE fields=6
 * </pre>
 */
public final class ChestHack397Probe {

    public static void main(String[] args) {
        // 1. A fresh chest graph: the card already carries the door's numbers.
        NpcDialogueGraph graph = ChestGraph396.defaultGraph();
        NpcDialogueGraph.Node card = ChestGraph396.chestNode(graph);
        System.out.println("DEFAULTS keys=" + ChestLock397.keys(card)
                + " sec=" + ChestLock397.seconds(card)
                + " diff=" + ChestLock397.difficulty(card)
                + " locked=" + ChestLock397.locked(card)
                + " state=" + ChestLock397.stateKeyShort(card));

        // 2. The four setters write the card, and the four readers give it back - what the author drags in
        //    the inspector is what the panel of this chest will ask for.
        ChestLock397.setKeys(card, 4);
        ChestLock397.setSeconds(card, 20);
        ChestLock397.setDifficulty(card, 3);
        ChestLock397.setLocked(card, true);
        System.out.println("SET keys=" + ChestLock397.keys(card)
                + " sec=" + ChestLock397.seconds(card)
                + " diff=" + ChestLock397.difficulty(card)
                + " locked=" + ChestLock397.locked(card)
                + " state=" + ChestLock397.stateKey(card));

        // 3. The ranges: the setters clamp, so nothing out of range can reach the panel.
        ChestLock397.setKeys(card, 99);
        ChestLock397.setSeconds(card, 0);
        ChestLock397.setDifficulty(card, -5);
        int keys = ChestLock397.keys(card);
        int sec = ChestLock397.seconds(card);
        int diff = ChestLock397.difficulty(card);
        System.out.println("RANGE keys=" + keys + " sec=" + sec + " diff=" + diff);

        // 4. A card written by build 396: three zeroes where the numbers are now. Reading it gives the
        //    defaults, never a zero-key or zero-second panel.
        NpcDialogueGraph old = ChestGraph396.defaultGraph();
        NpcDialogueGraph.Node legacy = ChestGraph396.chestNode(old);
        legacy.varCompare = 0;
        legacy.varOp = 0;
        legacy.checkTarget = 0;
        System.out.println("RAW keys=" + ChestLock397.keys(legacy)
                + " sec=" + ChestLock397.seconds(legacy)
                + " diff=" + ChestLock397.difficulty(legacy));

        // 5. A stock node of a chest's graph is not a card, and the lock never touches it.
        NpcDialogueGraph.Node line = old.add(NpcDialogueGraph.LINE, 320, 120);
        ChestLock397.setKeys(line, 8);
        ChestLock397.setSeconds(line, 12);
        ChestLock397.setDifficulty(line, 2);
        ChestLock397.setLocked(line, true);
        boolean untouched = line != null && line.varCompare == 0 && line.varOp == 0
                && line.checkTarget == 0 && !line.pointsShared;
        System.out.println("STOCK untouched=" + untouched);

        // 6. The mapping is written down where the next reader of this file will look for it.
        String note = ChestLock397.FIELD_NOTE;
        int fields = 0;
        int at = 0;
        while ((at = note.indexOf('=', at)) >= 0) {
            fields++;
            at++;
        }
        System.out.println("NOTE fields=" + fields);
    }
}
