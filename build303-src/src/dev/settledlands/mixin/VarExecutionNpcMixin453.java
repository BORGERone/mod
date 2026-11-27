package dev.settledlands.mixin;

import dev.settledlands.npc.GraphRanges450;
import dev.settledlands.npc.GuideDialogue;
import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcActions;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.VarScopeStore453;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 453: executes full math operations and dual orange/blue inputs for {@code VARIABLE (4)} and
 * {@code IF (3)} inside {@link GuideDialogue} (dialogue advance, {@code resolveExecutionTarget},
 * {@code peekExecutionTarget}, and {@code evalIfNode}). Zero synthetic lambdas and no private inner
 * bridge classes.
 */
@Mixin(value = GuideDialogue.class, remap = false)
public abstract class VarExecutionNpcMixin453 {

    private static final ThreadLocal<NpcDialogueGraph> ACTIVE_GRAPH = new ThreadLocal<>();
    private static final ThreadLocal<ServerPlayer> ACTIVE_PLAYER = new ThreadLocal<>();
    private static final ThreadLocal<GuideNpc> ACTIVE_NPC = new ThreadLocal<>();
    private static final ThreadLocal<NpcDialogueGraph.Node> ACTIVE_IF_NODE = new ThreadLocal<>();

    @Shadow
    private static boolean evalCondition(int actual, int op, int target) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean isIncomingIfSatisfied(ServerPlayer player, GuideNpc npc, int relation,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node) {
        throw new AssertionError();
    }

    @Inject(method = "evalIfNode(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;ILdev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$evalIfFull453(ServerPlayer player, GuideNpc npc, int fallback,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node,
            CallbackInfoReturnable<Boolean> cir) {
        if (node == null || node.type != NpcDialogueGraph.IF) {
            cir.setReturnValue(Boolean.TRUE);
            return;
        }
        int actual = VarScopeStore453.readIfLeftValueForNpc(
                player, null, npc, fallback, graph, node, null);
        if ((node.varOp == 0 || node.varOp == 1)
                && GraphRanges450.hasRange(node, GraphRanges450.KEY_IF_COMPARE)) {
            boolean in = GraphRanges450.inRange(node, GraphRanges450.KEY_IF_COMPARE, actual);
            cir.setReturnValue(node.varOp == 0 ? in : !in);
            return;
        }
        cir.setReturnValue(evalCondition(actual, node.varOp, node.varCompare));
    }

