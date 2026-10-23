package com.hazelism.nomansland.client.model;

import com.hazelism.nomansland.NoMansLand;
import com.hazelism.nomansland.entity.BannerBearerEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * Placeholder assets: the corrupted illager's geometry/texture until the dedicated
 * banner bearer model arrives (drop-in replacement of the three files below).
 * The copied animation file additionally carries a simple cast loop.
 * Pre-built constants: these getters run per render - never allocate here.
 */
public class BannerBearerModel extends GeoModel<BannerBearerEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(NoMansLand.MOD_ID, "geo/entity/banner_bearer.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(NoMansLand.MOD_ID, "textures/entity/banner_bearer.png");
    private static final ResourceLocation ANIMATIONS = ResourceLocation.fromNamespaceAndPath(NoMansLand.MOD_ID, "animations/entity/banner_bearer.animation.json");

    @Override
    public ResourceLocation getModelResource(BannerBearerEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(BannerBearerEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(BannerBearerEntity animatable) {
        return ANIMATIONS;
    }

    /**
     * Permanent hunch: this is a frail, stooped skeleton, so the torso and head are
     * pitched forward on top of whatever the animations do (radians, ~20 and ~12
     * degrees). Skipped while casting - the cast pose rears up with the banner.
     * If the dedicated herald model arrives already hunched, delete this override.
     */
    @Override
    public void setCustomAnimations(BannerBearerEntity animatable, long instanceId, AnimationState<BannerBearerEntity> state) {
        super.setCustomAnimations(animatable, instanceId, state);
        if (animatable.isCasting()) {
            return;
        }
        getBone("body").ifPresent(bone -> bone.setRotX(bone.getRotX() + 0.35F));
        getBone("head").ifPresent(bone -> bone.setRotX(bone.getRotX() + 0.21F));
    }
}
