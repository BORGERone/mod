package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.settledlands.model.BbModel;
import dev.settledlands.model.BbModelManager;
import dev.settledlands.npc.PossessionClientSync;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the possessed NPC's arm model & texture in first person:
 *  1) Vanilla / modded first-person hand rendering (RenderArmEvent & PlayerRenderer.renderRightHand/renderLeftHand).
 *  2) PAL / HnS THIRD_PERSON_MODEL first-person sword swing pass (AgeableListModel.renderToBuffer
 *     when FirstPersonMode.isFirstPersonPass() is active), preserving PlayerItemInHandLayer & BladeTracker.
 */
public final class FirstPersonPossession256 {
    private static final float VANILLA_PLAYER_ARM_PIVOT_Y = 2.0f;
    private static final float DEFAULT_IVY_ARM_ROOT_Y = 1.0f;
    private static final int MAX_CONTEXT_DEPTH = 4;

    private static final ThreadLocal<Deque<RenderContext>> RENDER_CONTEXTS = new ThreadLocal<>();
    private static volatile boolean palChecked;
    private static Method palIsFirstPersonPass;

    private FirstPersonPossession256() {}

    public static void pushPlayerRenderContext(PlayerRenderer renderer,
                                               AbstractClientPlayer player,
                                               MultiBufferSource buffer,
                                               int packedLight) {
        if (renderer == null || player == null || buffer == null) return;
        Deque<RenderContext> stack = RENDER_CONTEXTS.get();
        if (stack == null) {
            stack = new ArrayDeque<>(MAX_CONTEXT_DEPTH);
            RENDER_CONTEXTS.set(stack);
        }
        while (stack.size() >= MAX_CONTEXT_DEPTH) {
            stack.removeLast();
        }
        stack.push(new RenderContext(renderer.getModel(), player, buffer, packedLight, false));
    }

    /** Capture the vanilla hands pass so alternate first-person renderers (including Punchy)
     * can be retargeted when they render a PlayerModel directly instead of firing RenderArmEvent. */
    public static void pushFirstPersonRenderContext(AbstractClientPlayer player,
                                                    MultiBufferSource buffer,
                                                    int packedLight) {
        if (player == null || buffer == null) return;
        Deque<RenderContext> stack = RENDER_CONTEXTS.get();
        if (stack == null) {
            stack = new ArrayDeque<>(MAX_CONTEXT_DEPTH);
            RENDER_CONTEXTS.set(stack);
        }
        while (stack.size() >= MAX_CONTEXT_DEPTH) {
            stack.removeLast();
        }
        stack.push(new RenderContext(null, player, buffer, packedLight, true));
    }

