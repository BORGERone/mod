package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.TeleportNode424;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 424: wires Teleport nodes sequentially instead of collapsing them into {@code Node#triggers}.
 *
 * <p>Stock {@code NpcDialogueGraph#connect} places any {@code type == TRIGGER} target into
 * {@code from.triggers}, which {@code BlockTrigger351#fireTriggers} stops at instead of advancing
 * along {@code next}. Intercepting both incoming edges (to a Teleport node) and outgoing edges
 * (from a Teleport node to any node including another {@code TRIGGER}) keeps chains like
 * {@code Line -> Teleport -> Line2} and {@code Card -> Teleport -> Trigger -> Line} intact in every
 * editor and interpreter.</p>
 */
@Mixin(targets = "dev.settledlands.npc.NpcDialogueGraph", remap = false)
public abstract class TeleportWiringMixin424 {

    private static final int STATS_TYPE = 16;

    @Inject(method = "connect(III)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$connectTeleport424(int fromId, int toId, int port,
            CallbackInfoReturnable<Boolean> cir) {
        if (fromId == toId) return;
        NpcDialogueGraph graph = (NpcDialogueGraph) (Object) this;
        NpcDialogueGraph.Node from = graph.get(fromId);
        NpcDialogueGraph.Node to = graph.get(toId);
        if (from == null || to == null) return;
        if (from.special != 0 || to.special != 0) return;
        if (from.type == STATS_TYPE || to.type == STATS_TYPE) return;

        boolean toTeleport = TeleportNode424.isTeleportNode(to);
        boolean fromTeleport = TeleportNode424.isTeleportNode(from);
        if (!toTeleport && !fromTeleport) return;

        if (from.type == NpcDialogueGraph.IF) {
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
