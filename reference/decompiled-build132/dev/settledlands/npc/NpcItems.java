package dev.settledlands.npc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class NpcItems {
   public static final List<String> IDS = List.of(
      "minecraft:emerald",
      "minecraft:diamond",
      "minecraft:gold_ingot",
      "minecraft:iron_ingot",
      "minecraft:copper_ingot",
      "minecraft:coal",
      "minecraft:charcoal",
      "minecraft:redstone",
      "minecraft:lapis_lazuli",
      "minecraft:quartz",
      "minecraft:amethyst_shard",
      "minecraft:wheat",
      "minecraft:bread",
      "minecraft:apple",
      "minecraft:carrot",
      "minecraft:potato",
      "minecraft:beetroot",
      "minecraft:leather",
      "minecraft:string",
      "minecraft:bone",
      "minecraft:flint",
      "minecraft:feather",
      "minecraft:torch",
      "minecraft:book",
      "minecraft:paper",
      "minecraft:iron_sword",
      "minecraft:iron_pickaxe",
      "minecraft:iron_axe",
      "minecraft:bow",
      "minecraft:arrow",
      "minecraft:shield",
      "minecraft:stick"
   );
   private static List<ItemStack> everyStack;

   private NpcItems() {
   }

   public static ItemStack stack(String var0, int var1) {
      if (var0 != null && !var0.isEmpty()) {
         ResourceLocation var2 = ResourceLocation.tryParse(var0);
         if (var2 == null) {
            return ItemStack.EMPTY;
         } else {
            Item var3 = (Item)BuiltInRegistries.ITEM.get(var2);
            return var3 != null && var3 != Items.AIR ? new ItemStack(var3, Math.max(1, Math.min(64, var1))) : ItemStack.EMPTY;
         }
      } else {
         return ItemStack.EMPTY;
      }
   }

   public static String idOf(ItemStack var0) {
      return var0 != null && !var0.isEmpty() ? BuiltInRegistries.ITEM.getKey(var0.getItem()).toString() : "";
   }

   public static int indexOf(ItemStack var0) {
      return IDS.indexOf(idOf(var0));
   }

   public static List<ItemStack> allStacks() {
      if (everyStack == null) {
         ArrayList var0 = new ArrayList();

         for (Item var2 : BuiltInRegistries.ITEM) {
            ItemStack var3 = new ItemStack(var2);
            if (!var3.isEmpty()) {
               var0.add(var3);
            }
         }

         everyStack = List.copyOf(var0);
      }

      return everyStack;
   }

   public static List<ItemStack> filter(List<ItemStack> var0, String var1) {
      String var2 = var1 == null ? "" : var1.trim().toLowerCase(Locale.ROOT);
      if (var2.isEmpty()) {
         return var0;
      } else {
         ArrayList var3 = new ArrayList();

         for (ItemStack var5 : var0) {
            if (idOf(var5).contains(var2) || var5.getHoverName().getString().toLowerCase(Locale.ROOT).contains(var2)) {
               var3.add(var5);
            }
         }

         return var3;
      }
   }
}
