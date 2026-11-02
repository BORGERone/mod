package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.settledlands.model.BbModel;
import dev.settledlands.model.BbModelManager;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Build 339: the possessed NPC's arm inside Punchy!'s first-person pass, drawn where Punchy! puts it.
 *
 * <p>Punchy! draws the first-person arms itself and never calls the vanilla hand pass the mod
 * replaces, so a possessed player saw the player's own arms - Steve, without a skin. Build 338 drew
 * the NPC arm from the arm's own place in the model, which is only correct for a pose stack that the
 * player model itself is being drawn into; Punchy! hands over its own hand space instead, and because
 * 338 also cancelled Punchy!'s arm, a possessed player was left with no hands at all.</p>
 *
 * <p>This build never guesses the space: it takes the arm Punchy! is about to draw and applies
 * <em>that arm's own transform</em> - the game's {@code ModelPart.translateAndRotate}, which is exactly
 * the transform Punchy! applies to its own arm, animation included - and draws the NPC arm around
 * that pivot. Whatever PoseStack Punchy! works in, the NPC arm lands on the arm it replaces, and it
 * keeps the swing, the punch and any interaction reach the player model carries that frame.</p>
 *
 * <p>Nothing is drawn and nothing is cancelled for an unpossessed player, nor when the NPC model has
 * no arm bone on that side: Punchy!'s own arm stays whenever there is nothing to put in its place. The
 * arm's parts are saved and put back in a {@code finally}, and a failure of any kind returns false, so
 * the arm Punchy! would have drawn is drawn by Punchy!.</p>
 */
public final class PunchyArm339 {

    /** The arm whose Punchy! render was replaced, read once by the armour pass that follows. */
    private static final ThreadLocal<HumanoidArm> REPLACED = new ThreadLocal<>();

    /** Saved/restored state of the bones of one NPC arm; rebuilt only when the model is reloaded. */
    private static final Map<String, ArmCache[]> CACHE = new ConcurrentHashMap<>();

    /** Fields per bone: x, y, z, xRot, yRot, zRot, xScale, yScale, zScale, visible, skipDraw. */
    private static final int FIELDS = 11;

    private PunchyArm339() {
    }

    /**
     * Draws the possessed NPC's arm in the place of the arm Punchy! was about to draw.
     *
     * @return true when the NPC arm was submitted, in which case the caller must not draw its own
     */
    public static boolean replace(PlayerModel<?> model, AbstractClientPlayer player, HumanoidArm arm,
                                  PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (model == null || player == null || arm == null || poseStack == null || buffer == null) {
            return false;
        }
        try {
            if (FirstPersonPossession256.possessedModelName(player).isEmpty()) return false;
            ModelPart own = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
            if (own == null) return false;

            boolean drawn = false;
            poseStack.pushPose();
            try {
                own.translateAndRotate(poseStack);
                drawn = draw(poseStack, buffer, packedLight, player, arm == HumanoidArm.RIGHT);
            } finally {
                poseStack.popPose();
            }
            if (drawn) REPLACED.set(arm);
            return drawn;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Draws one NPC arm around the pivot the caller has already pushed, or nothing. */
    private static boolean draw(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                AbstractClientPlayer player, boolean right) {
        String modelName = FirstPersonPossession256.possessedModelName(player);
        if (modelName.isEmpty()) return false;
        BbModel npc = BbModelManager.getModel(modelName);
        if (npc == null) return false;
        ResourceLocation texture = npc.getTextureLocation();
        if (texture == null) return false;
        ArmCache cache = cacheFor(npc, right);
        if (cache == null) return false;
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityTranslucent(texture));
        if (vertices == null) return false;

        Integer lodLevel = BbModel.CURRENT_LOD_LEVEL.get();
        Boolean lod = BbModel.CURRENT_LOD.get();
        try {
            BbModel.CURRENT_LOD_LEVEL.set(0);
            BbModel.CURRENT_LOD.set(Boolean.FALSE);
            cache.save();
            cache.reset();
            cache.arm.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY, -1);
            return true;
        } finally {
            cache.restore();
            BbModel.CURRENT_LOD_LEVEL.set(lodLevel != null ? lodLevel : 0);
            BbModel.CURRENT_LOD.set(lod != null ? lod : Boolean.FALSE);
        }
    }

