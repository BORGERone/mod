package net.minecraft.client.gui;

public class Font {
    public int width(String s) {
        return s == null ? 0 : s.length() * 6;
    }
}
