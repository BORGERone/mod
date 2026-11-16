package dev.settledlands.npc;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Build 420: one server tick, one look at the anchors.
 *
 * <p>The pass is paced on the world's own clock - every {@link NpcGraphAnchor420#PERIOD} ticks - and it
 * is the same shape the radius pass of build 354 already uses: a plain {@code ServerTickEvent.Post}, no
 * mixin, nothing that can fail to apply on a different loader version.</p>
 *
 * <p>A world with no players (or a world the author is not standing in) is left alone by the pass itself,
 * so a dedicated server with nobody on it does no work at all.</p>
 */
@EventBusSubscriber(modid = "settledlands")
public final class NpcGraphAnchorEvents420 {

    private NpcGraphAnchorEvents420() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event == null) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        if (server.overworld() == null) return;
        if (server.overworld().getGameTime() % NpcGraphAnchor420.PERIOD != 0L) return;
        NpcGraphAnchor420.pass(server);
    }
}
