package dev.settledlands.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zigythebird.playeranim.accessors.IAnimatedPlayer;
import com.zigythebird.playeranim.animation.PlayerAnimManager;
import com.zigythebird.playeranim.util.RenderUtil;
import com.zigythebird.playeranimcore.animation.AnimationProcessor;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;

/**
 * Optional adapter to the public PAL 1.1.6 API used by HnS beta2.5.
 * Loaded only by reflection after both version checks. This class contains no
 * bundled animation, and never starts or changes a combat action. Animation
 * evaluation stays owned by the installed PAL/HnS layers.
 */
public final class HackSlashAccess {
    private static final ResourceLocation ATTACK = ResourceLocation.fromNamespaceAndPath("hackersandslashers", "attack_layer");
    private HackSlashAccess() {}

    private static final class Frame {
        final PlayerAnimManager manager;
        PlayerAnimBone rightItem, leftItem;
        Frame(PlayerAnimManager manager) { this.manager = manager; }
    }

    /** Called at the omitted PlayerRenderer.setupRotations stage. */
    public static Object prepare(AbstractClientPlayer player) {
        if (!(player instanceof IAnimatedPlayer animated)) return null;
        IAnimation attack = animated.playerAnimLib$getAnimation(ATTACK);
        AnimationProcessor processor = animated.playerAnimLib$getAnimProcessor();
        PlayerAnimManager manager = animated.playerAnimLib$getAnimManager();
        if (attack == null || !attack.isActive() || processor == null || manager == null || !manager.isActive()) return null;
        // PlayerRenderer.render HEAD has already set PAL's tick delta before Pre.
        // Its setupRotations RETURN is skipped by the cancelled player render.
        processor.handleAnimations(manager.getTickDelta(), false);
        return new Frame(manager);
    }

    /** PAL's body transform, applied inside a temporary pose-stack guard. */
    public static void root(Object frameObject, PoseStack stack) {
        Frame frame = (Frame) frameObject;
        PlayerAnimBone body = checked(frame.manager.get3DTransform(new PlayerAnimBone("body")));
        stack.scale(body.getScaleX(), body.getScaleY(), body.getScaleZ());
        stack.translate(-body.getPosX() / 16f, body.getPosY() / 16f + .75f, body.getPosZ() / 16f);
        body.setRotX(-body.getRotX());
        body.setRotY(-body.getRotY());
        RenderUtil.rotateMatrixAroundBone(stack, body);
        stack.translate(0f, -.75f, 0f);
    }

    /** Overlay the PAL animation on the already-evaluated FA/TnM pose. */
    public static void parts(Object frameObject, PlayerModel<?> model) {
        Frame frame = (Frame) frameObject;
        apply(frame.manager, model.head, "head");
        apply(frame.manager, model.body, "torso");
        apply(frame.manager, model.rightArm, "right_arm");
        apply(frame.manager, model.leftArm, "left_arm");
        apply(frame.manager, model.rightLeg, "right_leg");
        apply(frame.manager, model.leftLeg, "left_leg");
    }

    private static void apply(PlayerAnimManager manager, ModelPart part, String name) {
        PlayerAnimBone seed = RenderUtil.copyVanillaPart(part, new PlayerAnimBone(name));
        // There is no visible vanilla sleeve in this isolated biped. PAL writes
        // both parameters identically; passing the same part keeps its contract.
        manager.updatePart(part, part, seed);
        if (!Float.isFinite(part.x) || !Float.isFinite(part.y) || !Float.isFinite(part.z)
                || !Float.isFinite(part.xRot) || !Float.isFinite(part.yRot) || !Float.isFinite(part.zRot)
                || !Float.isFinite(part.xScale) || !Float.isFinite(part.yScale) || !Float.isFinite(part.zScale))
            throw new IllegalStateException("Non-finite PAL part: " + name);
    }

    /** Before the existing held-item orientation, as in PAL's ItemInHandLayer. */
    public static void itemPosition(Object frameObject, PoseStack stack, boolean left) {
        Frame frame = (Frame) frameObject;
        PlayerAnimBone bone = checked(frame.manager.get3DTransform(new PlayerAnimBone(left ? "left_item" : "right_item")));
        if (left) frame.leftItem = bone; else frame.rightItem = bone;
        stack.translate(bone.getPosX() / 16f, -bone.getPosY() / 16f, bone.getPosZ() / 16f);
    }

    /** Immediately before renderItem, after the existing held-item orientation. */
    public static void itemPose(Object frameObject, PoseStack stack, boolean left) {
        Frame frame = (Frame) frameObject;
        PlayerAnimBone bone = left ? frame.leftItem : frame.rightItem;
        if (bone == null) return; // an empty item does not reach the first stage
        stack.mulPose(new Quaternionf().rotationZ(-bone.getRotY()));
        stack.mulPose(new Quaternionf().rotationY(-bone.getRotZ()));
        stack.mulPose(new Quaternionf().rotationX(-bone.getRotX()));
        stack.scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
    }

    private static PlayerAnimBone checked(PlayerAnimBone bone) {
        if (bone == null || !Float.isFinite(bone.getPosX()) || !Float.isFinite(bone.getPosY()) || !Float.isFinite(bone.getPosZ())
                || !Float.isFinite(bone.getRotX()) || !Float.isFinite(bone.getRotY()) || !Float.isFinite(bone.getRotZ())
                || !Float.isFinite(bone.getScaleX()) || !Float.isFinite(bone.getScaleY()) || !Float.isFinite(bone.getScaleZ())
                || Math.abs(bone.getScaleX()) < 1e-8f || Math.abs(bone.getScaleY()) < 1e-8f || Math.abs(bone.getScaleZ()) < 1e-8f)
            throw new IllegalStateException("Invalid PAL transform");
        return bone;
    }
}
