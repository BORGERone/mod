package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.trial.TrialGraph446;
import dev.settledlands.trial.TrialModule447;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 452: executes signaled {@link TrialModule447#SLOT_MOBS} and
 * {@link TrialModule447#SLOT_REWARDS} nodes during {@code BlockTrigger351} graph traversal and
 * stops cleanly before entering the central {@link TrialGraph446#TRIAL_TYPE} hub when the pool's
 * output is wired to the hub slot, while letting non-hub chains continue sequentially.
 *
 * <p>Zero synthetic lambdas are declared in this mixin class.</p>
 */
@Mixin(targets = "dev.settledlands.npc.BlockTrigger351", remap = false)
public abstract class TrialModuleSignalMixin452 {

    @Inject(method = "act(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$skipStockModuleAct452(ServerPlayer player,
            NpcDialogueGraph.Node node, CallbackInfo ci) {
        if (TrialModule447.isModuleNode(node)) {
            ci.cancel();
        }
    }

    @Inject(method = "actServerOnly(Lnet/minecraft/server/MinecraftServer;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$skipStockModuleActServer452(MinecraftServer server,
            NpcDialogueGraph.Node node, CallbackInfo ci) {
        if (TrialModule447.isModuleNode(node)) {
            ci.cancel();
        }
    }

    @Inject(method = "fireTriggers(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ljava/util/Set;)V",
            at = @At("HEAD"), remap = false)
    private static void settledlands$signalModuleOnWalk452(ServerPlayer player,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node, Set<Integer> visited,
            CallbackInfo ci) {
        if (!TrialModule447.isModuleNode(node)) return;
        ServerLevel level = (player != null && player.level() instanceof ServerLevel sl) ? sl : null;
        TrialModule447.onSignalTriggered(level, graph, node, player);
        if (visited != null && graph != null && node.next >= 0
                && TrialGraph446.isTrialNode(graph.get(node.next))) {
            // Do not walk out of the hub's wave-clear / destroy pins when a pool feeds a hub slot.
            visited.add(Integer.valueOf(node.next));
        }
    }

    @Inject(method = "fireTriggersServerOnly(Lnet/minecraft/server/MinecraftServer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ljava/util/Set;)V",
            at = @At("HEAD"), remap = false)
    private static void settledlands$signalModuleServerOnly452(MinecraftServer server,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node, Set<Integer> visited,
            CallbackInfo ci) {
        if (!TrialModule447.isModuleNode(node)) return;
        ServerLevel level = server == null ? null : server.overworld();
        TrialModule447.onSignalTriggered(level, graph, node, null);
        if (visited != null && graph != null && node.next >= 0
                && TrialGraph446.isTrialNode(graph.get(node.next))) {
            visited.add(Integer.valueOf(node.next));
        }
    }
}
