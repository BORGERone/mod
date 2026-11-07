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
    /**
     * Build 401: the clicker is a <b>player</b>, and the descriptor says so.
     *
     * <p>This mirror said {@code Entity} for four builds, and the jar believed it: every
     * {@code event.getEntity()} in a block or door click path asked for
     * {@code getEntity()Lnet/minecraft/world/entity/Entity;}, while the game's own method - through
     * {@code PlayerEvent}, which narrows {@code EntityEvent}'s return type to {@code Player} - answers only
     * {@code getEntity()Lnet/minecraft/world/entity/player/Player;}. The call therefore threw
     * {@code NoSuchMethodError} inside the {@code try} of every click handler, and the handler stopped where it
     * stood: a locked door was still refused (the cancel came first), a chest's crack was never read, and the
     * author's reports - a door that opens by hand and says nothing, a chest whose «взлом» ran in the wrong
     * order - are exactly what a click handler that dies halfway looks like. The base mod's own compiled
     * classes call the {@code Player} descriptor on this very event ({@code GuideDialogue},
     * {@code PossessionSystem}), which is what settled the question.</p>
     */
    public net.minecraft.world.entity.player.Player getEntity() { return null; }
    public Level getLevel() { return null; }
    public ItemStack getItemStack() { return null; }
    public void setCanceled(boolean canceled) {}

    /** Build 382: an already answered click (the wand on a door) must not be answered twice. */
    public boolean isCanceled() { return false; }
    public void setCancellationResult(InteractionResult result) {}

    /**
     * Build 396: which hand made the click. The base mod calls this on the same event class
     * ({@code NpcStatsInteraction#specific} and {@code #generic}), so the shape is proven by the jar.
     */
    public net.minecraft.world.InteractionHand getHand() { return null; }

    // Build 373 removed setUseBlock/setUseItem from this mirror: NeoForge 1.21.1 does not have them.
    // 371 called them, threw NoSuchMethodError at the click, and quietly refused nothing.

    /** Right click on a block: adds the hit position. */
    public static class RightClickBlock extends PlayerInteractEvent {
        public BlockPos getPos() { return null; }
    }
}
