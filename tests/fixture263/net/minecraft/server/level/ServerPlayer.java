package net.minecraft.server.level;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;

public final class ServerPlayer {
    public final MinecraftServer server;
    private final UUID uuid;
    public ServerPlayer(MinecraftServer server, UUID uuid) { this.server = server; this.uuid = uuid; }
    public UUID getUUID() { return uuid; }
}
