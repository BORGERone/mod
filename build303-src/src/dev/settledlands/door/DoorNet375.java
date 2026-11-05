package dev.settledlands.door;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 376: the registration of {@code settledlands:door_sync}, and nothing else.
 *
 * <p>NeoForge 1.21.1: {@code event.registrar("1").playToClient(type, codec, handler)} on the mod bus.
 * The handler runs on the main thread by default, which is what writing to a client-side set needs.</p>
 *
 * <p>Registration is wrapped: a network registry that refuses this payload must cost the author the
 * quick opening of ordinary doors, not the launch. Without a snapshot the client stays in the strict
 * mode of build 375 - it refuses every door click - which is correct, only slower.</p>
 */
@EventBusSubscriber(modid = "settledlands", bus = EventBusSubscriber.Bus.MOD)
public final class DoorNet375 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/DoorNet375");

    private static boolean failed;

    private DoorNet375() {}

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        try {
            if (event == null) return;
            event.registrar("1").playToClient(DoorSync375.TYPE, DoorSync375.STREAM_CODEC, DoorNet375::handle);
            LOG.info("door: settledlands:door_sync registered (build 376 door-sync)");
        } catch (Throwable failure) {
            if (failed) return;
            failed = true;
            LOG.warn("door: the lock sync could not be registered, the client will keep refusing every "
                    + "door click: " + failure);
        }
    }

    private static void handle(DoorSync375 payload, IPayloadContext context) {
        if (payload == null) return;
        DoorSync375.apply(payload.data());
    }
}
