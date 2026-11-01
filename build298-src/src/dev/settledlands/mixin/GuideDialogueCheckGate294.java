package dev.settledlands.mixin;

import dev.settledlands.npc.GuideDialogue;
import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcValueSource290;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Build 294: a check node that feeds a value wire must not swallow the "nobody" answer.
 *
 * <p>Reported chain: "В радиусе" #4 -&gt; "Условие" #5 with {@code #4 == 0}, True -&gt;
 * {@code say true}, False -&gt; {@code say false}. Somebody in the radius printed
 * {@code false}; nobody in the radius printed nothing at all.</p>
 *
 * <p>Why: the walk treats the node as a gate - when {@code checkProximityCondition} is
 * false it sets the next node to -1 on the spot, so the IF never ran with the "nobody"
 * answer and its {@code == 0} branch was unreachable.</p>
 *
 * <p>The redirect keeps the stock result whenever the check passes, and whenever the node
 * is not wired to a value consumer. Only a check that some node reads through a blue wire,
 * and that carries no action of its own, lets the walk continue while nobody is in the
 * radius - so the consumer sees 0 and its False chain runs.</p>
 */
@Mixin(targets = "dev.settledlands.npc.GuideDialogue", remap = false)
public abstract class GuideDialogueCheckGate294 {

    @Redirect(method = "resolveExecutionTarget(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;Ldev/settledlands/npc/GuideDialogue$Session;Ldev/settledlands/npc/NpcDialogueGraph;ILjava/util/Set;)I",
            at = @At(value = "INVOKE",
                    target = "Ldev/settledlands/npc/GuideDialogue;checkProximityCondition(Ldev/settledlands/npc/GuideNpc;Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/NpcDialogueGraph$Node;)Z"),
            remap = false)
    private static boolean settledlands$checkGate(GuideNpc npc, ServerPlayer player,
            NpcDialogueGraph.Node node) {
        if (GuideDialogue.checkProximityCondition(npc, player, node)) return true;
        return NpcValueSource290.passThroughWhenValue(npc, node);
    }
}
