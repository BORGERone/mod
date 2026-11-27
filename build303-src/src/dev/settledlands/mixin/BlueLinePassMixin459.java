package dev.settledlands.mixin;

import dev.settledlands.npc.BlockTalk358;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.BlueLineTarget459;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.RadiusPass354;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 459: marks {@link RadiusPass354#timerEvents}, {@link RadiusPass354#timeEvents}, and
 * non-player {@link RadiusPass354#check} dispatches into {@link BlockTrigger351#fire} as
 * autonomous/no-event-target so dialogue/button/cutscene nodes require an explicit blue entity
 * wire, and allows block timers/time nodes to run for blue-wired targets beyond the default
 * 32-block range. Zero lambdas.
 */
@Mixin(value = RadiusPass354.class, remap = false)
public abstract class BlueLinePassMixin459 {

    @Unique
    private static final ThreadLocal<NpcDialogueGraph> TIMER_GRAPH = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<NpcDialogueGraph> TIME_GRAPH = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Boolean> CHECK_NON_PLAYER = new ThreadLocal<>();

    @Shadow
    private static ServerPlayer nearestPlayer(ServerLevel level, RadiusPass354.Spot block,
            double range, List players) {
        throw new AssertionError();
    }

    // ---- timerEvents ----

    @Inject(method = "timerEvents(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;)V",
            at = @At("HEAD"), remap = false)
    private static void settledlands$enterTimerEvents459(ServerLevel level,
            RadiusPass354.Spot spot, NpcDialogueGraph graph, List players, CallbackInfo ci) {
        TIMER_GRAPH.set(graph);
    }

    @Inject(method = "timerEvents(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;)V",
            at = @At("RETURN"), remap = false)
    private static void settledlands$exitTimerEvents459(ServerLevel level,
            RadiusPass354.Spot spot, NpcDialogueGraph graph, List players, CallbackInfo ci) {
        TIMER_GRAPH.remove();
    }

    @Redirect(method = "timerEvents(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/RadiusPass354;nearestPlayer(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;DLjava/util/List;)Lnet/minecraft/server/level/ServerPlayer;"),
            remap = false)
    private static ServerPlayer settledlands$timerNearestOrBlue459(ServerLevel level,
            RadiusPass354.Spot spot, double range, List players) {
        ServerPlayer found = nearestPlayer(level, spot, range, players);
        if (found != null && !found.isSpectator()) {
            return found;
        }
        ServerPlayer blue = BlueLineTarget459.findReachableBluePlayerForAutonomousStart(
                level, spot, TIMER_GRAPH.get());
        return blue != null ? blue : found;
    }

    @Redirect(method = "timerEvents(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/BlockTrigger351;fire(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ldev/settledlands/npc/BlockTalk358$Where;)Z"),
            remap = false)
    private static boolean settledlands$fireTimerNoTarget459(ServerPlayer player,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node, BlockTalk358.Where where) {
        BlueLineTarget459.pushNoEventTarget(true);
        try {
            return BlockTrigger351.fire(player, graph, node, where);
        } finally {
            BlueLineTarget459.popNoEventTarget();
        }
    }

    // ---- timeEvents ----

    @Inject(method = "timeEvents(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;)V",
            at = @At("HEAD"), remap = false)
    private static void settledlands$enterTimeEvents459(ServerLevel level,
            RadiusPass354.Spot spot, NpcDialogueGraph graph, List players, CallbackInfo ci) {
        TIME_GRAPH.set(graph);
    }

    @Inject(method = "timeEvents(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;)V",
            at = @At("RETURN"), remap = false)
    private static void settledlands$exitTimeEvents459(ServerLevel level,
            RadiusPass354.Spot spot, NpcDialogueGraph graph, List players, CallbackInfo ci) {
        TIME_GRAPH.remove();
    }

    @Redirect(method = "timeEvents(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/RadiusPass354;nearestPlayer(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;DLjava/util/List;)Lnet/minecraft/server/level/ServerPlayer;"),
            remap = false)
    private static ServerPlayer settledlands$timeNearestOrBlue459(ServerLevel level,
            RadiusPass354.Spot spot, double range, List players) {
        ServerPlayer found = nearestPlayer(level, spot, range, players);
        if (found != null && !found.isSpectator()) {
            return found;
        }
        ServerPlayer blue = BlueLineTarget459.findReachableBluePlayerForAutonomousStart(
                level, spot, TIME_GRAPH.get());
        return blue != null ? blue : found;
    }

    @Redirect(method = "timeEvents(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/BlockTrigger351;fire(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ldev/settledlands/npc/BlockTalk358$Where;)Z"),
            remap = false)
    private static boolean settledlands$fireTimeNoTarget459(ServerPlayer player,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node, BlockTalk358.Where where) {
        BlueLineTarget459.pushNoEventTarget(true);
        try {
            return BlockTrigger351.fire(player, graph, node, where);
        } finally {
            BlueLineTarget459.popNoEventTarget();
        }
    }

    // ---- check ----

    @Inject(method = "check(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;J)V",
            at = @At("HEAD"), remap = false)
    private static void settledlands$enterCheckEvents459(ServerLevel level,
            RadiusPass354.Spot spot, NpcDialogueGraph graph, List players, long now,
            CallbackInfo ci) {
        CHECK_NON_PLAYER.remove();
    }

    @Redirect(method = "check(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;J)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/RadiusPass354;nearestPlayer(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;DLjava/util/List;)Lnet/minecraft/server/level/ServerPlayer;"),
            remap = false)
    private static ServerPlayer settledlands$markNonPlayerCheck459(ServerLevel level,
            RadiusPass354.Spot spot, double range, List players) {
        CHECK_NON_PLAYER.set(Boolean.TRUE);
        return nearestPlayer(level, spot, range, players);
    }

    @Redirect(method = "check(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;J)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/BlockTrigger351;fire(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ldev/settledlands/npc/BlockTalk358$Where;)Z"),
            remap = false)
    private static boolean settledlands$fireCheckWithFlag459(ServerPlayer player,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node, BlockTalk358.Where where) {
        boolean nonPlayer = Boolean.TRUE.equals(CHECK_NON_PLAYER.get());
        CHECK_NON_PLAYER.remove();
        BlueLineTarget459.pushNoEventTarget(nonPlayer);
        try {
            return BlockTrigger351.fire(player, graph, node, where);
        } finally {
            BlueLineTarget459.popNoEventTarget();
        }
    }

    @Inject(method = "check(Lnet/minecraft/server/level/ServerLevel;Ldev/settledlands/npc/RadiusPass354$Spot;Ldev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;J)V",
            at = @At("RETURN"), remap = false)
    private static void settledlands$exitCheckEvents459(ServerLevel level,
            RadiusPass354.Spot spot, NpcDialogueGraph graph, List players, long now,
            CallbackInfo ci) {
        CHECK_NON_PLAYER.remove();
    }
}
