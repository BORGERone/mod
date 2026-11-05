package net.neoforged.neoforge.network.event;

import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Build 376: the mod-bus event every modded packet is registered on. */
public class RegisterPayloadHandlersEvent extends Event {
    public PayloadRegistrar registrar(String version) {
        return null;
    }
}
