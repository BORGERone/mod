package net.minecraft.world.entity;

/**
 * Compile-time shape of the game's equipment slot enum (Build 348).
 *
 * <p>Only the armour order matters here: the body renderer walks HEAD, CHEST, LEGS, FEET and asks a
 * living entity for the stack in each of them. The constants are the game's own, spelled the same way
 * vanilla's armour layer spells them.</p>
 */
public enum EquipmentSlot {
    MAINHAND, OFFHAND, FEET, LEGS, CHEST, HEAD, BODY
}
