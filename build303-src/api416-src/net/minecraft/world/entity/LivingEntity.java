package net.minecraft.world.entity;

import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.item.ItemStack;

/**
 * Compile-time mirror (Build 416): {@code LivingEntity} with {@link #hasLineOfSight(Entity)},
 * declared on {@code LivingEntity} in Minecraft 1.21.1 and already called on {@code GuideNpc} by
 * {@code NpcAutoAiEngine255#findHostileTarget} and {@code IvyCombatAi} in the base jar.
 */
public class LivingEntity extends Entity {
    public final WalkAnimationState walkAnimation = null;
    public float xxa;
    public float yya;
    public float zza;
    public float yBodyRot;
    public float yHeadRot;

    public MoveControl getMoveControl() { return null; }
    public ItemStack getItemBySlot(EquipmentSlot slot) { return null; }
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}
    public boolean hasLineOfSight(Entity entity) { return true; }
}
