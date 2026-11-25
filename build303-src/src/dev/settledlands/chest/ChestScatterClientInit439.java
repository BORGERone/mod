package dev.settledlands.chest;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Build 439: client-only MOD-bus initializer registering the optional Diegesis behavior that
 * commits client-computed 3D loot scatter poses back to the server on first station open.
 */
@EventBusSubscriber(modid = "settledlands", bus = EventBusSubscriber.Bus.MOD, value = {Dist.CLIENT})
public final class ChestScatterClientInit439 {

    private ChestScatterClientInit439() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (event == null) return;
        event.enqueueWork(DiegesisScatterClient439::registerBehavior);
    }
}
