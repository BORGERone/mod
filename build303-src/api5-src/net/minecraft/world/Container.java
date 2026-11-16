package net.minecraft.world;

import net.minecraft.world.item.ItemStack;

/** Compile-time shape of a container (Build 348): the NPC inventory is read slot by slot. */
public interface Container {
    ItemStack getItem(int slot);

    int getContainerSize();
}
