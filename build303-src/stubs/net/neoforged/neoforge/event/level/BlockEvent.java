package net.neoforged.neoforge.event.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;

/**
 * Compile-time shape of the block events (build 368). Only {@code BreakEvent} is used here, with the
 * four members the mod asks for: the level, the position, the state and the player who broke it.
 */
public class BlockEvent extends Event {
    /**
     * Build 401: {@code LevelAccessor}, because that is what the game declares.
     *
     * <p>This mirror said {@code Level}, and two handlers compiled against it asked for a descriptor no class
     * has ({@code getLevel()Lnet/minecraft/world/level/Level;}). The base mod's own {@code WorldEvents} calls
     * the {@code LevelAccessor} one on the same event; a {@code BreakEvent} carries a {@code ServerLevel} at
     * run time, but the <b>descriptor</b> is the inherited return type, and only that one resolves. A
     * {@code NoSuchMethodError} thrown inside the handler's {@code try} is a handler that stops halfway - the
     * chest's break guard and the door's were never reached.</p>
     */
    public LevelAccessor getLevel() { return null; }

    public BlockPos getPos() { return null; }

    public BlockState getState() { return null; }

    public static class BreakEvent extends BlockEvent {
        public Player getPlayer() { return null; }

        public void setCanceled(boolean canceled) {}
    }
}
