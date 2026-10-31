package dev.settledlands.audio;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = "settledlands")
public final class AudioEvents {
    private AudioEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        PlayAudioCommand.register(event.getDispatcher());
    }
}
