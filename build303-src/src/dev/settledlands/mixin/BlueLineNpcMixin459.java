package dev.settledlands.mixin;

import dev.settledlands.npc.BlueLineTarget459;
import dev.settledlands.npc.CutsceneCamera;
import dev.settledlands.npc.GuideDialogue;
import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.VarScopeStore453;
import dev.settledlands.npc.VarScopeStore453.NpcResolveFrame;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 459: distinguishes autonomous/non-player event flows ({@code TIMER}, {@code TIME},
 * non-player {@code CHECK}, non-player {@code DAMAGE}) inside {@link GuideDialogue} from
 * player-targeted flows, and redirects {@code LINE}/{@code CUTSCENE} recipients to the player
 * resolved by an attached blue entity wire (or suppresses delivery when none exists). Zero lambdas.
 */
@Mixin(value = GuideDialogue.class, remap = false)
public abstract class BlueLineNpcMixin459 {

    @Unique
    private static final ThreadLocal<Boolean> CHECK_NON_PLAYER = new ThreadLocal<>();

    @Shadow
    private static int resolveExecutionTarget(ServerPlayer player, GuideNpc npc,
            GuideDialogue.Session session, NpcDialogueGraph graph, int startId, Set visited) {
        throw new AssertionError();
    }

    @Shadow
    private static void startAtLine(ServerPlayer player, GuideNpc npc, int lineId, Set visited) {
        throw new AssertionError();
    }

    @Shadow
    private static void triggerCheckChain(GuideNpc npc, ServerPlayer player,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node) {
        throw new AssertionError();
    }

    @Shadow
    private static void triggerDamageChain(GuideNpc npc, ServerPlayer player,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node, boolean playerWasAttacker) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean initiatorExecutesWithRadiusGate(ServerPlayer player, GuideNpc npc,
            List attachments) {
        throw new AssertionError();
    }

    @Unique
    private static int settledlands$dispatchResolvedChain459(ServerPlayer player, GuideNpc npc,
            GuideDialogue.Session session, NpcDialogueGraph graph, int startId, Set visited) {
        BlueLineTarget459.pushResolvedSlot();
        int target;
        ServerPlayer resolved;
        try {
            target = resolveExecutionTarget(player, npc, session, graph, startId, visited);
        } finally {
            resolved = BlueLineTarget459.popResolvedPlayer();
        }
        if (target >= 0 && resolved != null
                && (resolved != player || BlueLineTarget459.isNoEventTarget())) {
            if (npc != null && npc.profile() != null && npc.profile().dialogueGraph == graph) {
                startAtLine(resolved, npc, target, visited);
            }
            return -1;
        }
        return target;
    }

    // ---- Autonomous TIMER / TIME context ----

    @Inject(method = "tickGraphTimerNodes(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/Map;)V",
            at = @At("HEAD"), remap = false)
    private static void settledlands$enterTimer459(GuideNpc npc, NpcDialogueGraph graph,
            Map cooldowns, CallbackInfo ci) {
        BlueLineTarget459.pushNoEventTarget(true);
    }

    @Inject(method = "tickGraphTimerNodes(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/Map;)V",
            at = @At("RETURN"), remap = false)
    private static void settledlands$exitTimer459(GuideNpc npc, NpcDialogueGraph graph,
            Map cooldowns, CallbackInfo ci) {
        BlueLineTarget459.popNoEventTarget();
    }

    @Inject(method = "tickGraphTimeNodes(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/Map;)V",
            at = @At("HEAD"), remap = false)
    private static void settledlands$enterTime459(GuideNpc npc, NpcDialogueGraph graph,
            Map lastDayFired, CallbackInfo ci) {
        BlueLineTarget459.pushNoEventTarget(true);
    }

    @Inject(method = "tickGraphTimeNodes(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/Map;)V",
            at = @At("RETURN"), remap = false)
    private static void settledlands$exitTime459(GuideNpc npc, NpcDialogueGraph graph,
            Map lastDayFired, CallbackInfo ci) {
        BlueLineTarget459.popNoEventTarget();
    }

    // ---- CHECK node: mark non-player hits as no-event-target ----

    @Inject(method = "tickGraphCheckNodes(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/Map;)V",
            at = @At("HEAD"), remap = false)
    private static void settledlands$enterCheck459(GuideNpc npc, NpcDialogueGraph graph,
            Map cooldowns, CallbackInfo ci) {
        CHECK_NON_PLAYER.remove();
    }

