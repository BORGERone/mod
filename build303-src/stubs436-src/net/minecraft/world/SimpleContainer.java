package net.minecraft.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Compile-time mirror (Build 436): implements expanded {@link Container}. */
public class SimpleContainer implements Container {
    private final List<ItemStack> items = new ArrayList<>();

    public SimpleContainer(int size) {
        for (int i = 0; i < size; i++) {
            this.items.add(ItemStack.EMPTY);
        }
    }

    @Override
    public ItemStack getItem(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public void setItem(int slot, ItemStack stack) {}

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return true;
    }

    public void setChanged() {}
}
