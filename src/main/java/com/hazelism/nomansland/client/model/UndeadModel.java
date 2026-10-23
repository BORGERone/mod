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

    /**
     * Ports the Bedrock render controller's part_visibility:
     * the "broken" bone variants are hidden until that limb is actually broken.
     * Without this both variants render at once and the mob looks like a pile of parts.
     *
     * <p>Runs per render frame - only constant-name bone lookups and boolean writes,
     * no allocations. The limb-loss stage will flip these from the entity's synced data.
     */
    @Override
    public void setCustomAnimations(UndeadEntity animatable, long instanceId, AnimationState<UndeadEntity> state) {
        super.setCustomAnimations(animatable, instanceId, state);
        setBrokenHidden("broken_head", true);
        setBrokenHidden("broken_jaw", true);
        setBrokenHidden("broken_body", true);
        setBrokenHidden("broken_left_arm", true);
        setBrokenHidden("broken_right_arm", true);
        setBrokenHidden("broken_left_leg", true);
        setBrokenHidden("broken_right_leg", true);
    }

    private void setBrokenHidden(String boneName, boolean hidden) {
        getBone(boneName).ifPresent(bone -> bone.setHidden(hidden));
    }
}
