package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 413: the guide's door pass runs from the guide's own tick.
 *
 * <p><b>What the author saw.</b> In build 412 a door that was already standing open when the world loaded
 * was walked through for minutes on end and never closed behind the guide, and the moment he shut it the
 * guide stopped dead in front of it. Opening a door and closing one behind a guide are the same few lines
 * of {@code NpcDoors368}: if the first worked and the second never happened, the pass was not running at
 * all. And a pass that is not running looks, from inside the game, exactly like a pass that was never
 * written - the code is all in the jar and all correct, and none of it is ever called.</p>
 *
 * <p>The only way in used to be a static {@code ServerTickEvent.Post} handler on {@code NpcDoors368}. If
 * that hook is not posted, or the class is not registered as the bus expects, nothing in the door feature
 * exists at runtime and no offline gate can tell: the bytecode is present, the annotation is present, the
 * event class is present. A hook that rides the entity itself cannot go missing - the guide ticks, or the
 * guide does not walk.</p>
 *
 * <h2>Why this method</h2>
 *
 * <p>{@code customServerAiStep()} is declared by {@code GuideNpc} itself (a mixin cannot inject into a
 * method the target merely inherits) and it is the one that runs the guide's own walking code -
 * {@code tickCustomMovement} and {@code tickMobility} are both called from it, as the class's own bytecode
 * shows. So this callback runs on exactly the ticks on which the guide is being moved, which is when a
 * door needs opening.</p>
 *
 * <p>{@code require = 0} for the same reason build 369 wrote it: an injection that cannot be verified
 * offline must never be able to stop the game from booting. If the shape ever changes, the server tick of
 * build 412 remains the first way in and this one is skipped with a warning.</p>
 */
@Mixin(targets = "dev.settledlands.npc.GuideNpc", remap = false)
public abstract class GuideDoorPass413Mixin {

    @Inject(method = "customServerAiStep()V", at = @At("HEAD"), require = 0, remap = false)
    private void settledlands$doorPassOnGuideTick(CallbackInfo info) {
        dev.settledlands.door.NpcDoors368.pass((GuideNpc) (Object) this);
    }
}
