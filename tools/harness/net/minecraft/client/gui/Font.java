package net.minecraft.client.gui;

/** Harness: six pixels per character. */
public class Font {
    public int width(String text) {
        return text == null ? 0 : text.length() * 6;
    }
}
