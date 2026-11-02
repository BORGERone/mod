import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.npc.client.TouchNMotionPose340;
import java.lang.reflect.Method;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Runs build 340 the way the game will: whatever the event carries, the listener must never throw
 * into the render pass, and with Touch'n Motion absent from the classpath it must quietly resolve to
 * nothing instead of loading its classes.
 */
public class TouchNMotionPoseSmokeTest {

    static int failures = 0;

    static void check(String what, boolean ok) {
        if (ok) {
            System.out.println("ok    " + what);
        } else {
            failures++;
            System.out.println("FAIL  " + what);
        }
    }

    static boolean quiet(String what, Runnable body) {
        try {
            body.run();
            return true;
        } catch (Throwable t) {
            System.out.println("FAIL  " + what + " threw " + t);
            failures++;
            return false;
        }
    }

    public static void main(String[] args) throws Exception {
        check("no event at all", quiet("null event",
                () -> TouchNMotionPose340.onRenderPlayerPre(null)));
        check("an event without a player", quiet("null entity",
                () -> TouchNMotionPose340.onRenderPlayerPre(
                        new RenderPlayerEvent.Pre(null, new PoseStack(), null, 0, 0.0F))));
        check("an event without a PoseStack", quiet("null pose",
                () -> TouchNMotionPose340.onRenderPlayerPre(
                        new RenderPlayerEvent.Pre(new AbstractClientPlayer(), null, null, 0, 0.0F))));
        check("a player the mod knows nothing about", quiet("unpossessed",
                () -> TouchNMotionPose340.onRenderPlayerPre(
                        new RenderPlayerEvent.Pre(new AbstractClientPlayer(), new PoseStack(), null,
                                15, 0.5F))));

        Method resolve = TouchNMotionPose340.class.getDeclaredMethod("poseBefore");
        resolve.setAccessible(true);
        Object first = resolve.invoke(null);
        Object second = resolve.invoke(null);
        check("Touch'n Motion absent from the classpath resolves to nothing", first == null);
        check("and it stays resolved, not looked up again", first == second);

        if (failures == 0) {
            System.out.println("ALL OK");
        } else {
            System.out.println("FAILURES: " + failures);
            System.exit(1);
        }
    }
}
