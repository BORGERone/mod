package dev.settledlands.mixin;

import dev.settledlands.npc.NpcSelfLink337;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 337: no wire from a node to itself.
 *
 * <p>{@code NpcDialogueGraph#connect(int, int, int)} is the only place the editor makes a wire, so it
 * is the only place that has to say no. The check sits at the head of the call, before the port is
 * looked at: on a "next" wire a self-link is an endless dialogue, on an answer or a trigger wire it is
 * a node that can fire itself forever - neither is something an author means to draw.</p>
 *
 * <p>The refusal is remembered so the editor can say so in words instead of its generic "wrong kind
 * of link" line.</p>
 */
@Mixin(targets = "dev.settledlands.npc.NpcDialogueGraph", remap = false)
public abstract class NpcSelfLinkMixin337 {

    @Inject(method = "connect(III)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$noSelfLink(int from, int to, int port,
            CallbackInfoReturnable<Boolean> cir) {
        if (!NpcSelfLink337.isSelfLink(from, to)) return;
        NpcSelfLink337.refuse();
        cir.setReturnValue(false);
    }
}
