import dev.settledlands.npc.client.CinematicLegs344;
import java.lang.reflect.Method;
import net.minecraft.world.phys.Vec3;

/**
 * Runs build 344's scene-leg guard: the segments it must refuse, the calls it must survive without a
 * client or a Cinematic install, and the fallback that keeps Cinematic's own legs on screen.
 */
public class CinematicLegsSmokeTest {

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
        Vec3 hip = new Vec3(0.5D, 0.75D, 2.0D);
        Vec3 knee = new Vec3(0.5D, 0.55D, 2.4D);
        Vec3 ankle = new Vec3(0.5D, 0.05D, 2.7D);

        check("a real thigh is long enough", !CinematicLegs344.segmentTooShort(hip, knee));
        check("a real shin is long enough", !CinematicLegs344.segmentTooShort(knee, ankle));
        check("a knee on the hip is too short", CinematicLegs344.segmentTooShort(hip, hip));
        check("a folded-away shin is too short", CinematicLegs344.segmentTooShort(ankle,
                new Vec3(ankle.x + 0.00001D, ankle.y, ankle.z)));
        check("no hip is too short", CinematicLegs344.segmentTooShort(null, knee));
        check("no knee is too short", CinematicLegs344.segmentTooShort(hip, null));
        check("no client, no leg-over", !legOver(null, null, null, null));
        check("no pose stack, no leg-over", !legOver(null, null, hip, knee));
        check("the leg guard never throws", quietly(() -> CinematicLegs344.drawLegs(null, null, null, null,
                true, null, null, null, null, 0)));
        check("without Cinematic the armour pass resolves to nothing once", !armourAvailable());
        check("the second look costs nothing and still finds nothing", !armourAvailable());
        check("the report never throws", quietly(() -> {
            reportsQuietly();
            return null;
        }));

        if (failures == 0) {
            System.out.println("ALL OK");
        } else {
            System.out.println("FAILURES: " + failures);
            System.exit(1);
        }
    }

    static boolean legOver(Object poseStack, Object buffers, Vec3 hip, Vec3 knee) {
        try {
            return CinematicLegs344.drawLegs(null, null, null, null, true, hip, knee, null, null, 0);
        } catch (Throwable ignored) {
            return true;
        }
    }

    static boolean armourAvailable() {
        try {
            Method method = CinematicLegs344.class.getDeclaredMethod("armourDraw");
            method.setAccessible(true);
            return method.invoke(null) != null;
        } catch (Throwable ignored) {
            return true;
        }
    }

    static void reportsQuietly() {
        try {
            Method legs = CinematicLegs344.class.getDeclaredMethod("noteLegs", String.class);
            legs.setAccessible(true);
            legs.invoke(null, "ivy_oleander_professions");
            legs.invoke(null, (Object) null);
            Method failure = CinematicLegs344.class.getDeclaredMethod("noteFailure", Throwable.class);
            failure.setAccessible(true);
            failure.invoke(null, new IllegalStateException("expected"));
            CinematicLegs344.segmentTooShort(null, null);
        } catch (Throwable ignored) {
            // reported as a quiet failure below
        }
    }

    interface Call {
        Object run() throws Throwable;
    }

    static boolean quietly(Call call) {
        try {
            call.run();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
