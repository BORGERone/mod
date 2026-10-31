package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcValueSource290;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 295: the "nobody in the radius" half of the check node.
 *
 * <p>The scan in {@code tickGraphCheckNodes} starts a chain only when it finds a body. For a
 * check node that feeds a value wire that leaves half of the graph unreachable: the IF wired
 * to it can only ever run with somebody present, and its {@code == 0} branch never fires -
 * the log of the reported case shows "check node #4 scanning..." and no walk at all.</p>
 *
 * <p>This hook runs after the stock scan and hands the same graph to
 * {@code NpcValueSource290#tickEmptyChecks}, which starts the chain at the node's next node
 * with no initiator when the radius is empty. Everything the stock tick already handled -
 * a body present, a body on its cooldown, the NPC talking - stays with the stock tick.</p>
 */
@Mixin(targets = "dev.settledlands.npc.GuideDialogue", remap = false)
public abstract class GuideDialogueEmptyCheck295 {

    @Inject(method = "tickGraphCheckNodes(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/Map;)V",
            at = @At("TAIL"), remap = false)
    private static void settledlands$emptyCheck(GuideNpc npc, NpcDialogueGraph graph,
            Map<Integer, Map<java.util.UUID, Long>> cooldowns, CallbackInfo ci) {
        NpcValueSource290.tickEmptyChecks(npc, graph);
    }
}
