package dev.settledlands.mixin;

import dev.settledlands.chest.LootPool436;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.TeleportNode424;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 436: wires «Пул предметов» nodes sequentially (including RANDOM/IF outputs) instead of
 * collapsing them into {@code Node#triggers}.
 */
@Mixin(targets = "dev.settledlands.npc.NpcDialogueGraph", remap = false)
public abstract class LootPoolWiringMixin436 {

    private static final int STATS_TYPE = 16;

    @Inject(method = "connect(III)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$connectLootPool436(int fromId, int toId, int port,
            CallbackInfoReturnable<Boolean> cir) {
        if (fromId == toId) return;
        NpcDialogueGraph graph = (NpcDialogueGraph) (Object) this;
        NpcDialogueGraph.Node from = graph.get(fromId);
        NpcDialogueGraph.Node to = graph.get(toId);
        if (from == null || to == null) return;
        if (from.special != 0 || to.special != 0) return;
        if (from.type == STATS_TYPE || to.type == STATS_TYPE) return;
        if (TeleportNode424.isTeleportNode(from) || TeleportNode424.isTeleportNode(to)) return;

        boolean toPool = LootPool436.isLootPoolNode(to);
        boolean fromPool = LootPool436.isLootPoolNode(from);
        if (!toPool && !fromPool) return;

        if (from.type == NpcDialogueGraph.IF || from.type == 40 || from.type == 50) {
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
