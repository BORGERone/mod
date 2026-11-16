package net.minecraft.world.item;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * Compile-time shape of an armour item (Build 348).
 *
 * <p>Both members are the ones vanilla's armour layer branches on before it draws a piece:
 * {@code armoritem.getEquipmentSlot() == slot} and {@code armoritem.getMaterial().value()}.</p>
 */
public class ArmorItem extends Item {
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.CHEST;
    }

    public Holder<ArmorMaterial> getMaterial() {
        return null;
    }
}
