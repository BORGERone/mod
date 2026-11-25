package net.minecraft.network.chat;

import net.minecraft.ChatFormatting;

/** Compile-time mirror (Build 443) verified via ForgeJavaDocs-NG 1.21.x-neoforge. */
public class Style {
    public static final Style EMPTY = new Style();

    public TextColor getColor() { return null; }

    public boolean isBold() { return false; }

    public boolean isItalic() { return false; }

    public boolean isStrikethrough() { return false; }

    public boolean isUnderlined() { return false; }

    public boolean isObfuscated() { return false; }

    public Style withColor(TextColor color) { return this; }

    public Style withColor(ChatFormatting formatting) { return this; }

    public Style withColor(int rgb) { return this; }

    public Style withBold(Boolean bold) { return this; }

    public Style withItalic(Boolean italic) { return this; }

    public Style withUnderlined(Boolean underlined) { return this; }

    public Style withStrikethrough(Boolean strikethrough) { return this; }

    public Style withObfuscated(Boolean obfuscated) { return this; }
}
