package net.minecraft.world.level.block.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * Compile-time mirror (Build 425): {@code BlockEntity} with MC 1.21.1 {@link #loadWithComponents} and
 * {@link #setChanged()}. Verified via ForgeJavaDocs-NG 1.21.x-neoforge.
 */
public abstract class BlockEntity {
    public final void loadWithComponents(CompoundTag tag, HolderLookup.Provider registries) {}

    public void setChanged() {}
}
