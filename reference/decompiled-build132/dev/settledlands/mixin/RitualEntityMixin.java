package dev.settledlands.mixin;

import dev.settledlands.ritual.RitualManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Entity.class})
public abstract class RitualEntityMixin {
   @Inject(
      method = {"move"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void settledlands$freeze(MoverType type, Vec3 delta, CallbackInfo ci) {
      Entity e = (Entity)this;
      if (e.level() instanceof ServerLevel && RitualManager.active(e)) {
         e.setDeltaMovement(Vec3.ZERO);
         ci.cancel();
      }
   }

   @Inject(
      method = {"thunderHit"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void settledlands$protectReplacement(ServerLevel level, LightningBolt bolt, CallbackInfo ci) {
      if (bolt.getPersistentData().getBoolean("settledlands_ritual_bolt")
         && bolt.getPersistentData().hasUUID("settledlands_ritual_replacement")
         && bolt.getPersistentData().getUUID("settledlands_ritual_replacement").equals(((Entity)this).getUUID())) {
         ci.cancel();
      }
   }
}
