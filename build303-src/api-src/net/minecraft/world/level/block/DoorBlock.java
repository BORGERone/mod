package net.minecraft.world.level.block;

import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Compile-time mirror of the door. Only what build 368 needs: the open flag of a state, and the fact
 * that a door is a door.
 */
public class DoorBlock extends Block {
    public static final BooleanProperty OPEN = null;
}
