package dev.settledlands.mixin;

import dev.settledlands.npc.BlockTalk358;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.GraphRanges450;
import dev.settledlands.npc.NpcDialogueGraph;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 450: re-rolls variable/trigger ranges when walking block/door/chest/trial graphs and
 * supports {@code IF == a-b} range membership matching. Zero synthetic lambdas.
 */
@Mixin(value = BlockTrigger351.class, remap = false)
public abstract class TriggerRange450Mixin {

    @Inject(method = "walk(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;Ldev/settledlands/npc/BlockTalk358$Where;[Z)I",
            at = @At("HEAD"), remap = false)
    private static void settledlands$rerollWalk450(ServerPlayer player, NpcDialogueGraph graph,
            int startId, Set visited, BlockTalk358.Where where, boolean[] stop,
            CallbackInfoReturnable<Integer> cir) {
        GraphRanges450.rerollGraph(graph, ThreadLocalRandom.current());
    }

    @Inject(method = "startServerOnly(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/NpcDialogueGraph;ILdev/settledlands/npc/BlockTalk358$Where;)I",
            at = @At("HEAD"), remap = false)
    private static void settledlands$rerollServerWalk450(ServerLevel level, NpcDialogueGraph graph,
            int startId, BlockTalk358.Where where, CallbackInfoReturnable<Integer> cir) {
        GraphRanges450.rerollGraph(graph, ThreadLocalRandom.current());
    }

    @Inject(method = "value(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)I",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void settledlands$ifRangePlayer450(ServerPlayer player, NpcDialogueGraph graph,
            NpcDialogueGraph.Node node, CallbackInfoReturnable<Integer> cir) {
        if (node != null && node.type == NpcDialogueGraph.IF
                && (node.varOp == 0 || node.varOp == 1)
                && GraphRanges450.hasRange(node, GraphRanges450.KEY_IF_COMPARE)) {
            Integer ret = cir.getReturnValue();
            if (ret != null) {
                boolean in = GraphRanges450.inRange(node, GraphRanges450.KEY_IF_COMPARE, ret);
                cir.setReturnValue(in ? node.varCompare : (node.varCompare == Integer.MAX_VALUE ? 0 : node.varCompare + 1));
            }
        }
    }

    @Inject(method = "valueServerOnly(Lnet/minecraft/server/MinecraftServer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)I",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void settledlands$ifRangeServer450(MinecraftServer server, NpcDialogueGraph graph,
            NpcDialogueGraph.Node node, CallbackInfoReturnable<Integer> cir) {
        if (node != null && node.type == NpcDialogueGraph.IF
                && (node.varOp == 0 || node.varOp == 1)
                && GraphRanges450.hasRange(node, GraphRanges450.KEY_IF_COMPARE)) {
            Integer ret = cir.getReturnValue();
            if (ret != null) {
                boolean in = GraphRanges450.inRange(node, GraphRanges450.KEY_IF_COMPARE, ret);
                cir.setReturnValue(in ? node.varCompare : (node.varCompare == Integer.MAX_VALUE ? 0 : node.varCompare + 1));
            }
        }
    }
}
