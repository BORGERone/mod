package net.neoforged.bus.api;

/**
 * Compile-time shape of the bus event base class. Build 371 needs {@code Event.Result}: the explicit
 * "deny this use" answer a cancelled interaction can be replaced with.
 */
public class Event {

    public enum Result {
        DENY,
        DEFAULT,
        ALLOW
    }
}
