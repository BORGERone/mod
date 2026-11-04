package net.minecraft.world.level.block.state;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

public class BlockState {
    public Block getBlock() { return null; }

    public <T extends Comparable<T>> T getValue(Property<T> property) { return null; }

    public <T extends Comparable<T>> BlockState setValue(Property<T> property, T value) { return this; }

    public boolean is(Block block) { return false; }

    public boolean hasProperty(Property<?> property) { return false; }
}
