package net.neoforged.neoforge.common.extensions;

import net.minecraft.nbt.CompoundTag;

/**
 * Build 438: NeoForge 1.21.1 {@code IBlockEntityExtension#getPersistentData()} - returns the
 * writable compound stored under {@code "NeoForgeData"} in {@code BlockEntity} NBT.
 */
public interface IBlockEntityExtension {

    CompoundTag getPersistentData();
}
