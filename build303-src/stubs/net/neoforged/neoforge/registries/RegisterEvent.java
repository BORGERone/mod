package net.neoforged.neoforge.registries;

import java.util.function.Supplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

/**
 * Compile-time shape of the mod-bus registration event the shipped jar already uses for its mob
 * effect ({@code PossessionSystem$ModBus}). Build 368 registers one item with it.
 */
public class RegisterEvent extends Event {
    public ResourceKey<?> getRegistryKey() { return null; }

    public <T> void register(ResourceKey<?> registryKey, ResourceLocation name, Supplier<T> supplier) {
    }
}
