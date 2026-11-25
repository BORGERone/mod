package net.minecraft.server.network;

import java.util.Optional;

/** Compile-time mirror (Build 443) verified via ForgeJavaDocs-NG 1.21.x-neoforge. */
public final class Filterable<T> {
    private final T raw;
    private final Optional<T> filtered;

    public Filterable(T raw, Optional<T> filtered) {
        this.raw = raw;
        this.filtered = filtered;
    }

    public static <T> Filterable<T> passThrough(T value) {
        return new Filterable<>(value, Optional.empty());
    }

    public T raw() {
        return raw;
    }

    public Optional<T> filtered() {
        return filtered;
    }
}
