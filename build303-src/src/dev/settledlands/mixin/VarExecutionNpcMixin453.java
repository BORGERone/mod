package dev.settledlands.mixin;

import dev.settledlands.npc.GraphRanges450;
import dev.settledlands.npc.GuideDialogue;
import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.VarScopeStore453;
import dev.settledlands.npc.VarScopeStore453.NpcResolveFrame;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 453/454: executes full math operations and dual orange/blue inputs for {@code VARIABLE (4)}
 * and {@code IF (3)} inside {@code GuideDialogue} ({@code resolveExecutionTarget},
 * {@code peekExecutionTarget}, and {@code evalIfNode}). Uses external {@link NpcResolveFrame} stack
 * (handles {@code session != null} and recursive {@code RADIUS} fan-outs cleanly), zero synthetic
 * lambdas, and zero invokedynamic.
 */
@Mixin(targets = "dev.settledlands.npc.GuideDialogue", remap = false)
public abstract class VarExecutionNpcMixin453 {

    @Unique
    private static final ThreadLocal<Deque<NpcResolveFrame>> RESOLVE_STACK = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<NpcDialogueGraph> PEEK_GRAPH = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<ServerPlayer> PEEK_PLAYER = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<NpcDialogueGraph.Node> PEEK_IF_NODE = new ThreadLocal<>();

    @Shadow
    private static boolean evalCondition(int actual, int op, int target) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean isIncomingIfSatisfied(ServerPlayer player, GuideNpc npc, int relation,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node) {
        throw new AssertionError();
    }

    @Unique
    private static Deque<NpcResolveFrame> settledlands$resolveStack454(boolean create) {
        Deque<NpcResolveFrame> st = RESOLVE_STACK.get();
        if (st == null && create) {
            st = new ArrayDeque<>();
            RESOLVE_STACK.set(st);
        }
        return st;
    }

    @Unique
    private static NpcResolveFrame settledlands$peekResolve454() {
        Deque<NpcResolveFrame> st = RESOLVE_STACK.get();
        return st == null ? null : st.peek();
    }

    @Unique
    private static void settledlands$popResolve454() {
        Deque<NpcResolveFrame> st = RESOLVE_STACK.get();
        if (st == null) return;
        if (!st.isEmpty()) st.pop();
        if (st.isEmpty()) RESOLVE_STACK.remove();
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
        if (GraphRanges450.hasRange(node, GraphRanges450.KEY_IF_COMPARE)) {
            if (node.varOp == 0 || node.varOp == 1) {
                boolean in = GraphRanges450.inRange(node, GraphRanges450.KEY_IF_COMPARE, actual);
                cir.setReturnValue(node.varOp == 0 ? in : !in);
                return;
            }
            GraphRanges450.rerollNode(node, ThreadLocalRandom.current());
        }
        cir.setReturnValue(evalCondition(actual, node.varOp, node.varCompare));
    }

    @Inject(method = "resolveExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I",
            at = @At("HEAD"), remap = false)
    private static void settledlands$pushResolve454(ServerPlayer player, Entity contextEntity,
            GuideNpc npc, GuideDialogue.Session session, NpcDialogueGraph graph, int startId,
            Set visited, CallbackInfoReturnable<Integer> cir) {
        settledlands$resolveStack454(true).push(
                new NpcResolveFrame(player, contextEntity, npc, graph));
    }

    @Inject(method = "resolveExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I",
            at = @At("RETURN"), remap = false)
    private static void settledlands$popResolve454Inject(ServerPlayer player, Entity contextEntity,
            GuideNpc npc, GuideDialogue.Session session, NpcDialogueGraph graph, int startId,
            Set visited, CallbackInfoReturnable<Integer> cir) {
        settledlands$popResolve454();
    }

