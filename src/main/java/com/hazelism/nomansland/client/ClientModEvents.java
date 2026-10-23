package com.hazelism.nomansland.client;

import com.hazelism.nomansland.NoMansLand;
import com.hazelism.nomansland.client.renderer.BannerBearerRenderer;
import com.hazelism.nomansland.client.renderer.UndeadRenderer;
import com.hazelism.nomansland.registry.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-only registrations. This class is loaded exclusively on the physical client
 * (EventBusSubscriber value = Dist.CLIENT), so the dedicated server never touches renderer classes.
 */
@EventBusSubscriber(modid = NoMansLand.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.UNDEAD.get(), UndeadRenderer::new);
        event.registerEntityRenderer(ModEntities.BANNER_BEARER.get(), BannerBearerRenderer::new);
    }

    private ClientModEvents() {}
}
