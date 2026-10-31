package dev.settledlands.mixin;

import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Item.class})
public abstract class BannerEnchantabilityMixin {
   @Inject(
      method = {"isEnchantable"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void settledlands$enchantable(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
      if (this instanceof BannerItem) {
         cir.setReturnValue(stack.getCount() == 1);
      }
   }

   @Inject(
      method = {"getEnchantmentValue()I"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void settledlands$value(CallbackInfoReturnable<Integer> cir) {
      if (this instanceof BannerItem) {
         cir.setReturnValue(10);
      }
   }
}
