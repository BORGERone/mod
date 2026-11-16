package net.minecraft.world.entity;

import net.minecraft.world.item.ItemStack;

/**
 * Compile-time mirror (Build 348) of the living entity, narrowed to the equipment pair.
 *
 * <p>{@code getItemBySlot(EquipmentSlot)} is what vanilla's armour layer reads for every piece it
 * draws; {@code setItemSlot(EquipmentSlot, ItemStack)} is the write vanilla's own {@code
 * swapHandItems} makes in 1.21.1 (see the NeoForge patch of {@code LivingEntity}), and it is what
 * marks a slot as changed so the server entity tracker sends the vanilla equipment update. A stub is
 * needed because the profile this project compiles against only declares the members earlier builds
 * happened to call.</p>
 */
public class LivingEntity extends Entity {
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return null;
    }

    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }
}
