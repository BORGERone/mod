package net.minecraft.world.item;

/** Compile-time mirror (Build 436): adds two-arg constructor and stack count/limit helpers already live in MC 1.21.1. */
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

    public int getCount() {
        return 0;
    }

    public void setCount(int count) {}

    public void shrink(int amount) {}

    public int getMaxStackSize() {
        return 64;
    }
}
