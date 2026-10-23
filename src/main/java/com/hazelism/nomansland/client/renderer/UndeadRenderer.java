package com.hazelism.nomansland.client.renderer;

import com.hazelism.nomansland.client.model.UndeadModel;
import com.hazelism.nomansland.entity.UndeadEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Plain GeckoLib renderer: the corrupted illager model is all cubes, no custom passes needed. */
public class UndeadRenderer extends GeoEntityRenderer<UndeadEntity> {
    public UndeadRenderer(EntityRendererProvider.Context context) {
        super(context, new UndeadModel());
        this.shadowRadius = 0.5F; // matches a 0.6-wide humanoid
    }
}
