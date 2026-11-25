package net.minecraft.client.gui;

import java.util.Collections;
import java.util.List;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/** Compile-time mirror (Build 443): extends Build 440 Font with split() & width(FormattedText). */
public class Font {
    public int width(String s) {
        return s == null ? 0 : s.length() * 6;
    }

    public int width(FormattedText text) {
        return text == null ? 0 : width(text.getString());
    }

    public String plainSubstrByWidth(String text, int maxWidth) {
        return text == null ? "" : text;
    }

    public List<FormattedCharSequence> split(FormattedText text, int maxWidth) {
        return Collections.emptyList();
    }
}
