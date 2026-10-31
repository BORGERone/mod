package dev.settledlands.npc;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;

public class NpcWandItem extends Item {
   public NpcWandItem(Properties var1) {
      super(var1);
   }

   public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
      var3.add(Component.translatable("tooltip.settledlands.npc_wand.use").withStyle(ChatFormatting.GRAY));
      var3.add(Component.translatable("tooltip.settledlands.npc_wand.op").withStyle(ChatFormatting.DARK_GRAY));
   }
}
