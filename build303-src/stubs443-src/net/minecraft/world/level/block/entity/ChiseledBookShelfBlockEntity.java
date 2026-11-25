package net.minecraft.world.level.block.entity;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Compile-time mirror (Build 443) verified via ForgeJavaDocs-NG 1.21.x-neoforge. */
public class ChiseledBookShelfBlockEntity extends BlockEntity implements Container {
    @Override public int getContainerSize() { return 6; }

    @Override public ItemStack getItem(int slot) { return ItemStack.EMPTY; }

    @Override public void setItem(int slot, ItemStack stack) {}

    @Override public int getMaxStackSize() { return 1; }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return false; }
}
