package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcCommonGraph334;
import dev.settledlands.npc.NpcDialogueGraph;
import java.util.Map;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 334: a damaged NPC runs the "Общее" graph, like it runs it on tick.
 *
 * <p>{@code GuideNpc#hurt} hands the NPC's own tab to {@code GuideDialogue.handleNpcDamaged}, which
 * walks the damage chain of it (play an animation, answer, open a dialogue). With the tab common,
 * that chain belongs to the shared graph as well, otherwise a shared "when damaged" row would only
 * ever fire for the one NPC it was edited from.</p>
 *
 * <p>The hook hands the call to {@link NpcCommonGraph334#damage}, which runs the same method with
 * the common graph and an entry guard, so the re-entering call falls through to the stock body.
 * When no common graph has been saved yet the stock body runs untouched.</p>
 */
@Mixin(targets = "dev.settledlands.npc.GuideDialogue", remap = false)
public abstract class NpcCommonGraphDamageMixin334 {

    @Inject(method = "handleNpcDamaged(Ldev/settledlands/npc/GuideNpc;"
            + "Lnet/minecraft/world/damagesource/DamageSource;FLjava/util/Map;"
            + "Ldev/settledlands/npc/NpcDialogueGraph;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$damageCommon(GuideNpc npc, DamageSource source, float amount,
            Map<Integer, Long> cooldowns, NpcDialogueGraph graph, CallbackInfo ci) {
        if (NpcCommonGraph334.damage(npc, source, amount, cooldowns)) ci.cancel();
    }
}
