package dev.settledlands.mixin;

import dev.settledlands.client.RitualClient;
import net.minecraft.client.model.AbstractZombieModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AbstractZombieModel.class})
public abstract class ZombieRitualAnimationMixin {
   @Inject(
      method = {"setupAnim(Lnet/minecraft/world/entity/monster/Monster;FFFFF)V"},
      at = {@At("TAIL")}
   )
   private void settledlands$animate(Monster entity, float walk, float amount, float age, float yaw, float pitch, CallbackInfo ci) {
      RitualClient.apply((HumanoidModel<?>)this, entity, age);
   }
}
