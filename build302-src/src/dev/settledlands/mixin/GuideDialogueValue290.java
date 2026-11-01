package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcValueSource290;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 290: what the blue value wires do on the server.
 *
 * <p>Four hooks, one per place the graph already does the work, so the stock engine keeps
 * running everything else:</p>
 *
 * <ul>
 *   <li><b>Урон</b> - every damage event goes through {@code handleNpcDamaged}; the attacker
 *       is remembered there, and the "Урон" node's blue output hands it out
 *       ({@code NpcDamageValue290});</li>
 *   <li><b>Условие</b> - not hooked (Build 297): the node compares its own variable,
 *       operator and number, exactly as the game does. The blue wire only says whose
 *       variable to read, and the chain already carries the body the check node found;</li>
 *   <li><b>Переменная</b> - reading a variable whose node reads a body answers 1 or 0, so
 *       the same IF can compare it like any other variable;</li>
 *   <li><b>Реплика</b> - {@code %target%}, {@code %target_kind%} and {@code %target_uuid%}
 *       in the line text become the body the node reads. The text is produced inside
 *       {@code sendGraph}, and the redirect sees the very node it is building, so the
 *       substitution can never land on another node's text;</li>
 *   <li><b>Триггер</b> - the node's commands are skipped unless the chain carries the body
 *       its input reads. The list is compared by identity - it is the list stored on the
 *       node - so no other node can be gated by mistake.</li>
 * </ul>
 *
 * <p>Every hook first asks whether the node even has a blue wire, and returns to the stock
 * behaviour when it does not: a graph without value wires runs exactly as it did before.</p>
 */
@Mixin(targets = "dev.settledlands.npc.GuideDialogue", remap = false)
public abstract class GuideDialogueValue290 {

    /** Урон: remember who hit the NPC, for the blue output of the "Урон" node. */
    @Inject(method = "handleNpcDamaged(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/world/damagesource/DamageSource;FLjava/util/Map;)V",
            at = @At("HEAD"), remap = false)
    private static void settledlands$recordDamage(GuideNpc npc, DamageSource source, float amount,
            Map<Integer, Long> cooldowns, CallbackInfo ci) {
        NpcValueSource290.recordDamage(npc, source, amount);
    }

    /** Переменная: a variable whose node reads a body answers 1 or 0. */
    @Inject(method = "readVarValue(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;Ljava/lang/String;ZZ)I",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$varFromValue(ServerPlayer player, GuideNpc npc, String name,
            boolean shared, boolean local, CallbackInfoReturnable<Integer> cir) {
        int value = NpcValueSource290.variablePresence(npc, name);
        if (value >= 0) cir.setReturnValue(value);
    }

    /**
     * Реплика: the dialogue view is built inside sendGraph, and the line text is produced
     * there - so the NPC has to be known while it runs. The hook is placed on the NPC's
     * own {@code relationTo(player)} call that sendGraph makes before it builds the view:
     * that call hands us the NPC and carries no package-private type, unlike sendGraph
     * itself, whose session parameter cannot be named from this package (Build 290 typed
     * it Object and the game refused to apply the mixin at startup).
     */
    @Redirect(method = "sendGraph(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/GuideNpc;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideNpc;relationTo(Lnet/minecraft/server/level/ServerPlayer;)I"),
            remap = false)
    private static int settledlands$enterSendGraph(GuideNpc npc, ServerPlayer player) {
        NpcValueSource290.enter(npc);
        return npc.relationTo(player);
    }

    /**
     * Реплика: the line text of the node the view is being built for. The redirect receives
     * the node itself, so only that node's tokens are substituted.
     */
    @Redirect(method = "sendGraph(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/GuideNpc;)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/NpcDialogueGraph$Node;lineFor(I)Ljava/lang/String;"),
            remap = false)
    private static String settledlands$lineText(NpcDialogueGraph.Node node, int relation) {
        return NpcValueSource290.lineText(node, node.lineFor(relation));
    }

    /** Триггер: the node's commands run only for the body its blue input reads. */
    @Inject(method = "executeGraphTriggers(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;ILdev/settledlands/npc/NpcDialogueGraph;Ljava/util/List;Ljava/util/Set;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$gateTrigger(ServerPlayer player, Entity entity, GuideNpc npc,
            int relation, NpcDialogueGraph graph, List<String> triggers, Set<Integer> seen,
            CallbackInfo ci) {
        if (NpcValueSource290.triggerBlocked(npc, graph, triggers, entity)) ci.cancel();
    }
}
