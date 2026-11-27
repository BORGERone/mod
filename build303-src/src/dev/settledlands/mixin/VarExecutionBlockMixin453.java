package dev.settledlands.mixin;

import dev.settledlands.npc.BlockTalk358;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.GraphRanges450;
import dev.settledlands.npc.GuideDialogue;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcPoints;
import dev.settledlands.npc.VarScopeStore453;
import dev.settledlands.npc.VarScopeStore453.BlockWalkFrame;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 453/454/456/457: executes {@link VarDataNode453} write-on-input, all 14 math operations,
 * dynamic blue-var IF comparisons, object-local variables ({@link VarScopeStore453}), blue
 * entity/player inputs, and variable placeholder substitution across block/door/chest/trial graphs
 * in {@link BlockTrigger351}. Uses external {@link BlockWalkFrame}, zero lambdas.
 */
@Mixin(value = BlockTrigger351.class, remap = false)
public abstract class VarExecutionBlockMixin453 {

    @Unique
    private static final ThreadLocal<Deque<BlockWalkFrame>> WALK_STACK = new ThreadLocal<>();

    @Shadow
    private static void act(ServerPlayer player, NpcDialogueGraph.Node node) {
        throw new AssertionError();
    }

    @Shadow
    private static void actServerOnly(MinecraftServer server, NpcDialogueGraph.Node node) {
        throw new AssertionError();
    }

    @Unique
    private static Deque<BlockWalkFrame> settledlands$stack453(boolean create) {
        Deque<BlockWalkFrame> st = WALK_STACK.get();
        if (st == null && create) {
            st = new ArrayDeque<>();
            WALK_STACK.set(st);
        }
        return st;
    }

    @Unique
    private static BlockWalkFrame settledlands$peekWalkFrame453() {
        Deque<BlockWalkFrame> st = WALK_STACK.get();
        return st == null ? null : st.peek();
    }

    @Unique
    private static void settledlands$popFrame453() {
        Deque<BlockWalkFrame> st = WALK_STACK.get();
        if (st == null) return;
        if (!st.isEmpty()) st.pop();
        if (st.isEmpty()) WALK_STACK.remove();
    }

    @Inject(method = "walk(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;Ldev/settledlands/npc/BlockTalk358$Where;[Z)I",
            at = @At("HEAD"), remap = false)
    private static void settledlands$pushWalkFrame453(ServerPlayer player, NpcDialogueGraph graph,
            int startId, Set visited, BlockTalk358.Where where, boolean[] stop,
            CallbackInfoReturnable<Integer> cir) {
        MinecraftServer srv = player != null ? player.getServer()
                : (where != null && where.level() != null ? where.level().getServer() : null);
        settledlands$stack453(true).push(new BlockWalkFrame(graph, where, srv));
        VarScopeStore453.pushTextContext(srv, player, null, where, graph, null);
    }

    @Inject(method = "walk(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;Ldev/settledlands/npc/BlockTalk358$Where;[Z)I",
            at = @At("RETURN"), remap = false)
    private static void settledlands$popWalkFrame453(ServerPlayer player, NpcDialogueGraph graph,
            int startId, Set visited, BlockTalk358.Where where, boolean[] stop,
            CallbackInfoReturnable<Integer> cir) {
        settledlands$popFrame453();
        VarScopeStore453.popTextContext();
    }

