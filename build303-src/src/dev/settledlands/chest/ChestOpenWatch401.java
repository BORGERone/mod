package dev.settledlands.chest;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Build 401: one client tick a call into {@link ChestOpen401#watch()}, and nothing else.
 *
 * <p>The same shape as the door's watch of build 380 ({@code DoorHackWatch380}) and for the same reason: the
 * click of a cracked chest now waits for the window of its «взлом» chain to close, and a screen closing is
 * not something this mod can subscribe to. {@code value = Dist.CLIENT} is the marker the base mod's own
 * client listener carries, so this class is never loaded on a dedicated server.</p>
 */
@EventBusSubscriber(modid = "settledlands", value = {Dist.CLIENT})
public final class ChestOpenWatch401 {

    private ChestOpenWatch401() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ChestOpen401.watch();
    }
}
