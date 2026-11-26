package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.TeleportNode424;
import dev.settledlands.trial.TrialControlNode446;
import dev.settledlands.trial.TrialGraph446;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 446: wires {@link TrialControlNode446} nodes sequentially (instead of folding them into
 * {@code Node#triggers}) and routes stock wiring out of {@link TrialGraph446#TRIAL_TYPE} cards.
 */
@Mixin(targets = "dev.settledlands.npc.NpcDialogueGraph", remap = false)
public abstract class TrialControlWiringMixin446 {

    private static final int STATS_TYPE = 16;

    @Inject(method = "connect(III)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$connectTrialControl446(int fromId, int toId, int port,
            CallbackInfoReturnable<Boolean> cir) {
        if (fromId == toId) return;
        NpcDialogueGraph graph = (NpcDialogueGraph) (Object) this;
        NpcDialogueGraph.Node from = graph.get(fromId);
        NpcDialogueGraph.Node to = graph.get(toId);
        if (from == null || to == null) return;
        if (from.special != 0 || to.special != 0) return;
        if (from.type == STATS_TYPE || to.type == STATS_TYPE) return;

        boolean fromTrialCard = TrialGraph446.isTrialNode(from);
        boolean toTrialCard = TrialGraph446.isTrialNode(to);
        if (toTrialCard) {
            cir.setReturnValue(Boolean.FALSE);
            return;
        }

        boolean toControl = TrialControlNode446.isControlNode(to);
        boolean fromControl = TrialControlNode446.isControlNode(from);
        if (!fromTrialCard && !toControl && !fromControl) return;

        // Avoid stepping on Teleport wiring when neither side is a Trial card/control-specific combo
        if (!fromTrialCard && !fromControl && TeleportNode424.isTeleportNode(from)) return;

        if (from.type == NpcDialogueGraph.IF || from.type == 40 || from.type == 50 || fromTrialCard) {
            if (port == 1) {
                from.falseNext = toId;
            } else {
                from.next = toId;
            }
            cir.setReturnValue(Boolean.TRUE);
            return;
        }
        if (from.type == NpcDialogueGraph.RANDOM) {
            int branch = Math.max(0, port);
            if (from.randomBranches == null) {
                cir.setReturnValue(Boolean.FALSE);
                return;
            }
            while (from.randomBranches.size() <= branch) {
                from.randomBranches.add(-1);
            }
            from.randomBranches.set(branch, toId);
            cir.setReturnValue(Boolean.TRUE);
            return;
        }
        from.next = toId;
        cir.setReturnValue(Boolean.TRUE);
    }
}
