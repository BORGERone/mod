import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.npc.client.PunchyArm338;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Runs build 338 against the shapes the game will hand it: an untouched call must never draw, never
 * throw, and never leave an arm marked as replaced - Punchy! has to keep its own arm whenever the mod
 * has nothing to put in its place.
 */
public class PunchyArm338SmokeTest {

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

    public static void main(String[] args) {
        PoseStack pose = new PoseStack();
        MultiBufferSource buffer = new MultiBufferSource() { };
        PlayerModel<Object> model = new PlayerModel<>();
        model.rightArm = new net.minecraft.client.model.geom.ModelPart();
        model.leftArm = new net.minecraft.client.model.geom.ModelPart();
        // the game's own model parts start out visible
        model.rightArm.visible = true;
        model.leftArm.visible = true;
        AbstractClientPlayer player = new AbstractClientPlayer();

        check("no model, no arm", !PunchyArm338.replace(null, player, HumanoidArm.RIGHT, pose, buffer, 0, false));
        check("no player, no arm", !PunchyArm338.replace(model, null, HumanoidArm.RIGHT, pose, buffer, 0, false));
        check("no side, no arm", !PunchyArm338.replace(model, player, null, pose, buffer, 0, false));
        check("no pose, no arm", !PunchyArm338.replace(model, player, HumanoidArm.RIGHT, null, buffer, 0, false));
        check("no buffer, no arm", !PunchyArm338.replace(model, player, HumanoidArm.RIGHT, pose, null, 0, false));

        model.rightArm.visible = false;
        check("a hidden arm is left to Punchy", !PunchyArm338.replace(model, player, HumanoidArm.RIGHT, pose, buffer, 0, false));
        model.rightArm.visible = true;

        check("an unpossessed player keeps Punchy's arm",
                !PunchyArm338.replace(model, player, HumanoidArm.RIGHT, pose, buffer, 0, false));
        check("and the call never throws", quiet("replace", () ->
                PunchyArm338.replace(model, player, HumanoidArm.LEFT, pose, buffer, 15, true)));
        check("the visible flags are left as they were",
                model.rightArm.visible && model.leftArm.visible);
        check("nothing is marked as replaced", !PunchyArm338.consumeArmor(HumanoidArm.RIGHT));
        check("and no side at all is inert", !PunchyArm338.consumeArmor(null));

        if (failures == 0) {
            System.out.println("ALL OK");
        } else {
            System.out.println("FAILURES: " + failures);
            System.exit(1);
        }
    }
}
