package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.RadiusZone456;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 456: stores the square-vs-circle zone flag directly on {@link NpcDialogueGraph.Node} and
 * preserves it across {@link NpcDialogueGraph.Node#copy()} (undo/redo snapshots). Zero lambdas.
 */
@Mixin(targets = "dev.settledlands.npc.NpcDialogueGraph$Node", remap = false)
public abstract class RadiusZoneNodeMixin456 implements RadiusZone456.ZoneShapeHost {

    @Unique
    private boolean settledlands$squareZone456;

    @Override
    public boolean settledlands$isSquareZone456() {
        return this.settledlands$squareZone456;
    }

    @Override
    public void settledlands$setSquareZone456(boolean square) {
        this.settledlands$squareZone456 = square;
    }

    @Inject(method = "copy()Ldev/settledlands/npc/NpcDialogueGraph$Node;",
            at = @At("RETURN"), remap = false)
    private void settledlands$copySquareZone456(
            CallbackInfoReturnable<NpcDialogueGraph.Node> cir) {
        NpcDialogueGraph.Node copy = cir.getReturnValue();
        if (copy != null) {
            RadiusZone456.setSquare(copy, this.settledlands$squareZone456);
        }
    }
}
