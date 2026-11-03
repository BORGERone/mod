package net.neoforged.neoforge.event.entity.player;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Compile-time signature only (Build 351). The shape is copied from the base jar's own use of the
 * event in {@code GuideDialogue#onRightClickBlock}, which runs in game: {@code getItemStack},
 * {@code getPos}, {@code getEntity}, {@code getLevel}, {@code setCanceled},
 * {@code setCancellationResult}.
 */
public class PlayerInteractEvent {
    public Entity getEntity() { return null; }
    public Level getLevel() { return null; }
    public ItemStack getItemStack() { return null; }
    public void setCanceled(boolean canceled) {}
    public void setCancellationResult(InteractionResult result) {}

    /** Right click on a block: adds the hit position. */
    public static class RightClickBlock extends PlayerInteractEvent {
        public BlockPos getPos() { return null; }
    }
}
