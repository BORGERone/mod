package net.minecraft.world.entity.player;

import net.minecraft.world.entity.LivingEntity;

/** Compile-time mirror (Build 440): exposes getInventory() alongside Build 396 Player members. */
public class Player extends LivingEntity {
    public boolean isSpectator() { return false; }

    public boolean hasPermissions(int level) { return true; }

    public void displayClientMessage(net.minecraft.network.chat.Component message, boolean actionBar) {}

    public net.minecraft.world.item.ItemStack getItemInHand(net.minecraft.world.InteractionHand hand) {
        return null;
    }

    public Inventory getInventory() {
        return null;
    }

    public net.minecraft.world.inventory.AbstractContainerMenu containerMenu;
    public net.minecraft.world.inventory.InventoryMenu inventoryMenu;
}
