package dev.settledlands.mixin;

import net.minecraft.world.entity.LightningBolt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LightningBolt.class})
public abstract class RitualLightningMixin {
   @Inject(
      method = {"spawnFire"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void settledlands$noBlockFire(int attempts, CallbackInfo ci) {
      if (((LightningBolt)this).getPersistentData().getBoolean("settledlands_ritual_bolt")) {
         ci.cancel();
      }
   }
}
