package dev.settledlands.npc;

import java.util.ArrayList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public final class NpcShop {
   private NpcShop() {
   }

   public static void send(ServerPlayer var0, GuideNpc var1) {
      if (var1 == null || var1.isEscorting()) return;
      NpcProfile var2 = var1.profile();
      int var3 = var1.relationTo(var0);
      ArrayList var4 = new ArrayList(var2.offers.size());

      for (int var5 = 0; var5 < var2.offers.size(); var5++) {
         NpcProfile.Offer var6 = var2.offers.get(var5);
         if (!var6.stack.isEmpty() && !var6.price.isEmpty()) {
            ItemStack var7 = var6.price.copyWithCount(var2.priceFor(var6.price.getCount(), var3));
            var4.add(new NpcNet.ShopEntry(var5, var6.stack.copy(), var7, var6.description, var6.minRelation));
         }
      }

      NpcNet.sendTo(var0, new NpcNet.ShopView(var1.getId(), var3, var2.maxPerPurchase, var4));
   }

   public static void buy(ServerPlayer var0, int var1, int var2, int var3) {
      if (GuideDialogue.isObserver(var0)) {
         return;
      }
      GuideNpc var4 = GuideNpc.find(var0, var1);
      if (var4 != null && var4.isEscorting()) return;
      if (var4 == null) {
         var0.displayClientMessage(Component.translatable("message.settledlands.shop.far"), true);
      } else {
         NpcProfile var5 = var4.profile();
         if (var2 >= 0 && var2 < var5.offers.size()) {
            int var6 = NpcProfile.clamp(var3, 1, NpcProfile.clamp(var5.maxPerPurchase, 1, 8));
            NpcProfile.Offer var7 = var5.offers.get(var2);
            if (!var7.stack.isEmpty() && !var7.price.isEmpty()) {
               int var8 = var4.relationTo(var0);
               if (var8 < var7.minRelation) {
                  var0.displayClientMessage(
                     Component.translatable("message.settledlands.shop.relation", new Object[]{Component.literal(String.valueOf(var7.minRelation))}), true
                  );
                  var0.level().playSound(null, var0.getX(), var0.getY(), var0.getZ(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.7F, 0.9F);
               } else {
                  int var9 = var5.priceFor(var7.price.getCount(), var8);
                  int var10 = var9 * var6;
                  int var11 = take(var0, var7.price.copyWithCount(1), var10);
                  if (var11 < var10) {
                     giveBack(var0, var7.price.copyWithCount(var11));
                     var0.displayClientMessage(
                        Component.translatable(
                           "message.settledlands.shop.poor",
                           new Object[]{var7.price.copyWithCount(var10).getHoverName(), Component.literal(String.valueOf(var10))}
                        ),
                        true
                     );
                     var0.level().playSound(null, var0.getX(), var0.getY(), var0.getZ(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.7F, 0.9F);
                  } else {
                     ItemStack var12 = var7.stack.copy();
                     var12.setCount(var7.stack.getCount() * var6);
                     if (!var0.getInventory().add(var12)) {
                        var0.drop(var12, false);
                     }

                     var0.containerMenu.broadcastChanges();
                     var0.displayClientMessage(
                        Component.translatable(
                           "message.settledlands.shop.bought",
                           new Object[]{var12.getHoverName(), Component.literal(String.valueOf(var12.getCount())), Component.literal(String.valueOf(var10))}
                        ),
                        true
                     );
                     var0.level().playSound(null, var0.getX(), var0.getY(), var0.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.7F, 1.1F);
                     send(var0, var4);
                  }
               }
            }
         }
      }
   }

   private static int take(ServerPlayer var0, ItemStack var1, int var2) {
      int var3 = var2;

      for (int var4 = 0; var4 < var0.getInventory().getContainerSize() && var3 > 0; var4++) {
         ItemStack var5 = var0.getInventory().getItem(var4);
         if (ItemStack.isSameItemSameComponents(var5, var1)) {
            int var6 = Math.min(var3, var5.getCount());
            var5.shrink(var6);
            var3 -= var6;
         }
      }

      return var2 - var3;
   }

   private static void giveBack(ServerPlayer var0, ItemStack var1) {
      if (!var1.isEmpty()) {
         if (!var0.getInventory().add(var1)) {
            var0.drop(var1, false);
         }
      }
   }
}
