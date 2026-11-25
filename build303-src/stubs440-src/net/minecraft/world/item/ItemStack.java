package net.minecraft.world.item;

import net.minecraft.network.chat.Component;

/** Compile-time mirror (Build 440): adds getHoverName, getDescriptionId, copyWithCount verified in base JAR. */
public final class ItemStack {
    public static final ItemStack EMPTY = new ItemStack();

    public ItemStack(net.minecraft.world.level.ItemLike item) {}

    public ItemStack(net.minecraft.world.level.ItemLike item, int count) {}

    public ItemStack() {}

    public boolean isEmpty() {
        return true;
    }

    public Item getItem() {
        return null;
    }

    public boolean hasFoil() {
        return false;
    }

    public boolean is(Item item) {
        return false;
    }

    public ItemStack copy() {
        return this;
    }

    public ItemStack copyWithCount(int count) {
        return this;
    }

    public int getCount() {
        return 0;
    }

    public void setCount(int count) {}

    public void shrink(int amount) {}

    public int getMaxStackSize() {
        return 64;
    }

    public Component getHoverName() {
        return Component.empty();
    }

    public String getDescriptionId() {
        return "";
    }
}
