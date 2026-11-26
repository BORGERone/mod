package net.minecraft.world.level.block.entity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.extensions.IBlockEntityExtension;

/** Compile-time mirror (Build 446): preserves Build 438 {@code BlockEntity}. */
public abstract class BlockEntity implements IBlockEntityExtension {

    private final CompoundTag persistentData = new CompoundTag();

    @Override
    public CompoundTag getPersistentData() {
        return persistentData;
    }

    public final void loadWithComponents(CompoundTag tag, HolderLookup.Provider registries) {}

    public void setChanged() {}
}
