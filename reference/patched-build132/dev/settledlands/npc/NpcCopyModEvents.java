package dev.settledlands.npc;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = "settledlands", bus = EventBusSubscriber.Bus.MOD)
public final class NpcCopyModEvents {
    private NpcCopyModEvents() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        NpcCopyPickPayload.register(event);
        CutsceneCamExitPayload.register(event);
        CutsceneCamStatePayload.register(event);
        CutsceneRecTogglePayload.register(event);
        CutsceneSceneListPayload.register(event);
        CutscenePlayingPayload.register(event);
    }
}
