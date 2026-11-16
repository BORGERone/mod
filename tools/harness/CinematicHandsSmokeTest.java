import dev.settledlands.npc.client.CinematicArms343;
import net.minecraft.world.phys.Vec3;

/**
 * Runs build 343's scene-hand guard: everything it has to refuse, the one pair of points it accepts,
 * and the fallback that keeps the game - and a hand - on screen when Cinematic is not there at all.
 */
public class CinematicHandsSmokeTest {

    static int failures = 0;

    static void check(String what, boolean ok) {
        if (ok) {
            System.out.println("ok    " + what);
        } else {
            failures++;
            System.out.println("FAIL  " + what);
        }
    }

    public static void main(String[] args) {
        Vec3 hand = new Vec3(0.5D, 1.0D, 2.0D);
        Vec3 shoulder = new Vec3(0.5D, 1.4D, 1.5D);

        check("a real arm is long enough", !CinematicArms343.armTooShort(hand, shoulder));
        check("a hand on the shoulder is too short", CinematicArms343.armTooShort(hand, hand));
        check("no hand is too short", CinematicArms343.armTooShort(null, shoulder));
        check("no shoulder is too short", CinematicArms343.armTooShort(hand, null));
        check("no player is not possessed", !CinematicArms343.possessed(null));
        check("no client, no hand-over", CinematicArms343.handOwner(hand, shoulder) == null);
        check("a scene hand is Cinematic's own when nothing is possessed",
                CinematicArms343.handOwner(hand, shoulder) == null);
        check("the report never throws", reportsQuietly());
        check("without Cinematic the hand-over refuses instead of throwing",
                !handOverQuietly(hand, shoulder));
        check("the second look costs nothing and still refuses", !handOverQuietly(hand, shoulder));

        if (failures == 0) {
            System.out.println("ALL OK");
        } else {
            System.out.println("FAILURES: " + failures);
            System.exit(1);
        }
    }

    static boolean handOverQuietly(Vec3 hand, Vec3 shoulder) {
        try {
            return CinematicArms343.drawModelArm(null, null, hand, hand, shoulder, true, 0, shoulder, null);
        } catch (Throwable ignored) {
            return true;
        }
    }

    static boolean reportsQuietly() {
        try {
            CinematicArms343.noteHandOver("ivy_oleander_professions");
            CinematicArms343.noteHandOver(null);
            CinematicArms343.noteFailure(new IllegalStateException("expected"));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
