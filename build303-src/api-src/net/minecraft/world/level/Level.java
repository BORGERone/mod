package net.minecraft.world.level;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class Level {
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
}
