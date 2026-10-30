package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcDialogueGraph.Node;
import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.PursuitGraphNode272;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 273: override canvas hit/visible for pursuit nodes.
 *
 * The Build 272 {@code settledlands$pursuitFocus} handler was removed here:
 * {@code NpcGraphCanvas#focus(Node)} returns void, so the callback has to be
 * {@code CallbackInfo}, and a CallbackInfoReturnable on a void target is a
 * hard mixin apply failure (client crash). Canvas focus now stays vanilla.
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcGraphCanvas", remap = false)
public abstract class NpcPursuitCanvasMixin272 {

    @Inject(method = "visible(Ldev/settledlands/npc/NpcDialogueGraph$Node;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$pursuitVisible(Node n, CallbackInfoReturnable<Boolean> cir) {
        if (PursuitGraphNode272.isPursuitNode(n)) {
            cir.setReturnValue(PursuitGraphNode272.visible((NpcGraphCanvas) (Object) this, n));
        }
    }

    @Inject(method = "hit(Ldev/settledlands/npc/NpcDialogueGraph;DD)I",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$pursuitHit(NpcDialogueGraph graph, double sx, double sy,
            CallbackInfoReturnable<Integer> cir) {
        int id = PursuitGraphNode272.hit((NpcGraphCanvas) (Object) this, graph, sx, sy);
        if (id >= 0) cir.setReturnValue(id);
    }

    @Inject(method = "hitPort(Ldev/settledlands/npc/NpcDialogueGraph;DDZ)I",
            at = @At("TAIL"), remap = false)
    private void settledlands$pursuitHitPort(NpcDialogueGraph graph, double x, double y, boolean output,
            CallbackInfoReturnable<Integer> cir) {
        Integer id = cir.getReturnValue();
        if (id != null && id >= 0) {
            Node n = graph.get(id);
            if (n != null && PursuitGraphNode272.isPursuitNode(n)) {
                cir.setReturnValue(-1);
            }
        }
    }
}
