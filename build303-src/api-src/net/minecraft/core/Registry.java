package net.minecraft.core;

import net.minecraft.resources.ResourceLocation;

/**
 * Build 420: the two directions a block fingerprint needs - {@code getKey} and {@code get}.
 *
 * <p>Both shapes are read off the base jar, not remembered: {@code NpcItems} calls
 * {@code BuiltInRegistries.ITEM.getKey(stack.getItem())} and keeps the answer as a
 * {@link ResourceLocation} (so {@code getKey} is the id itself and may be null), and
 * {@code WorldEvents} does the same for an entity type. {@code get} answers the value or null, which is
 * what a schematic naming a block of a mod that is not installed has to get back.</p>
 */
public interface Registry<T> {

    /** The id of a registered value, or null when the value was never registered. */
    default ResourceLocation getKey(T value) {
        return null;
    }

    /** The value behind an id, or null when nothing is registered under it. */
    default T get(ResourceLocation id) {
        return null;
    }

    /** True when the id is registered here. */
    default boolean containsKey(ResourceLocation id) {
        return false;
    }
}
