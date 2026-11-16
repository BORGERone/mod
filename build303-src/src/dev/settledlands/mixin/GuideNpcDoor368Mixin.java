package dev.settledlands.mixin;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Build 368: a guide answers "yes" to the question the path finder asks about closed doors.
 *
 * <p>Vanilla treats a closed door as a wall unless the mob says it can open doors, so a guide walking
 * to a point behind a door would always take the long way round - or stand still.</p>
 *
 * <h2>Build 403: this answer was never heard</h2>
 *
 * <p>In 1.21 the question is no longer put to the mob. {@code Mob} has no {@code canOpenDoors} at all
 * (see the NeoForge 1.21 javadoc of {@code Mob} and of {@code PathNavigation}), and the flag the path
 * finder actually reads is {@code NodeEvaluator#canOpenDoors}, reached through the public
 * {@code PathNavigation#getNodeEvaluator()}. The method this mixin adds is therefore a method nothing
 * calls: a guide computed its paths with doors shut, walked around them or stopped at them, and the
 * door the 368 pass opened was never on its way - «они не проходят даже через открытые» in its first
 * half.</p>
 *
 * <p>The switch is now thrown where the game reads it: {@code NpcDoors368#allowDoorNodes} sets
 * {@code setCanOpenDoors(true)} on the guide's own node maker on every pass. This method is kept as a
 * harmless override - it costs nothing and it is still the right answer if a game version asks the mob
 * again - but the fix does not depend on it.</p>
 *
 * <p>It is not a lock bypass in disguise either: the lock of build 368 is a rule for players and
 * monsters ({@code DoorEvents368}), and the author asked for the guides to pass through their own
 * doors.</p>
 */
@Mixin(targets = "dev.settledlands.npc.GuideNpc", remap = false)
public abstract class GuideNpcDoor368Mixin {

    /** The answer the path finder used to look for - see the class comment. */
    public boolean canOpenDoors() {
        return true;
    }
}
