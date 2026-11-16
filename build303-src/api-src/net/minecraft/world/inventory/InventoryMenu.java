package net.minecraft.world.inventory;

/**
 * Build 396: the player's own inventory menu - the menu {@code Player#inventoryMenu} really has, as the
 * base jar's {@code ServerPlayer#inventoryMenu} reference says
 * ({@code Lnet/minecraft/world/inventory/InventoryMenu;}). "No window is open" is
 * {@code containerMenu == inventoryMenu}, which is the comparison {@code NpcEscort} of the base mod makes.
 */
public class InventoryMenu extends AbstractContainerMenu {
}
