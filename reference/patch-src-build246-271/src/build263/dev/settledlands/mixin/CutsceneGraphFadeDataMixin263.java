package dev.settledlands.mixin;

import dev.settledlands.npc.CutsceneFadeGraphData263;
import dev.settledlands.npc.NpcDialogueGraph;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds backward-compatible NBT fields for the two optional cutscene transition flags. */
@Mixin(targets = {"dev.settledlands.npc.NpcDialogueGraph"}, remap = false)
public abstract class CutsceneGraphFadeDataMixin263 {
    @Inject(method = {"save()Lnet/minecraft/nbt/CompoundTag;"}, at = {@At("RETURN")}, remap = false)
    private void settledlands$saveCutsceneFadeOptions(CallbackInfoReturnable<CompoundTag> cir) {
        CutsceneFadeGraphData263.save((NpcDialogueGraph) (Object) this, cir.getReturnValue());
    }

    @Inject(method = {"load(Lnet/minecraft/nbt/CompoundTag;)Ldev/settledlands/npc/NpcDialogueGraph;"},
            at = {@At("RETURN")}, remap = false)
    private static void settledlands$loadCutsceneFadeOptions(CompoundTag tag,
            CallbackInfoReturnable<NpcDialogueGraph> cir) {
        CutsceneFadeGraphData263.load(cir.getReturnValue(), tag);
    }
}
