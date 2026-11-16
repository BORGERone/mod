package net.minecraft.client.player;

/**
 * Compile-time shape of the client's own player.
 *
 * <p>Build 342 asks the client which player a Cinematic Interactions scene hand belongs to, and the
 * answer the game keeps is the local player. Only the type is needed: the instance goes straight to the
 * possession state of Build 256, which is where the possessed model lives. A stub is needed because the
 * profile this project compiles against does not ship the client player classes.</p>
 */
public class LocalPlayer extends AbstractClientPlayer {
}
