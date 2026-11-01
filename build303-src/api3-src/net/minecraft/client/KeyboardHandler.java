package net.minecraft.client;

/**
 * Compile-time mirror (Build 318).
 *
 * <p>Only the clipboard pair is declared. They are the two methods vanilla {@code EditBox} calls
 * for Ctrl+C and Ctrl+V, so every mod that puts text on the system clipboard calls them the same
 * way.</p>
 */
public class KeyboardHandler {
    public void setClipboard(String text) { }

    public String getClipboard() { return ""; }
}
