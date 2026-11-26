package net.minecraft.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Compile-time mirror (Build 446): extends Build 420 {@code CommandSourceStack} with
 * {@link #withPermission(int)} matching base JAR external refs.
 */
public class CommandSourceStack {

    public Entity getEntity() {
        return null;
    }

    public ServerPlayer getPlayerOrException() throws CommandSyntaxException {
        return null;
    }

    public ServerLevel getLevel() {
        return null;
    }

    public Vec3 getPosition() {
        return null;
    }

    public MinecraftServer getServer() {
        return null;
    }

    public boolean hasPermission(int level) {
        return false;
    }

    public CommandSourceStack withPermission(int level) {
        return this;
    }

    public void sendFailure(Component message) {
    }

    public void sendSuccess(java.util.function.Supplier<Component> message, boolean broadcastToAdmins) {
    }
}
