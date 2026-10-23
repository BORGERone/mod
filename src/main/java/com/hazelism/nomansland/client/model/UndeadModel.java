package com.hazelism.nomansland.client.model;

import com.hazelism.nomansland.NoMansLand;
import com.hazelism.nomansland.entity.UndeadEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Points GeckoLib at the assets ported 1:1 from the Bedrock resource pack:
 * models/entity/undead.geo.json -> geo/entity/undead.geo.json etc.
 * ResourceLocations are pre-built constants: getXxxResource() runs per render - never allocate here.
 */
public class UndeadModel extends GeoModel<UndeadEntity> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(NoMansLand.MOD_ID, "geo/entity/undead.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(NoMansLand.MOD_ID, "textures/entity/undead.png");
    private static final ResourceLocation ANIMATIONS = ResourceLocation.fromNamespaceAndPath(NoMansLand.MOD_ID, "animations/entity/undead.animation.json");

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

    // Bone variant visibility (normal vs broken) is baked into the geometry:
    // broken_* bones carry "neverRender": true (see tools/convert_bedrock_geo.py),
    // mirroring the Bedrock render controller's part_visibility defaults.
    // The limb-loss stage will unhide them at runtime via GeoBone.setHidden.
}
