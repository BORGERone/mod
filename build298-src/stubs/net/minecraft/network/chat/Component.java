package net.minecraft.network.chat;

/** Compile-time signature only; never bundled into the mod JAR. */
public interface Component {
    static MutableComponent translatable(String s) {
        return new MutableComponent(s);
    }

    static MutableComponent literal(String s) {
        return new MutableComponent(s);
    }

    String getString();
}
