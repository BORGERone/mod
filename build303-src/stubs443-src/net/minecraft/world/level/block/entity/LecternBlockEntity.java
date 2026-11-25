package net.minecraft.world.level.block.entity;

import net.minecraft.world.item.ItemStack;

/** Compile-time mirror (Build 443) verified via ForgeJavaDocs-NG 1.21.x-neoforge (non-Container!). */
public class LecternBlockEntity extends BlockEntity {
    public boolean hasBook() {
        return false;
    }

    public ItemStack getBook() {
        return ItemStack.EMPTY;
    }

    public void setBook(ItemStack stack) {}
}
