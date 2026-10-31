package dev.settledlands.npc;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public class GuideTomeItem extends Item {
   private static final String KEY_PHRASE = "phrase";
   private static final String KEY_NUMBER = "number";
   public static Consumer<ItemStack> editor = var0 -> {};

   public GuideTomeItem(Properties var1) {
      super(var1);
   }

   public static GuidePhrase phraseOf(ItemStack var0) {
      if (var0 != null && !var0.isEmpty()) {
         CustomData var1 = (CustomData)var0.get(DataComponents.CUSTOM_DATA);
         if (var1 == null) {
            return null;
         } else {
            CompoundTag var2 = var1.copyTag();
            String var3 = var2.getString("phrase").trim();
            return var3.isEmpty() ? null : new GuidePhrase(var2.getInt("number"), var3);
         }
      } else {
         return null;
      }
   }

   public static void write(ItemStack var0, int var1, String var2) {
      CompoundTag var3 = new CompoundTag();
      var3.putString("phrase", var2);
      var3.putInt("number", var1);
      var0.set(DataComponents.CUSTOM_DATA, CustomData.of(var3));
      var0.set(DataComponents.ITEM_NAME, Component.literal(String.valueOf(var1)));
      var0.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
   }

   public static String sanitize(String var0) {
      if (var0 == null) {
         return "";
      } else {
         String var1 = var0.replace('\n', ' ').replace('\r', ' ').replaceAll("[\\p{Cntrl}]", " ").replaceAll("\\s{2,}", " ").trim();
         return var1.length() > 480 ? var1.substring(0, 480) : var1;
      }
   }

   public InteractionResultHolder<ItemStack> use(Level var1, Player var2, InteractionHand var3) {
      ItemStack var4 = var2.getItemInHand(var3);
      if (var1.isClientSide()) {
         editor.accept(var4);
      }

      return InteractionResultHolder.sidedSuccess(var4, var1.isClientSide());
   }

   public static void sign(ServerPlayer var0, GuideNet.Sign var1) {
      InteractionHand var2 = var1.offhand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
      ItemStack var3 = var0.getItemInHand(var2);
      if (var3.getItem() instanceof GuideTomeItem) {
         String var4 = sanitize(var1.text());
         if (!var4.isEmpty()) {
            int var5 = Math.max(1, Math.min(999999, var1.number()));
            write(var3, var5, var4);
            var0.level().playSound(null, var0.getX(), var0.getY(), var0.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.9F, 1.1F);
            var0.displayClientMessage(
               Component.translatable("message.settledlands.tome.signed", new Object[]{Component.literal(String.valueOf(var5))})
                  .withStyle(ChatFormatting.LIGHT_PURPLE),
               true
            );
         }
      }
   }

   public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
      GuidePhrase var5 = phraseOf(var1);
      if (var5 == null) {
         var3.add(Component.translatable("tooltip.settledlands.tome.blank").withStyle(ChatFormatting.GRAY));
         var3.add(Component.translatable("tooltip.settledlands.tome.how").withStyle(ChatFormatting.DARK_GRAY));
      } else {
         var3.add(
            Component.translatable(
                  "tooltip.settledlands.tome.number", new Object[]{Component.literal(String.valueOf(var5.number())).withStyle(ChatFormatting.AQUA)}
               )
               .withStyle(ChatFormatting.GRAY)
         );
         var3.add(Component.literal("“" + var5.text() + "”").withStyle(ChatFormatting.DARK_PURPLE));
         var3.add(Component.translatable("tooltip.settledlands.tome.give").withStyle(ChatFormatting.DARK_GRAY));
      }
   }
}
