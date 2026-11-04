package net.minecraft.core;

public class BlockPos {
    public BlockPos() {}
    public BlockPos(int x, int y, int z) {}
    public static BlockPos containing(double x, double y, double z) { return new BlockPos(); }
    public int getX() { return 0; }
    public int getY() { return 0; }
    public int getZ() { return 0; }
    /** Build 368: the door pass walks the block next to the one it holds. */
    public BlockPos offset(int dx, int dy, int dz) { return new BlockPos(); }
    public BlockPos above() { return new BlockPos(); }
    public BlockPos below() { return new BlockPos(); }
    public BlockPos immutable() { return this; }
}
