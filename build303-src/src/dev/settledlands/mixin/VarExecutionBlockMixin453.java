package dev.settledlands.mixin;

import dev.settledlands.npc.BlockTalk358;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcPoints;
import dev.settledlands.npc.VarScopeStore453;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 453: executes all 14 math operations, UE5 {@link dev.settledlands.npc.VarDataNode453}
 * orange inputs, object-local variables ({@link VarScopeStore453}), and blue entity/player inputs
 * across block/door/chest/trial graphs in {@link BlockTrigger351}. Zero synthetic lambdas.
 */
@Mixin(value = BlockTrigger351.class, remap = false)
public abstract class VarExecutionBlockMixin453 {

    private static final class Frame {
        final NpcDialogueGraph graph;
        final BlockTalk358.Where where;
        final MinecraftServer server;

        Frame(NpcDialogueGraph graph, BlockTalk358.Where where, MinecraftServer server) {
            this.graph = graph;
            this.where = where;
            this.server = server;
        }
    }

    private static final ThreadLocal<Deque<Frame>> WALK_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Shadow
    private static void act(ServerPlayer player, NpcDialogueGraph.Node node) {
        throw new AssertionError();
    }

    @Shadow
    private static void actServerOnly(MinecraftServer server, NpcDialogueGraph.Node node) {
        throw new AssertionError();
    }

    @Inject(method = "walk(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;Ldev/settledlands/npc/BlockTalk358$Where;[Z)I",
            at = @At("HEAD"), remap = false)
    private static void settledlands$pushWalkFrame453(ServerPlayer player, NpcDialogueGraph graph,
            int startId, Set visited, BlockTalk358.Where where, boolean[] stop,
            CallbackInfoReturnable<Integer> cir) {
        MinecraftServer srv = player != null ? player.getServer()
                : (where != null && where.level() != null ? where.level().getServer() : null);
        WALK_STACK.get().push(new Frame(graph, where, srv));
    }

    @Inject(method = "walk(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;Ldev/settledlands/npc/BlockTalk358$Where;[Z)I",
            at = @At("RETURN"), remap = false)
    private static void settledlands$popWalkFrame453(ServerPlayer player, NpcDialogueGraph graph,
            int startId, Set visited, BlockTalk358.Where where, boolean[] stop,
            CallbackInfoReturnable<Integer> cir) {
        Deque<Frame> st = WALK_STACK.get();
        if (!st.isEmpty()) st.pop();
        if (st.isEmpty()) WALK_STACK.remove();
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
        if (node != null && node.type == NpcDialogueGraph.VARIABLE) {
            Frame f = WALK_STACK.get().peek();
            if (f != null) {
                VarScopeStore453.executeVariableForBlock(player, f.server, f.graph, node, f.where);
            }
        }
        act(player, node);
    }

    @Inject(method = "startServerOnly(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/NpcDialogueGraph;ILdev/settledlands/npc/BlockTalk358$Where;)I",
            at = @At("HEAD"), remap = false)
    private static void settledlands$pushServerFrame453(ServerLevel level, NpcDialogueGraph graph,
            int startId, BlockTalk358.Where where, CallbackInfoReturnable<Integer> cir) {
        MinecraftServer srv = level != null ? level.getServer()
                : (where != null && where.level() != null ? where.level().getServer() : null);
        WALK_STACK.get().push(new Frame(graph, where, srv));
    }

    @Inject(method = "startServerOnly(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/NpcDialogueGraph;ILdev/settledlands/npc/BlockTalk358$Where;)I",
            at = @At("RETURN"), remap = false)
    private static void settledlands$popServerFrame453(ServerLevel level, NpcDialogueGraph graph,
            int startId, BlockTalk358.Where where, CallbackInfoReturnable<Integer> cir) {
        Deque<Frame> st = WALK_STACK.get();
        if (!st.isEmpty()) st.pop();
        if (st.isEmpty()) WALK_STACK.remove();
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
        if (node != null && node.type == NpcDialogueGraph.VARIABLE) {
            Frame f = WALK_STACK.get().peek();
            if (f != null) {
                VarScopeStore453.executeVariableForBlock(null, server != null ? server : f.server,
                        f.graph, node, f.where);
            }
        }
        actServerOnly(server, node);
    }

    @Inject(method = "value(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)I",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$valueFullPlayer453(ServerPlayer player,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node,
            CallbackInfoReturnable<Integer> cir) {
        Frame f = WALK_STACK.get().peek();
        BlockTalk358.Where where = f == null ? null : f.where;
        MinecraftServer srv = player != null ? player.getServer() : (f == null ? null : f.server);
        cir.setReturnValue(VarScopeStore453.readIfLeftValueForBlock(
                player, srv, graph, node, where));
    }

    @Inject(method = "valueServerOnly(Lnet/minecraft/server/MinecraftServer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)I",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$valueFullServer453(MinecraftServer server,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node,
            CallbackInfoReturnable<Integer> cir) {
        Frame f = WALK_STACK.get().peek();
        BlockTalk358.Where where = f == null ? null : f.where;
        MinecraftServer srv = server != null ? server : (f == null ? null : f.server);
        cir.setReturnValue(VarScopeStore453.readIfLeftValueForBlock(
                null, srv, graph, node, where));
    }
}
