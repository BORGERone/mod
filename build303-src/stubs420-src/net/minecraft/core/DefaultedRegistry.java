package net.minecraft.core;

/**
 * Build 420: the static type the game gives the built-in registries.
 *
 * <p>The base jar reads {@code BuiltInRegistries.ITEM} and {@code BuiltInRegistries.ENTITY_TYPE} as
 * {@code Lnet/minecraft/core/DefaultedRegistry;} and calls {@code get}/{@code getKey} on them, so the two
 * new reads are written against the same type and compile to the same owner constant.</p>
 */
public class DefaultedRegistry<T> implements Registry<T> {
}
