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

    /** Build 382: an already answered click (the wand on a door) must not be answered twice. */
    public boolean isCanceled() { return false; }
    public void setCancellationResult(InteractionResult result) {}

    // Build 373 removed setUseBlock/setUseItem from this mirror: NeoForge 1.21.1 does not have them.
    // 371 called them, threw NoSuchMethodError at the click, and quietly refused nothing.

    /** Right click on a block: adds the hit position. */
    public static class RightClickBlock extends PlayerInteractEvent {
        public BlockPos getPos() { return null; }
    }
}
