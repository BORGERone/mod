package net.minecraft.server.level;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Compile-time mirror (Build 446): extends Build 438 {@code ServerLevel} with 3-arg
 * {@link #destroyBlock(BlockPos, boolean, Entity)} matching base JAR external refs.
 */
public class ServerLevel extends Level implements ServerLevelAccessor {
    @Override
    public ServerLevel getLevel() { return this; }

    public MinecraftServer getServer() { return null; }
    public Iterable<Entity> getAllEntities() { return null; }
    public long getGameTime() { return 0; }
    public long getDayTime() { return 0; }
    public long getSeed() { return 0L; }
    public ResourceKey<Level> dimension() { return null; }

    public int sendParticles(net.minecraft.core.particles.ParticleOptions particle, double x, double y,
            double z, int count, double dx, double dy, double dz, double speed) {
        return 0;
    }

    public java.util.List<ServerPlayer> players() { return null; }

    public boolean addFreshEntity(Entity entity) {
        return true;
    }

    public net.minecraft.world.level.storage.DimensionDataStorage getDataStorage() {
        return null;
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {}

    public boolean destroyBlock(BlockPos pos, boolean dropBlock, Entity entity) {
        return true;
    }

    public Entity getEntity(java.util.UUID uuid) {
        return null;
    }

    public Entity getEntity(int id) {
        return null;
    }

    public boolean isPositionEntityTicking(BlockPos pos) {
        return true;
    }

    public ServerChunkCache getChunkSource() {
        return null;
    }
}
