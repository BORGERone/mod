package net.minecraft.client.player;

import net.minecraft.world.entity.player.Player;

/**
 * Compile-time shape of the client player class the arm bridge names.
 *
 * <p>Only the type is needed: the instance is passed straight to the possession state of Build 256,
 * which is where the skin lookup and the possession state live. It extends {@link Player} because the
 * render event hands out a {@code Player}. A stub is needed because the profile this project compiles
 * against does not ship the client player classes.</p>
 */
public class AbstractClientPlayer extends Player {
}
