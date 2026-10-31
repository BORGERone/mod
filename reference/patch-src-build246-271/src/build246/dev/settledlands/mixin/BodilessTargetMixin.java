package dev.settledlands.mixin;

import dev.settledlands.npc.NpcBodilessRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TargetingConditions.class)
public abstract class BodilessTargetMixin {
    @Inject(method="test(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z",at=@At("HEAD"),cancellable=true)
    private void settledlands$ignoreBodiless(LivingEntity observer,LivingEntity target,CallbackInfoReturnable<Boolean> cir){
        NpcBodilessRules.rejectTarget(observer,target,cir);
    }
}
