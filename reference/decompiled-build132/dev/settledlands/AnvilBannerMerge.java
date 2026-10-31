package dev.settledlands;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.core.Holder;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments.Mutable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

public final class AnvilBannerMerge {
   @SubscribeEvent
   public void update(AnvilUpdateEvent event) {
      ItemStack left = event.getLeft();
      ItemStack right = event.getRight();
      if (left.getItem() instanceof BannerItem && left.getItem() == right.getItem()) {
         if (left.getCount() == 1 && right.getCount() == 1) {
            ItemEnchantments received = EnchantmentHelper.getEnchantmentsForCrafting(right);
            if (!received.isEmpty()) {
               Mutable merged = new Mutable(EnchantmentHelper.getEnchantmentsForCrafting(left));
               boolean changed = false;

               for (Entry<Holder<Enchantment>> entry : received.entrySet()) {
                  Holder<Enchantment> enchantment = (Holder<Enchantment>)entry.getKey();
                  int given = entry.getIntValue();
                  int current = merged.getLevel(enchantment);
                  int level = current == 0
                     ? given
                     : (current == given ? Math.min(current + 1, ((Enchantment)enchantment.value()).getMaxLevel()) : Math.max(current, given));
                  if (level > current) {
                     merged.set(enchantment, level);
                     changed = true;
                  }
               }

               if (changed) {
                  ItemEnchantments result = merged.toImmutable();
                  int cost = 0;

                  for (Entry<Holder<Enchantment>> entryx : result.entrySet()) {
                     cost += price((Holder<Enchantment>)entryx.getKey(), entryx.getIntValue());
                  }

                  ItemStack output = left.copy();
                  EnchantmentHelper.setEnchantments(output, result);
                  event.setOutput(output);
                  event.setCost(Math.max(1, cost));
                  event.setMaterialCost(0);
               }
            }
         }
      }
   }

   private static int price(Holder<Enchantment> enchantment, int level) {
      return enchantment.is(Sanctity.KEY)
         ? level >= 2 ? (Integer)Settings.COST_LEVEL_II.get() : (Integer)Settings.COST_LEVEL_I.get()
         : 1 + ((Enchantment)enchantment.value()).getAnvilCost() * level;
   }
}
