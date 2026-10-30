package dev.settledlands.mixin;

import dev.settledlands.npc.CutsceneFadeGraphData263;
import dev.settledlands.npc.CutsceneFadeNodeAccess263;
import dev.settledlands.npc.NpcDialogueGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stores independent flags on graph nodes and carries them into graph undo/copy snapshots. */
@Mixin(targets = {"dev.settledlands.npc.NpcDialogueGraph$Node"}, remap = false)
public abstract class CutsceneNodeFadeMixin263 implements CutsceneFadeNodeAccess263 {
    @Unique private boolean settledlands$fadeOut;
    @Unique private boolean settledlands$fadeIn;

    @Override
    @Unique
    public boolean settledlands$getFadeOut() { return settledlands$fadeOut; }

    @Override
    @Unique
    public void settledlands$setFadeOut(boolean value) { settledlands$fadeOut = value; }

    @Override
    @Unique
    public boolean settledlands$getFadeIn() { return settledlands$fadeIn; }

    @Override
    @Unique
    public void settledlands$setFadeIn(boolean value) { settledlands$fadeIn = value; }

    @Inject(method = {"copy()Ldev/settledlands/npc/NpcDialogueGraph$Node;"},
            at = {@At("RETURN")}, remap = false)
    private void settledlands$copyFadeFlags(
            CallbackInfoReturnable<NpcDialogueGraph.Node> cir) {
        CutsceneFadeGraphData263.copy((NpcDialogueGraph.Node) (Object) this, cir.getReturnValue());
    }
}
