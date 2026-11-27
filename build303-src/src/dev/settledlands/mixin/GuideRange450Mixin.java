package dev.settledlands.mixin;

import dev.settledlands.npc.GraphRanges450;
import dev.settledlands.npc.GuideDialogue;
import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcProfile;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 450: evaluates {@code IF} ranges accurately and re-rolls stochastic node ranges across
 * NPC dialogue execution and timer/time/check/damage cycles. Zero synthetic lambdas.
 */
@Mixin(value = GuideDialogue.class, remap = false)
public abstract class GuideRange450Mixin {

    @Shadow
    private static int readVarValue(ServerPlayer player, GuideNpc npc, String name,
            boolean shared, boolean local) {
        throw new AssertionError();
    }

    @Inject(method = "start(Lnet/minecraft/world/entity/player/Player;Ldev/settledlands/npc/GuideNpc;)V",
            at = @At("HEAD"), remap = false)
    private static void settledlands$rerollStart450(Player player, GuideNpc npc, CallbackInfo ci) {
        if (npc == null) return;
        NpcProfile p = npc.profile();
        if (p != null && p.dialogueGraph != null) {
            GraphRanges450.rerollGraph(p.dialogueGraph, ThreadLocalRandom.current());
        }
    }

    @Inject(method = "evalIfNode(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;ILdev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$evalIfRange450(ServerPlayer player, GuideNpc npc, int fallback,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node,
            CallbackInfoReturnable<Boolean> cir) {
        if (node == null || node.type != NpcDialogueGraph.IF) return;
        if (!GraphRanges450.hasRange(node, GraphRanges450.KEY_IF_COMPARE)) return;
        if (node.varOp != 0 && node.varOp != 1) {
            GraphRanges450.rerollNode(node, ThreadLocalRandom.current());
            return;
        }
        String name = node.pointsName;
        boolean shared = node.pointsShared;
        boolean local = node.varLocal;
        if (node.varInputNode >= 0 && graph != null) {
            NpcDialogueGraph.Node src = graph.get(node.varInputNode);
            if (src != null && src.type == NpcDialogueGraph.VARIABLE
                    && src.pointsName != null && !src.pointsName.isBlank()) {
                name = src.pointsName;
                shared = src.pointsShared;
                local = src.varLocal;
            }
        }
        int actual = (name == null || name.isBlank())
                ? fallback
                : readVarValue(player, npc, name, shared, local);
        boolean in = GraphRanges450.inRange(node, GraphRanges450.KEY_IF_COMPARE, actual);
        cir.setReturnValue(node.varOp == 0 ? in : !in);
    }

    @Inject(method = "effectiveNext(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)I",
            at = @At("HEAD"), remap = false)
    private static void settledlands$rerollEffectiveNext450(ServerPlayer player,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node,
            CallbackInfoReturnable<Integer> cir) {
        GraphRanges450.rerollGraph(graph, ThreadLocalRandom.current());
    }

    @Inject(method = "peekExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;I)I",
            at = @At("HEAD"), remap = false)
    private static void settledlands$rerollPeek450(ServerPlayer player, NpcDialogueGraph graph,
            int startId, CallbackInfoReturnable<Integer> cir) {
        GraphRanges450.rerollGraph(graph, ThreadLocalRandom.current());
    }

    @Inject(method = "executeGraphTriggers(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;ILdev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;)V",
            at = @At("HEAD"), remap = false)
    private static void settledlands$rerollExecTrig450(ServerPlayer player, GuideNpc npc,
            int sourceId, NpcDialogueGraph graph, List triggers, CallbackInfo ci) {
        GraphRanges450.rerollGraph(graph, ThreadLocalRandom.current());
    }

    @Inject(method = "executeIncomingTriggers(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;ILdev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)V",
            at = @At("HEAD"), remap = false)
    private static void settledlands$rerollIncomingTrig450(ServerPlayer player, GuideNpc npc,
            int sourceId, NpcDialogueGraph graph, int targetId, Set visited, CallbackInfo ci) {
        GraphRanges450.rerollGraph(graph, ThreadLocalRandom.current());
    }

    @Inject(method = "triggerTimerChain(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("RETURN"), remap = false)
    private static void settledlands$rerollNpcTimer450(GuideNpc npc, ServerPlayer player,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node, CallbackInfo ci) {
        GraphRanges450.rerollNode(node, ThreadLocalRandom.current());
    }

    @Inject(method = "triggerTimeChain(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At("RETURN"), remap = false)
    private static void settledlands$rerollNpcTime450(GuideNpc npc, ServerPlayer player,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node, CallbackInfo ci) {
        GraphRanges450.rerollNode(node, ThreadLocalRandom.current());
    }

    @Inject(method = "tickGraphCheckNodes(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/Map;)V",
            at = @At("RETURN"), remap = false)
    private static void settledlands$rerollNpcCheck450(GuideNpc npc, NpcDialogueGraph graph,
            Map state, CallbackInfo ci) {
        GraphRanges450.rerollGraphOfType(graph, ThreadLocalRandom.current(), NpcDialogueGraph.CHECK);
    }
}
