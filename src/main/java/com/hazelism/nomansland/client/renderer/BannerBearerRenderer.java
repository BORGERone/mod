package com.hazelism.nomansland.client.renderer;

import com.hazelism.nomansland.client.model.BannerBearerModel;
import com.hazelism.nomansland.entity.BannerBearerEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Plain GeckoLib renderer; the glowing-through-walls effect during casts is a vanilla mob effect. */
public class BannerBearerRenderer extends GeoEntityRenderer<BannerBearerEntity> {
    public BannerBearerRenderer(EntityRendererProvider.Context context) {
        super(context, new BannerBearerModel());
        this.shadowRadius = 0.5F;
    }
}
