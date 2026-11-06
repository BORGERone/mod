import dev.settledlands.door.DoorHack377;

/**
 * Build 395 probe: the cracked door's rule has no clock in it.
 *
 * <p>The author: «чето немного странное происходит, дверь после взлома сама закрывается через секунд 5-10».
 * Build 377 remembered a cracked door with a timestamp and let the guard of 371 close it eight seconds
 * later - a guess, not a rule. Build 395 replaced the timer with four facts: this mod cracked the door
 * open, it is still a door, it is still open, and a player is at it. That rule is
 * {@code DoorHack377#keepOpen}, and it is pure, so the whole truth table can be walked here: sixteen
 * combinations, exactly one of which keeps a door open.</p>
 *
 * <pre>
 *   RULE keeps=1 of=16          only "cracked by us, a door, open, somebody at it" keeps the door
 *   TIMER keep=yes close=no     the timer cannot keep a door open: every other combination closes it
 *   NULL doors=false            no world, no exception: a call from the guard cannot break a tick
 * </pre>
 */
public final class DoorCracked395Probe {

    public static void main(String[] args) {
        int keeps = 0;
        int of = 0;
        boolean onlyTheRightOne = true;
        for (int bits = 0; bits < 16; bits++) {
            boolean marked = (bits & 1) != 0;
            boolean door = (bits & 2) != 0;
            boolean open = (bits & 4) != 0;
            boolean near = (bits & 8) != 0;
            boolean keep = DoorHack377.keepOpen(marked, door, open, near);
            of++;
            if (keep) {
                keeps++;
                if (!(marked && door && open && near)) onlyTheRightOne = false;
            }
        }
        if (!onlyTheRightOne) keeps = -1;
        System.out.println("RULE keeps=" + keeps + " of=" + of);

        // The two facts the guard actually has: a door nothing cracked is never kept, and a door kept open
        // without somebody at it is exactly what 377 did - the answer must be no.
        System.out.println("TIMER keep="
                + (DoorHack377.keepOpen(true, true, true, true) ? "yes" : "no")
                + " close=" + (DoorHack377.keepOpen(true, true, true, false) ? "yes" : "no"));

        boolean doors = false;
        try {
            doors = DoorHack377.crackedOpen(null, null);
        } catch (Throwable failure) {
            doors = true;
        }
        System.out.println("NULL doors=" + (doors ? "true" : "false"));
    }
}
