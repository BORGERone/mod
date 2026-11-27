package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcProfile;
import dev.settledlands.npc.NpcValueSource290;
import dev.settledlands.npc.VarDataNode453;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 453: makes {@link NpcValueSource290#sourceOf} and {@link NpcValueSource290#feedsValueWire}
 * aware of {@code entityInput453} on {@code IF}/{@code VARIABLE} nodes so blue entity wires and
 * orange variable wires never conflict on the server. Zero synthetic lambdas.
 */
@Mixin(value = NpcValueSource290.class, remap = false)
public abstract class VarValueSourceBridgeMixin453 {

    @Shadow
    private static NpcProfile profileOf(GuideNpc npc) {
        throw new AssertionError();
    }

    @Shadow
    private static List<NpcDialogueGraph> graphs(NpcProfile profile) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean isSourceNode(NpcDialogueGraph.Node node) {
        throw new AssertionError();
    }

    @Inject(method = "sourceOf(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph$Node;)Ldev/settledlands/npc/NpcDialogueGraph$Node;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$sourceOfEntityInput453(GuideNpc npc,
            NpcDialogueGraph.Node consumer,
            CallbackInfoReturnable<NpcDialogueGraph.Node> cir) {
        if (npc == null || consumer == null) return;
        if (consumer.type != NpcDialogueGraph.IF && consumer.type != NpcDialogueGraph.VARIABLE) {
            return;
        }
        int blueId = VarDataNode453.entityInput(consumer);
        if (blueId < 0 || blueId == consumer.id) return;
        NpcProfile profile = profileOf(npc);
        if (profile == null) return;
        for (NpcDialogueGraph g : graphs(profile)) {
            if (g == null || g.nodes == null) continue;
            if (g.get(consumer.id) != consumer) continue;
            NpcDialogueGraph.Node src = g.get(blueId);
            cir.setReturnValue(src);
            return;
        }
    }

    @Inject(method = "feedsValueWire(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph$Node;)Z",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void settledlands$feedsEntityInput453(GuideNpc npc,
            NpcDialogueGraph.Node source, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (npc == null || source == null || !isSourceNode(source)) return;
        NpcProfile profile = profileOf(npc);
        if (profile == null) return;
        for (NpcDialogueGraph g : graphs(profile)) {
            if (g == null || g.nodes == null) continue;
            if (g.get(source.id) != source) continue;
            for (NpcDialogueGraph.Node n : g.nodes.values()) {
                if (n != null && (n.type == NpcDialogueGraph.IF || n.type == NpcDialogueGraph.VARIABLE)
                        && VarDataNode453.entityInput(g, n) == source.id) {
                    cir.setReturnValue(Boolean.TRUE);
                    return;
                }
            }
        }
    }
}
