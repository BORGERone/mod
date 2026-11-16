package net.minecraft.world.level.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Compile-time mirror (Build 425): adds {@link #getStateDefinition()} on {@code Block}.
 */
public class Block {
    public BlockState defaultBlockState() { return null; }

    public StateDefinition<Block, BlockState> getStateDefinition() { return null; }
}
