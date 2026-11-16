package net.minecraft.core;

/**
 * Compile-time mirror of a block position (builds 368-371).
 *
 * <p>The members are the ones the door pass uses: the three-argument constructor, the three getters,
 * {@code offset/above/below} and {@code immutable}. The stub keeps the numbers so a harness can check a
 * parsed position; nothing here is packaged, the game has the real class.</p>
 */
public class BlockPos {

    private final int x;
    private final int y;
    private final int z;

    public BlockPos() {
        this(0, 0, 0);
    }

    public BlockPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static BlockPos containing(double x, double y, double z) {
        return new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    /** Build 368: the door pass walks the block next to the one it holds. */
    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    public BlockPos above() {
        return offset(0, 1, 0);
    }

    public BlockPos below() {
        return offset(0, -1, 0);
    }

    public BlockPos immutable() {
        return this;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BlockPos)) return false;
        BlockPos pos = (BlockPos) other;
        return x == pos.x && y == pos.y && z == pos.z;
    }

    @Override
    public int hashCode() {
        return (y + z * 31) * 31 + x;
    }

    @Override
    public String toString() {
        return x + "," + y + "," + z;
    }
}
