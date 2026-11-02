package net.minecraft.server;

import net.minecraft.server.level.ServerLevel;

/**
 * Compile-time mirror. Build 334 adds {@code overworld()}: CutsceneStorage and NpcMobilityGroups
 * already call it to reach the world data folder (both are in the base jar).
 */
public class MinecraftServer {
    public Iterable<ServerLevel> getAllLevels() { return null; }

    public ServerLevel overworld() { return null; }
}
