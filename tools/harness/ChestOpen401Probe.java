import dev.settledlands.chest.ChestLock397;

/**
 * Build 401 probe: the crack belongs to one player and one chest, it is spent by one click, and the click
 * that opens a cracked chest waits where the chain can see it.
 *
 * <p>Three reports stand behind this probe:</p>
 *
 * <ul>
 *   <li>«для сундука тоже сначала реплика, потом инвентарь» - build 400 lifted a chest's lock for <b>ten
 *       seconds</b> and let whoever clicked first spend it. A «Реплика» is read at the player's own pace, so
 *       the lift could be gone before the window of the chain was even closed - and anyone else could spend
 *       it. The crack is now keyed by the player who solved the panel <b>and</b> by the chest, and it lasts
 *       ten minutes;</li>
 *   <li>one click, then nothing: the crack must answer true exactly once, so the second opening of the same
 *       chest by the same player is an ordinary opening and runs «открытие», not «взлом» again;</li>
 *   <li>the click of a solved chest is no longer the panel's ({@code HackPanel377#onClose} would open the
 *       chest before the chain had said a word). It is {@code ChestOpen401}'s, and what this probe checks is
 *       that a request can be armed and forgotten without a client: the class must survive being touched on
 *       a machine with no game, which is also what the gate's own run proves.</li>
 * </ul>
 *
 * <pre>
 *   CRACK_BEFORE false
 *   CRACK_ONE true
 *   CRACK_OTHER false
 *   CRACK_SPENT cracked=true take1=true take2=false
 *   ARM_BEFORE false
 *   ARM_READY true
 *   ARM_CLEARED false
 *   WATCH_QUIET ok
 * </pre>
 */
public final class ChestOpen401Probe {

    public static void main(String[] args) {
        // 1. The crack of one player's panel, on one chest.
        String key = "minecraft:overworld@1,64,2";
        String a = "BORGER";
        String b = "Steve";
        System.out.println("CRACK_BEFORE " + ChestLock397.cracked(a, key));
        ChestLock397.crack(a, key);
        boolean one = ChestLock397.cracked(a, key);
        System.out.println("CRACK_ONE " + one);
        // Somebody else's click on the same chest is not a crack: the «взлом» chain is the panel's story.
        System.out.println("CRACK_OTHER " + ChestLock397.cracked(b, key));

        // 2. What ChestGraph396Events#onUse does with that answer: take it, once.
        boolean cracked = ChestLock397.takeCrack(a, key);
        boolean first = cracked;
        boolean second = ChestLock397.takeCrack(a, key);
        System.out.println("CRACK_SPENT cracked=" + first
                + " take1=" + first
                + " take2=" + second);

        // 3. The click of a solved chest: armed by the server's word, forgotten on demand, harmless without a
        // game.
        System.out.println("ARM_BEFORE " + dev.settledlands.chest.ChestOpen401.armed());
        dev.settledlands.chest.ChestOpen401.arm(key, new net.minecraft.core.BlockPos(1, 64, 2), true);
        System.out.println("ARM_READY " + dev.settledlands.chest.ChestOpen401.armed());
        dev.settledlands.chest.ChestOpen401.clear();
        System.out.println("ARM_CLEARED " + dev.settledlands.chest.ChestOpen401.armed());
        // Nothing armed, nothing to click, no client: one tick must be a no-op, never a throw.
        try {
            dev.settledlands.chest.ChestOpen401.watch();
            System.out.println("WATCH_QUIET ok");
        } catch (Throwable failure) {
            System.out.println("WATCH_QUIET threw:" + failure);
        }
    }
}
