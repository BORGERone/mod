package net.minecraft.resources;

/** Compile-time mirror (Build 436): adds getNamespace/getPath already live in MC 1.21.1 and base jar. */
public class ResourceLocation {
    private final String raw;

    public ResourceLocation() { this(""); }

    public ResourceLocation(String namespace, String path) {
        this.raw = namespace + ":" + path;
    }

    private ResourceLocation(String raw) {
        this.raw = raw;
    }

    public static ResourceLocation fromNamespaceAndPath(String namespace, String path) {
        return new ResourceLocation(namespace, path);
    }

    public static ResourceLocation tryParse(String location) {
        if (location == null || location.isEmpty()) return null;
        return new ResourceLocation(location.indexOf(':') >= 0 ? location : "minecraft:" + location);
    }

    public String getNamespace() {
        int colon = raw.indexOf(':');
        return colon >= 0 ? raw.substring(0, colon) : "minecraft";
    }

    public String getPath() {
        int colon = raw.indexOf(':');
        return colon >= 0 ? raw.substring(colon + 1) : raw;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        return other instanceof ResourceLocation rl && raw.equals(rl.raw);
    }

    @Override
    public int hashCode() {
        return raw.hashCode();
    }

    @Override public String toString() { return raw; }
}
