package net.minecraft.core;

/** Compile-time mirror (Build 436): adds {@link #getOpposite()}. */
public enum Direction {
    DOWN,
    UP,
    NORTH,
    SOUTH,
    WEST,
    EAST;

    public Direction getOpposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }
}
