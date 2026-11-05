package dev.settledlands.door;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The two door packets, and nothing else.
 *
 * <p>NeoForge 1.21.1: {@code event.registrar("1").playToClient(type, codec, handler)} and
 * {@code .playToServer(...)} on the mod bus. Handlers run on the main thread by default, which is what
 * writing to a client-side set and opening a door both need.</p>
 *
 * <ul>
 *   <li><b>376</b> {@code settledlands:door_sync} - server to client: which doors are locked, so the
 *       client can predict an ordinary door again without ever predicting a locked one.</li>
 *   <li><b>377</b> {@code settledlands:door_hack} - client to server: the lock panel was solved, open
 *       this door if it is still locked and the player is still beside it.</li>
 * </ul>
 *
 * <p>Each registration is guarded on its own: a network registry that refuses a payload must cost the
 * author that one feature, never the launch. Without the sync the client stays in the strict mode of
 * 375 (every door refused, correct but slower); without the hack packet the panel is a dead end and
 * the locked door is exactly what it was in 376.</p>
 */
@EventBusSubscriber(modid = "settledlands", bus = EventBusSubscriber.Bus.MOD)
public final class DoorNet375 {

    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/DoorNet375");

    private DoorNet375() {}

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        if (event == null) return;
        PayloadRegistrar registrar;
        try {
            registrar = event.registrar("1");
        } catch (Throwable failure) {
            LOG.warn("door: the door packets could not be registered at all, every door click will be "
                    + "refused on the client and no panel will open: " + failure);
            return;
        }
        try {
            registrar.playToClient(DoorSync375.TYPE, DoorSync375.STREAM_CODEC, DoorNet375::sync);
        } catch (Throwable failure) {
            LOG.warn("door: the lock sync could not be registered, the client will keep refusing every "
                    + "door click: " + failure);
        }
        try {
            registrar.playToServer(DoorHack377.TYPE, DoorHack377.STREAM_CODEC, DoorHack377::handle);
        } catch (Throwable failure) {
            LOG.warn("door: the hacking packet could not be registered, a solved panel will change "
                    + "nothing: " + failure);
        }
        LOG.info("door: settledlands:door_sync and settledlands:door_hack registered "
                + "(build 377 door hacking)");
    }

    private static void sync(DoorSync375 payload, IPayloadContext context) {
        if (payload == null) return;
        DoorSync375.apply(payload.data());
    }
}
