package dev.settledlands.npc.client;

import net.minecraft.client.Minecraft;

/**
 * Build 318: the system clipboard, and nothing else.
 *
 * <p>Two calls -<br>
 * {@code Minecraft.getInstance().keyboardHandler.setClipboard(text)} and
 * {@code ...getClipboard()}. They are the same pair vanilla {@code EditBox} uses for Ctrl+C and
 * Ctrl+V, so they are as verified as it gets without the game jar; everything that can go wrong
 * (no client, no handler, a broken clipboard owner on the OS side) is swallowed, and the caller
 * only sees an empty string.</p>
 *
 * <p>Kept apart from {@link dev.settledlands.npc.NpcGraphClipboard318} so that the graph side stays
 * plain strings and can be tested outside the game.</p>
 */
public final class NpcClipboard318 {

    private NpcClipboard318() {
    }

    public static void write(String text) {
        if (text == null || text.isEmpty()) return;
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.keyboardHandler != null) {
                minecraft.keyboardHandler.setClipboard(text);
            }
        } catch (Throwable ignored) {
            // A clipboard failure must never break the editor.
        }
    }

    public static String read() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.keyboardHandler != null) {
                String text = minecraft.keyboardHandler.getClipboard();
                return text == null ? "" : text;
            }
        } catch (Throwable ignored) {
            // See write.
        }
        return "";
    }
}
