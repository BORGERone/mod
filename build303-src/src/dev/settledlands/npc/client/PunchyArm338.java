package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.settledlands.model.BbModel;
import dev.settledlands.model.BbModelManager;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Build 338: the possessed NPC's arms inside Punchy!'s first-person pass.
 *
 * <p>Punchy! draws the first-person arms itself: it animates its own player model and submits
 * {@code model.rightArm} / {@code model.leftArm} with the player's own skin, and it cancels the
 * vanilla hand rendering that the mod already replaces. Vanilla possession therefore shows the
 * Player's arms - Steve, for a player with no skin - while every non-Punchy first-person path shows
 * the NPC's bbmodel arm. This class closes that gap: when Punchy! asks to draw an arm of a possessed
 * player, the NPC's arm is drawn in its place and Punchy!'s own arm is skipped.</p>
 *
 * <p>Where it hooks: {@code PunchyArmRenderer#renderArm}, at the head, before Punchy! applies the
 * arm's own transform - so the pose stack is still in the player-model space that the possession arm
 * renderer of Build 256 expects, and it can copy the arm pose Punchy! has just animated onto the NPC
 * arm. That is what makes the NPC's arm follow Punchy!'s punches instead of standing still.</p>
 *
 * <p>Only one arm is drawn per call: the shared renderer submits every visible arm, so the other one
 * is hidden for the duration and put back afterwards. Nothing is written into Punchy!'s model: the
 * visibility flags are restored in a {@code finally}, and any failure simply lets Punchy! draw its
 * own arm, exactly as if this class did not exist.</p>
 */
public final class PunchyArm338 {

    /** The arm whose Punchy! render was replaced by the NPC arm, read once by the armour pass. */
    private static final ThreadLocal<HumanoidArm> REPLACED = new ThreadLocal<>();

    /** Punchy! nudges slim arms by one texel; the NPC arm is drawn in the same place. */
    private static final double SLIM_OFFSET = 0.03125D;

    private PunchyArm338() {
    }

    /**
     * Draws the possessed NPC's arm where Punchy! was about to draw the player's.
     *
     * @return true when the NPC arm was submitted, in which case the caller must not draw its own
     */
    public static boolean replace(PlayerModel<?> model, AbstractClientPlayer player, HumanoidArm arm,
                                  PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                  boolean slim) {
        if (model == null || player == null || arm == null || poseStack == null || buffer == null) {
            return false;
        }
        try {
            ModelPart own = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
            if (own == null || !own.visible) return false;
            String modelName = FirstPersonPossession256.possessedModelName(player);
            if (modelName.isEmpty()) return false;
            // Nothing of the NPC to put in Punchy!'s place on this side: leave its own arm alone
            // rather than cancel it and leave an empty hand.
            BbModel npc = BbModelManager.getModel(modelName);
            boolean right = arm == HumanoidArm.RIGHT;
            if (npc == null || npc.getTextureLocation() == null || armPart(npc, right) == null) {
                return false;
            }

            boolean savedRight = model.rightArm != null && model.rightArm.visible;
            boolean savedLeft = model.leftArm != null && model.leftArm.visible;
            boolean drawn = false;
            poseStack.pushPose();
            try {
                if (slim) {
                    poseStack.translate(right ? SLIM_OFFSET : -SLIM_OFFSET, 0.0D, 0.0D);
                }
                if (model.rightArm != null) model.rightArm.visible = right;
                if (model.leftArm != null) model.leftArm.visible = arm == HumanoidArm.LEFT;
                FirstPersonPossession256.pushFirstPersonRenderContext(player, buffer, packedLight);
                try {
                    drawn = FirstPersonPossession256.renderPossessedFirstPersonModelArms(
                            model, poseStack, packedLight, OverlayTexture.NO_OVERLAY, -1);
                } finally {
                    FirstPersonPossession256.popPlayerRenderContext();
                }
            } finally {
                poseStack.popPose();
                if (model.rightArm != null) model.rightArm.visible = savedRight;
                if (model.leftArm != null) model.leftArm.visible = savedLeft;
            }
            if (drawn) REPLACED.set(arm);
            return drawn;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** The NPC bone Punchy!'s arm is drawn from, under the names bbmodel exports use. */
    private static ModelPart armPart(BbModel npc, boolean right) {
        ModelPart part = npc.getPart(right ? "rightarm" : "leftarm");
        return part != null ? part : npc.getPart(right ? "right_arm" : "left_arm");
    }

    /** True once, for the armour pass that follows an arm this class has already drawn. */
    public static boolean consumeArmor(HumanoidArm arm) {
        if (arm == null || REPLACED.get() != arm) return false;
        REPLACED.remove();
        return true;
    }
}
