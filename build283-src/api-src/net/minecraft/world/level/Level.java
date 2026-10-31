package net.minecraft.world.level;

import net.minecraft.core.BlockPos;

public class Level {
    public boolean isClientSide;
    public boolean hasChunkAt(BlockPos pos) { return true; }
}
