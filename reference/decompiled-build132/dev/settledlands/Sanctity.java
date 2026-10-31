package dev.settledlands;

import dev.settledlands.ritual.RitualWard;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent.Load;
import net.neoforged.neoforge.event.level.ChunkEvent.Unload;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;

public final class Sanctity {
   public static final ResourceKey<Enchantment> KEY = ResourceKey.create(
      Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath("settledlands", "sanctity")
   );
   private static final Map<ServerLevel, Long2ObjectOpenHashMap<LongOpenHashSet>> INDEX = new IdentityHashMap<>();
   private static final int SCAN_THRESHOLD = 1024;
   private static final int BURN_INTERVAL = 10;
   private final LongOpenHashSet burnCells = new LongOpenHashSet();

   public static boolean holy(BannerBlockEntity var0) {
      ItemEnchantments var1 = (ItemEnchantments)var0.components().getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

      for (Entry var3 : var1.entrySet()) {
         if (var3.getIntValue() > 0 && ((Holder)var3.getKey()).is(KEY)) {
            return true;
         }
      }

      return false;
   }

   public static int level(BannerBlockEntity var0) {
      ItemEnchantments var1 = (ItemEnchantments)var0.components().getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

      for (Entry var3 : var1.entrySet()) {
         if (((Holder)var3.getKey()).is(KEY)) {
            return Math.max(0, var3.getIntValue());
         }
      }

      return 0;
   }

   public static boolean inflammable(LivingEntity var0) {
      return SanctityFire.extinguished(var0.getRemainingFireTicks())
         && var0.isAlive()
         && !var0.isSpectator()
         && !var0.fireImmune()
         && !var0.isInWaterRainOrBubble()
         && (var0.getType().getCategory() == MobCategory.MONSTER || var0.getType().is(EntityTypeTags.UNDEAD) || var0 instanceof Monster);
   }

   public static void changed(BannerBlockEntity var0) {
      if (var0.getLevel() instanceof ServerLevel var1) {
         if (!var1.getServer().isSameThread()) {
            var1.getServer().execute(() -> changed(var0));
         } else {
            long var6 = Cell.at(var0.getBlockPos());
            Long2ObjectOpenHashMap var4 = INDEX.computeIfAbsent(var1, var0x -> new Long2ObjectOpenHashMap());
            if (holy(var0) && !var0.isRemoved()) {
               ((LongOpenHashSet)var4.computeIfAbsent(var6, var0x -> new LongOpenHashSet())).add(var0.getBlockPos().asLong());
            } else {
               LongOpenHashSet var5 = (LongOpenHashSet)var4.get(var6);
               if (var5 != null) {
                  var5.remove(var0.getBlockPos().asLong());
                  if (var5.isEmpty()) {
                     var4.remove(var6);
                  }
               }
            }
         }
      }
   }

   public static int count(ServerLevel var0, long var1) {
      Long2ObjectOpenHashMap var3 = INDEX.get(var0);
      if (var3 == null) {
         return 0;
      } else {
         LongOpenHashSet var4 = (LongOpenHashSet)var3.get(var1);
         if (var4 != null && var0.hasChunkAt(Cell.min(var1))) {
            int var5 = 0;
            LongIterator var6 = var4.iterator();

            while (var6.hasNext()) {
               if (var0.getBlockEntity(BlockPos.of(var6.nextLong())) instanceof BannerBlockEntity var7 && !var7.isRemoved() && holy(var7)) {
                  var5++;
               } else {
                  var6.remove();
               }
            }

            if (var4.isEmpty()) {
               var3.remove(var1);
            }

            return var5;
         } else {
            return 0;
         }
      }
   }

