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

    /**
     * Build 397: open a menu for this player - a chest's window, after the panel that locks it was
     * cracked. Returns the menu id, which this mod does not use; the descriptor (including
     * {@code OptionalInt}) is the base jar's own reference to this very method.
     */
    public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider) { return null; }
}
