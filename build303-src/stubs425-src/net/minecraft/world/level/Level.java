package net.minecraft.world.level;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Compile-time mirror (Build 425): preserves all Build 416 {@code Level} members and adds
 * {@link #registryAccess()}, {@link #getSharedSpawnPos()} and {@link #getBlockEntity(BlockPos)}.
 */
public class Level implements LevelAccessor {
    public boolean isClientSide;

    public boolean hasChunkAt(BlockPos pos) { return true; }
    public long getGameTime() { return 0; }
    public Player getNearestPlayer(Entity entity, double distance) { return null; }
    public List<? extends Player> players() { return Collections.emptyList(); }

    public <T extends Entity> List<T> getEntitiesOfClass(Class<T> type, AABB box, Predicate<? super T> test) {
        return null;
    }

    public BlockState getBlockState(BlockPos pos) { return null; }
    public boolean setBlock(BlockPos pos, BlockState state, int flags) { return true; }
    public boolean destroyBlock(BlockPos pos, boolean dropBlock) { return true; }
    public boolean isEmptyBlock(BlockPos pos) { return true; }
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {}
    public ResourceKey<Level> dimension() { return null; }
    public MinecraftServer getServer() { return null; }

    public RegistryAccess registryAccess() { return RegistryAccess.EMPTY; }
    public BlockPos getSharedSpawnPos() { return new BlockPos(0, 64, 0); }
    public BlockEntity getBlockEntity(BlockPos pos) { return null; }
}
