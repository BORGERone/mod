package dev.settledlands.mixin;

import dev.settledlands.npc.GuideNpc;
import dev.settledlands.npc.NpcCommonGraph334;
import java.util.Map;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 347: a hit runs the damage nodes of the shared graph <em>as well as</em> the NPC's own.
 *
 * <p>Build 334 hooked the shared {@code GuideDialogue.handleNpcDamaged} and cancelled it for the
 * common graph. That one method is what all three stock chains go through - the NPC's own tab, the
 * walk tab and the animation tab - so a world that had a shared graph lost the damage nodes of all
 * three at once. The trace of build 346 shows it exactly: one hit, and the three calls that reach the
 * chain all carry the shared graph ({@code nodes=2 enabled=false entry=-1 damage=0}), while the NPC's
 * own tab - {@code nodes=5 damage=1}, with the node the author was looking at - is never walked. A
 * shared graph that nobody authored therefore switched the whole feature off, silently.</p>
 *
 * <p>The shared graph is now one more chain, not a replacement: this hook runs it once per hit from
 * {@code GuideNpc.hurt}, next to the three chains the stock body runs by itself. Nothing is
 * cancelled, an empty shared graph costs one iteration over its nodes, and a damage node fires
 * whichever of the four tabs it was put in.</p>
 */
@Mixin(targets = "dev.settledlands.npc.GuideNpc", remap = false)
public abstract class NpcCommonGraphDamageMixin347 {

    /** The NPC's own damage cooldowns; the shared chain uses the same map, keyed by node id. */
    @Shadow
    private Map<Integer, Long> damageCooldowns;

    @Inject(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At("HEAD"), remap = false)
    private void settledlands$damageCommon(DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        NpcCommonGraph334.damage((GuideNpc) (Object) this, source, amount, this.damageCooldowns);
    }
}
