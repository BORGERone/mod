package dev.settledlands.mixin;

import dev.settledlands.npc.NpcDamageFilter345;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 345: a damage node fires for the source the author picked.
 *
 * <p>{@code GuideDialogue.matchesDamageFilter} is the only place that answers "does this damage match
 * the node's source", and both the damage trigger and the damage value node ask it. It read the four
 * editor rows in a different order than the dropdown and the node card use, so "От игрока (Player)"
 * was tested as "от монстра" and never fired for a player's hit. The call is answered from
 * {@link NpcDamageFilter345} instead, which keeps the numbering the screen shows.</p>
 *
 * <p>One hook, at the head of one private static method, and the stock body is never reached: there is
 * nothing to cancel conditionally, and no shape here that Mixin could refuse.</p>
 */
@Mixin(targets = "dev.settledlands.npc.GuideDialogue", remap = false)
public abstract class NpcDamageFilterMixin345 {

    @Inject(method = "matchesDamageFilter(ILnet/minecraft/world/damagesource/DamageSource;)Z",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$damageFilter(int filter, DamageSource source,
            CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(NpcDamageFilter345.matches(filter, source));
    }
}
