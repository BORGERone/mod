package net.minecraft.core;

/**
 * Compile-time mirror (Build 425): {@code RegistryAccess} extends {@link HolderLookup.Provider} in MC 1.21.1.
 */
public interface RegistryAccess extends HolderLookup.Provider {
    interface Frozen extends RegistryAccess {}

    Frozen EMPTY = new Frozen() {};
}
