package net.neoforged.neoforge.client.event;

/**
 * Compile-time shape of the client tick event the base mod already listens to
 * ({@code dev.settledlands.client.RitualClient}: {@code ClientTickEvent.Post}).
 */
public class ClientTickEvent {

    public static class Pre extends ClientTickEvent {
    }

    public static class Post extends ClientTickEvent {
    }
}
