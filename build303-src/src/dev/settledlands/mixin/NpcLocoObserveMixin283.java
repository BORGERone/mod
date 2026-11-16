package dev.settledlands.mixin;

import dev.settledlands.model.NpcLocoDriver283;
import dev.settledlands.npc.GuideNpc;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Build 283: takes one locomotion sample of the NPC per rendered frame.
 *
 * <p>{@code syncFrom} is the one place the bridge runs per NPC per frame and it
 * has the NPC at hand, so the frame-rate driver is advanced from here. The two
 * reads the bridge does right after ({@code walkAnimation.position(partialTick)}
 * and {@code walkAnimation.speed(partialTick)}) then return this frame's values
 * instead of the tick-stepped mob ones.</p>
 *
 * <p>Only reads the NPC: no field of the proxy is written, nothing is synced.</p>
 *
 * <p>Build 285: the walk state field belongs to {@link LivingEntity}, not to
 * {@code Entity}. Build 283 cached it through an {@code Entity} reference and threw
 * {@code NoSuchFieldError} on every NPC render; the accessor here is typed
 * correctly.</p>
 */
@Mixin(targets = "dev.settledlands.model.NpcMotionPlayerProxy256", remap = false)
public abstract class NpcLocoObserveMixin283 {

    @Inject(method = "syncFrom", at = @At("TAIL"), remap = false)
    private void settledlands$observeLocomotion(GuideNpc npc, CallbackInfo ci) {
        if (npc == null) return;
        LivingEntity body = npc;
        LivingEntity proxy = (LivingEntity) (Object) this;
        NpcLocoDriver283.observe(body.walkAnimation, npc);
        NpcLocoDriver283.alias(body.walkAnimation, proxy.walkAnimation);
    }
}
