package net.minecraft.world.level.block.state.properties;

/**
 * Compile-time signature only; never bundled into the mod JAR.
 *
 * <p>Build 382: a door is two blocks and the state says which half it is, so the store of a door's
 * graph can always be keyed by the lower one.</p>
 */
public enum DoubleBlockHalf {
    UPPER,
    LOWER
}
