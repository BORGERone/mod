package net.minecraft.resources;

public class ResourceLocation {
    public ResourceLocation() {}

    public ResourceLocation(String namespace, String path) {}

    /** Build 368: the door key asks for its own registry name. */
    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    @Override public String toString() { return ""; }
}
