package dev.settledlands.mixin;

import dev.settledlands.npc.GuideDialogue;
import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcDamageTrace346;
import dev.settledlands.npc.NpcDialogueGraph;
import java.util.Map;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 346: the rest of the damage trace - which graph the chain walks, and what each damage node
 * of that graph decides.
 *
 * <p>{@code handleNpcDamaged} is the single place that turns a hit into a trigger, and it is called
 * for the NPC's own tab, for the walk graph, for the animation graph and for the common graph, so
 * one hook covers all four. It only reads: nothing is cancelled and nothing is skipped.</p>
 */
@Mixin(targets = "dev.settledlands.npc.GuideDialogue", remap = false)
public abstract class NpcDamageTraceChainMixin346 {

    @Inject(method = "handleNpcDamaged(Ldev/settledlands/npc/GuideNpc;"
            + "Lnet/minecraft/world/damagesource/DamageSource;FLjava/util/Map;"
            + "Ldev/settledlands/npc/NpcDialogueGraph;)V",
            at = @At("HEAD"), remap = false)
    private static void settledlands$traceChain(GuideNpc npc, DamageSource source, float amount,
            Map<Integer, Long> cooldowns, NpcDialogueGraph graph, CallbackInfo ci) {
        NpcDamageTrace346.chain(npc, source, amount, cooldowns, graph);
    }
}
