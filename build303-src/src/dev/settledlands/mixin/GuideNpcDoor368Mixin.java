package dev.settledlands.mixin;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Build 368: a guide answers "yes" to the question the path finder asks about closed doors.
 *
 * <p>Vanilla treats a closed door as a wall unless the mob says it can open doors, so a guide walking
 * to a point behind a door would always take the long way round - or stand still. Answering true here
 * lets the path go through the doorway; the door itself is opened by {@code NpcDoors368}, which is the
 * half vanilla leaves to an AI goal a guide does not use.</p>
 *
 * <p>This is not a lock bypass in disguise: the lock of build 368 is a rule for players and monsters
 * ({@code DoorEvents368}), and the author asked for the guides to pass through their own doors.</p>
 */
@Mixin(targets = "dev.settledlands.npc.GuideNpc", remap = false)
public abstract class GuideNpcDoor368Mixin {

    /**
     * The answer the path finder looks for. A guide does not define this method itself - it inherits
     * "no" from the stock mob - so this one takes its place in the merged class.
     */
    public boolean canOpenDoors() {
        return true;
    }
}
