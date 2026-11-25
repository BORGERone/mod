package net.minecraft.world.phys;

/** Compile-time mirror (Build 436): adds {@link #getDirection()} and {@link #getBlockPos()}. */
public class BlockHitResult extends HitResult {

    public BlockHitResult(Vec3 location, net.minecraft.core.Direction direction,
            net.minecraft.core.BlockPos blockPos, boolean miss) {
    }

    public net.minecraft.core.BlockPos getBlockPos() {
        return null;
    }

    public net.minecraft.core.Direction getDirection() {
        return null;
    }
}
