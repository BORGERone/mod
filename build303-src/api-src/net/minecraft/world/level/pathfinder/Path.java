package net.minecraft.world.level.pathfinder;

import net.minecraft.core.BlockPos;

/**
 * Compile-time mirror (Build 403) of a computed path, narrowed to what a pass over the guide's next nodes
 * needs: the count, where the mob is, and the block of each node.
 */
public class Path {
    public int getNodeCount() { return 0; }
    public int getNextNodeIndex() { return 0; }
    public BlockPos getNodePos(int index) { return null; }
    public BlockPos getTarget() { return null; }
    public boolean isDone() { return true; }
}