    @Redirect(method = "resolveExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideDialogue;isIncomingIfSatisfied(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;ILdev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)Z"),
            remap = false)
    private static boolean settledlands$interceptIncomingAndVar453(ServerPlayer player, GuideNpc npc,
            int relation, NpcDialogueGraph graph, NpcDialogueGraph.Node node) {
        boolean ok = isIncomingIfSatisfied(player, npc, relation, graph, node);
        if (ok && node != null && node.type == NpcDialogueGraph.VARIABLE) {
            NpcResolveFrame f = settledlands$peekResolve454();
            Entity ctx = f == null ? null : f.contextEntity;
            VarScopeStore453.executeVariableForNpc(player, ctx, npc, graph, node, null);
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
            NpcResolveFrame f = settledlands$peekResolve454();
            if (f != null) {
                f.activeIfNode = n;
            }
        }
        return n;
    }

    @Redirect(method = "resolveExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideDialogue;evalCondition(III)Z"),
            remap = false)
    private static boolean settledlands$evalIfResolve453(int stockActual, int op, int target) {
        NpcResolveFrame f = settledlands$peekResolve454();
        NpcDialogueGraph.Node ifNode = f == null ? null : f.activeIfNode;
        if (f != null) f.activeIfNode = null;
        if (f == null || ifNode == null || f.graph == null) {
            return evalCondition(stockActual, op, target);
        }
        int actual = VarScopeStore453.readIfLeftValueForNpc(
                f.player, f.contextEntity, f.npc, 0, f.graph, ifNode, null);
        if (GraphRanges450.hasRange(ifNode, GraphRanges450.KEY_IF_COMPARE)) {
            if (ifNode.varOp == 0 || ifNode.varOp == 1) {
                boolean in = GraphRanges450.inRange(ifNode, GraphRanges450.KEY_IF_COMPARE, actual);
                return ifNode.varOp == 0 ? in : !in;
            }
            GraphRanges450.rerollNode(ifNode, ThreadLocalRandom.current());
        }
        return evalCondition(actual, ifNode.varOp, ifNode.varCompare);
    }

    @Inject(method = "peekExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;I)I",
            at = @At("HEAD"), remap = false)
    private static void settledlands$enterPeek453(ServerPlayer player, NpcDialogueGraph graph,
            int startId, CallbackInfoReturnable<Integer> cir) {
        PEEK_PLAYER.set(player);
        PEEK_GRAPH.set(graph);
        PEEK_IF_NODE.remove();
    }

    @Redirect(method = "peekExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;I)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/NpcDialogueGraph;get(I)Ldev/settledlands/npc/NpcDialogueGraph$Node;"),
            remap = false)
    private static NpcDialogueGraph.Node settledlands$trackIfInPeek453(NpcDialogueGraph graph, int id) {
        NpcDialogueGraph.Node n = graph == null ? null : graph.get(id);
        if (n != null && n.type == NpcDialogueGraph.IF) {
            PEEK_GRAPH.set(graph);
            PEEK_IF_NODE.set(n);
        }
        return n;
    }

    @Redirect(method = "peekExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;I)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideDialogue;evalCondition(III)Z"),
            remap = false)
    private static boolean settledlands$evalIfPeek453(int stockActual, int op, int target) {
        NpcDialogueGraph.Node ifNode = PEEK_IF_NODE.get();
        NpcDialogueGraph graph = PEEK_GRAPH.get();
        ServerPlayer player = PEEK_PLAYER.get();
        PEEK_IF_NODE.remove();
        if (ifNode == null || graph == null) {
            return evalCondition(stockActual, op, target);
        }
        int actual = VarScopeStore453.readIfLeftValueForNpc(
                player, null, null, 0, graph, ifNode, null);
        if (GraphRanges450.hasRange(ifNode, GraphRanges450.KEY_IF_COMPARE)) {
            if (ifNode.varOp == 0 || ifNode.varOp == 1) {
                boolean in = GraphRanges450.inRange(ifNode, GraphRanges450.KEY_IF_COMPARE, actual);
                return ifNode.varOp == 0 ? in : !in;
            }
            GraphRanges450.rerollNode(ifNode, ThreadLocalRandom.current());
        }
        return evalCondition(actual, ifNode.varOp, ifNode.varCompare);
    }

    @Inject(method = "peekExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;I)I",
            at = @At("RETURN"), remap = false)
    private static void settledlands$exitPeek453(ServerPlayer player, NpcDialogueGraph graph,
            int startId, CallbackInfoReturnable<Integer> cir) {
        PEEK_GRAPH.remove();
        PEEK_PLAYER.remove();
        PEEK_IF_NODE.remove();
    }
}