    public static void popPlayerRenderContext() {
        Deque<RenderContext> stack = RENDER_CONTEXTS.get();
        if (stack == null) return;
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            RENDER_CONTEXTS.remove();
        }
    }

    public static String possessedModelName(AbstractClientPlayer player) {
        if (player == null || player.getUUID() == null) return "";
        if (player.isSpectator() || player.isInvisible()) return "";
        PossessionClientSync.State state = PossessionClientSync.CLIENT_STATES.get(player.getUUID());
        return state != null && state.model != null ? state.model : "";
    }

    public static boolean renderPossessedArm(PoseStack poseStack,
                                             MultiBufferSource buffer,
                                             int packedLight,
                                             AbstractClientPlayer player,
                                             boolean rightArm) {
        if (poseStack == null || buffer == null) {
            return false;
        }
        String modelName = possessedModelName(player);
        if (modelName.isEmpty()) {
            return false;
        }
        BbModel model = BbModelManager.getModel(modelName);
        if (model == null) {
            return false;
        }
        ResourceLocation tex = model.getTextureLocation();
        if (tex == null) {
            return false;
        }
        ModelPart bbArm = findArmPart(model, rightArm);
        if (bbArm == null) {
            return false;
        }

        float rootArmY = computeRootArmY(model, bbArm);
        List<ModelPart> subtree = bbArm.getAllParts().toList();
        PartSnapshot[] saved = snapshotParts(subtree);
        Integer prevLodLevel = BbModel.CURRENT_LOD_LEVEL.get();
        Boolean prevLod = BbModel.CURRENT_LOD.get();
        try {
            BbModel.CURRENT_LOD_LEVEL.set(0);
            BbModel.CURRENT_LOD.set(Boolean.FALSE);
            for (ModelPart p : subtree) {
                p.resetPose();
                p.visible = true;
                p.skipDraw = false;
            }
            bbArm.x = rightArm ? -5.0f : 5.0f;
            bbArm.y = rootArmY;
            bbArm.z = 0.0f;
            bbArm.xRot = 0.0f;
            bbArm.yRot = 0.0f;
            bbArm.zRot = 0.0f;
            bbArm.xScale = 1.0f;
            bbArm.yScale = 1.0f;
            bbArm.zScale = 1.0f;

            VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucent(tex));
            if (vc == null) return false;
            bbArm.render(poseStack, vc, packedLight, OverlayTexture.NO_OVERLAY, -1);
            return true;
        } finally {
            restoreParts(subtree, saved);
            BbModel.CURRENT_LOD_LEVEL.set(prevLodLevel != null ? prevLodLevel : 0);
            BbModel.CURRENT_LOD.set(prevLod != null ? prevLod : Boolean.FALSE);
        }
    }

    public static boolean renderPossessedFirstPersonModelArms(PlayerModel<?> pm,
                                                              PoseStack poseStack,
                                                              int packedLight,
                                                              int packedOverlay,
                                                              int color) {
        if (pm == null || poseStack == null) return false;
        Deque<RenderContext> stack = RENDER_CONTEXTS.get();
        if (stack == null || stack.isEmpty()) return false;
        RenderContext ctx = stack.peek();
        boolean palPass = isPalFirstPersonPass();
        if (!palPass) {
            for (RenderContext candidate : stack) {
                if (candidate != null && candidate.firstPersonPass) {
                    ctx = candidate;
                    break;
                }
            }
        }
        if (ctx == null || (ctx.playerModel != null && ctx.playerModel != pm)
                || ctx.buffer == null || ctx.player == null || ctx.player.getUUID() == null
                || (!ctx.firstPersonPass && !palPass)) {
            return false;
        }
        String modelName = possessedModelName(ctx.player);
        if (modelName.isEmpty()) {
            return false;
        }
        BbModel model = BbModelManager.getModel(modelName);
        if (model == null) return false;
        ResourceLocation tex = model.getTextureLocation();
        if (tex == null) return false;

        ModelPart bbRight = findArmPart(model, true);
        ModelPart bbLeft = findArmPart(model, false);
        if (bbRight == null && bbLeft == null) return false;

        List<ModelPart> rightSubtree = bbRight != null ? bbRight.getAllParts().toList() : List.of();
        List<ModelPart> leftSubtree = bbLeft != null ? bbLeft.getAllParts().toList() : List.of();
        PartSnapshot[] savedRight = snapshotParts(rightSubtree);
        PartSnapshot[] savedLeft = snapshotParts(leftSubtree);
        Integer prevLodLevel = BbModel.CURRENT_LOD_LEVEL.get();
        Boolean prevLod = BbModel.CURRENT_LOD.get();
        try {
            BbModel.CURRENT_LOD_LEVEL.set(0);
            BbModel.CURRENT_LOD.set(Boolean.FALSE);
            VertexConsumer vc = ctx.buffer.getBuffer(RenderType.entityTranslucent(tex));
            if (vc == null) return false;
            int light = packedLight != 0 ? packedLight : ctx.packedLight;

            if (pm.rightArm != null && pm.rightArm.visible && bbRight != null) {
                renderAnimatedArm(model, bbRight, rightSubtree, pm.rightArm, poseStack, vc, light, packedOverlay, color);
            }
            if (pm.leftArm != null && pm.leftArm.visible && bbLeft != null) {
                renderAnimatedArm(model, bbLeft, leftSubtree, pm.leftArm, poseStack, vc, light, packedOverlay, color);
            }
            return true;
        } finally {
            restoreParts(rightSubtree, savedRight);
            restoreParts(leftSubtree, savedLeft);
            BbModel.CURRENT_LOD_LEVEL.set(prevLodLevel != null ? prevLodLevel : 0);
            BbModel.CURRENT_LOD.set(prevLod != null ? prevLod : Boolean.FALSE);
        }
    }

    private static void renderAnimatedArm(BbModel model,
                                          ModelPart bbArm,
                                          List<ModelPart> subtree,
                                          ModelPart vanillaArm,
                                          PoseStack poseStack,
                                          VertexConsumer vc,
                                          int light,
                                          int overlay,
                                          int color) {
        float rootArmY = computeRootArmY(model, bbArm);
        for (ModelPart p : subtree) {
            p.resetPose();
            p.visible = true;
            p.skipDraw = false;
        }
        bbArm.x = vanillaArm.x;
        bbArm.y = vanillaArm.y + (rootArmY - VANILLA_PLAYER_ARM_PIVOT_Y);
        bbArm.z = vanillaArm.z;
        bbArm.xRot = vanillaArm.xRot;
        bbArm.yRot = vanillaArm.yRot;
        bbArm.zRot = vanillaArm.zRot;
        bbArm.xScale = vanillaArm.xScale;
        bbArm.yScale = vanillaArm.yScale;
        bbArm.zScale = vanillaArm.zScale;
        bbArm.render(poseStack, vc, light, overlay, color);
    }

    private static ModelPart findArmPart(BbModel model, boolean rightArm) {
        ModelPart primary = model.getPart(rightArm ? "rightarm" : "leftarm");
        if (primary != null) return primary;
        return model.getPart(rightArm ? "right_arm" : "left_arm");
    }

    private static float computeRootArmY(BbModel model, ModelPart bbArm) {
        IdentityHashMap<ModelPart, ModelPart> parents = model.settledlands$partParents();
        if (parents == null) return DEFAULT_IVY_ARM_ROOT_Y;
        float sumY = 0.0f;
        int steps = 0;
        ModelPart cursor = bbArm;
        while (cursor != null && steps < 64) {
            PartSnapshot rest = restPoseOf(cursor);
            sumY += rest.y;
            cursor = parents.get(cursor);
            steps++;
        }
        if (!Float.isFinite(sumY) || steps == 0) return DEFAULT_IVY_ARM_ROOT_Y;
        return sumY;
    }

    private static PartSnapshot restPoseOf(ModelPart part) {
        PartSnapshot cur = new PartSnapshot(part);
        part.resetPose();
        PartSnapshot rest = new PartSnapshot(part);
        cur.restore(part);
        return rest;
    }

    private static boolean isPalFirstPersonPass() {
        if (!palChecked) {
            synchronized (FirstPersonPossession256.class) {
                if (!palChecked) {
                    try {
                        Class<?> fpMode = Class.forName(
                            "com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode",
                            false,
                            FirstPersonPossession256.class.getClassLoader()
                        );
                        palIsFirstPersonPass = fpMode.getMethod("isFirstPersonPass");
                    } catch (Throwable ignored) {
                    }
                    palChecked = true;
                }
            }
        }
        if (palIsFirstPersonPass == null) return false;
        try {
            return Boolean.TRUE.equals(palIsFirstPersonPass.invoke(null));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static PartSnapshot[] snapshotParts(List<ModelPart> parts) {
        PartSnapshot[] out = new PartSnapshot[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            out[i] = new PartSnapshot(parts.get(i));
        }
        return out;
    }

    private static void restoreParts(List<ModelPart> parts, PartSnapshot[] saved) {
        if (saved == null) return;
        int n = Math.min(parts.size(), saved.length);
        for (int i = 0; i < n; i++) {
            saved[i].restore(parts.get(i));
        }
    }

    private static final class RenderContext {
        final PlayerModel<?> playerModel;
        final AbstractClientPlayer player;
        final MultiBufferSource buffer;
        final int packedLight;
        final boolean firstPersonPass;

        RenderContext(PlayerModel<?> playerModel, AbstractClientPlayer player, MultiBufferSource buffer,
                      int packedLight, boolean firstPersonPass) {
            this.playerModel = playerModel;
            this.player = player;
            this.buffer = buffer;
            this.packedLight = packedLight;
            this.firstPersonPass = firstPersonPass;
        }
    }

    private static final class PartSnapshot {
        final float x, y, z, xRot, yRot, zRot, xScale, yScale, zScale;
        final boolean visible, skipDraw;

        PartSnapshot(ModelPart p) {
            this.x = p.x;
            this.y = p.y;
            this.z = p.z;
            this.xRot = p.xRot;
            this.yRot = p.yRot;
            this.zRot = p.zRot;
            this.xScale = p.xScale;
            this.yScale = p.yScale;
            this.zScale = p.zScale;
            this.visible = p.visible;
            this.skipDraw = p.skipDraw;
        }

        void restore(ModelPart p) {
            p.x = x;
            p.y = y;
            p.z = z;
            p.xRot = xRot;
            p.yRot = yRot;
            p.zRot = zRot;
            p.xScale = xScale;
            p.yScale = yScale;
            p.zScale = zScale;
            p.visible = visible;
            p.skipDraw = skipDraw;
        }
    }
}
