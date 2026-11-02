package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcDamageTrace346;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 346: the first line of the damage trace - the hit arrived at the NPC.
 *
 * <p>{@code GuideNpc#hurt} runs the damage chains itself and only then calls the stock body, so this
 * hook proves whether the chain was entered at all. A hit that never reaches here means the attack
 * was stopped before the NPC (another mod cancelling it, an invulnerable body, a click that never
 * became an attack) and no graph can do anything about it.</p>
 */
@Mixin(targets = "dev.settledlands.npc.GuideNpc", remap = false)
public abstract class NpcDamageTraceHurtMixin346 {

    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD"), remap = false)
    private void settledlands$traceHurt(DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        NpcDamageTrace346.hurt((GuideNpc) (Object) this, source, amount);
    }
}
