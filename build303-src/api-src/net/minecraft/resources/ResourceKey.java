package net.minecraft.resources;

import net.minecraft.core.Registry;

public class ResourceKey<T> {
    private final ResourceLocation loc;

    public ResourceKey() { this.loc = null; }
    private ResourceKey(ResourceLocation loc) { this.loc = loc; }

    public ResourceLocation location() { return loc; }

    public static <T> ResourceKey<T> create(ResourceKey<? extends Registry<T>> registry,
            ResourceLocation location) {
        return location == null ? null : new ResourceKey<>(location);
    }

    @Override public String toString() {
        return loc == null ? "ResourceKey[null]" : "ResourceKey[minecraft:dimension / " + loc + "]";
    }
}
