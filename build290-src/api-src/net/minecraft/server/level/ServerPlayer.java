package net.minecraft.server.level;

import net.minecraft.world.entity.player.Player;

/** ServerPlayer extends Player in 1.21.1 - the stub mirrors that, not PathfinderMob. */
public class ServerPlayer extends Player {
    public boolean isSpectator() { return false; }
}
