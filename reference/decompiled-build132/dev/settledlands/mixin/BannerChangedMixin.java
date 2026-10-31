package dev.settledlands.mixin;

import dev.settledlands.Sanctity;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({BlockEntity.class})
public abstract class BannerChangedMixin {
   @Inject(
      method = {"setChanged()V"},
      at = {@At("TAIL")}
   )
   private void settledlands$indexBanner(CallbackInfo ci) {
      if (this instanceof BannerBlockEntity banner) {
         Sanctity.changed(banner);
      }
   }
}
