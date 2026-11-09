package net.minecraft.world.level.block.state;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

public class BlockState {
    public Block getBlock() { return null; }

    public <T extends Comparable<T>> T getValue(Property<T> property) { return null; }

    /**
     * Build 381: THE descriptor bug of builds 368-380.
     *
     * <p>In 1.21.1 {@code setValue} is not declared here at all - it comes from
     * {@code net.minecraft.world.level.block.state.StateHolder<O, S>} as
     * {@code <T extends Comparable<T>, V extends T> S setValue(Property<T> property, V value)}, and
     * {@code S} is unbounded, so the erased return type the game publishes is {@link Object}. This
     * mirror used to declare it as returning {@code BlockState} and taking {@code T}; the compiler then
     * emitted {@code setValue(Property, Comparable)BlockState}, a method that does not exist anywhere in
     * the game. Every door this mod opened since build 368 died right there:
     *
     * <pre>java.lang.NoSuchMethodError: 'net.minecraft.world.level.block.state.BlockState
     *   net.minecraft.world.level.block.state.BlockState.setValue(
     *     net.minecraft.world.level.block.state.properties.Property, java.lang.Comparable)'</pre>
     *
     * <p>Evidence: the 1.21.1 javadoc for StateHolder (lexxie.dev/neoforge/1.21.1) and the author's own
     * log of build 380. The call is raw on purpose: raw erasure is exactly what the game publishes.</p>
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Object setValue(Property property, Comparable value) { return null; }

    public boolean is(Block block) { return false; }

    /**
     * Build 405: is this the air block? The one question a guide's next cell is asked before that guide is
     * moved into it, and it comes from {@code BlockBehaviour.BlockStateBase#isAir()} - {@code ()Z}, no
     * arguments, published by the game exactly like this (NeoForge 1.21.1 javadoc, {@code BlockStateBase},
     * method {@code isAir}). Anything that is not air is treated as an obstacle: a plant, a torch, water, a
     * slab and a wall are all "the guide does not step there", which is the safe reading for a move that
     * has no path behind it.
     */
    public boolean isAir() { return false; }

    public boolean hasProperty(Property<?> property) { return false; }

    /**
     * Build 397: the menu of the block at this position - a chest's own window, and the way this mod
     * opens it once its lock was cracked. The descriptor is the base jar's own reference to this very
     * method, so nothing here is a guess.
     */
    public net.minecraft.world.MenuProvider getMenuProvider(net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos) {
        return null;
    }
}