    @Redirect(method = "resolveExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideDialogue;isIncomingIfSatisfied(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;ILdev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)Z"),
            remap = false)
    private static boolean settledlands$interceptIncomingAndVar453(ServerPlayer player, GuideNpc npc,
            int relation, NpcDialogueGraph graph, NpcDialogueGraph.Node node) {
        boolean ok = isIncomingIfSatisfied(player, npc, relation, graph, node);
        if (ok && node != null && node.type == NpcDialogueGraph.VARIABLE) {
            VarScopeStore453.executeVariableForNpc(player, null, npc, graph, node, null);
        }
        return ok;
    }

    @Redirect(method = "resolveExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideDialogue;addVarValue(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ljava/lang/String;ZZI)V"),
            remap = false)
    private static void settledlands$suppressLegacyAddVar453(ServerPlayer player, GuideNpc npc,
            String name, boolean shared, boolean local, int delta) {
        // Handled with full math operations and dual inputs in settledlands$interceptIncomingAndVar453
    }

    @Redirect(method = "resolveExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/NpcDialogueGraph;get(I)Ldev/settledlands/npc/NpcDialogueGraph$Node;"),
            remap = false)
    private static NpcDialogueGraph.Node settledlands$trackIfInResolve453(NpcDialogueGraph graph, int id) {
        NpcDialogueGraph.Node n = graph == null ? null : graph.get(id);
        if (n != null && n.type == NpcDialogueGraph.IF) {
            ACTIVE_GRAPH.set(graph);
            ACTIVE_IF_NODE.set(n);
        }
        return n;
    }

    @Redirect(method = "resolveExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideNpc;relationTo(Lnet/minecraft/server/level/ServerPlayer;)I"),
            remap = false)
    private static int settledlands$captureContextInResolve453(GuideNpc npc, ServerPlayer player) {
        ACTIVE_NPC.set(npc);
        ACTIVE_PLAYER.set(player);
        return npc.relationTo(player);
    }

    @Redirect(method = "resolveExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideDialogue;evalCondition(III)Z"),
            remap = false)
    private static boolean settledlands$evalIfResolve453(int stockActual, int op, int target) {
        NpcDialogueGraph.Node ifNode = ACTIVE_IF_NODE.get();
        NpcDialogueGraph graph = ACTIVE_GRAPH.get();
        ServerPlayer player = ACTIVE_PLAYER.get();
        GuideNpc npc = ACTIVE_NPC.get();
        ACTIVE_IF_NODE.remove();
        if (ifNode == null || graph == null) {
            return evalCondition(stockActual, op, target);
        }
        int actual = VarScopeStore453.readIfLeftValueForNpc(
                player, null, npc, 0, graph, ifNode, null);
        if ((ifNode.varOp == 0 || ifNode.varOp == 1)
                && GraphRanges450.hasRange(ifNode, GraphRanges450.KEY_IF_COMPARE)) {
            boolean in = GraphRanges450.inRange(ifNode, GraphRanges450.KEY_IF_COMPARE, actual);
            return ifNode.varOp == 0 ? in : !in;
        }
        return evalCondition(actual, ifNode.varOp, ifNode.varCompare);
    }

    @Inject(method = "peekExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;I)I",
            at = @At("HEAD"), remap = false)
    private static void settledlands$enterPeek453(ServerPlayer player, NpcDialogueGraph graph,
            int startId, CallbackInfoReturnable<Integer> cir) {
        ACTIVE_PLAYER.set(player);
        ACTIVE_GRAPH.set(graph);
        ACTIVE_NPC.remove();
        ACTIVE_IF_NODE.remove();
    }

    @Redirect(method = "peekExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;I)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/NpcDialogueGraph;get(I)Ldev/settledlands/npc/NpcDialogueGraph$Node;"),
            remap = false)
    private static NpcDialogueGraph.Node settledlands$trackIfInPeek453(NpcDialogueGraph graph, int id) {
        NpcDialogueGraph.Node n = graph == null ? null : graph.get(id);
        if (n != null && n.type == NpcDialogueGraph.IF) {
            ACTIVE_GRAPH.set(graph);
            ACTIVE_IF_NODE.set(n);
        }
        return n;
    }

    @Redirect(method = "peekExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;I)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideDialogue;evalCondition(III)Z"),
            remap = false)
    private static boolean settledlands$evalIfPeek453(int stockActual, int op, int target) {
        NpcDialogueGraph.Node ifNode = ACTIVE_IF_NODE.get();
        NpcDialogueGraph graph = ACTIVE_GRAPH.get();
        ServerPlayer player = ACTIVE_PLAYER.get();
        ACTIVE_IF_NODE.remove();
        if (ifNode == null || graph == null) {
            return evalCondition(stockActual, op, target);
        }
        int actual = VarScopeStore453.readIfLeftValueForNpc(
                player, null, null, 0, graph, ifNode, null);
        if ((ifNode.varOp == 0 || ifNode.varOp == 1)
                && GraphRanges450.hasRange(ifNode, GraphRanges450.KEY_IF_COMPARE)) {
            boolean in = GraphRanges450.inRange(ifNode, GraphRanges450.KEY_IF_COMPARE, actual);
            return ifNode.varOp == 0 ? in : !in;
        }
        return evalCondition(actual, ifNode.varOp, ifNode.varCompare);
    }

    @Inject(method = "peekExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;I)I",
            at = @At("RETURN"), remap = false)
    private static void settledlands$exitPeek453(ServerPlayer player, NpcDialogueGraph graph,
            int startId, CallbackInfoReturnable<Integer> cir) {
        ACTIVE_GRAPH.remove();
        ACTIVE_PLAYER.remove();
        ACTIVE_NPC.remove();
        ACTIVE_IF_NODE.remove();
    }
}
