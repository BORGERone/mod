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

    /** Build 352: the eight-argument call GuideNpc, GuideCorpseNpc and NpcRadiusZone320 make. */
    public int sendParticles(net.minecraft.core.particles.ParticleOptions particle, double x, double y,
            double z, int count, double dx, double dy, double dz, double speed) {
        return 0;
    }

    /** Build 351: the same call CutsceneStorage and NpcCommonGraphStorage already make. */
    public net.minecraft.world.level.storage.DimensionDataStorage getDataStorage() {
        return null;
    }
}
