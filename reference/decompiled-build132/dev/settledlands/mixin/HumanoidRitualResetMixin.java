package dev.settledlands.mixin;

import dev.settledlands.client.RitualClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({HumanoidModel.class})
public abstract class HumanoidRitualResetMixin {
   @Inject(
      method = {"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"},
      at = {@At("HEAD")}
   )
   private void settledlands$reset(LivingEntity entity, float walk, float amount, float age, float yaw, float pitch, CallbackInfo ci) {
      if (entity instanceof Zombie) {
         RitualClient.reset((HumanoidModel<?>)this);
      }
   }
}
