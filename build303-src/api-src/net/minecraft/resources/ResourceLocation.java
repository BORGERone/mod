package net.minecraft.resources;

public class ResourceLocation {
    private final String raw;

    public ResourceLocation() { this(""); }

    public ResourceLocation(String namespace, String path) {
        this.raw = namespace + ":" + path;
    }

    private ResourceLocation(String raw) {
        this.raw = raw;
    }

    /** Build 368: the door key asks for its own registry name. */
    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    /** Build 424: base jar parses dimension resource locations via tryParse. */
    public static ResourceLocation tryParse(String location) {
        if (location == null || location.isEmpty()) return null;
        return new ResourceLocation(location.indexOf(':') >= 0 ? location : "minecraft:" + location);
    }

    @Override public String toString() { return raw; }
}
