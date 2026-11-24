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
 * Compile-time mirror (Build 425 / 431): preserves all Build 351/420 {@code ServerLevel} members,
 * implements {@link ServerLevelAccessor}, and adds {@link #getSeed()},
 * {@link #getBlockEntity(BlockPos)}, and {@link #sendBlockUpdated}.
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

    public boolean isPositionEntityTicking(BlockPos pos) {
        return true;
    }
}
