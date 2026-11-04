package dev.settledlands.door;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Build 368: what a door is, whether it is open, and how to open it - in one place.
 *
 * <p>A door in Minecraft is <b>two</b> blocks with their own states, and the game keeps them in step
 * through {@code DoorBlock#setOpen}. A mod that writes one half and forgets the other leaves a door
 * that looks closed, cannot be walked through and cannot be opened again, so every write here goes to
 * every part of the door ({@link #parts}).</p>
 */
public final class Doors368 {

    private Doors368() {}

    /** True for both halves of any wooden or iron door. */
    public static boolean isDoor(BlockState state) {
        return state != null && state.getBlock() instanceof DoorBlock;
    }

    /** True when the part is open. */
    public static boolean isOpen(BlockState state) {
        if (!isDoor(state)) return false;
        return Boolean.TRUE.equals(state.getValue(DoorBlock.OPEN));
    }

    /**
     * The one or two blocks of the door at this position: the half that was hit, and the half above or
     * below it when there is a door there.
     */
    public static List<BlockPos> parts(Level level, BlockPos pos) {
        List<BlockPos> out = new ArrayList<>(2);
        if (level == null || pos == null) return out;
        BlockState here = level.getBlockState(pos);
        if (!isDoor(here)) return out;
        out.add(pos);
        BlockPos up = pos.above();
        BlockPos down = pos.offset(0, -1, 0);
        if (isDoor(level.getBlockState(up))) out.add(up);
        else if (isDoor(level.getBlockState(down))) out.add(down);
        return out;
    }

    /**
     * Opens or closes the whole door. Returns true when something actually changed, so a caller can
     * tell "opened" from "was already open" without reading the state twice.
     */
    public static boolean setOpen(Level level, BlockPos pos, boolean open) {
        List<BlockPos> parts = parts(level, pos);
        boolean changed = false;
        for (int i = 0; i < parts.size(); i++) {
            BlockPos part = parts.get(i);
            BlockState state = level.getBlockState(part);
            if (!isDoor(state)) continue;
            if (isOpen(state) == open) continue;
            level.setBlock(part, state.setValue(DoorBlock.OPEN, Boolean.valueOf(open)), 10);
            changed = true;
        }
        return changed;
    }

    /** True when any part of the door at this position is open. */
    public static boolean anyOpen(Level level, BlockPos pos) {
        List<BlockPos> parts = parts(level, pos);
        for (int i = 0; i < parts.size(); i++) {
            if (isOpen(level.getBlockState(parts.get(i)))) return true;
        }
        return false;
    }
}
