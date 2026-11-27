package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcProfile;
import dev.settledlands.npc.NpcValueSource290;
import dev.settledlands.npc.VarDataNode453;
import dev.settledlands.npc.VarScopeStore453;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 453/457: makes {@link NpcValueSource290#sourceOf}, {@link NpcValueSource290#feedsValueWire},
 * {@link NpcValueSource290#lineText}, and {@link NpcValueSource290#triggerBlocked} aware of
 * {@code entityInput453} AND {@link VarDataNode453} wired into blue consumer inputs across NPC
 * graphs so variable values and entity bodies coexist cleanly without clobbering each other.
 * Zero synthetic lambdas.
 */
@Mixin(value = NpcValueSource290.class, remap = false)
public abstract class VarValueSourceBridgeMixin453 {

    @Shadow
    private static ThreadLocal<GuideNpc> CURRENT;

    @Shadow
    private static ThreadLocal<Boolean> PENDING;

    @Shadow
    private static NpcProfile profileOf(GuideNpc npc) {
        throw new AssertionError();
    }

    @Shadow
    private static List<NpcDialogueGraph> graphs(NpcProfile profile) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean isSourceNode(NpcDialogueGraph.Node node) {
        throw new AssertionError();
    }

    @Inject(method = "sourceOf(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph$Node;)Ldev/settledlands/npc/NpcDialogueGraph$Node;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$sourceOfEntityInput453(GuideNpc npc,
            NpcDialogueGraph.Node consumer,
            CallbackInfoReturnable<NpcDialogueGraph.Node> cir) {
        if (npc == null || consumer == null) return;
        if (consumer.type != NpcDialogueGraph.IF && consumer.type != NpcDialogueGraph.VARIABLE
                && !VarDataNode453.isVarDataNode(consumer)) {
            return;
        }
        int blueId = VarDataNode453.entityInput(consumer);
        if (blueId < 0 || blueId == consumer.id) return;
        NpcProfile profile = profileOf(npc);
        if (profile == null) return;
        for (NpcDialogueGraph g : graphs(profile)) {
            if (g == null || g.nodes == null) continue;
            if (g.get(consumer.id) != consumer) continue;
            NpcDialogueGraph.Node src = g.get(blueId);
            cir.setReturnValue(src);
            return;
        }
    }

    @Inject(method = "feedsValueWire(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph$Node;)Z",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void settledlands$feedsEntityInput453(GuideNpc npc,
            NpcDialogueGraph.Node source, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) return;
        if (npc == null || source == null || (!isSourceNode(source) && !VarDataNode453.isVarDataNode(source))) {
            return;
        }
        NpcProfile profile = profileOf(npc);
        if (profile == null) return;
        for (NpcDialogueGraph g : graphs(profile)) {
            if (g == null || g.nodes == null) continue;
            if (g.get(source.id) != source) continue;
            for (NpcDialogueGraph.Node n : g.nodes.values()) {
                if (n != null && VarDataNode453.entityInput(g, n) == source.id) {
                    cir.setReturnValue(Boolean.TRUE);
                    return;
                }
            }
        }
    }

    @Inject(method = "lineText(Ldev/settledlands/npc/NpcDialogueGraph$Node;Ljava/lang/String;)Ljava/lang/String;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands457$lineTextBlueVarHead(NpcDialogueGraph.Node node, String text,
            CallbackInfoReturnable<String> cir) {
        if (node == null || text == null || text.indexOf('%') < 0) return;
        GuideNpc npc = CURRENT != null ? CURRENT.get() : null;
        VarScopeStore453.TextVarContext ctx = VarScopeStore453.peekTextContext();
        NpcDialogueGraph graph = ctx != null ? ctx.graph : null;
        if ((graph == null || graph.get(node.id) != node) && npc != null) {
            NpcProfile profile = profileOf(npc);
            if (profile != null) {
                for (NpcDialogueGraph g : graphs(profile)) {
                    if (g != null && g.get(node.id) == node) {
                        graph = g;
                        break;
                    }
                }
            }
        }
        if (graph != null && VarScopeStore453.resolveBlueVarNode(graph, node) != null) {
            MinecraftServer srv = ctx != null && ctx.server != null ? ctx.server
                    : (npc != null && npc.level() != null ? npc.level().getServer() : null);
            String out = VarScopeStore453.substituteVarTokens(
                    text, srv, ctx != null ? ctx.player : null, npc,
                    ctx != null ? ctx.where : null, graph, node);
            cir.setReturnValue(out);
        }
    }

    @Inject(method = "lineText(Ldev/settledlands/npc/NpcDialogueGraph$Node;Ljava/lang/String;)Ljava/lang/String;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private static void settledlands457$lineTextVarsReturn(NpcDialogueGraph.Node node, String text,
            CallbackInfoReturnable<String> cir) {
        String cur = cir.getReturnValue();
        if (cur == null || cur.indexOf('%') < 0) return;
        GuideNpc npc = CURRENT != null ? CURRENT.get() : null;
        VarScopeStore453.TextVarContext ctx = VarScopeStore453.peekTextContext();
        NpcDialogueGraph graph = ctx != null ? ctx.graph : null;
        if ((graph == null || (node != null && graph.get(node.id) != node)) && npc != null && node != null) {
            NpcProfile profile = profileOf(npc);
            if (profile != null) {
                for (NpcDialogueGraph g : graphs(profile)) {
                    if (g != null && g.get(node.id) == node) {
                        graph = g;
                        break;
                    }
                }
            }
        }
        MinecraftServer srv = ctx != null && ctx.server != null ? ctx.server
                : (npc != null && npc.level() != null ? npc.level().getServer() : null);
        String out = VarScopeStore453.substituteVarTokens(
                cur, srv, ctx != null ? ctx.player : null, npc,
                ctx != null ? ctx.where : null, graph, node);
        if (!out.equals(cur)) {
            cir.setReturnValue(out);
        }
    }

    @Inject(method = "triggerBlocked(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands457$allowBlueVarTrigger(GuideNpc npc, NpcDialogueGraph graph,
            List triggers, Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (PENDING != null && Boolean.TRUE.equals(PENDING.get())) return;
        if (graph == null || graph.nodes == null || triggers == null || triggers.isEmpty()) return;
        for (NpcDialogueGraph.Node n : graph.nodes.values()) {
            if (n != null && n.type == NpcDialogueGraph.TRIGGER && n.triggers == triggers) {
                if (VarScopeStore453.resolveBlueVarNode(graph, n) != null) {
                    cir.setReturnValue(Boolean.FALSE);
                }
                return;
            }
        }
    }
}
