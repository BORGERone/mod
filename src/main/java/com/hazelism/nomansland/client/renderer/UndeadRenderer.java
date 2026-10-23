package com.hazelism.nomansland.client.renderer;

import com.hazelism.nomansland.client.model.UndeadModel;
import com.hazelism.nomansland.entity.UndeadEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** GeckoLib renderer; corpses are tipped onto their back (the model has no death clip). */
public class UndeadRenderer extends GeoEntityRenderer<UndeadEntity> {
    public UndeadRenderer(EntityRendererProvider.Context context) {
        super(context, new UndeadModel());
        this.shadowRadius = 0.5F; // matches a 0.6-wide humanoid
    }

    @Override
    protected void applyRotations(UndeadEntity animatable, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTick, float nativeScale) {
        super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick, nativeScale);
        if (animatable.isCorpse()) {
            // Rotate around the feet so the body falls backwards flat onto the ground.
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        }
    }
}
