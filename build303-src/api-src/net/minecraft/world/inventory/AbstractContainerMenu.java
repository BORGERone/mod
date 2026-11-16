package net.minecraft.world.inventory;

/**
 * Build 396: compile-time mirror of the menu a player has open, in the one use this mod needs it for -
 * telling the chest's own window apart from the player's inventory.
 *
 * <p>The two fields this class types are on {@code Player} ({@code containerMenu}, {@code inventoryMenu}),
 * and they are read by the base jar itself: {@code ServerPlayer#containerMenu} with the descriptor
 * {@code Lnet/minecraft/world/inventory/AbstractContainerMenu;}, {@code ServerPlayer#inventoryMenu} with the
 * descriptor {@code Lnet/minecraft/world/inventory/InventoryMenu;}. Those two entries are in the shipped
 * 395 jar's own constant pool, so the shape is proven by a jar that runs in game, not by memory - which is
 * the lesson of build 381 and 373.</p>
 */
public class AbstractContainerMenu {
}
