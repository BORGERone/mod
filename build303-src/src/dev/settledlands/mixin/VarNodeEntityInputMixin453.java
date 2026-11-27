package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.VarDataNode453;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 453: adds dedicated blue entity/target input storage ({@code entityInput453}) to every
 * {@link NpcDialogueGraph.Node} so {@code IF} and {@code VARIABLE} keep blue and orange inputs
 * independently and preserve both across {@code Node.copy()}. Zero synthetic lambdas.
 */
@Mixin(targets = "dev.settledlands.npc.NpcDialogueGraph$Node", remap = false)
public abstract class VarNodeEntityInputMixin453 implements VarDataNode453.EntityInputHost {

    @Unique
    private int settledlands$entityInput453 = -1;

    @Override
    public int settledlands$getEntityInput453() {
        return settledlands$entityInput453;
    }

    @Override
    public void settledlands$setEntityInput453(int nodeId) {
        this.settledlands$entityInput453 = nodeId;
    }

    @Inject(method = "copy()Ldev/settledlands/npc/NpcDialogueGraph$Node;",
            at = @At("RETURN"), remap = false)
    private void settledlands$copyEntityInput453(CallbackInfoReturnable<NpcDialogueGraph.Node> cir) {
        NpcDialogueGraph.Node copy = cir.getReturnValue();
        if (copy != null) {
            VarDataNode453.setEntityInput(copy, this.settledlands$entityInput453);
        }
    }
}
