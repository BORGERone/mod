package com.hazelism.nomansland.client.model;

import com.hazelism.nomansland.NoMansLand;
import com.hazelism.nomansland.entity.BannerBearerEntity;
import net.minecraft.resources.ResourceLocation;
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
}
