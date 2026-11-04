package net.neoforged.neoforge.event.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;

/**
 * Compile-time shape of the block events (build 368). Only {@code BreakEvent} is used here, with the
 * four members the mod asks for: the level, the position, the state and the player who broke it.
 */
public class BlockEvent extends Event {
    public Level getLevel() { return null; }

    public BlockPos getPos() { return null; }

    public BlockState getState() { return null; }

    public static class BreakEvent extends BlockEvent {
        public Player getPlayer() { return null; }

        public void setCanceled(boolean canceled) {}
    }
}
