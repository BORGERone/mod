package dev.settledlands;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class SanctityTooltip {
   public static String descKey(int level) {
      return switch (level) {
         case 1 -> "enchantment.settledlands.sanctity.desc.1";
         case 2 -> "enchantment.settledlands.sanctity.desc.2";
         default -> null;
      };
   }

   @SubscribeEvent
   public void tooltip(ItemTooltipEvent event) {
      ItemStack stack = event.getItemStack();
      if (stack.getItem() instanceof BannerItem) {
         ItemEnchantments enchantments = (ItemEnchantments)stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

         for (Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            if (((Holder)entry.getKey()).is(Sanctity.KEY)) {
               String key = descKey(entry.getIntValue());
               if (key != null) {
                  event.getToolTip().add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
               }
            }
         }
      }
   }
}
