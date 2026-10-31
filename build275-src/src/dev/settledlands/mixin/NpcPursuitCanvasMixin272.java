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
 * Build 272/275: canvas hit + visible for the pursuit node.
 *
 * Build 275 drops the hitPort injector: the pursuit card is now the stock
 * 220x100 node, so NpcGraphCanvas#hitPort (generic input at x, output at
 * x + 220, both at y + 50) already returns the right port. The old injector
 * returned -1 for pursuit nodes, which is why the node had no working ports.
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
}
