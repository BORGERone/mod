package net.minecraft.world.phys;

/**
 * Compile-time mirror of the block hit a door is clicked through (build 373), and of the hit build 400 builds
 * itself: {@code new BlockHitResult(Vec3, Direction, BlockPos, boolean)} is the constructor the game's own
 * client handler uses when it turns a right-click into {@code useItemOn}.
 */
public class BlockHitResult extends HitResult {

    public BlockHitResult(Vec3 location, net.minecraft.core.Direction direction,
            net.minecraft.core.BlockPos blockPos, boolean miss) {
    }
}
