package dev.settledlands.door;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Build 380: one client tick a call into {@link DoorHack377#watch()}, and nothing else.
 *
 * <p>The author's report was "the hacking is what I want now, but the door still does not open" - and a
 * door that does not open is the same picture whether the panel never sent anything, the packet never
 * arrived or the server refused it. The client keeps its own list of the doors it has asked about and
 * this tick is what reads it: opened, re-asked, or reported as still shut.</p>
 *
 * <p>{@code value = Dist.CLIENT} is the same marker the base mod's own client listener carries
 * ({@code dev.settledlands.client.RitualClient}), so the class is never loaded on a dedicated server.</p>
 */
@EventBusSubscriber(modid = "settledlands", value = {Dist.CLIENT})
public final class DoorHackWatch380 {

    private DoorHackWatch380() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        DoorHack377.watch();
    }
}
