package dev.settledlands.mixin;

import dev.settledlands.npc.NpcSelfLink337;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 337: say what actually went wrong when a wire is dropped on its own node.
 *
 * <p>The graph refuses the link, and the editor answers any refusal with its generic line - "a
 * choice leads into a line; a line into a line or a choice" - which sends the author looking for a
 * wrong kind of node instead of a wrong target. The refusal itself is what knows the reason, so the
 * line is replaced right after {@code mouseReleased} has finished with the drop. Everything else the
 * status line normally says (connected, disconnected, clipboard) is untouched.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcSelfLinkStatusMixin337 {

    @Shadow private String status;

    @Shadow private String tr(String key) { throw new AssertionError(); }

    @Inject(method = "mouseReleased(DDI)Z", at = @At("TAIL"), remap = false)
    private void settledlands$selfLinkStatus(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        if (!NpcSelfLink337.consumeRefusal()) return;
        status = tr(NpcSelfLink337.SELF_KEY);
    }
}
