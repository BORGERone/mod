package net.minecraft.world.level;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Build 401: {@code Level} is a {@code LevelAccessor}, because the game's is - and a block event hands back the
 * interface, so an {@code instanceof ServerLevel} on that answer has to be possible.
 */
public class Level implements LevelAccessor {
    public boolean isClientSide;
    public boolean hasChunkAt(BlockPos pos) { return true; }
    public long getGameTime() { return 0; }

    /** Real owner of the lookup: Level#getEntitiesOfClass(Class, AABB, Predicate). */
    public <T extends Entity> List<T> getEntitiesOfClass(Class<T> type, AABB box, Predicate<? super T> test) {
        return null;
    }

    /** Build 368: the door lock reads and writes one block. */
    public BlockState getBlockState(BlockPos pos) { return null; }

    public boolean setBlock(BlockPos pos, BlockState state, int flags) { return true; }

    public boolean destroyBlock(BlockPos pos, boolean dropBlock) { return true; }

    public boolean isEmptyBlock(BlockPos pos) { return true; }

    /**
     * Build 371: push the authoritative state of one block to the clients that track it. A client opens
     * a door the instant it is clicked and asks the server afterwards; a locked door has to be put back
     * in the client's hands at once, or the door stays open in that picture for as long as it takes.
     */
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {}

    /** Build 376: a client has to name the dimension of a door to look its lock up. */
    public ResourceKey<Level> dimension() { return null; }

    /** Build 376: null on the client, the integrated or dedicated server on the other side. */
    public MinecraftServer getServer() { return null; }
}
