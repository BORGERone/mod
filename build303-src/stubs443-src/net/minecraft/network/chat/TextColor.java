package net.minecraft.network.chat;

/** Compile-time mirror (Build 443) verified via ForgeJavaDocs-NG 1.21.x-neoforge. */
public final class TextColor {
    private final int value;

    private TextColor(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static TextColor fromRgb(int rgb) {
        return new TextColor(rgb & 0xFFFFFF);
    }
}
