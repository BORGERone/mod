package net.minecraft.server.level;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
public class ServerLevel extends Level {
    public MinecraftServer getServer() { return null; }
    public Iterable<Entity> getAllEntities() { return null; }
    public long getGameTime() { return 0; }
    public ResourceKey<Level> dimension() { return null; }
}