    @Redirect(method = "tickGraphCheckNodes(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/Map;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;getNearestPlayer(Lnet/minecraft/world/entity/Entity;D)Lnet/minecraft/world/entity/player/Player;"),
            remap = false)
    private static Player settledlands$markNonPlayerCheck459(ServerLevel level, Entity entity,
            double range) {
        CHECK_NON_PLAYER.set(Boolean.TRUE);
        return level.getNearestPlayer(entity, range);
    }

    @Redirect(method = "tickGraphCheckNodes(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/Map;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideDialogue;triggerCheckChain(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V"),
            remap = false)
    private static void settledlands$wrapCheckChain459(GuideNpc npc, ServerPlayer player,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node) {
        boolean nonPlayer = Boolean.TRUE.equals(CHECK_NON_PLAYER.get());
        CHECK_NON_PLAYER.remove();
        BlueLineTarget459.pushNoEventTarget(nonPlayer);
        try {
            triggerCheckChain(npc, player, graph, node);
        } finally {
            BlueLineTarget459.popNoEventTarget();
        }
    }

    @Inject(method = "tickGraphCheckNodes(Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/Map;)V",
            at = @At("RETURN"), remap = false)
    private static void settledlands$exitCheck459(GuideNpc npc, NpcDialogueGraph graph,
            Map cooldowns, CallbackInfo ci) {
        CHECK_NON_PLAYER.remove();
    }

    // ---- DAMAGE node: mark non-player damage sources as no-event-target ----

    @Redirect(method = "handleNpcDamaged(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/world/damagesource/DamageSource;FLjava/util/Map;Ldev/settledlands/npc/NpcDialogueGraph;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideDialogue;triggerDamageChain(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Z)V"),
            remap = false)
    private static void settledlands$wrapDamageChain459(GuideNpc npc, ServerPlayer player,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node, boolean playerWasAttacker) {
        BlueLineTarget459.pushNoEventTarget(!playerWasAttacker);
        try {
            triggerDamageChain(npc, player, graph, node, playerWasAttacker);
        } finally {
            BlueLineTarget459.popNoEventTarget();
        }
    }

    // ---- RADIUS (15) fan-out: explicitly finds target entities, so restore normal targeting ----

    @Inject(method = "expandRadiusAttachments(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ljava/util/List;Ljava/util/Set;)V",
            at = @At("HEAD"), remap = false)
    private static void settledlands$enterRadiusExpand459(ServerPlayer player, GuideNpc npc,
            NpcDialogueGraph graph, NpcDialogueGraph.Node sourceNode, List radiusIds, Set visited,
            CallbackInfo ci) {
        BlueLineTarget459.pushNoEventTarget(false);
    }

    @Inject(method = "expandRadiusAttachments(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ljava/util/List;Ljava/util/Set;)V",
            at = @At("RETURN"), remap = false)
    private static void settledlands$exitRadiusExpand459(ServerPlayer player, GuideNpc npc,
            NpcDialogueGraph graph, NpcDialogueGraph.Node sourceNode, List radiusIds, Set visited,
            CallbackInfo ci) {
        BlueLineTarget459.popNoEventTarget();
    }

    @Redirect(method = "resolveExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideDialogue;resolveExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I"),
            remap = false)
    private static int settledlands$radiusFanOutPlayerBlue459(ServerPlayer player, GuideNpc npc,
            GuideDialogue.Session session, NpcDialogueGraph graph, int startId, Set visited) {
        BlueLineTarget459.pushNoEventTarget(false);
        try {
            return resolveExecutionTarget(player, npc, session, graph, startId, visited);
        } finally {
            BlueLineTarget459.popNoEventTarget();
        }
    }

    @Redirect(method = "resolveExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideDialogue;initiatorExecutesWithRadiusGate(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ljava/util/List;)Z"),
            remap = false)
    private static boolean settledlands$trackAttachedRadiusGate459(ServerPlayer player,
            GuideNpc npc, List attachments) {
        boolean ok = initiatorExecutesWithRadiusGate(player, npc, attachments);
        if (ok && attachments != null && !attachments.isEmpty()) {
            NpcResolveFrame f = VarScopeStore453.peekResolveFrame();
            if (f != null) {
                f.nodeHasAttachedRadiusPlayerGate = true;
            }
        }
        return ok;
    }

    // ---- Redirect startAtLine recipient in trigger*Chain when blue wire resolved a specific player ----

    @Redirect(method = "triggerTimerChain(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideDialogue;resolveExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I"),
            remap = false)
    private static int settledlands$dispatchTimerBlue459(ServerPlayer player, GuideNpc npc,
            GuideDialogue.Session session, NpcDialogueGraph graph, int startId, Set visited) {
        return settledlands$dispatchResolvedChain459(player, npc, session, graph, startId, visited);
    }

    @Redirect(method = "triggerTimeChain(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideDialogue;resolveExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I"),
            remap = false)
    private static int settledlands$dispatchTimeBlue459(ServerPlayer player, GuideNpc npc,
            GuideDialogue.Session session, NpcDialogueGraph graph, int startId, Set visited) {
        return settledlands$dispatchResolvedChain459(player, npc, session, graph, startId, visited);
    }

    @Redirect(method = "triggerCheckChain(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideDialogue;resolveExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I"),
            remap = false)
    private static int settledlands$dispatchCheckBlue459(ServerPlayer player, GuideNpc npc,
            GuideDialogue.Session session, NpcDialogueGraph graph, int startId, Set visited) {
        return settledlands$dispatchResolvedChain459(player, npc, session, graph, startId, visited);
    }

    @Redirect(method = "triggerDamageChain(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Z)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideDialogue;resolveExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I"),
            remap = false)
    private static int settledlands$dispatchDamageBlue459(ServerPlayer player, GuideNpc npc,
            GuideDialogue.Session session, NpcDialogueGraph graph, int startId, Set visited) {
        return settledlands$dispatchResolvedChain459(player, npc, session, graph, startId, visited);
    }

    // ---- CUTSCENE in resolveExecutionTarget ----

    @Redirect(method = "resolveExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/CutsceneCamera;play(Lnet/minecraft/server/level/ServerPlayer;Ljava/lang/String;)Z"),
            remap = false)
    private static boolean settledlands$gateCutsceneBlue459(ServerPlayer player, String name) {
        if (BlueLineTarget459.isNoEventTarget()) {
            NpcResolveFrame f = VarScopeStore453.peekResolveFrame();
            if (f == null || (!f.hasSession && !f.radiusTargetedInitiator
                    && !f.nodeHasAttachedRadiusPlayerGate)) {
                if (f == null || f.activeNode == null || f.npc == null || f.graph == null) {
                    return false;
                }
                ServerPlayer bluePlayer = BlueLineTarget459.resolveNpcLineBluePlayer(
                        f.npc, f.graph, f.activeNode);
                if (bluePlayer == null) {
                    return false;
                }
                return CutsceneCamera.play(bluePlayer, name);
            }
        }
        return CutsceneCamera.play(player, name);
    }
}
