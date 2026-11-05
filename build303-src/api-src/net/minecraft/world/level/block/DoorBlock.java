package net.minecraft.world.level.block;

import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Compile-time mirror of the door. Only what build 368 needs: the open flag of a state, and the fact
 * that a door is a door.
 */
public class DoorBlock extends Block {
    public static final BooleanProperty OPEN = null;

    /** Build 382: which half of the two-block door this state is. */
    public static final EnumProperty<DoubleBlockHalf> HALF = null;
}
