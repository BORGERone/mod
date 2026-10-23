package com.hazelism.nomansland.client.model;

import com.hazelism.nomansland.NoMansLand;
import com.hazelism.nomansland.entity.UndeadEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * Points GeckoLib at the assets ported 1:1 from the Bedrock resource pack:
 * models/entity/undead.geo.json -> geo/entity/undead.geo.json etc.
 * ResourceLocations are pre-built constants: getXxxResource() runs per render - never allocate here.
 */
public class UndeadModel extends GeoModel<UndeadEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(NoMansLand.MOD_ID, "geo/entity/corrupted_illager.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(NoMansLand.MOD_ID, "textures/entity/corrupted_illager.png");
    private static final ResourceLocation ANIMATIONS = ResourceLocation.fromNamespaceAndPath(NoMansLand.MOD_ID, "animations/entity/corrupted_illager.animation.json");

    @Override
    public ResourceLocation getModelResource(UndeadEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(UndeadEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(UndeadEntity animatable) {
        return ANIMATIONS;
    }

    /**
     * Left-arm break stages drive bone visibility (synced from the server):
     * 0 = left_arm, 1 = short_left_arm, 2 = left_arm_stomp.
     * Crossed-arms bones stay hidden via neverRender in the geometry.
     */
    @Override
    public void setCustomAnimations(UndeadEntity animatable, long instanceId, AnimationState<UndeadEntity> state) {
        super.setCustomAnimations(animatable, instanceId, state);
        int stage = animatable.getLeftArmStage();
        getBone("left_arm").ifPresent(bone -> bone.setHidden(stage != 0));
        getBone("short_left_arm").ifPresent(bone -> bone.setHidden(stage != 1));
        getBone("left_arm_stomp").ifPresent(bone -> bone.setHidden(stage != 2));
    }

    // Alternative limb bones (short_left_arm, left_arm_stomp, crossed arms) carry
    // "neverRender": true in the converted geometry; the limb-loss stage will
    // unhide them at runtime via GeoBone.setHidden.
}
