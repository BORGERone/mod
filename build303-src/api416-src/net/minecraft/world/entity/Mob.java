package net.minecraft.world.entity;

import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.item.ItemStack;

/**
 * Compile-time mirror (Build 416): {@code Mob} with {@link #getTarget()}, already called on
 * {@code GuideNpc} by {@code NpcAutoAiEngine255} and {@code NpcMotionBridge256} in the base jar.
 */
public class Mob extends LivingEntity {
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}
    public MoveControl getMoveControl() { return null; }
    public LivingEntity getTarget() { return null; }
}
