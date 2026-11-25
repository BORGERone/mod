package net.minecraft.world;

import net.minecraft.world.item.ItemStack;

/** Compile-time mirror (Build 436): adds {@code setItem}, {@code getMaxStackSize} and {@code canPlaceItem}. */
public interface Container {
    ItemStack getItem(int slot);

    int getContainerSize();

    void setItem(int slot, ItemStack stack);

    int getMaxStackSize();

    boolean canPlaceItem(int slot, ItemStack stack);
}
