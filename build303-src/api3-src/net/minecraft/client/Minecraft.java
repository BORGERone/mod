package net.minecraft.client;

import net.minecraft.client.player.LocalPlayer;

/**
 * Compile-time mirror (Build 318): the clipboard hangs off the client instance.
 *
 * <p>Build 342 adds the one field a scene hand needs: the player the client draws in first person.
 * The mirror returns no instance, so a harness sees {@code null} here and the hand-over refuses
 * itself - which is exactly what the guard in {@code CinematicArms342} is for.</p>
 */
public class Minecraft {
    public KeyboardHandler keyboardHandler;
    public LocalPlayer player;

    public static Minecraft getInstance() { return null; }
}