   public static int countFire(ServerLevel var0, long var1) {
      Long2ObjectOpenHashMap var3 = INDEX.get(var0);
      if (var3 == null) {
         return 0;
      } else {
         LongOpenHashSet var4 = (LongOpenHashSet)var3.get(var1);
         if (var4 != null && var0.hasChunkAt(Cell.min(var1))) {
            int var5 = 0;
            LongIterator var6 = var4.iterator();

            while (var6.hasNext()) {
               long var7 = var6.next();
               if (var0.getBlockEntity(BlockPos.of(var7)) instanceof BannerBlockEntity var10 && !var10.isRemoved() && SanctityFire.burns(level(var10))) {
                  var5++;
               }
            }

            return var5;
         } else {
            return 0;
         }
      }
   }

   @SubscribeEvent
   public void burning(Post var1) {
      if ((Boolean)Settings.SANCTITY_FIRE.get() && var1.getServer().getTickCount() % 10 == 0) {
         for (ServerLevel var3 : var1.getServer().getAllLevels()) {
            this.burnLevel(var3);
         }
      }
   }

   public void burnLevel(ServerLevel var1) {
      Long2ObjectOpenHashMap var2 = INDEX.get(var1);
      if (var2 != null && !var2.isEmpty()) {
         ObjectIterator var3 = var2.long2ObjectEntrySet().iterator();

         while (var3.hasNext()) {
            it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry var4 = (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry)var3.next();
            BlockPos var5 = Cell.min(var4.getLongKey());
            if (var1.hasChunkAt(var5)) {
               this.burnCells.clear();
               LongIterator var6 = ((LongOpenHashSet)var4.getValue()).iterator();

               while (var6.hasNext()) {
                  long var7 = var6.nextLong();
                  if (var1.getBlockEntity(BlockPos.of(var7)) instanceof BannerBlockEntity var10 && !var10.isRemoved() && SanctityFire.burns(level(var10))) {
                     this.burnCells.add(var7);
                  }
               }

               if (!this.burnCells.isEmpty()) {
                  AABB var20 = new AABB(var5.getX() - 12, var5.getY() - 12, var5.getZ() - 12, var5.getX() + 28, var5.getY() + 20, var5.getZ() + 28);

                  for (LivingEntity var21 : var1.getEntitiesOfClass(LivingEntity.class, var20, Sanctity::inflammable)) {
                     BlockPos var22 = var21.blockPosition();
                     int var11 = var22.getX();
                     int var12 = var22.getY();
                     int var13 = var22.getZ();
                     var6 = this.burnCells.iterator();

                     while (var6.hasNext()) {
                        long var14 = var6.nextLong();
                        int var16 = BlockPos.getX(var14) - var11;
                        int var17 = BlockPos.getY(var14) - var12;
                        int var18 = BlockPos.getZ(var14) - var13;
                        if (var16 * var16 + var17 * var17 + var18 * var18 <= 144) {
                           var21.setRemainingFireTicks(120);
                           break;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static BlockPos ritualTarget(ServerLevel var0, BlockPos var1) {
      Long2ObjectOpenHashMap var2 = INDEX.get(var0);
      if (var2 != null && !var2.isEmpty()) {
         int var3 = var1.getX();
         int var4 = var1.getY();
         int var5 = var1.getZ();
         BlockPos[] var6 = new BlockPos[1];
         int[] var7 = new int[]{1025};
         if (var2.size() <= 1024) {
            ObjectIterator var8 = var2.long2ObjectEntrySet().iterator();

            while (var8.hasNext()) {
               it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry var9 = (it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry)var8.next();
               long var10 = var9.getLongKey();
               if (Math.abs(BlockPos.getX(var10) - (var3 >> 4)) <= 3
                  && Math.abs(BlockPos.getZ(var10) - (var5 >> 4)) <= 3
                  && Math.abs(BlockPos.getY(var10) - (var4 >> 3)) <= 5) {
                  consider(var0, (LongOpenHashSet)var9.getValue(), var3, var4, var5, var6, var7);
               }
            }
         } else {
            int var15 = var3 >> 4;
            int var16 = var4 >> 3;
            int var17 = var5 >> 4;

            for (int var11 = -3; var11 <= 3; var11++) {
               for (int var12 = -3; var12 <= 3; var12++) {
                  for (int var13 = -5; var13 <= 5; var13++) {
                     LongOpenHashSet var14 = (LongOpenHashSet)var2.get(Cell.key(var15 + var11, var16 + var13, var17 + var12));
                     if (var14 != null) {
                        consider(var0, var14, var3, var4, var5, var6, var7);
                     }
                  }
               }
            }
         }

         return var6[0];
      } else {
         return null;
      }
   }

   private static void consider(ServerLevel var0, LongOpenHashSet var1, int var2, int var3, int var4, BlockPos[] var5, int[] var6) {
      LongIterator var7 = var1.iterator();

      while (var7.hasNext()) {
         long var8 = var7.nextLong();
         int var10 = BlockPos.getX(var8) - var2;
         int var11 = BlockPos.getY(var8) - var3;
         int var12 = BlockPos.getZ(var8) - var4;
         int var13 = var10 * var10 + var11 * var11 + var12 * var12;
         if (var13 < var6[0]) {
            BlockPos var14 = BlockPos.of(var8);
            if (validRitualTarget(var0, var14)) {
               var6[0] = var13;
               var5[0] = var14;
            }
         }
      }
   }

   public static void copyEnchantments(BannerBlockEntity var0, ItemStack var1) {
      if (var1.getItem() instanceof BannerItem && holy(var0)) {
         ItemEnchantments var2 = (ItemEnchantments)var0.components().getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
         EnchantmentHelper.updateEnchantments(var1, var1x -> {
            for (Entry var3 : var2.entrySet()) {
               var1x.upgrade((Holder)var3.getKey(), var3.getIntValue());
            }
         });
      }
   }

   @SubscribeEvent
   public void loaded(Load var1) {
      if (var1.getLevel() instanceof ServerLevel var2 && var1.getChunk() instanceof LevelChunk var3) {
         Runnable var6 = () -> {
            removeChunk(var2, var3.getPos());

            for (BlockEntity var3x : var3.getBlockEntities().values()) {
               if (var3x instanceof BannerBlockEntity var4) {
                  changed(var4);
               }
            }
         };
         if (var2.getServer().isSameThread()) {
            var6.run();
         } else {
            var2.getServer().execute(var6);
         }
      }
   }

   private static void removeChunk(ServerLevel var0, ChunkPos var1) {
      Long2ObjectOpenHashMap var2 = INDEX.get(var0);
      if (var2 != null) {
         for (int var3 = Math.floorDiv(var0.getMinBuildHeight(), 8); var3 <= Math.floorDiv(var0.getMaxBuildHeight() - 1, 8); var3++) {
            var2.remove(Cell.key(var1.x, var3, var1.z));
         }
      }
   }

   @SubscribeEvent
   public void unloaded(Unload var1) {
      if (var1.getLevel() instanceof ServerLevel var2) {
         ChunkPos var4 = var1.getChunk().getPos();
         if (var2.getServer().isSameThread()) {
            removeChunk(var2, var4);
         } else {
            var2.getServer().execute(() -> removeChunk(var2, var4));
         }
      }
   }

   @SubscribeEvent
   public void levelUnloaded(net.neoforged.neoforge.event.level.LevelEvent.Unload var1) {
      if (var1.getLevel() instanceof ServerLevel var2) {
         INDEX.remove(var2);
      }
   }

   @SubscribeEvent
   public void stopped(ServerStoppedEvent var1) {
      INDEX.clear();
   }

   public static boolean validRitualTarget(ServerLevel var0, BlockPos var1) {
      return var1 != null
         && var0.hasChunkAt(var1)
         && var0.getBlockEntity(var1) instanceof BannerBlockEntity var2
         && !var2.isRemoved()
         && holy(var2)
         && !RitualWard.immune(var2, var0.getGameTime());
   }
}