    @Redirect(method = "walk(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;Ldev/settledlands/npc/BlockTalk358$Where;[Z)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/NpcPoints;add(Ljava/util/UUID;Ljava/lang/String;ZI)I"),
            remap = false)
    private static int settledlands$suppressLegacyBlockVarAdd453(NpcPoints pts, UUID uuid,
            String name, boolean shared, int delta) {
        return 0;
    }

    @Redirect(method = "walk(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;Ldev/settledlands/npc/BlockTalk358$Where;[Z)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/BlockTrigger351;act(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V"),
            remap = false)
    private static void settledlands$executeBlockVarAndAct453(ServerPlayer player,
            NpcDialogueGraph.Node node) {
        BlockWalkFrame f = settledlands$peekWalkFrame453();
        if (node != null && node.type == NpcDialogueGraph.VARIABLE && f != null) {
            VarScopeStore453.executeVariableForBlock(player, f.server, f.graph, node, f.where);
        }
        NpcDialogueGraph.Node effNode = node;
        if (node != null && node.action != null && node.action.indexOf('%') >= 0 && f != null) {
            String sub = VarScopeStore453.substituteVarTokens(
                    node.action, f.server, player, null, f.where, f.graph, node);
            if (!sub.equals(node.action)) {
                effNode = node.copy();
                effNode.action = sub;
            }
        }
        act(player, effNode);
    }

    @Inject(method = "startServerOnly(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/NpcDialogueGraph;ILdev/settledlands/npc/BlockTalk358$Where;)I",
            at = @At("HEAD"), remap = false)
    private static void settledlands$pushServerFrame453(ServerLevel level, NpcDialogueGraph graph,
            int startId, BlockTalk358.Where where, CallbackInfoReturnable<Integer> cir) {
        MinecraftServer srv = level != null ? level.getServer()
                : (where != null && where.level() != null ? where.level().getServer() : null);
        settledlands$stack453(true).push(new BlockWalkFrame(graph, where, srv));
        VarScopeStore453.pushTextContext(srv, null, null, where, graph, null);
    }

    @Inject(method = "startServerOnly(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/NpcDialogueGraph;ILdev/settledlands/npc/BlockTalk358$Where;)I",
            at = @At("RETURN"), remap = false)
    private static void settledlands$popServerFrame453(ServerLevel level, NpcDialogueGraph graph,
            int startId, BlockTalk358.Where where, CallbackInfoReturnable<Integer> cir) {
        settledlands$popFrame453();
        VarScopeStore453.popTextContext();
    }

    @Redirect(method = "startServerOnly(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/NpcDialogueGraph;ILdev/settledlands/npc/BlockTalk358$Where;)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/NpcPoints;add(Ljava/util/UUID;Ljava/lang/String;ZI)I"),
            remap = false)
    private static int settledlands$suppressLegacyServerVarAdd453(NpcPoints pts, UUID uuid,
            String name, boolean shared, int delta) {
        return 0;
    }

    @Redirect(method = "startServerOnly(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/NpcDialogueGraph;ILdev/settledlands/npc/BlockTalk358$Where;)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/BlockTrigger351;actServerOnly(Lnet/minecraft/server/MinecraftServer;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V"),
            remap = false)
    private static void settledlands$executeServerVarAndAct453(MinecraftServer server,
            NpcDialogueGraph.Node node) {
        BlockWalkFrame f = settledlands$peekWalkFrame453();
        MinecraftServer srv = server != null ? server : (f != null ? f.server : null);
        if (node != null && node.type == NpcDialogueGraph.VARIABLE && f != null) {
            VarScopeStore453.executeVariableForBlock(null, srv, f.graph, node, f.where);
        }
        NpcDialogueGraph.Node effNode = node;
        if (node != null && node.action != null && node.action.indexOf('%') >= 0 && f != null) {
            String sub = VarScopeStore453.substituteVarTokens(
                    node.action, srv, null, null, f.where, f.graph, node);
            if (!sub.equals(node.action)) {
                effNode = node.copy();
                effNode.action = sub;
            }
        }
        actServerOnly(server, effNode);
    }

    @Redirect(method = "walk(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;Ldev/settledlands/npc/BlockTalk358$Where;[Z)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/BlockTrigger351;value(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)I"),
            remap = false)
    private static int settledlands$valueFullPlayer453(ServerPlayer player,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node) {
        BlockWalkFrame f = settledlands$peekWalkFrame453();
        if (f != null) f.activeIfNode = node;
        BlockTalk358.Where where = f == null ? null : f.where;
        MinecraftServer srv = player != null ? player.getServer() : (f == null ? null : f.server);
        int val = VarScopeStore453.readIfLeftValueForBlock(player, srv, graph, node, where);
        if (node != null && node.type == NpcDialogueGraph.IF
                && (node.varOp == 0 || node.varOp == 1)
                && GraphRanges450.hasRange(node, GraphRanges450.KEY_IF_COMPARE)) {
            boolean in = GraphRanges450.inRange(node, GraphRanges450.KEY_IF_COMPARE, val);
            val = in ? node.varCompare : (node.varCompare == Integer.MAX_VALUE ? 0 : node.varCompare + 1);
        }
        return val;
    }

    @Redirect(method = "walk(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;Ldev/settledlands/npc/BlockTalk358$Where;[Z)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideDialogue;evalCondition(III)Z"),
            remap = false, require = 0)
    private static boolean settledlands457$evalBlockCondPlayer(int actual, int op, int target) {
        BlockWalkFrame f = settledlands$peekWalkFrame453();
        NpcDialogueGraph.Node ifNode = f == null ? null : f.activeIfNode;
        if (f != null) f.activeIfNode = null;
        if (f != null && ifNode != null && !GraphRanges450.hasRange(ifNode, GraphRanges450.KEY_IF_COMPARE)) {
            VarScopeStore453.TextVarContext ctx = VarScopeStore453.peekTextContext();
            ServerPlayer player = ctx != null ? ctx.player : null;
            target = VarScopeStore453.readIfRightValueForBlock(player, f.server, f.graph, ifNode, f.where);
        }
        return GuideDialogue.evalCondition(actual, op, target);
    }

    @Redirect(method = "startServerOnly(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/NpcDialogueGraph;ILdev/settledlands/npc/BlockTalk358$Where;)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/BlockTrigger351;valueServerOnly(Lnet/minecraft/server/MinecraftServer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)I"),
            remap = false)
    private static int settledlands$valueFullServer453(MinecraftServer server,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node) {
        BlockWalkFrame f = settledlands$peekWalkFrame453();
        if (f != null) f.activeIfNode = node;
        BlockTalk358.Where where = f == null ? null : f.where;
        MinecraftServer srv = server != null ? server : (f == null ? null : f.server);
        int val = VarScopeStore453.readIfLeftValueForBlock(null, srv, graph, node, where);
        if (node != null && node.type == NpcDialogueGraph.IF
                && (node.varOp == 0 || node.varOp == 1)
                && GraphRanges450.hasRange(node, GraphRanges450.KEY_IF_COMPARE)) {
            boolean in = GraphRanges450.inRange(node, GraphRanges450.KEY_IF_COMPARE, val);
            val = in ? node.varCompare : (node.varCompare == Integer.MAX_VALUE ? 0 : node.varCompare + 1);
        }
        return val;
    }

    @Redirect(method = "startServerOnly(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/NpcDialogueGraph;ILdev/settledlands/npc/BlockTalk358$Where;)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideDialogue;evalCondition(III)Z"),
            remap = false, require = 0)
    private static boolean settledlands457$evalBlockCondServer(int actual, int op, int target) {
        BlockWalkFrame f = settledlands$peekWalkFrame453();
        NpcDialogueGraph.Node ifNode = f == null ? null : f.activeIfNode;
        if (f != null) f.activeIfNode = null;
        if (f != null && ifNode != null && !GraphRanges450.hasRange(ifNode, GraphRanges450.KEY_IF_COMPARE)) {
            target = VarScopeStore453.readIfRightValueForBlock(null, f.server, f.graph, ifNode, f.where);
        }
        return GuideDialogue.evalCondition(actual, op, target);
    }
}
