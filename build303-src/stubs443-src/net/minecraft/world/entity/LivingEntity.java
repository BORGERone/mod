package net.minecraft.world.entity;

import net.minecraft.world.item.ItemStack;

/**
 * Compile-time mirror (Build 443): preserves Build 348 LivingEntity equipment methods and adds
 * {@code getMainHandItem()} / {@code getOffhandItem()} (already present in base JAR).
 */
public class LivingEntity extends Entity {
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return null;
    }

    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    public ItemStack getMainHandItem() {
        return ItemStack.EMPTY;
    }

    public ItemStack getOffhandItem() {
        return ItemStack.EMPTY;
    }
}
