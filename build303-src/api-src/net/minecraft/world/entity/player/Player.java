package net.minecraft.world.entity.player;

import net.minecraft.world.entity.LivingEntity;

public class Player extends LivingEntity {
    /** Declared on Player in 1.21.1; ServerPlayer inherits it. */
    public boolean isSpectator() { return false; }

    /** Build 351: GuideNpc#mobInteract already gates the wand on this exact call. */
    public boolean hasPermissions(int level) { return true; }

    /** Build 351: same call the base jar's wand and dialogue feedback use. */
    public void displayClientMessage(net.minecraft.network.chat.Component message, boolean actionBar) {}

    /** Build 352: declared on Player; the base jar holds the wand through this exact call. */
    public net.minecraft.world.item.ItemStack getItemInHand(net.minecraft.world.InteractionHand hand) {
        return null;
    }

}
