package net.minecraft.client.model.geom;

import net.minecraft.client.model.EntityModel;

/**
 * Compile-time shape of the baked-model set (Build 348).
 *
 * <p>{@code bakeLayer(ModelLayerLocation)} is the call every 1.21 mod makes to get a model the client
 * can draw; its erasure returns {@code EntityModel}, which is what this mirror declares.</p>
 */
public class EntityModelSet {
    public EntityModel<?> bakeLayer(ModelLayerLocation location) {
        return null;
    }
}