    /** True once, for the armour pass that follows an arm this class has already drawn. */
    public static boolean consumeArmor(HumanoidArm arm) {
        if (arm == null || REPLACED.get() != arm) return false;
        REPLACED.remove();
        return true;
    }

    /** The NPC bone the arm is drawn from, under the names bbmodel exports use. */
    private static ModelPart armPart(BbModel npc, boolean right) {
        ModelPart part = npc.getPart(right ? "rightarm" : "leftarm");
        return part != null ? part : npc.getPart(right ? "right_arm" : "left_arm");
    }

    private static ArmCache cacheFor(BbModel npc, boolean right) {
        String key = npc.getName();
        if (key == null) return null;
        ArmCache[] slots = CACHE.computeIfAbsent(key, unused -> new ArmCache[2]);
        int slot = right ? 0 : 1;
        ModelPart arm = armPart(npc, right);
        if (arm == null) {
            slots[slot] = null;
            return null;
        }
        ArmCache cache = slots[slot];
        if (cache == null || cache.model != npc || cache.arm != arm) {
            List<ModelPart> parts = arm.getAllParts().toList();
            cache = new ArmCache(npc, arm, parts.toArray(new ModelPart[0]), parts.size());
            slots[slot] = cache;
        }
        return cache;
    }

    /** The bones of one arm with their state kept aside: built once per model, reused every frame. */
    private static final class ArmCache {
        private final BbModel model;
        private final ModelPart arm;
        private final ModelPart[] parts;
        private final float[] saved;

        private ArmCache(BbModel model, ModelPart arm, ModelPart[] parts, int count) {
            this.model = model;
            this.arm = arm;
            this.parts = parts;
            this.saved = new float[count * FIELDS];
        }

        private void save() {
            for (int i = 0; i < parts.length; i++) {
                ModelPart part = parts[i];
                int at = i * FIELDS;
                saved[at] = part.x;
                saved[at + 1] = part.y;
                saved[at + 2] = part.z;
                saved[at + 3] = part.xRot;
                saved[at + 4] = part.yRot;
                saved[at + 5] = part.zRot;
                saved[at + 6] = part.xScale;
                saved[at + 7] = part.yScale;
                saved[at + 8] = part.zScale;
                saved[at + 9] = part.visible ? 1.0F : 0.0F;
                saved[at + 10] = part.skipDraw ? 1.0F : 0.0F;
            }
        }

        /** The arm at rest around its own pivot: the caller's PoseStack supplies the pose. */
        private void reset() {
            for (ModelPart part : parts) {
                part.x = 0.0F;
                part.y = 0.0F;
                part.z = 0.0F;
                part.xRot = 0.0F;
                part.yRot = 0.0F;
                part.zRot = 0.0F;
                part.xScale = 1.0F;
                part.yScale = 1.0F;
                part.zScale = 1.0F;
                part.visible = true;
                part.skipDraw = false;
            }
        }

        private void restore() {
            for (int i = 0; i < parts.length; i++) {
                ModelPart part = parts[i];
                int at = i * FIELDS;
                part.x = saved[at];
                part.y = saved[at + 1];
                part.z = saved[at + 2];
                part.xRot = saved[at + 3];
                part.yRot = saved[at + 4];
                part.zRot = saved[at + 5];
                part.xScale = saved[at + 6];
                part.yScale = saved[at + 7];
                part.zScale = saved[at + 8];
                part.visible = saved[at + 9] != 0.0F;
                part.skipDraw = saved[at + 10] != 0.0F;
            }
        }
    }
}
