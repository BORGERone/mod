package net.minecraft.commands;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Build 420: who ran a command and from where.
 *
 * <p>Every member here is one the base jar already calls on this class -
 * {@code NpcEscortCommands} and {@code CutsceneCommands} use {@code getPlayerOrException},
 * {@code ModCommands} uses {@code getLevel}, {@code getPosition}, {@code getServer},
 * {@code hasPermission}, {@code sendFailure} and {@code sendSuccess} - so the shapes are the game's,
 * not this build's idea of them.</p>
 */
public class CommandSourceStack {

    public Entity getEntity() {
        return null;
    }

    public ServerPlayer getPlayerOrException() throws com.mojang.brigadier.CommandSyntaxException {
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

    public void sendFailure(Component message) {
    }

    public void sendSuccess(java.util.function.Supplier<Component> message, boolean broadcastToAdmins) {
    }
}
