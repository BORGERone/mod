package net.minecraft.client.gui;

/** Compile-time mirror (Build 440): exposes plainSubstrByWidth alongside width(String). */
public class Font {
    public int width(String s) {
        return s == null ? 0 : s.length() * 6;
    }

    public String plainSubstrByWidth(String text, int maxWidth) {
        return text == null ? "" : text;
    }
}
