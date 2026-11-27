package dev.settledlands.mixin;

import dev.settledlands.npc.BlockTalk358;
import dev.settledlands.npc.BlockTrigger351;
import dev.settledlands.npc.BlueLineTarget459;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.VarScopeStore453;
import dev.settledlands.npc.VarScopeStore453.BlockWalkFrame;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Build 459: enforces strict blue-target recipient gating and redirection on {@code LINE},
 * {@code BUTTON}, and {@code CUTSCENE} nodes in {@link BlockTrigger351#walk} (blocks, doors,
 * chests, and trials) when triggered by autonomous/no-target events. Coordinates with
 * {@link VarExecutionBlockMixin453} through {@link BlockWalkFrame} so suppressed steps skip chat,
 * actions, attached triggers, windows/choices/cutscenes while allowing subsequent nodes along
 * {@code cur.next} to continue. Zero lambdas.
 */
@Mixin(value = BlockTrigger351.class, remap = false)
public abstract class BlueLineBlockMixin459 {

    @Shadow
    private static void say(ServerPlayer player, NpcDialogueGraph graph,
            NpcDialogueGraph.Node node) {
        throw new AssertionError();
    }

    @Shadow
    private static void fireTriggers(ServerPlayer player, NpcDialogueGraph graph,
            NpcDialogueGraph.Node node, Set visited) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean playCutscene(ServerPlayer player, NpcDialogueGraph graph,
            NpcDialogueGraph.Node node, BlockTalk358.Where where) {
        throw new AssertionError();
    }

    @Redirect(method = "walk(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;Ldev/settledlands/npc/BlockTalk358$Where;[Z)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/BlockTalk358;say(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ldev/settledlands/npc/BlockTalk358$Where;)Z"),
            remap = false)
    private static boolean settledlands$sayWindowBlue459(ServerPlayer player,
            NpcDialogueGraph graph, NpcDialogueGraph.Node cur, BlockTalk358.Where where) {
        BlockWalkFrame wf = VarScopeStore453.peekBlockWalkFrame();
        if (wf != null) {
            wf.blueLineOverride = null;
            wf.suppressStep = false;
        }
        ServerPlayer effective = BlueLineTarget459.resolveBlockDialogTarget(player, graph, cur, where);
        if (effective == null) {
            if (wf != null) wf.suppressStep = true;
            return false;
        }
        if (wf != null && effective != player) {
            wf.blueLineOverride = effective;
        }
        return BlockTalk358.say(effective, graph, cur, where);
    }

    @Redirect(method = "walk(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;Ldev/settledlands/npc/BlockTalk358$Where;[Z)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/BlockTrigger351;say(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;)V"),
            remap = false)
    private static void settledlands$sayChatBlue459(ServerPlayer player,
            NpcDialogueGraph graph, NpcDialogueGraph.Node cur) {
        BlockWalkFrame wf = VarScopeStore453.peekBlockWalkFrame();
        if (wf != null && wf.suppressStep) {
            return;
        }
        ServerPlayer eff = (wf != null && wf.blueLineOverride != null) ? wf.blueLineOverride : player;
        say(eff, graph, cur);
    }

    @Redirect(method = "walk(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;Ldev/settledlands/npc/BlockTalk358$Where;[Z)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/BlockTrigger351;fireTriggers(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ljava/util/Set;)V"),
            remap = false)
    private static void settledlands$fireTriggersBlue459(ServerPlayer player,
            NpcDialogueGraph graph, NpcDialogueGraph.Node node, Set visited) {
        BlockWalkFrame wf = VarScopeStore453.peekBlockWalkFrame();
        if (wf != null && node != null
                && (node.type == NpcDialogueGraph.LINE
                        || node.type == NpcDialogueGraph.BUTTON
                        || node.type == NpcDialogueGraph.CUTSCENE)) {
            boolean suppress = wf.suppressStep;
            ServerPlayer eff = wf.blueLineOverride != null ? wf.blueLineOverride : player;
            if (node.type == NpcDialogueGraph.LINE) {
                wf.suppressStep = false;
                wf.blueLineOverride = null;
            }
            if (suppress) {
                return;
            }
            player = eff;
        }
        fireTriggers(player, graph, node, visited);
    }

    @Redirect(method = "walk(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;Ldev/settledlands/npc/BlockTalk358$Where;[Z)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/BlockTalk358;choices(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ldev/settledlands/npc/BlockTalk358$Where;)Z"),
            remap = false)
    private static boolean settledlands$choicesBlue459(ServerPlayer player,
            NpcDialogueGraph graph, NpcDialogueGraph.Node cur, BlockTalk358.Where where) {
        BlockWalkFrame wf = VarScopeStore453.peekBlockWalkFrame();
        if (wf != null) {
            boolean suppress = wf.suppressStep;
            ServerPlayer eff = wf.blueLineOverride != null ? wf.blueLineOverride : player;
            wf.suppressStep = false;
            wf.blueLineOverride = null;
            if (suppress) return false;
            return BlockTalk358.choices(eff, graph, cur, where);
        }
        ServerPlayer effective = BlueLineTarget459.resolveBlockDialogTarget(player, graph, cur, where);
        if (effective == null) return false;
        return BlockTalk358.choices(effective, graph, cur, where);
    }

    @Redirect(method = "walk(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;Ldev/settledlands/npc/BlockTalk358$Where;[Z)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/BlockTrigger351;playCutscene(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph;Ldev/settledlands/npc/NpcDialogueGraph$Node;Ldev/settledlands/npc/BlockTalk358$Where;)Z"),
            remap = false)
    private static boolean settledlands$cutsceneBlue459(ServerPlayer player,
            NpcDialogueGraph graph, NpcDialogueGraph.Node cur, BlockTalk358.Where where) {
        BlockWalkFrame wf = VarScopeStore453.peekBlockWalkFrame();
        if (wf != null) {
            boolean suppress = wf.suppressStep;
            ServerPlayer eff = wf.blueLineOverride != null ? wf.blueLineOverride : player;
            wf.suppressStep = false;
            wf.blueLineOverride = null;
            if (suppress) return false;
            return playCutscene(eff, graph, cur, where);
        }
        ServerPlayer effective = BlueLineTarget459.resolveBlockDialogTarget(player, graph, cur, where);
        if (effective == null) return false;
        return playCutscene(effective, graph, cur, where);
    }
}
