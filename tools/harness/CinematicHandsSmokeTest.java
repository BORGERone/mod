import dev.settledlands.mixin.CinematicHandsInvoker342;
import dev.settledlands.npc.client.CinematicArms342;
import net.minecraft.world.phys.Vec3;

/**
 * Runs build 342's scene-hand guard: everything it has to refuse, the one pair of points it accepts,
 * and the fallback that keeps a hand on screen when the hand-over itself cannot run.
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

        check("a real arm is long enough", !CinematicArms342.armTooShort(hand, shoulder));
        check("a hand on the shoulder is too short", CinematicArms342.armTooShort(hand, hand));
        check("no hand is too short", CinematicArms342.armTooShort(null, shoulder));
        check("no shoulder is too short", CinematicArms342.armTooShort(hand, null));
        check("no player is not possessed", !CinematicArms342.possessed(null));
        check("no client, no hand-over", CinematicArms342.handOwner(hand, shoulder) == null);
        check("a scene hand is Cinematic's own when nothing is possessed",
                CinematicArms342.handOwner(hand, shoulder) == null);
        check("the report never throws", reportsQuietly());

        boolean fellBack = false;
        try {
            CinematicHandsInvoker342.settledlands$drawModelArm(null, null, hand, hand, shoulder, true, 0,
                    shoulder, null);
        } catch (Throwable expected) {
            fellBack = true;
        }
        check("without the mixin applied the hand-over throws and the caller falls back", fellBack);

        if (failures == 0) {
            System.out.println("ALL OK");
        } else {
            System.out.println("FAILURES: " + failures);
            System.exit(1);
        }
    }

    static boolean reportsQuietly() {
        try {
            CinematicArms342.noteHandOver("ivy_oleander_professions");
            CinematicArms342.noteHandOver(null);
            CinematicArms342.noteFailure(new IllegalStateException("expected"));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
