package dev.settledlands.npc.client;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcCopyPickPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Middle click on a guide NPC: ask the server for a copy egg (vanilla pick-block is bypassed). */
@EventBusSubscriber(modid = "settledlands", value = Dist.CLIENT)
public final class NpcPickCopy {
    private NpcPickCopy() {}

    @SubscribeEvent
    public static void onKeyTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isPickBlock()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.hitResult == null) return;
        if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof GuideNpc npc)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        PacketDistributor.sendToServer(new NpcCopyPickPayload(npc.getId()));
    }
}
