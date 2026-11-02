package net.minecraft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Compile-time shape of a baked entity model (Build 348).
 *
 * <p>Two things are needed: the type {@code EntityModelSet#bakeLayer} hands back, and the draw call
 * vanilla's armour layer uses - {@code renderToBuffer(pose, buffer, packedLight, packedOverlay,
 * color)}, the five-argument form the 1.21.1 layer calls with {@code OverlayTexture.NO_OVERLAY} and
 * the layer tint. A stub is needed because the profile this project compiles against does not ship
 * the client model package.</p>
 */
public abstract class EntityModel<T> {
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight, int packedOverlay,
                               int color) {
    }
}
