package net.minecraft.network.chat;

/** Compile-time signature only (Build 440); never bundled into the mod JAR. */
public interface Component {
    static MutableComponent translatable(String s) {
        return new MutableComponent(s);
    }

    static MutableComponent translatable(String s, Object... args) {
        return new MutableComponent(s);
    }

    static MutableComponent literal(String s) {
        return new MutableComponent(s);
    }

    static MutableComponent empty() {
        return new MutableComponent("");
    }

    String getString();
}
