package dev.settledlands.mixin;

import dev.settledlands.Sanctity;
import dev.settledlands.ritual.RitualWard;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour.BlockStateBase;
import net.minecraft.world.level.storage.loot.LootParams.Builder;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BlockStateBase.class})
public abstract class BannerDropsMixin {
   @Inject(
      method = {"getDrops"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void settledlands$retainSanctity(Builder context, CallbackInfoReturnable<List<ItemStack>> cir) {
      if (context.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof BannerBlockEntity banner) {
         if (RitualWard.noDrop(banner, context.getLevel().getGameTime())) {
            cir.setReturnValue(List.of());
            return;
         }

         for (ItemStack stack : (List)cir.getReturnValue()) {
            Sanctity.copyEnchantments(banner, stack);
         }
      }
   }
}
