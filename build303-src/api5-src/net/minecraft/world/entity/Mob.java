package net.minecraft.world.entity;

import net.minecraft.world.item.ItemStack;

/**
 * Compile-time mirror (Build 348) of the mob, narrowed to the equipment write.
 *
 * <p>{@code setItemSlot(EquipmentSlot, ItemStack)} is the setter the game itself uses when it puts a
 * stack on a mob; the client learns about it through the equipment packet the server entity tracker
 * sends, so no packet of our own is involved.</p>
 */
public class Mob extends LivingEntity {
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }
}
