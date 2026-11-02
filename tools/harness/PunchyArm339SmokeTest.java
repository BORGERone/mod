import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.npc.client.PunchyArm339;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Runs build 339 against the shapes the game will hand it: an untouched call must never draw, never
 * throw, never leave the PoseStack unbalanced and never mark an arm as replaced - Punchy! has to keep
 * its own arm whenever the mod has nothing to put in its place.
 */
public class PunchyArm339SmokeTest {

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
        MultiBufferSource buffer = new MultiBufferSource() {
            public com.mojang.blaze3d.vertex.VertexConsumer getBuffer(
                    net.minecraft.client.renderer.RenderType renderType) {
                return null;
            }
        };
        PlayerModel<Object> model = new PlayerModel<>();
        model.rightArm = new ModelPart();
        model.leftArm = new ModelPart();
        AbstractClientPlayer player = new AbstractClientPlayer();

        check("no model, no arm",
                !PunchyArm339.replace(null, player, HumanoidArm.RIGHT, pose, buffer, 0));
        check("no player, no arm",
                !PunchyArm339.replace(model, null, HumanoidArm.RIGHT, pose, buffer, 0));
        check("no side, no arm",
                !PunchyArm339.replace(model, player, null, pose, buffer, 0));
        check("no pose, no arm",
                !PunchyArm339.replace(model, player, HumanoidArm.RIGHT, null, buffer, 0));
        check("no buffer, no arm",
                !PunchyArm339.replace(model, player, HumanoidArm.RIGHT, pose, null, 0));

        model.rightArm = null;
        check("an arm the model does not have is left to Punchy",
                !PunchyArm339.replace(model, player, HumanoidArm.RIGHT, pose, buffer, 0));
        model.rightArm = new ModelPart();

        check("an unpossessed player keeps Punchy's arm",
                !PunchyArm339.replace(model, player, HumanoidArm.RIGHT, pose, buffer, 0));
        check("and the call never throws", quiet("replace", () ->
                PunchyArm339.replace(model, player, HumanoidArm.LEFT, pose, buffer, 15)));
        check("the model's own parts are untouched",
                model.rightArm.x == 0.0F && model.rightArm.xRot == 0.0F
                        && model.leftArm.visible && model.rightArm.xScale == 1.0F);
        check("nothing is marked as replaced", !PunchyArm339.consumeArmor(HumanoidArm.RIGHT));
        check("and neither side is", !PunchyArm339.consumeArmor(HumanoidArm.LEFT));
        check("no side at all is inert", !PunchyArm339.consumeArmor(null));

        if (failures == 0) {
            System.out.println("ALL OK");
        } else {
            System.out.println("FAILURES: " + failures);
            System.exit(1);
        }
    }
}
