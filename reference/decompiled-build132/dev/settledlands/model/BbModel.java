package dev.settledlands.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.settledlands.npc.GuideNpc;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.*;

public final class BbModel {
    public static record PartRestPose(
        ModelPart part,
        float rx, float ry, float rz,
        float px, float py, float pz
    ) {}

    private final String name;
    private final int textureWidth;
    private final int textureHeight;
    private final ModelPart rootPart;
    private final ResourceLocation textureLocation;
    private final Map<String, ModelPart> partsByName;
    private final Map<String, float[]> restRotations;
    private final Map<String, float[]> restPositions;
    private final Map<String, BbAnimation> animations;
    private final Map<String, Set<String>> boneSubtrees;
    private final PartRestPose[] uniqueRestPoses;
    private final ModelPart cachedHeadPart;
    private final BbAnimation cachedIdleAnim;
    private final BbAnimation cachedWalkAnim;
    private final BbAnimation cachedTalkAnim;

    public BbModel(
        String name,
        int textureWidth,
        int textureHeight,
        ModelPart rootPart,
        ResourceLocation textureLocation,
        Map<String, ModelPart> partsByName,
        Map<String, float[]> restRotations,
        Map<String, float[]> restPositions,
        Map<String, BbAnimation> animations
    ) {
        this(name, textureWidth, textureHeight, rootPart, textureLocation, partsByName, restRotations, restPositions, animations, Collections.emptyMap());
    }

    public BbModel(
        String name,
        int textureWidth,
        int textureHeight,
        ModelPart rootPart,
        ResourceLocation textureLocation,
        Map<String, ModelPart> partsByName,
        Map<String, float[]> restRotations,
        Map<String, float[]> restPositions,
        Map<String, BbAnimation> animations,
        Map<String, Set<String>> boneSubtrees
    ) {
        this.name = name;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.rootPart = rootPart;
        this.textureLocation = textureLocation;
        this.partsByName = partsByName != null ? partsByName : Collections.emptyMap();
        this.restRotations = restRotations != null ? restRotations : Collections.emptyMap();
        this.restPositions = restPositions != null ? restPositions : Collections.emptyMap();
        this.animations = animations != null ? animations : Collections.emptyMap();
        this.boneSubtrees = boneSubtrees != null ? boneSubtrees : Collections.emptyMap();

        List<PartRestPose> restList = new ArrayList<>();
        Set<ModelPart> seenParts = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var entry : this.partsByName.entrySet()) {
            ModelPart part = entry.getValue();
            if (part != null && seenParts.add(part)) {
                String k = entry.getKey();
                float[] r = this.restRotations.get(k);
                float[] p = this.restPositions.get(k);
                float rx = r != null ? r[0] : part.xRot;
                float ry = r != null ? r[1] : part.yRot;
                float rz = r != null ? r[2] : part.zRot;
                float px = p != null ? p[0] : part.x;
                float py = p != null ? p[1] : part.y;
                float pz = p != null ? p[2] : part.z;
                restList.add(new PartRestPose(part, rx, ry, rz, px, py, pz));
            }
        }
        this.uniqueRestPoses = restList.toArray(new PartRestPose[0]);

        ModelPart h = getPart("head");
        if (h == null) h = getPart("head_root");
        if (h == null) h = getPart("head_box");
        this.cachedHeadPart = h;

