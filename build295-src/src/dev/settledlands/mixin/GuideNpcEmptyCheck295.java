package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcValueSource290;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 295: the "nobody in the radius" half of the check node.
 *
 * <p>The graph scan starts a chain only when it finds a body, so a check node that feeds a
 * value wire leaves half of its graph unreachable: the condition wired to it can only ever
 * run with somebody present, and its {@code == 0} branch never fires.</p>
 *
 * <p>The hook sits on the NPC's own server tick - the place that calls the scan in the first
 * place - and hands the NPC to {@code NpcValueSource290#tickEmptyChecks}, which starts the
 * chain at the check node's next node with no initiator when the radius is empty. That keeps
 * the whole decision outside the stock scan: what it handles (a body present, a body on its
 * cooldown, the NPC talking) is left to it, so nothing can fire twice.</p>
 */
@Mixin(targets = "dev.settledlands.npc.GuideNpc", remap = false)
public abstract class GuideNpcEmptyCheck295 {

    @Inject(method = "customServerAiStep()V", at = @At("HEAD"), remap = false)
    private void settledlands$emptyCheck(CallbackInfo ci) {
        NpcValueSource290.tickEmptyChecks((GuideNpc) (Object) this);
    }
}
