package net.minecraft.world.entity;

import net.minecraft.world.item.ItemStack;

/**
 * Compile-time mirror (Build 348) of the mob, narrowed to the equipment write.
 *
 * <p>{@code setItemSlot(EquipmentSlot, ItemStack)} is the setter the game itself uses when it puts a
 * stack on a mob; the client learns about it through the equipment packet the server entity tracker
 * sends, so no packet of our own is involved.</p>
 *
 * <p>Build 403 note: the {@code goalSelector} field is deliberately <b>not</b> mirrored. In the game it
 * is package-private ({@code final GoalSelector goalSelector}), so a mod in its own package cannot read
 * it; build 403 opens doors from the navigator instead, which is public all the way down
 * ({@code getNavigation().getNodeEvaluator()}, {@code getPath()}, {@code getNodePos}).</p>
 */
public class Mob extends LivingEntity {
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    /**
     * Build 404: the move control - the object that actually steers a mob's steps. {@code public
     * MoveControl getMoveControl()} on the real class; a path that ends at an open door can be walked
     * through by aiming the steps past the door, the way the game itself pushes a mob step by step.
     */
    public net.minecraft.world.entity.ai.control.MoveControl getMoveControl() {
        return null;
    }
}