        this.cachedIdleAnim = resolveIdleAnimation();
        this.cachedWalkAnim = resolveWalkAnimation();
        this.cachedTalkAnim = resolveTalkAnimation();
    }

    private BbAnimation resolveIdleAnimation() {
        for (String kw : new String[]{"idle", "stand", "ambient", "wait", "stay", "rest", "look"}) {
            BbAnimation anim = findAnimation(kw);
            if (anim != null) return anim;
        }
        BbAnimation def = findAnimation("default");
        if (def != null) return def;
        BbAnimation pose = findAnimation("pose");
        if (pose != null) return pose;

        // Any looping animation is considered an idle animation (action clips with once are not)
        for (BbAnimation a : animations.values()) {
            if ("loop".equalsIgnoreCase(a.getLoop())) {
                return a;
            }
        }
        return null;
    }

    private BbAnimation resolveWalkAnimation() {
        BbAnimation walk = findAnimation("walk");
        if (walk == null) walk = findAnimation("run");
        if (walk == null) walk = findAnimation("move");
        return walk;
    }

    private BbAnimation resolveTalkAnimation() {
        BbAnimation talk = findAnimation("talk");
        if (talk == null) talk = findAnimation("dialog");
        return talk;
    }

    public String getName() {
        return name;
    }

    public int getTextureWidth() {
        return textureWidth;
    }

    public int getTextureHeight() {
        return textureHeight;
    }

    public ModelPart getRootPart() {
        return rootPart;
    }

    public ResourceLocation getTextureLocation() {
        return textureLocation;
    }

    public Map<String, ModelPart> getPartsByName() {
        return partsByName;
    }

    public Map<String, float[]> getRestRotations() {
        return restRotations;
    }

    public Map<String, float[]> getRestPositions() {
        return restPositions;
    }

    public Map<String, BbAnimation> getAnimations() {
        return animations;
    }

    public ModelPart getPart(String name) {
        if (name == null) return null;
        return partsByName.get(name.toLowerCase());
    }

    public boolean hasAnimation(String name) {
        return findAnimation(name) != null;
    }

    public boolean isBoneInMask(String boneName, String mask) {
        if (mask == null || mask.isBlank() || mask.equalsIgnoreCase("all") || mask.equalsIgnoreCase("все") || mask.equalsIgnoreCase("вся модель")) {
            return true;
        }
        if (boneName == null) return false;
        String bLower = boneName.toLowerCase(Locale.ROOT);
        String mLower = mask.toLowerCase(Locale.ROOT);
        if (bLower.equals(mLower)) return true;

        Set<String> subtree = boneSubtrees.get(mLower);
        if (subtree != null && subtree.contains(bLower)) return true;

        if (mLower.equals("head") || mLower.equals("голова")) {
            return bLower.contains("head") || bLower.contains("hat") || bLower.contains("jaw") || bLower.contains("eye") || bLower.contains("hair") || bLower.contains("ear");
        }
        if (mLower.equals("right_arm") || mLower.equals("правая рука")) {
            return (bLower.contains("arm") || bLower.contains("hand")) && (bLower.contains("right") || bLower.contains("r_") || bLower.startsWith("r"));
        }
        if (mLower.equals("left_arm") || mLower.equals("левая рука")) {
            return (bLower.contains("arm") || bLower.contains("hand")) && (bLower.contains("left") || bLower.contains("l_") || bLower.startsWith("l"));
        }
        if (mLower.equals("body") || mLower.equals("торс") || mLower.equals("тело")) {
            return bLower.contains("body") || bLower.contains("torso") || bLower.contains("chest") || bLower.contains("waist") || bLower.contains("spine");
        }
        if (mLower.equals("legs") || mLower.equals("ноги")) {
            return bLower.contains("leg") || bLower.contains("foot") || bLower.contains("feet") || bLower.contains("boot");
        }

        return false;
    }

    public BbAnimation findAnimation(String keyword) {
        if (keyword == null || animations.isEmpty()) return null;
        String kw = keyword.toLowerCase();
        for (var entry : animations.entrySet()) {
            if (entry.getKey().contains(kw)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public BbAnimation findIdleAnimation() {
        return cachedIdleAnim;
    }

    public void setupAnim(
        GuideNpc npc,
        float limbSwing,
        float limbSwingAmount,
        float ageInTicks,
        float netHeadYaw,
        float headPitch,
        float partialTicks
    ) {
        // 1. Reset all unique bones to rest pose (O(unique bones) flat array, zero map lookup)
        for (PartRestPose rp : uniqueRestPoses) {
            rp.part.xRot = rp.rx;
            rp.part.yRot = rp.ry;
            rp.part.zRot = rp.rz;
            rp.part.x = rp.px;
            rp.part.y = rp.py;
            rp.part.z = rp.pz;
            rp.part.xScale = 1.0f;
            rp.part.yScale = 1.0f;
            rp.part.zScale = 1.0f;
        }

        // 2. Select base animation (Layer 0)
        BbAnimation baseAnim = null;
        float baseAnimTime = 0.0f;
        if (npc.isTalking()) {
            baseAnim = cachedTalkAnim;
            if (baseAnim == null) baseAnim = cachedIdleAnim;
            baseAnimTime = (npc.tickCount + partialTicks) / 20.0f;
        } else if (limbSwingAmount > 0.05f) {
            baseAnim = cachedWalkAnim;
            if (baseAnim != null) {
                baseAnimTime = limbSwing * 0.6f;
            } else {
                baseAnim = cachedIdleAnim;
                baseAnimTime = (npc.tickCount + partialTicks) / 20.0f;
            }
        } else {
            baseAnim = cachedIdleAnim;
            baseAnimTime = (npc.tickCount + partialTicks) / 20.0f;
        }

        // 3. Select custom animation (Layer 1)
        BbAnimation customAnim = null;
        float customAnimTime = 0.0f;
        String customAnimName = npc.getCustomAnimName();
        String customAnimBone = npc.getCustomAnimBone();

        if (customAnimName != null && !customAnimName.isBlank()) {
            BbAnimation custom = findAnimation(customAnimName);
            if (custom != null) {
                int elapsedTicks = npc.tickCount - npc.getCustomAnimStartTick();
                float elapsedSec = Math.max(0.0f, (elapsedTicks + partialTicks) / 20.0f);
                float animLen = custom.getLength();
                if (animLen <= 0.05f) animLen = 1.0f;

                boolean loopMode = npc.isCustomAnimLoop() || "loop".equalsIgnoreCase(custom.getLoop());
                if (loopMode) {
                    customAnim = custom;
                    customAnimTime = animLen > 0.001f ? (elapsedSec % animLen) : 0.0f;
                } else if (elapsedSec <= animLen) {
                    customAnim = custom;
                    customAnimTime = elapsedSec;
                }
            }
        }

        boolean hasMask = customAnimBone != null && !customAnimBone.isBlank() && !customAnimBone.equalsIgnoreCase("all");

        // 4. Apply Layer 0 (baseAnim)
        if (baseAnim != null) {
            float len = baseAnim.getLength();
            if (len > 0.001f) {
                baseAnimTime = baseAnimTime % len;
                if (baseAnimTime < 0.0f) baseAnimTime += len;
            }

            for (String boneName : baseAnim.getAnimatedBones()) {
                if (customAnim != null) {
                    if (!hasMask && customAnim.getAnimatedBones().contains(boneName)) {
                        continue;
                    }
                    if (hasMask && isBoneInMask(boneName, customAnimBone) && customAnim.getAnimatedBones().contains(boneName)) {
                        continue;
                    }
                }

                ModelPart part = partsByName.get(boneName);
                if (part == null) continue;

                float[] rot = baseAnim.sample(baseAnimTime, boneName, "rotation");
                if (rot != null) {
                    part.xRot += (float) Math.toRadians(-rot[0]);
                    part.yRot += (float) Math.toRadians(-rot[1]);
                    part.zRot += (float) Math.toRadians(rot[2]);
                }

                float[] pos = baseAnim.sample(baseAnimTime, boneName, "position");
                if (pos != null) {
                    part.x += -pos[0];
                    part.y -= pos[1];
                    part.z += pos[2];
                }

                float[] scale = baseAnim.sample(baseAnimTime, boneName, "scale");
                if (scale != null) {
                    part.xScale *= scale[0];
                    part.yScale *= scale[1];
                    part.zScale *= scale[2];
                }
            }
        } else {
            // Procedural fallback if model has no idle/walk animation at all
            if (limbSwingAmount > 0.05f) {
                ModelPart rightArm = getPart("right_arm");
                ModelPart leftArm = getPart("left_arm");
                ModelPart rightLeg = getPart("right_leg");
                ModelPart leftLeg = getPart("left_leg");
                if (rightArm != null) rightArm.xRot += Mth.cos(limbSwing * 0.6662F + (float)Math.PI) * 1.2F * limbSwingAmount;
                if (leftArm != null) leftArm.xRot += Mth.cos(limbSwing * 0.6662F) * 1.2F * limbSwingAmount;
                if (rightLeg != null) rightLeg.xRot += Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
                if (leftLeg != null) leftLeg.xRot += Mth.cos(limbSwing * 0.6662F + (float)Math.PI) * 1.4F * limbSwingAmount;
            } else if (npc != null) {
                ModelPart body = getPart("body");
                if (body != null) {
                    body.xRot += (float) Math.sin((npc.tickCount + partialTicks) * 0.08f) * 0.02f;
                }
            }
        }

        // 5. Apply Layer 1 (customAnim)
        if (customAnim != null) {
            for (String boneName : customAnim.getAnimatedBones()) {
                if (hasMask && !isBoneInMask(boneName, customAnimBone)) {
                    continue;
                }

                ModelPart part = partsByName.get(boneName);
                if (part == null) continue;

                float[] rot = customAnim.sample(customAnimTime, boneName, "rotation");
                if (rot != null) {
                    part.xRot += (float) Math.toRadians(-rot[0]);
                    part.yRot += (float) Math.toRadians(-rot[1]);
                    part.zRot += (float) Math.toRadians(rot[2]);
                }

                float[] pos = customAnim.sample(customAnimTime, boneName, "position");
                if (pos != null) {
                    part.x += -pos[0];
                    part.y -= pos[1];
                    part.z += pos[2];
                }

                float[] scale = customAnim.sample(customAnimTime, boneName, "scale");
                if (scale != null) {
                    part.xScale *= scale[0];
                    part.yScale *= scale[1];
                    part.zScale *= scale[2];
                }
            }
        }

        // 6. Smooth head tracking: look towards the player if lookAtPlayer is enabled
        if (cachedHeadPart != null && npc != null && npc.profile().lookAtPlayer) {
            float clampedYaw = Mth.clamp(netHeadYaw, -50.0f, 50.0f);
            float clampedPitch = Mth.clamp(headPitch, -22.0f, 22.0f);
            cachedHeadPart.yRot += (float) Math.toRadians(clampedYaw);
            cachedHeadPart.xRot += (float) Math.toRadians(clampedPitch);
        }
    }

    public static final ThreadLocal<Integer> CURRENT_LOD_LEVEL = ThreadLocal.withInitial(() -> 0);
    public static final ThreadLocal<Boolean> CURRENT_LOD = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public void render(
        GuideNpc npc,
        float entityYaw,
        float partialTicks,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        int lodLevel
    ) {
        CURRENT_LOD_LEVEL.set(lodLevel);
        CURRENT_LOD.set(lodLevel > 0);
        try {
            poseStack.pushPose();

            float scale = npc != null ? npc.getScale() : 1.0f;
            if (scale <= 0.01f && npc != null) {
                scale = (float) npc.visualScale() / 100.0f;
            }
            if (scale <= 0.01f) scale = 1.0f;
            scale = Math.max(0.1f, Math.min(3.0f, scale));

            poseStack.scale(scale, scale, scale);

            float bodyRot = Mth.rotLerp(partialTicks, npc.yBodyRotO, npc.yBodyRot);
            float headRot = Mth.rotLerp(partialTicks, npc.yHeadRotO, npc.yHeadRot);
            float netHeadYaw = Mth.wrapDegrees(headRot - bodyRot);
            float headPitch = Mth.rotLerp(partialTicks, npc.xRotO, npc.getXRot());
            float limbSwing = npc.walkAnimation.position(partialTicks);
            float limbSwingAmount = npc.walkAnimation.speed(partialTicks);
            float ageInTicks = lodLevel >= 1 ? (float) npc.tickCount : ((float) npc.tickCount + partialTicks);

            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyRot));
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.translate(0.0F, -1.501F, 0.0F);

            setupAnim(npc, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, lodLevel >= 1 ? 0.0f : partialTicks);

            RenderType renderType = RenderType.entityCutoutNoCull(this.textureLocation);
            VertexConsumer vertexConsumer = buffer.getBuffer(renderType);
            rootPart.render(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, -1);

            poseStack.popPose();
        } finally {
            CURRENT_LOD_LEVEL.set(0);
            CURRENT_LOD.set(Boolean.FALSE);
        }
    }

    public void render(
        GuideNpc npc,
        float entityYaw,
        float partialTicks,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        boolean isLod
    ) {
        render(npc, entityYaw, partialTicks, poseStack, buffer, packedLight, isLod ? 1 : 0);
    }

    public void render(
        GuideNpc npc,
        float entityYaw,
        float partialTicks,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight
    ) {
        render(npc, entityYaw, partialTicks, poseStack, buffer, packedLight, false);
    }
}
