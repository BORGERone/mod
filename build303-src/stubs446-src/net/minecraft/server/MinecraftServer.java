package net.minecraft.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Compile-time mirror (Build 446): adds {@link #createCommandSourceStack()} and {@link #getCommands()}. */
public class MinecraftServer {
    public Iterable<ServerLevel> getAllLevels() { return null; }

    public ServerLevel overworld() { return null; }

    public ServerLevel getLevel(ResourceKey<Level> key) { return null; }

    public net.minecraft.server.players.PlayerList getPlayerList() { return null; }

    public CommandSourceStack createCommandSourceStack() { return null; }

    public Commands getCommands() { return null; }
}
