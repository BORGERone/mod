package net.minecraft.world.level.block.state.properties;

import java.util.Optional;

/**
 * Compile-time mirror (Build 425): adds {@link #getValue(String)} matching MC 1.21.1 {@code Property}.
 */
public abstract class Property<T extends Comparable<T>> {
    public String getName() { return ""; }

    public Optional<T> getValue(String value) { return Optional.empty(); }
}
