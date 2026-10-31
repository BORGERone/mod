package dev.settledlands.audio;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@SuppressWarnings("removal")
@EventBusSubscriber(modid = "settledlands", bus = EventBusSubscriber.Bus.MOD)
public final class AudioModEvents {
    private AudioModEvents() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PlayAudioPayload.register(event);
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        AudioStorage.init();
        dev.settledlands.model.BbModelStorage.init();
    }
}
