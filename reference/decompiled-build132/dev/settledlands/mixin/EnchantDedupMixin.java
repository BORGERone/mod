package dev.settledlands.mixin;

import dev.settledlands.EnchantDedup;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.BannerItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({EnchantmentMenu.class})
public abstract class EnchantDedupMixin {
   @Inject(
      method = {"slotsChanged"},
      at = {@At("TAIL")}
   )
   private void settledlands$dedup(Container container, CallbackInfo ci) {
      EnchantmentMenu menu = (EnchantmentMenu)this;
      if (menu.getSlot(0).getItem().getItem() instanceof BannerItem) {
         boolean[] blanks = EnchantDedup.blanks(menu.costs, menu.enchantClue, menu.levelClue);

         for (int i = 0; i < blanks.length; i++) {
            if (blanks[i]) {
               menu.costs[i] = 0;
               menu.enchantClue[i] = -1;
               menu.levelClue[i] = -1;
            }
         }
      }
   }
}
