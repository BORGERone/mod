package net.minecraft.server.level;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

/**
 * ServerPlayer extends Player in 1.21.1 - the stub mirrors that, not PathfinderMob.
 * Build 334 adds {@code getServer()}: the base jar already calls it on a ServerPlayer
 * (NpcMobilityGroups, GuideDialogue and ten more classes), the mirror just never had it.
 */
public class ServerPlayer extends Player {
    public boolean isSpectator() { return false; }

    public MinecraftServer getServer() { return null; }
}
