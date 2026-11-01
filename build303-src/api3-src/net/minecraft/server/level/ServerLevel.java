package net.minecraft.server.level;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Compile-time mirror (Build 320): adds {@code sendParticles}, the call the base jar already makes
 * from {@code GuideNpc} and {@code GuideCorpseNpc}.
 */
public class ServerLevel extends Level {
    public MinecraftServer getServer() { return null; }

    public Iterable<Entity> getAllEntities() { return null; }

    public long getGameTime() { return 0; }

    public ResourceKey<Level> dimension() { return null; }

    public int sendParticles(ParticleOptions particle, double x, double y, double z, int count,
            double dx, double dy, double dz, double speed) {
        return 0;
    }
}
