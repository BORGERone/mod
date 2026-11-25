package net.minecraft.world.entity.player;

import net.minecraft.world.item.ItemStack;

/** Compile-time mirror (Build 443): extends Build 440 Inventory with Inventory#add(ItemStack) from base JAR. */
public class Inventory {
    public int getContainerSize() {
        return 0;
    }

    public ItemStack getItem(int slot) {
        return ItemStack.EMPTY;
    }

    public boolean add(ItemStack stack) {
        return false;
    }
}
